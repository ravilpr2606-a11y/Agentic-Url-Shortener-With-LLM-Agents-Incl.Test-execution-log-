package com.example.urlshortener.dto;

public record ApiErrorResponse(
        String code,
        String message
) {
}