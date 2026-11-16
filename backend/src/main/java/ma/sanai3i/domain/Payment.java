package ma.sanai3i.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "artisan_id", nullable = false)
    public Long artisanId;

    /** SUBSCRIPTION or FEATURED */
    @Column(nullable = false, length = 12)
    public String kind;

    @Column(nullable = false)
    public int months;

    /** MAD, paid outside the app */
    @Column(nullable = false)
    public int amount;

    @Column(nullable = false, length = 30)
    public String method;

    @Column(length = 300)
    public String note;

    @Column(name = "recorded_by", nullable = false)
    public Long recordedBy;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
