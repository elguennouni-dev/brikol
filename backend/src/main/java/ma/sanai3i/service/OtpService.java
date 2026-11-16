package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.common.Phones;
import ma.sanai3i.domain.OtpCode;
import ma.sanai3i.repo.OtpCodeRepository;
import ma.sanai3i.repo.UserRepository;
import ma.sanai3i.sms.SmsSender;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 6-digit SMS codes: valid 10 minutes, 5 tries, one new code per minute and 5 per hour for a phone number.
 * When no SMS provider is configured the feature is off: sign-up works without code and "forgot password" is hidden.
 */
@Service
@Transactional
public class OtpService {
    public static final String REGISTER = "REGISTER";
    public static final String RESET = "RESET";
    private static final int MAX_TRIES = 5;

    private final OtpCodeRepository codes;
    private final UserRepository users;
    private final SmsSender sms;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpCodeRepository codes, UserRepository users, SmsSender sms, PasswordEncoder encoder) {
        this.codes = codes;
        this.users = users;
        this.sms = sms;
        this.encoder = encoder;
    }

    public boolean enabled() {
        return sms.enabled();
    }

    /** Sends a code. For RESET an unknown number gets no SMS but the same answer (no account enumeration). */
    public void send(String rawPhone, String purpose, String lang) {
        if (!enabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "SMS_DISABLED");
        }
        String phone = Phones.normalize(rawPhone);
        String p = RESET.equals(purpose) ? RESET : REGISTER;
        boolean exists = users.existsByPhone(phone);
        if (REGISTER.equals(p) && exists) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_TAKEN");
        }
        Instant now = Instant.now();
        OtpCode last = codes.findFirstByPhoneOrderByIdDesc(phone).orElse(null);
        if (last != null && last.createdAt.isAfter(now.minus(60, ChronoUnit.SECONDS))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_WAIT");
        }
        if (codes.countByPhoneAndCreatedAtAfter(phone, now.minus(1, ChronoUnit.HOURS)) >= 5) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_TOO_MANY");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        OtpCode o = new OtpCode();
        o.phone = phone;
        o.purpose = p;
        o.codeHash = encoder.encode(code);
        o.expiresAt = now.plus(10, ChronoUnit.MINUTES);
        codes.save(o);
        if (RESET.equals(p) && !exists) {
            return;
        }
        String text = "fr".equals(lang)
                ? "Brikol : votre code est " + code + ". Ne le partagez avec personne."
                : "Brikol: الكود ديالك هو " + code + ". ما تعطيه لحتى واحد.";
        sms.send(phone, text);
    }

    /**
     * Checks and consumes a code. Runs in its own transaction so that a wrong try is counted even when the
     * caller fails afterwards.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void verify(String phone, String purpose, String code) {
        if (code == null || code.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_REQUIRED");
        }
        OtpCode o = codes.findFirstByPhoneAndPurposeAndUsedFalseOrderByIdDesc(phone, purpose)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID"));
        if (o.expiresAt.isBefore(Instant.now()) || o.attempts >= MAX_TRIES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_EXPIRED");
        }
        o.attempts = o.attempts + 1;
        if (!encoder.matches(code.trim(), o.codeHash)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID");
        }
        o.used = true;
    }
}
