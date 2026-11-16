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

/** A client's request ("I need a painter"). Artisans answer it with offers. */
@Entity
@Table(name = "job_requests")
public class JobRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    public User client;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    public Category category;

    @Column(nullable = false)
    public String description;

    @Column(nullable = false)
    public String city;

    public String neighborhood;

    @Enumerated(EnumType.STRING)
    @Column(name = "when_pref", nullable = false)
    public WhenPref whenPref = WhenPref.FLEXIBLE;

    /** Budget proposed by the client, in MAD. Artisans may offer another price. */
    @Column(nullable = false)
    public int budget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public RequestStatus status = RequestStatus.OPEN;

    @Column(name = "accepted_offer_id")
    public Long acceptedOfferId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    /** Place picked on the map (optional for old requests). */
    public Double lat;
    public Double lng;
    @Column(length = 200)
    public String address;

    /** Price range allowed for this request (computed when it is created, see PricingService). */
    @Column(name = "price_min")
    public Integer priceMin;

    @Column(name = "price_max")
    public Integer priceMax;
}
