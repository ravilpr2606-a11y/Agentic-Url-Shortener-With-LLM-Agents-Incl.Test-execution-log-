package com.example.urlshortener.dto;

import java.time.Instant;

public record AnalyticsResponse(
        String shortCode,
        long totalClicks,
        Instant lastAccessedAt
) {
}