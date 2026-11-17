package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.common.Phones;
import ma.sanai3i.domain.Permission;
import ma.sanai3i.domain.Role;
import ma.sanai3i.domain.User;
import ma.sanai3i.repo.ArtisanProfileRepository;
import ma.sanai3i.repo.DeviceTokenRepository;
import ma.sanai3i.repo.JobRequestRepository;
import ma.sanai3i.repo.NotificationRepository;
import ma.sanai3i.repo.UserRepository;
import ma.sanai3i.security.AuthUser;
import ma.sanai3i.security.BlockList;
import ma.sanai3i.security.StaffAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The administration team. Only the owner (super admin) manages it: staff accounts are created here (never by
 * promoting an existing user) with the permissions they need. The owner cannot be changed by anyone else.
 */
@Service
@Transactional
public class StaffService {
    public record StaffRow(Long id, String fullName, String phone, boolean owner, List<String> permissions, boolean blocked,
                           Instant createdAt, Instant lastLoginAt) {
    }

    public record StaffInput(String fullName, String phone, String password, List<String> permissions) {
    }

    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UserRepository users;
    private final StaffAccess access;
    private final PasswordEncoder encoder;
    private final BlockList blockList;
    private final NotificationHub hub;
    private final AuditService audit;
    private final JobRequestRepository requests;
    private final ArtisanProfileRepository artisans;
    private final NotificationRepository notifications;
    private final DeviceTokenRepository devices;
    private final SecureRandom random = new SecureRandom();

    public StaffService(UserRepository users, StaffAccess access, PasswordEncoder encoder, BlockList blockList,
                        NotificationHub hub, AuditService audit, JobRequestRepository requests,
                        ArtisanProfileRepository artisans, NotificationRepository notifications, DeviceTokenRepository devices) {
        this.users = users;
        this.access = access;
        this.encoder = encoder;
        this.blockList = blockList;
        this.hub = hub;
        this.audit = audit;
        this.requests = requests;
        this.artisans = artisans;
        this.notifications = notifications;
        this.devices = devices;
    }

    @Transactional(readOnly = true)
    public List<StaffRow> list(AuthUser me) {
        access.owner(me);
        return users.findAll().stream().filter(u -> u.role == Role.ADMIN)
                .sorted(Comparator.comparing((User u) -> !u.superAdmin).thenComparing(u -> u.id))
                .map(StaffService::row).toList();
    }

    public StaffRow create(AuthUser me, StaffInput in) {
        User owner = access.owner(me);
        String phone = Phones.normalize(in.phone());
        if (users.existsByPhone(phone)) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_TAKEN");
        }
        if (in.fullName() == null || in.fullName().isBlank() || in.password() == null || in.password().length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION");
        }
        User u = new User();
        u.phone = phone;
        u.fullName = in.fullName().trim().length() > 120 ? in.fullName().trim().substring(0, 120) : in.fullName().trim();
        if (in.password() == null || in.password().length() < 8 || in.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_WEAK");
        }
        u.passwordHash = encoder.encode(in.password());
        u.role = Role.ADMIN;
        u.city = "Casablanca";
        u.permissions = encode(in.permissions());
        u.phoneVerified = true;
        users.save(u);
        audit.logUser(owner, "STAFF_CREATED", "USER", u.id, u.fullName + " · " + u.permissions);
        return row(u);
    }

    public StaffRow update(AuthUser me, Long id, StaffInput in) {
        User owner = access.owner(me);
        User u = target(owner, id);
        if (in.fullName() != null && !in.fullName().isBlank()) {
            u.fullName = in.fullName().trim();
        }
        if (in.permissions() != null) {
            u.permissions = encode(in.permissions());
        }
        audit.logUser(owner, "STAFF_UPDATED", "USER", u.id, u.fullName + " · " + u.permissions);
        return row(u);
    }

    public StaffRow setBlocked(AuthUser me, Long id, boolean blocked) {
        User owner = access.owner(me);
        User u = target(owner, id);
        u.blocked = blocked;
        u.blockedReason = blocked ? "staff" : null;
        if (blocked) {
            blockList.block(u.id);
                hub.disconnect(u.id);
        } else {
            blockList.unblock(u.id);
        }
        audit.logUser(owner, blocked ? "STAFF_BLOCKED" : "STAFF_UNBLOCKED", "USER", u.id, u.fullName);
        return row(u);
    }

    public String resetPassword(AuthUser me, Long id) {
        User owner = access.owner(me);
        User u = target(owner, id);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        u.passwordHash = encoder.encode(sb.toString());
        blockList.revokeTokens(u);
        hub.disconnect(u.id);
        audit.logUser(owner, "STAFF_PASSWORD_RESET", "USER", u.id, u.fullName);
        return sb.toString();
    }

    /** Takes the account out of the team: it becomes a normal client account again. */
    public void demote(AuthUser me, Long id) {
        User owner = access.owner(me);
        User u = target(owner, id);
        u.role = Role.CLIENT;
        u.permissions = null;
        blockList.revokeTokens(u);
        hub.disconnect(u.id);
        audit.logUser(owner, "STAFF_REMOVED", "USER", u.id, u.fullName);
    }

    /** Deletes a staff account that has no client or artisan history (otherwise use demote). */
    public void delete(AuthUser me, Long id) {
        User owner = access.owner(me);
        User u = target(owner, id);
        if (!requests.findByClientIdOrderByCreatedAtDesc(u.id).isEmpty() || artisans.findByUserId(u.id).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_HAS_DATA");
        }
        notifications.deleteByUserId(u.id);
        devices.deleteAll(devices.findByUserId(u.id));
        blockList.unblock(u.id);
        hub.disconnect(u.id);
        String name = u.fullName;
        users.delete(u);
        blockList.forget(u.id);
        audit.logUser(owner, "STAFF_DELETED", "USER", id, name);
    }

    private User target(User owner, Long id) {
        User u = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
        if (u.role != Role.ADMIN) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
        }
        if (u.id.equals(owner.id) || u.superAdmin) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_TARGET_OWNER");
        }
        return u;
    }

    private static String encode(Collection<String> names) {
        if (names == null) {
            return null;
        }
        Set<Permission> set = EnumSet.noneOf(Permission.class);
        for (String n : names) {
            try {
                set.add(Permission.valueOf(n.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION");
            }
        }
        return set.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private static StaffRow row(User u) {
        List<String> perms = u.superAdmin ? java.util.Arrays.stream(Permission.values()).map(Enum::name).toList()
                : u.permissionSet().stream().map(Enum::name).toList();
        return new StaffRow(u.id, u.fullName, u.phone, u.superAdmin, perms, u.blocked, u.createdAt, u.lastLoginAt);
    }
}
