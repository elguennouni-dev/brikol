package ma.sanai3i.repo;

import ma.sanai3i.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByStatusOrderByCreatedAtDesc(String status);

    long countByStatus(String status);

    long countByTargetUserId(Long userId);

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(Long reporterId, String targetType, Long targetId, String status);
}
