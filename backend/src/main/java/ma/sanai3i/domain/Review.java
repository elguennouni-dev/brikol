package ma.sanai3i.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "reviews")
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "artisan_id", nullable = false)
    public ArtisanProfile artisan;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    public User client;

    /** The finished job this review is about. Unique, so one review per job. */
    @Column(name = "request_id")
    public Long requestId;

    @Column(nullable = false)
    public int rating;

    public String body;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
