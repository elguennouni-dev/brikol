package ma.sanai3i.seed;

import ma.sanai3i.domain.User;
import ma.sanai3i.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

/**
 * Older versions seeded demo accounts with the public password "demo1234". Outside the dev profile (no
 * app.seed-password), any demo account still using it gets a random password at start-up.
 */
@Component
@Order(0)
public class DemoPasswordGuard implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoPasswordGuard.class);
    static final String OLD_PASSWORD = "demo1234";
    static final List<String> DEMO_PHONES = List.of("+212600000001", "+212600000002", "+212600000003",
            "+212611000001", "+212611000002", "+212611000003", "+212611000004", "+212611000005",
            "+212611000006", "+212611000007", "+212611000008", "+212611000009");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String seedPassword;

    public DemoPasswordGuard(UserRepository users, PasswordEncoder encoder, @Value("${app.seed-password:}") String seedPassword) {
        this.users = users;
        this.encoder = encoder;
        this.seedPassword = seedPassword;
    }

    static String randomPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 24; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (OLD_PASSWORD.equals(seedPassword)) {
            return;                                   // dev profile: the demo logins are meant to work
        }
        int changed = 0;
        for (String phone : DEMO_PHONES) {
            User u = users.findByPhone(phone).orElse(null);
            if (u != null && encoder.matches(OLD_PASSWORD, u.passwordHash)) {
                u.passwordHash = encoder.encode(randomPassword());
                u.tokenVersion = u.tokenVersion + 1;
                changed++;
            }
        }
        if (changed > 0) {
            log.warn("{} demo account(s) had the public password: replaced by random ones", changed);
        }
    }
}
