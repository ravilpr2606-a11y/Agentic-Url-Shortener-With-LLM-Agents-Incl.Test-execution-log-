package com.example.urlshortener.controller;

import com.example.urlshortener.domain.ShortUrl;
import com.example.urlshortener.dto.CreateShortUrlRequest;
import com.example.urlshortener.dto.CreateShortUrlResponse;
import com.example.urlshortener.service.UrlShortenerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlShortenerService urlShortenerService;
    private final String baseUrl;

    public UrlController(
            UrlShortenerService urlShortenerService,
            @Value("${app.base-url}") String baseUrl
    ) {
        this.urlShortenerService = urlShortenerService;
        this.baseUrl = baseUrl;
    }

    @PostMapping
    public ResponseEntity<CreateShortUrlResponse> createShortUrl(
            @Valid @RequestBody CreateShortUrlRequest request,
            @RequestHeader(value="Idempotency-Key", required=false) String idempotencyKey
    ) {
        ShortUrl shortUrl = urlShortenerService.createShortUrl(
                request.url(),
                request.expiresAt(),
                idempotencyKey
        );

        CreateShortUrlResponse response = new CreateShortUrlResponse(
                shortUrl.getShortCode(),
                buildShortUrl(shortUrl.getShortCode()),
                shortUrl.getOriginalUrl()
        );

        return ResponseEntity
                .created(URI.create(response.shortUrl()))
                .body(response);
    }

    private String buildShortUrl(String shortCode) {
        return baseUrl + "/" + shortCode;
    }
}