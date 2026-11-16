package ma.sanai3i.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** An artisan's answer to a request: a price and when he can come. Paid in cash, outside the app. */
@Entity
@Table(name = "offers")
public class Offer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    public JobRequest request;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "artisan_id", nullable = false)
    public ArtisanProfile artisan;

    @Column(nullable = false)
    public int price;

    @Column(name = "eta_text")
    public String etaText;

    public String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public OfferStatus status = OfferStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
