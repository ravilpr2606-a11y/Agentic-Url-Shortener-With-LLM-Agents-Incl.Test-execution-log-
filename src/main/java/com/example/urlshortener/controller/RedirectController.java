package com.example.urlshortener.controller;

import com.example.urlshortener.domain.ShortUrl;
import com.example.urlshortener.service.AnalyticsService;
import com.example.urlshortener.service.UrlShortenerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class RedirectController {

    private final UrlShortenerService urlShortenerService;
    private final AnalyticsService analyticsService;

    public RedirectController(
            UrlShortenerService urlShortenerService,
            AnalyticsService analyticsService
    ) {
        this.urlShortenerService = urlShortenerService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            @RequestHeader(value = "Referer", required = false) String referrer,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        ShortUrl shortUrl = urlShortenerService.getByShortCode(shortCode);

        analyticsService.recordClick(
                shortUrl,
                referrer,
                userAgent
        );

        return ResponseEntity
                .status(302)
                .location(URI.create(shortUrl.getOriginalUrl()))
                .build();
    }
}