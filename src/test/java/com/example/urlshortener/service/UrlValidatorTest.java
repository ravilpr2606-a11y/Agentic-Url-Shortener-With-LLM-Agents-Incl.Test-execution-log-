package com.example.urlshortener.service;

import com.example.urlshortener.exception.InvalidUrlException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator();

    @Test
    void shouldAcceptValidHttpUrl() {
        assertDoesNotThrow(() ->
                validator.validate("http://example.com"));
    }

    @Test
    void shouldAcceptValidHttpsUrl() {
        assertDoesNotThrow(() ->
                validator.validate("https://example.com/path"));
    }

    @Test
    void shouldRejectNullUrl() {
        assertThrows(
                InvalidUrlException.class,
                () -> validator.validate(null)
        );
    }

    @Test
    void shouldRejectBlankUrl() {
        assertThrows(
                InvalidUrlException.class,
                () -> validator.validate("   ")
        );
    }

    @Test
    void shouldRejectUnsupportedScheme() {
        assertThrows(
                InvalidUrlException.class,
                () -> validator.validate("ftp://example.com/file")
        );
    }

    @Test
    void shouldRejectMalformedUrl() {
        assertThrows(
                InvalidUrlException.class,
                () -> validator.validate("not-a-url")
        );
    }

    @Test
    void shouldRejectUrlWithoutHost() {
        assertThrows(
                InvalidUrlException.class,
                () -> validator.validate("https:///path")
        );
    }

    @Test
    void shouldRejectUrlLongerThan2048Characters() {
        String longUrl = "https://example.com/" + "a".repeat(2040);

        assertThrows(
                InvalidUrlException.class,
                () -> validator.validate(longUrl)
        );
    }
}