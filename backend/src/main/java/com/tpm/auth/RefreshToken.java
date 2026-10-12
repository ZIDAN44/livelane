package com.tpm.auth;

import java.time.Instant;
import java.util.UUID;

import com.tpm.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** SHA-256 of the raw token; the raw value only ever exists in the client's cookie. */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    private boolean rotated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RefreshToken() {}

    public RefreshToken(User user, String tokenHash, UUID familyId, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) { return now.isAfter(expiresAt); }
    public boolean isRevoked() { return revokedAt != null; }
    /** Revoked because it was exchanged for a successor token. */
    public void rotate() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
            rotated = true;
        }
    }

    /**
     * True if this token was rotated only moments ago: a benign race (two tabs refreshing at once),
     * not a replay. Tokens revoked by logout or theft detection never qualify.
     */
    public boolean rotatedWithin(java.time.Duration grace, Instant now) {
        return rotated && revokedAt != null && !revokedAt.plus(grace).isBefore(now);
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public UUID getFamilyId() { return familyId; }
    public Instant getExpiresAt() { return expiresAt; }
}
