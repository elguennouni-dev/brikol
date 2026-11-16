package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.common.Phones;
import ma.sanai3i.domain.ArtisanProfile;
import ma.sanai3i.domain.Category;
import ma.sanai3i.domain.Role;
import ma.sanai3i.domain.User;
import ma.sanai3i.dto.Dto.AuthResponse;
import ma.sanai3i.dto.Dto.LoginRequest;
import ma.sanai3i.dto.Dto.RegisterRequest;
import ma.sanai3i.dto.Dto.UserDto;
import ma.sanai3i.repo.ArtisanProfileRepository;
import ma.sanai3i.repo.CategoryRepository;
import ma.sanai3i.repo.UserRepository;
import ma.sanai3i.security.AuthUser;
import ma.sanai3i.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class AuthService {
    private final UserRepository users;
    private final CategoryRepository categories;
    private final ArtisanProfileRepository artisans;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Mapper mapper;
    private final AuditService audit;
    private final SettingsService settings;

    public AuthService(UserRepository users, CategoryRepository categories, ArtisanProfileRepository artisans,
                       PasswordEncoder encoder, JwtService jwt, Mapper mapper,
                       AuditService audit, SettingsService settings) {
        this.users = users;
        this.categories = categories;
        this.artisans = artisans;
        this.encoder = encoder;
        this.jwt = jwt;
        this.mapper = mapper;
        this.audit = audit;
        this.settings = settings;
    }

    public AuthResponse register(RegisterRequest in) {
        if (!settings.registrationsOpen()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REGISTRATIONS_CLOSED");
        }
        String phone = Phones.normalize(in.phone());
        if (users.existsByPhone(phone)) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_TAKEN");
        }
        // only CLIENT and ARTISAN can be chosen at sign-up: an admin is never created from the public form
        Role role = in.role() == Role.ARTISAN ? Role.ARTISAN : Role.CLIENT;

        Category category = null;
        if (role == Role.ARTISAN) {
            category = categories.findByCode(in.categoryCode() == null ? "" : in.categoryCode())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_CATEGORY"));
        }

        User user = new User();
        user.phone = phone;
        user.passwordHash = encoder.encode(in.password());
        user.fullName = in.fullName().trim();
        user.role = role;
        user.city = in.city().trim();
        users.save(user);

        ArtisanProfile profile = null;
        if (role == Role.ARTISAN) {
            profile = new ArtisanProfile();
            profile.user = user;
            profile.category = category;
            profile.nameAr = user.fullName;
            profile.nameFr = user.fullName;
            profile.tradeAr = category.nameAr;
            profile.tradeFr = category.nameFr;
            profile.neighborhoodAr = in.neighborhood();
            profile.neighborhoodFr = in.neighborhood();
            profile.city = user.city;
            profile.whatsapp = phone;
            artisans.save(profile);
        }
        audit.logUser(user, "REGISTER", "USER", user.id, role.name());
        return new AuthResponse(jwt.issue(user), mapper.user(user, profile));
    }

    public AuthResponse login(LoginRequest in) {
        String phone;
        try {
            phone = Phones.normalize(in.phone());
        } catch (ApiException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }
        User found = users.findByPhone(phone).orElse(null);
        if (found == null || !encoder.matches(in.password(), found.passwordHash)) {
            audit.log(found == null ? null : found.id, phone, "LOGIN_FAILED", "USER", found == null ? null : found.id, null);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }
        User user = found;
        if (user.blocked) {
            audit.logUser(user, "LOGIN_BLOCKED", "USER", user.id, user.blockedReason);
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_BLOCKED");
        }
        user.lastLoginAt = Instant.now();
        audit.logUser(user, "LOGIN", "USER", user.id, null);
        ArtisanProfile profile = artisans.findByUserId(user.id).orElse(null);
        return new AuthResponse(jwt.issue(user), mapper.user(user, profile));
    }

    @Transactional(readOnly = true)
    public UserDto me(AuthUser me) {
        User user = users.findById(me.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNKNOWN_USER"));
        return mapper.user(user, artisans.findByUserId(user.id).orElse(null));
    }
}
