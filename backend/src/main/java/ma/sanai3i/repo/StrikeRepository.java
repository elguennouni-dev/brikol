package ma.sanai3i.repo;

import ma.sanai3i.domain.Strike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface StrikeRepository extends JpaRepository<Strike, Long> {
    long countByUserIdAndRemovedFalseAndCreatedAtAfter(Long userId, Instant after);

    List<Strike> findByUserIdOrderByCreatedAtDesc(Long userId);
}
