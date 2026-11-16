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

/** One priced service of an artisan ("Peinture intérieure, dès 35 DH/m²"). */
@Entity
@Table(name = "artisan_services")
public class ServiceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "artisan_id", nullable = false)
    public ArtisanProfile artisan;

    @Column(name = "label_ar", nullable = false)
    public String labelAr;

    @Column(name = "label_fr", nullable = false)
    public String labelFr;

    @Column(nullable = false)
    public int price;

    @Column(name = "unit_ar", nullable = false)
    public String unitAr = "";

    @Column(name = "unit_fr", nullable = false)
    public String unitFr = "";
}
