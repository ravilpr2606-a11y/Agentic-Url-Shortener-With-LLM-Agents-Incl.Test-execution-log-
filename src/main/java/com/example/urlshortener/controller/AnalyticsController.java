package com.example.urlshortener.controller;

import com.example.urlshortener.domain.ShortUrl;
import com.example.urlshortener.dto.AnalyticsResponse;
import com.example.urlshortener.service.AnalyticsService;
import com.example.urlshortener.service.UrlShortenerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/urls")
public class AnalyticsController {

    private final UrlShortenerService urlShortenerService;
    private final AnalyticsService analyticsService;

    public AnalyticsController(
            UrlShortenerService urlShortenerService,
            AnalyticsService analyticsService
    ) {
        this.urlShortenerService = urlShortenerService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/{shortCode}/analytics")
    public AnalyticsResponse getAnalytics(
            @PathVariable String shortCode
    ) {
        ShortUrl shortUrl = urlShortenerService.getByShortCode(shortCode);

        long totalClicks =
                analyticsService.getClickCount(shortUrl.getId());

        Instant lastAccessedAt =
                analyticsService.getLastAccessedAt(shortUrl.getId());

        return new AnalyticsResponse(
                shortUrl.getShortCode(),
                totalClicks,
                lastAccessedAt
        );
    }
}