package ma.sanai3i.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "client_reviews")
public class ClientReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "request_id", nullable = false)
    public Long requestId;

    @Column(name = "artisan_id", nullable = false)
    public Long artisanId;

    @Column(name = "client_id", nullable = false)
    public Long clientId;

    @Column(nullable = false)
    public int rating;

    @Column(length = 1000)
    public String body;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
