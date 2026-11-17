package ma.sanai3i.repo;

import ma.sanai3i.domain.JobItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobItemRepository extends JpaRepository<JobItem, Long> {
    List<JobItem> findByRequestIdOrderByIdAsc(Long requestId);

    boolean existsByRequestIdAndKindAndStatus(Long requestId, String kind, String status);
}
