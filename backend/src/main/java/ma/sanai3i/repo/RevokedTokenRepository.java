package ma.sanai3i.repo;

import ma.sanai3i.domain.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
    List<RevokedToken> findByExpiresAtAfter(Instant now);

    @Modifying
    @Query("delete from RevokedToken t where t.expiresAt < :now")
    int deleteExpired(Instant now);
}
