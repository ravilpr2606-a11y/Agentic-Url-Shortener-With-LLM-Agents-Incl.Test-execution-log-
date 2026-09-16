package com.example.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateShortUrlRequest(

        @NotBlank(message = "URL must not be blank")
        @Size(max = 2048, message = "URL must not exceed 2048 characters")
        String url,

        Instant expiresAt
) {
}