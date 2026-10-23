package com.gesfut.models.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Plan 22/10 #14: token revocado por logout (solo el SHA-256, nunca el token) hasta su vencimiento. */
@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {
    @Id
    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Long expiresAt;

    protected RevokedToken() {}

    public RevokedToken(String tokenHash, Long expiresAt) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() { return tokenHash; }
    public Long getExpiresAt() { return expiresAt; }
}
