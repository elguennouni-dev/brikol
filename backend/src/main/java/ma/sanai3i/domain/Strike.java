package ma.sanai3i.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A warning on an account (no-show, late cancellation, unpaid...). Three in 30 days suspend the account for 7 days. */
@Entity
@Table(name = "strikes")
public class Strike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "user_id", nullable = false)
    public Long userId;
    @Column(name = "request_id")
    public Long requestId;
    @Column(name = "case_id")
    public Long caseId;
    @Column(nullable = false, length = 20)
    public String reason;
    /** Staff member who gave it; null when the system did. */
    @Column(name = "created_by")
    public Long createdBy;
    @Column(nullable = false)
    public boolean removed;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
