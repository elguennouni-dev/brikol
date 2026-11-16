package ma.sanai3i.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/*
 * Note on the domain classes: fields are public and mapped directly (field access).
 * All @ManyToOne associations are EAGER and repositories never use getReferenceById,
 * so no lazy proxies are created. DTOs are built inside @Transactional services.
 */
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String phone;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Column(name = "full_name", nullable = false)
    public String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false)
    public Role role;

    @Column(nullable = false)
    public String city;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    /** A blocked user cannot log in and his token stops working immediately. */
    @Column(nullable = false)
    public boolean blocked;

    @Column(name = "blocked_reason")
    public String blockedReason;

    /** Set for accounts created or linked with "Sign in with Google". */
    @Column(length = 160)
    public String email;

    @Column(name = "google_sub", length = 64)
    public String googleSub;

    @Column(name = "last_login_at")
    public Instant lastLoginAt;
}
