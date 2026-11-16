package ma.sanai3i.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_settings")
public class Setting {
    @Id
    @Column(name = "skey")
    public String key;

    @Column(name = "sval", nullable = false)
    public String value;
}
