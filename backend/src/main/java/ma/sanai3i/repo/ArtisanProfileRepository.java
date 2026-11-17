package ma.sanai3i.repo;

import ma.sanai3i.domain.ArtisanProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArtisanProfileRepository extends JpaRepository<ArtisanProfile, Long> {
    Optional<ArtisanProfile> findByUserId(Long userId);

    List<ArtisanProfile> findAllByOrderByVerifiedDescIdAsc();

    List<ArtisanProfile> findByCategory_CodeOrderByVerifiedDescIdAsc(String code);

    List<ArtisanProfile> findByCategoryId(Long categoryId);

    long countByCategoryId(Long categoryId);

    long countByVerifiedTrue();

    long countByStatus(ma.sanai3i.domain.ArtisanStatus status);

    List<ArtisanProfile> findBySubscriptionUntilBetween(java.time.Instant from, java.time.Instant to);

    List<ArtisanProfile> findByStatusOrderBySubmittedAtAsc(ma.sanai3i.domain.ArtisanStatus status);

    boolean existsByCinAndIdNot(String cin, Long id);

    java.util.List<ArtisanProfile> findByPendingChangesIsNotNullOrderByPendingSinceAsc();
}
