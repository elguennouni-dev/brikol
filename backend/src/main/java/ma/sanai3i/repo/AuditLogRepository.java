package ma.sanai3i.repo;

import ma.sanai3i.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Empty strings mean "no filter". Newest first. */
    @Query("select a from AuditLog a where (:action = '' or a.action = :action) "
            + "and (:q = '' or lower(coalesce(a.actorName, '')) like lower(concat('%', :q, '%')) "
            + "or lower(coalesce(a.detail, '')) like lower(concat('%', :q, '%'))) order by a.id desc")
    Page<AuditLog> search(@Param("action") String action, @Param("q") String q, Pageable pageable);

    List<AuditLog> findTop15ByActorIdOrderByIdDesc(Long actorId);

    List<AuditLog> findTop15ByTargetTypeAndTargetIdOrderByIdDesc(String targetType, Long targetId);
}
