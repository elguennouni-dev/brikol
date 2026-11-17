package ma.sanai3i.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A token signed out with "log out"; kept until it would have expired anyway. */
@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {
    @Id
    @Column(length = 36)
    public String jti;
    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;
}
