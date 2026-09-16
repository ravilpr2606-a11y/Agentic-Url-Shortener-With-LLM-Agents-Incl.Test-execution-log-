package com.example.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "short_urls")
public class ShortUrl {

    @Id
    private UUID id;

    @Column(name = "short_code", nullable = false, unique = true, length = 7)
    private String shortCode;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected ShortUrl() {
        // Required by JPA
    }

    public ShortUrl(
            UUID id,
            String shortCode,
            String originalUrl,
            Instant createdAt
    ) {
        this(id, shortCode, originalUrl, createdAt, null);
    }

    public ShortUrl(
            UUID id,
            String shortCode,
            String originalUrl,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.id = id;
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}