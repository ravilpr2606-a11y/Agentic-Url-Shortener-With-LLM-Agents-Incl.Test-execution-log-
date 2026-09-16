package com.example.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "click_events")
public class ClickEvent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "short_url_id", nullable = false)
    private ShortUrl shortUrl;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(name = "referrer", length = 2048)
    private String referrer;

    @Column(name = "user_agent", length = 1024)
    private String userAgent;

    protected ClickEvent() {
        // Required by JPA
    }

    public ClickEvent(
            UUID id,
            ShortUrl shortUrl,
            Instant clickedAt,
            String referrer,
            String userAgent
    ) {
        this.id = id;
        this.shortUrl = shortUrl;
        this.clickedAt = clickedAt;
        this.referrer = referrer;
        this.userAgent = userAgent;
    }

    public UUID getId() {
        return id;
    }

    public ShortUrl getShortUrl() {
        return shortUrl;
    }

    public Instant getClickedAt() {
        return clickedAt;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getUserAgent() {
        return userAgent;
    }
}