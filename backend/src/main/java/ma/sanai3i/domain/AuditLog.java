package ma.sanai3i.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One line of the system trail: who did what, to what, from which address. Rows are only ever added. */
@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "actor_id")
    public Long actorId;

    @Column(name = "actor_name")
    public String actorName;

    @Column(nullable = false)
    public String action;

    @Column(name = "target_type")
    public String targetType;

    @Column(name = "target_id")
    public Long targetId;

    public String detail;

    public String ip;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
