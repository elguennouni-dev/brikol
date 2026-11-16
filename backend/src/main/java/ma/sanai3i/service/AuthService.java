package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.common.Phones;
import ma.sanai3i.domain.ArtisanProfile;
import ma.sanai3i.domain.Category;
import ma.sanai3i.domain.Role;
import ma.sanai3i.domain.User;
import ma.sanai3i.dto.Dto.AuthResponse;
import ma.sanai3i.dto.Dto.GoogleAuthRequest;
import ma.sanai3i.dto.Dto.LoginRequest;
import ma.sanai3i.dto.Dto.RegisterRequest;
import ma.sanai3i.dto.Dto.UserDto;
import ma.sanai3i.repo.ArtisanProfileRepository;
import ma.sanai3i.repo.CategoryRepository;
import ma.sanai3i.repo.UserRepository;
import ma.sanai3i.security.AuthUser;
import ma.sanai3i.security.GoogleVerifier;
import ma.sanai3i.security.GoogleVerifier.GoogleIdentity;
import ma.sanai3i.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

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
    private final GoogleVerifier google;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, CategoryRepository categories, ArtisanProfileRepository artisans,
                       PasswordEncoder encoder, JwtService jwt, Mapper mapper,
                       AuditService audit, SettingsService settings, GoogleVerifier google) {
        this.google = google;
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
        User user = createAccount(in.phone(), in.fullName(), in.city(), in.role(), in.categoryCode(), in.neighborhood(),
                encoder.encode(in.password()), null, null);
        return issue(user);
    }

    /**
     * "Sign in with Google". Known Google account (or same verified e-mail): logged in. New account: the first call
     * answers 428 GOOGLE_SIGNUP_NEEDED and the app asks for the phone number and the role, then calls again.
     */
    public AuthResponse google(GoogleAuthRequest in) {
        GoogleIdentity g = google.verify(in.credential());
        if (g.sub() == null || g.email() == null || !g.emailVerified()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "GOOGLE_INVALID");
        }
        String email = g.email().trim().toLowerCase(Locale.ROOT);
        User user = users.findByGoogleSub(g.sub()).orElse(null);
        if (user == null) {
            user = users.findByEmailIgnoreCase(email).orElse(null);
            if (user != null) {
                user.googleSub = g.sub();
                audit.logUser(user, "GOOGLE_LINKED", "USER", user.id, email);
            }
        }
        if (user != null) {
            return loginChecked(user, "LOGIN_GOOGLE");
        }
        if (in.phone() == null || in.phone().isBlank() || in.role() == null) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "GOOGLE_SIGNUP_NEEDED");
        }
        // nobody knows this password: the account signs in with Google (an admin can still reset it)
        byte[] noise = new byte[24];
        random.nextBytes(noise);
        String name = g.name() == null || g.name().isBlank() ? email.substring(0, email.indexOf('@')) : g.name().trim();
        User created = createAccount(in.phone(), name.length() > 120 ? name.substring(0, 120) : name, null, in.role(),
                in.categoryCode(), in.neighborhood(), encoder.encode(Base64.getEncoder().encodeToString(noise)), email, g.sub());
        return issue(created);
    }

    private User createAccount(String rawPhone, String fullName, String city, Role wanted, String categoryCode,
                               String neighborhood, String passwordHash, String email, String googleSub) {
        if (!settings.registrationsOpen()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REGISTRATIONS_CLOSED");
        }
        String phone = Phones.normalize(rawPhone);
        if (users.existsByPhone(phone)) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_TAKEN");
        }
        // only CLIENT and ARTISAN can be chosen at sign-up: an admin is never created from the public form
        Role role = wanted == Role.ARTISAN ? Role.ARTISAN : Role.CLIENT;

        Category category = null;
        if (role == Role.ARTISAN) {
            category = categories.findByCode(categoryCode == null ? "" : categoryCode)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_CATEGORY"));
        }

        User user = new User();
        user.phone = phone;
        user.passwordHash = passwordHash;
        user.fullName = fullName.trim();
        user.role = role;
        user.city = city == null || city.isBlank() ? "Casablanca" : city.trim();
        user.email = email;
        user.googleSub = googleSub;
        user.lastLoginAt = Instant.now();
        users.save(user);

        if (role == Role.ARTISAN) {
            ArtisanProfile profile = new ArtisanProfile();
            profile.user = user;
            profile.category = category;
            profile.nameAr = user.fullName;
            profile.nameFr = user.fullName;
            profile.tradeAr = category.nameAr;
            profile.tradeFr = category.nameFr;
            profile.neighborhoodAr = neighborhood;
            profile.neighborhoodFr = neighborhood;
            profile.city = user.city;
            profile.whatsapp = phone;
            artisans.save(profile);
        }
        audit.logUser(user, googleSub == null ? "REGISTER" : "REGISTER_GOOGLE", "USER", user.id, role.name());
        return user;
    }

    private AuthResponse issue(User user) {
        return new AuthResponse(jwt.issue(user), mapper.user(user, artisans.findByUserId(user.id).orElse(null)));
    }

    private AuthResponse loginChecked(User user, String action) {
        if (user.blocked) {
            audit.logUser(user, "LOGIN_BLOCKED", "USER", user.id, user.blockedReason);
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_BLOCKED");
        }
        user.lastLoginAt = Instant.now();
        audit.logUser(user, action, "USER", user.id, null);
        return issue(user);
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
        return loginChecked(found, "LOGIN");
    }

    @Transactional(readOnly = true)
    public UserDto me(AuthUser me) {
        User user = users.findById(me.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNKNOWN_USER"));
        return mapper.user(user, artisans.findByUserId(user.id).orElse(null));
    }
}
