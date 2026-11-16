package ma.sanai3i.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Something that happened to a user (new offer, offer accepted...). The texts are written by the web app in the
 * user's language from kind + actor + detail, so the same row serves Darija and French.
 */
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(nullable = false, length = 30)
    public String kind;

    @Column(name = "request_id")
    public Long requestId;

    @Column(name = "actor_ar")
    public String actorAr;

    @Column(name = "actor_fr")
    public String actorFr;

    public String detail;

    @Column(name = "is_read", nullable = false)
    public boolean seen;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
