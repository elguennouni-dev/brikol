package ma.sanai3i.repo;

import ma.sanai3i.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import ma.sanai3i.domain.Role;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);
    Optional<User> findByGoogleSub(String googleSub);
    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByBlockedTrue();

    long countByRole(Role role);

    long countByBlockedTrue();

    long countByCreatedAtAfter(Instant since);
}
