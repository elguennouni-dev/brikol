package ma.sanai3i.repo;

import ma.sanai3i.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    java.util.Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserId(Long userId);

    void deleteByToken(String token);
}
