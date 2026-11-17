package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.common.Phones;
import ma.sanai3i.domain.ArtisanProfile;
import ma.sanai3i.domain.Category;
import ma.sanai3i.domain.Role;
import ma.sanai3i.domain.User;
import ma.sanai3i.dto.Dto.AuthResponse;
import ma.sanai3i.dto.Dto.GoogleAuthRequest;
import ma.sanai3i.dto.Dto.ResetPasswordRequest;
import ma.sanai3i.security.RateLimiter;
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
    private final OtpService otp;
    private final RateLimiter limiter;
    private final SecureRandom random = new SecureRandom();
    private final ma.sanai3i.security.BlockList blockList;
    /** Compared against when the phone is unknown, so both cases take the same time (no account discovery). */
    private String dummyHash;

    public AuthService(UserRepository users, CategoryRepository categories, ArtisanProfileRepository artisans,
                       PasswordEncoder encoder, JwtService jwt, Mapper mapper,
                       AuditService audit, SettingsService settings, GoogleVerifier google,
                       OtpService otp, RateLimiter limiter, ma.sanai3i.security.BlockList blockList) {
        this.blockList = blockList;
        this.google = google;
        this.otp = otp;
        this.limiter = limiter;
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
        checkPassword(in.password());
        boolean verified = checkOtp(in.phone(), in.otp());
        User user = createAccount(in.phone(), in.fullName(), in.city(), in.role(), in.categoryCode(), in.neighborhood(),
                encoder.encode(in.password()), null, null);
        user.phoneVerified = verified;
        return issue(user);
    }

    /** When SMS is on, a new account needs the code sent to its phone. Returns true when the phone was verified. */
    private boolean checkOtp(String rawPhone, String code) {
        if (!otp.enabled()) {
            return false;
        }
        String phone = Phones.normalize(rawPhone);
        otp.verify(phone, OtpService.REGISTER, code);          // first: only the owner of the number gets further
        if (users.existsByPhone(phone)) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_TAKEN");
        }
        return true;
    }

    /** Forgotten password: SMS code, new password, then signed in. */
    public AuthResponse resetPassword(ResetPasswordRequest in) {
        if (!otp.enabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "SMS_DISABLED");
        }
        String phone = Phones.normalize(in.phone());
        otp.verify(phone, OtpService.RESET, in.otp());
        User user = users.findByPhone(phone).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID"));
        checkPassword(in.password());
        user.passwordHash = encoder.encode(in.password());
        blockList.revokeTokens(user);             // whoever had the old password is signed out
        user.phoneVerified = true;
        limiter.reset("loginfail:" + phone);
        audit.logUser(user, "PASSWORD_RESET_SMS", "USER", user.id, null);
        return loginChecked(user, "LOGIN");
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
        boolean verified = checkOtp(in.phone(), in.otp());
        // nobody knows this password: the account signs in with Google (an admin can still reset it)
        byte[] noise = new byte[24];
        random.nextBytes(noise);
        String name = g.name() == null || g.name().isBlank() ? email.substring(0, email.indexOf('@')) : g.name().trim();
        User created = createAccount(in.phone(), name.length() > 120 ? name.substring(0, 120) : name, null, in.role(),
                in.categoryCode(), in.neighborhood(), encoder.encode(Base64.getEncoder().encodeToString(noise)), email, g.sub());
        created.phoneVerified = verified;
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
        // Brute force without locking the real owner out: the limit is per number AND per address. Someone failing
        // from one address cannot block the owner signing in from another. If a number gets many failures from
        // many addresses, only the addresses that already failed are stopped.
        String ip = ma.sanai3i.security.RateLimitFilter.currentIp();
        String pairKey = "loginfail:" + phone + "|" + ip;
        String phoneKey = "loginfail:" + phone;
        int pairFails = limiter.count(pairKey, LOCK_WINDOW_MS);
        if (pairFails >= PAIR_LIMIT || (pairFails > 0 && limiter.count(phoneKey, LOCK_WINDOW_MS) >= NUMBER_LIMIT)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS");
        }
        if (in.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");    // BCrypt refuses longer input
        }
        User found = users.findByPhone(phone).orElse(null);
        if (found == null) {
            if (dummyHash == null) {
                dummyHash = encoder.encode("brikol-timing-" + random.nextInt());
            }
            encoder.matches(in.password(), dummyHash);
        }
        if (found == null || !encoder.matches(in.password(), found.passwordHash)) {
            limiter.record(pairKey);
            limiter.record(phoneKey);
            audit.log(found == null ? null : found.id, phone, "LOGIN_FAILED", "USER", found == null ? null : found.id, null);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }
        return loginChecked(found, "LOGIN");
    }

    static final long LOCK_WINDOW_MS = 15 * 60_000L;
    static final int PAIR_LIMIT = 8;
    static final int NUMBER_LIMIT = 30;

    /** The most used passwords (also in Morocco): refused even when long enough. */
    static final java.util.Set<String> COMMON = java.util.Set.of("12345678", "123456789", "1234567890", "123123123", "11111111",
            "00000000", "12121212", "87654321", "password", "password1", "passw0rd", "azerty123", "azertyuiop", "qwerty123",
            "qwertyuiop", "motdepasse", "abc12345", "iloveyou", "11223344", "123456789a", "a1234567", "maroc123", "morocco123",
            "casablanca", "marrakech", "brikol123", "sanai3i123", "bismillah", "00000000a", "12344321", "1q2w3e4r", "zxcvbnm123");

    /** At least 8 characters, not a well-known password, and at most 72 bytes (BCrypt ignores the rest). */
    static void checkPassword(String password) {
        if (password == null || password.length() < 8
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || COMMON.contains(password.toLowerCase(java.util.Locale.ROOT))
                || password.chars().distinct().count() < 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_WEAK");
        }
    }

    /** Signs out this device only. */
    public void logout(AuthUser me, ma.sanai3i.security.TokenRevocations revocations) {
        if (me != null) {
            revocations.revoke(me);
        }
    }

    /** Signs out every device (lost phone...). */
    public void logoutAll(AuthUser me) {
        User u = users.findById(me.userId()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNKNOWN_USER"));
        blockList.revokeTokens(u);
        audit.logUser(u, "LOGOUT_ALL", "USER", u.id, null);
    }

    @Transactional(readOnly = true)
    public UserDto me(AuthUser me) {
        User user = users.findById(me.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNKNOWN_USER"));
        return mapper.user(user, artisans.findByUserId(user.id).orElse(null));
    }
}
