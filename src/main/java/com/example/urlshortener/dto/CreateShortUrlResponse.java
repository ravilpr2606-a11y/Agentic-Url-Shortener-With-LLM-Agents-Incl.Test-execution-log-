package com.example.urlshortener.dto;

public record CreateShortUrlResponse(
        String shortCode,
        String shortUrl,
        String originalUrl
) {
}