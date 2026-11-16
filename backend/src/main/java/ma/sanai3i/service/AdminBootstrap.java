package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.common.Phones;
import ma.sanai3i.domain.Role;
import ma.sanai3i.domain.User;
import ma.sanai3i.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator at start-up from ADMIN_PHONE / ADMIN_PASSWORD. Nothing happens when they are not set
 * or when the phone already has an account (an existing password is never overwritten).
 */
@Component
@org.springframework.core.annotation.Order(2)
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String phone;
    private final String password;
    private final String name;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder,
                          @Value("${app.admin.phone:}") String phone,
                          @Value("${app.admin.password:}") String password,
                          @Value("${app.admin.name:Administrateur}") String name) {
        this.users = users;
        this.encoder = encoder;
        this.phone = phone;
        this.password = password;
        this.name = name;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (phone.isBlank() || password.isBlank()) {
            ownerFallback();
            return;
        }
        String normalized;
        try {
            normalized = Phones.normalize(phone);
        } catch (ApiException e) {
            log.warn("ADMIN_PHONE is not a valid Moroccan number, no administrator created");
            return;
        }
        User existing = users.findByPhone(normalized).orElse(null);
        if (existing != null) {
            // the number in ADMIN_PHONE is the owner of the platform
            if (existing.role != Role.ADMIN || !existing.superAdmin) {
                existing.role = Role.ADMIN;
                existing.superAdmin = true;
                log.info("{} is the owner (super admin)", normalized);
            }
            return;
        }
        User admin = new User();
        admin.phone = normalized;
        admin.passwordHash = encoder.encode(password);
        admin.fullName = name;
        admin.role = Role.ADMIN;
        admin.superAdmin = true;
        admin.city = "Casablanca";
        users.save(admin);
        log.info("Administrator account created for {}", normalized);
    }

    /** Without ADMIN_PHONE: the oldest administrator becomes the owner, so the team can always be managed. */
    private void ownerFallback() {
        boolean hasOwner = users.findAll().stream().anyMatch(u -> u.role == Role.ADMIN && u.superAdmin);
        if (!hasOwner) {
            users.findAll().stream().filter(u -> u.role == Role.ADMIN).min(java.util.Comparator.comparing(u -> u.id))
                    .ifPresent(u -> u.superAdmin = true);
        }
    }
}
