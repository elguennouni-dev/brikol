package ma.sanai3i.repo;

import ma.sanai3i.domain.Media;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MediaRepository extends JpaRepository<Media, String> {
    List<Media> findByRequestIdOrderByCreatedAtAsc(Long requestId);

    List<Media> findByArtisanIdAndPurposeOrderByCreatedAtAsc(Long artisanId, String purpose);

    long countByOwnerIdAndCreatedAtAfter(Long ownerId, Instant since);
}
