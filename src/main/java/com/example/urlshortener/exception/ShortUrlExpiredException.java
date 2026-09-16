package com.example.urlshortener.exception;

public class ShortUrlExpiredException extends RuntimeException {

    public ShortUrlExpiredException(String message) {
        super(message);
    }
}