package ma.sanai3i.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "otp_codes")
public class OtpCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 20)
    public String phone;

    /** REGISTER or RESET */
    @Column(nullable = false, length = 12)
    public String purpose;

    @Column(name = "code_hash", nullable = false, length = 100)
    public String codeHash;

    @Column(nullable = false)
    public int attempts;

    @Column(nullable = false)
    public boolean used;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
