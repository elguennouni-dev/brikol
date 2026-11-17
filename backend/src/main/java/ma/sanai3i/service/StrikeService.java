package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.domain.Permission;
import ma.sanai3i.domain.Role;
import ma.sanai3i.domain.Strike;
import ma.sanai3i.domain.User;
import ma.sanai3i.repo.StrikeRepository;
import ma.sanai3i.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/** Warnings on accounts. Three in 30 days suspend the account for 7 days (no new requests or offers). */
@Service
@Transactional
public class StrikeService {
    public static final Set<String> REASONS = Set.of("NO_SHOW", "LATE_CANCEL", "UNPAID", "CLIENT_ABSENT", "WARRANTY_IGNORED",
            "BAD_WORK", "BEHAVIOR", "OTHER");
    public static final int LIMIT = 3;
    public static final int WINDOW_DAYS = 30;
    public static final int SUSPENSION_DAYS = 7;

    private final StrikeRepository strikes;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;

    public StrikeService(StrikeRepository strikes, UserRepository users, NotificationService notifications, AuditService audit) {
        this.strikes = strikes;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
    }

    /** by = staff member, or null for an automatic strike. */
    public Strike give(Long userId, Long requestId, Long caseId, String reason, User by) {
        if (!REASONS.contains(reason)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION");
        }
        User u = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
        if (u.role == Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION");
        }
        Strike s = new Strike();
        s.userId = userId;
        s.requestId = requestId;
        s.caseId = caseId;
        s.reason = reason;
        s.createdBy = by == null ? null : by.id;
        strikes.save(s);
        long recent = recent(userId);
        notifications.notify(userId, "STRIKE_GIVEN", requestId, null, null, reason);
        if (recent >= LIMIT && !u.suspended()) {
            u.suspendedUntil = Instant.now().plus(SUSPENSION_DAYS, ChronoUnit.DAYS);
            notifications.notify(userId, "ACCOUNT_SUSPENDED", null, null, null, u.suspendedUntil.toString());
            audit.log(null, "Brikol", "ACCOUNT_SUSPENDED", "USER", userId, recent + " strikes");
        }
        if (by != null) {
            audit.logUser(by, "STRIKE_GIVEN", "USER", userId, reason);
        } else {
            audit.log(null, "Brikol", "STRIKE_GIVEN", "USER", userId, reason);
        }
        return s;
    }

    public void remove(Long strikeId, User by) {
        Strike s = strikes.findById(strikeId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND"));
        s.removed = true;
        User u = users.findById(s.userId).orElse(null);
        if (u != null && u.suspended() && recent(u.id) < LIMIT) {
            u.suspendedUntil = null;
        }
        audit.logUser(by, "STRIKE_REMOVED", "USER", s.userId, s.reason);
    }

    /** Active strikes in the last 30 days. */
    @Transactional(readOnly = true)
    public long recent(Long userId) {
        return strikes.countByUserIdAndRemovedFalseAndCreatedAtAfter(userId, Instant.now().minus(WINDOW_DAYS, ChronoUnit.DAYS));
    }

    /** Throws ACCOUNT_SUSPENDED while the user is suspended. */
    public static void requireActive(User u) {
        if (u != null && u.suspended()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED");
        }
    }

    static boolean handlesCases(User admin) {
        return admin.role == Role.ADMIN && !admin.blocked && (admin.superAdmin || admin.permissionSet().contains(Permission.REPORTS));
    }
}
