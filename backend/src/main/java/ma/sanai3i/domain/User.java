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

    /** The owner of the platform (ADMIN_PHONE): every permission, manages the team, cannot be changed by others. */
    @Column(name = "super_admin", nullable = false)
    public boolean superAdmin;

    /** Staff permissions, comma separated (see Permission). */
    @Column(length = 300)
    public String permissions;

    public java.util.Set<Permission> permissionSet() {
        java.util.Set<Permission> out = java.util.EnumSet.noneOf(Permission.class);
        if (permissions != null) {
            for (String p : permissions.split(",")) {
                try {
                    out.add(Permission.valueOf(p.trim()));
                } catch (IllegalArgumentException e) {
                    // unknown permission name: ignored
                }
            }
        }
        return out;
    }

    @Column(name = "phone_verified", nullable = false)
    public boolean phoneVerified;

    @Column(name = "last_login_at")
    public Instant lastLoginAt;

    /** Too many strikes: no new requests (client) or offers (artisan) until then. */
    @Column(name = "suspended_until")
    public java.time.Instant suspendedUntil;

    public boolean suspended() {
        return suspendedUntil != null && suspendedUntil.isAfter(java.time.Instant.now());
    }

    /** Part of every token: bumping it signs the account out everywhere. */
    @Column(name = "token_version", nullable = false)
    public int tokenVersion;
}
