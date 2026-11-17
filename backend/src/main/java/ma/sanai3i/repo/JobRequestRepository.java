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

    /** Row lock: two "accept" calls at the same time are handled one after the other. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from JobRequest r where r.id = :id")
    java.util.Optional<JobRequest> lockById(Long id);

    long countByClientIdAndStatusIn(Long clientId, java.util.Collection<RequestStatus> statuses);

    long countByClientIdAndCreatedAtAfter(Long clientId, Instant since);

    long countByCategoryId(Long categoryId);

    long countByCreatedAtAfter(Instant since);

    List<JobRequest> findByStatusAndAppointmentConfirmedTrueAndReminderSentFalseAndAppointmentAtBetween(
            RequestStatus status, Instant from, Instant to);
}
