package ma.sanai3i.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String code;

    @Column(name = "name_ar", nullable = false)
    public String nameAr;

    @Column(name = "name_fr", nullable = false)
    public String nameFr;

    @Column(name = "sort_order", nullable = false)
    public int sortOrder;

    /** Hard price bounds of the trade, in MAD (set by the admin). */
    @Column(name = "min_price", nullable = false)
    public int minPrice = 100;

    @Column(name = "max_price", nullable = false)
    public int maxPrice = 50000;
}
