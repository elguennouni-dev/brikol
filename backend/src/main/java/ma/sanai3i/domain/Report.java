package ma.sanai3i.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "reports")
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "reporter_id", nullable = false)
    public Long reporterId;

    /** USER, REQUEST, REVIEW or MESSAGE */
    @Column(name = "target_type", nullable = false, length = 12)
    public String targetType;

    @Column(name = "target_id", nullable = false)
    public Long targetId;

    /** The person the report is about (for USER it is the target itself). */
    @Column(name = "target_user_id")
    public Long targetUserId;

    @Column(nullable = false, length = 30)
    public String reason;

    @Column(length = 1000)
    public String details;

    /** OPEN, RESOLVED or DISMISSED */
    @Column(nullable = false, length = 12)
    public String status = "OPEN";

    @Column(length = 500)
    public String resolution;

    @Column(name = "handled_by")
    public Long handledBy;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "handled_at")
    public Instant handledAt;
}
