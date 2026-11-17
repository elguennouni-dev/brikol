package ma.sanai3i.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** EXTRA (more work, artisan proposes, client answers) or ADVANCE (cash given early, the other side confirms). */
@Entity
@Table(name = "job_items")
public class JobItem {
    public static final String EXTRA = "EXTRA";
    public static final String ADVANCE = "ADVANCE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "request_id", nullable = false)
    public Long requestId;
    @Column(nullable = false, length = 10)
    public String kind;
    @Column(length = 120)
    public String label;
    @Column(nullable = false)
    public int amount;
    /** PENDING, ACCEPTED or REFUSED */
    @Column(nullable = false, length = 10)
    public String status = "PENDING";
    /** CLIENT or ARTISAN */
    @Column(name = "created_by", nullable = false, length = 10)
    public String createdBy;
    /** Optional photo (a receipt for materials). */
    @Column(name = "media_id", length = 36)
    public String mediaId;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
    @Column(name = "answered_at")
    public Instant answeredAt;
}
