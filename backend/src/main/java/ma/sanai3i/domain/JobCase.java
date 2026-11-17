package ma.sanai3i.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * PROBLEM: either side asks the team for help (no-show, unpaid, price, quality...). The team calls both and decides.
 * WARRANTY: the problem came back within 7 days; the artisan comes back for free, the client confirms it is fixed.
 * Status: OPEN, FIXED (warranty: artisan says fixed, waiting for the client), ESCALATED (to the team), RESOLVED.
 */
@Entity
@Table(name = "job_cases")
public class JobCase {
    public static final String PROBLEM = "PROBLEM";
    public static final String WARRANTY = "WARRANTY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "request_id", nullable = false)
    public Long requestId;
    @Column(nullable = false, length = 10)
    public String kind;
    @Column(nullable = false, length = 20)
    public String reason;
    @Column(name = "opened_by", nullable = false)
    public Long openedBy;
    @Column(name = "opened_role", nullable = false, length = 10)
    public String openedRole;
    @Column(length = 1000)
    public String details;
    @Column(name = "media_id", length = 36)
    public String mediaId;
    @Column(nullable = false, length = 12)
    public String status = "OPEN";
    @Column(length = 500)
    public String resolution;
    @Column(name = "handled_by")
    public Long handledBy;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();
    @Column(name = "handled_at")
    public Instant handledAt;
}
