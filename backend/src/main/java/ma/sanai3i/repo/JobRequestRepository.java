package ma.sanai3i.repo;

import ma.sanai3i.domain.JobRequest;
import ma.sanai3i.domain.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface JobRequestRepository extends JpaRepository<JobRequest, Long> {
    List<JobRequest> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<JobRequest> findByStatusAndCategoryIdOrderByCreatedAtDesc(RequestStatus status, Long categoryId);

    long countByStatus(RequestStatus status);

    long countByClientId(Long clientId);

    long countByCategoryId(Long categoryId);

    long countByCreatedAtAfter(Instant since);
}
