package ma.sanai3i.repo;

import ma.sanai3i.domain.JobCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface JobCaseRepository extends JpaRepository<JobCase, Long> {
    List<JobCase> findByRequestIdOrderByIdAsc(Long requestId);

    List<JobCase> findByStatusInOrderByCreatedAtDesc(Collection<String> statuses);

    List<JobCase> findTop200ByOrderByCreatedAtDesc();

    List<JobCase> findByKindAndStatusAndUpdatedAtBefore(String kind, String status, Instant before);

    boolean existsByRequestIdAndKindAndStatusIn(Long requestId, String kind, Collection<String> statuses);

    boolean existsByRequestIdAndOpenedByAndReasonAndStatusIn(Long requestId, Long openedBy, String reason, Collection<String> statuses);

    long countByStatusIn(Collection<String> statuses);
}
