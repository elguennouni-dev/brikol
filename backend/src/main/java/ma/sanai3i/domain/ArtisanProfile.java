package ma.sanai3i.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "artisan_profiles")
public class ArtisanProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    public User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    public Category category;

    @Column(name = "name_ar")
    public String nameAr;

    @Column(name = "name_fr")
    public String nameFr;

    @Column(name = "trade_ar")
    public String tradeAr;

    @Column(name = "trade_fr")
    public String tradeFr;

    @Column(name = "neighborhood_ar")
    public String neighborhoodAr;

    @Column(name = "neighborhood_fr")
    public String neighborhoodFr;

    @Column(nullable = false)
    public String city;

    @Column(name = "bio_ar")
    public String bioAr;

    @Column(name = "bio_fr")
    public String bioFr;

    @Column(name = "years_experience", nullable = false)
    public int yearsExperience;

    @Column(name = "jobs_done", nullable = false)
    public int jobsDone;

    @Column(nullable = false)
    public boolean verified;

    @Column(name = "available_today", nullable = false)
    public boolean availableToday = true;

    public String whatsapp;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "artisan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    public List<ServiceItem> services = new ArrayList<>();
}
