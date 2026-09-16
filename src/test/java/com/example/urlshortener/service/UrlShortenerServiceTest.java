package com.example.urlshortener.service;

import com.example.urlshortener.domain.ShortUrl;
import com.example.urlshortener.exception.InvalidUrlException;
import com.example.urlshortener.exception.ShortCodeGenerationException;
import com.example.urlshortener.exception.ShortUrlExpiredException;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private ShortCodeGenerator shortCodeGenerator;

    @Mock
    private UrlValidator urlValidator;

    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        service = new UrlShortenerService(
                shortUrlRepository,
                shortCodeGenerator,
                urlValidator
        );
    }

    @Test
    void shouldCreateShortUrl() {
        String originalUrl = "https://example.com";
        String shortCode = "abc1234";

        when(shortCodeGenerator.generate()).thenReturn(shortCode);
        when(shortUrlRepository.existsByShortCode(shortCode)).thenReturn(false);
        when(shortUrlRepository.save(any(ShortUrl.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrl result = service.createShortUrl(originalUrl);

        assertEquals(shortCode, result.getShortCode());
        assertEquals(originalUrl, result.getOriginalUrl());
        assertNull(result.getExpiresAt());

        verify(urlValidator).validate(originalUrl);
        verify(shortUrlRepository).save(any(ShortUrl.class));
    }

    @Test
    void shouldCreateShortUrlWithFutureExpiration() {
        String originalUrl = "https://example.com";
        String shortCode = "abc1234";
        Instant expiresAt = Instant.now().plusSeconds(3600);

        when(shortCodeGenerator.generate()).thenReturn(shortCode);
        when(shortUrlRepository.existsByShortCode(shortCode)).thenReturn(false);
        when(shortUrlRepository.save(any(ShortUrl.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrl result = service.createShortUrl(originalUrl, expiresAt);

        assertEquals(shortCode, result.getShortCode());
        assertEquals(originalUrl, result.getOriginalUrl());
        assertEquals(expiresAt, result.getExpiresAt());

        verify(urlValidator).validate(originalUrl);
        verify(shortUrlRepository).save(any(ShortUrl.class));
    }

    @Test
    void shouldRejectExpirationInThePast() {
        String originalUrl = "https://example.com";
        Instant expiresAt = Instant.now().minusSeconds(1);

        InvalidUrlException exception = assertThrows(
                InvalidUrlException.class,
                () -> service.createShortUrl(originalUrl, expiresAt)
        );

        assertEquals(
                "Expiration time must be in the future",
                exception.getMessage()
        );

        verify(urlValidator).validate(originalUrl);
        verifyNoInteractions(shortCodeGenerator);
        verify(shortUrlRepository, never()).save(any());
    }

    @Test
    void shouldRejectExpirationAtCurrentTime() {
        String originalUrl = "https://example.com";
        Instant expiresAt = Instant.now();

        InvalidUrlException exception = assertThrows(
                InvalidUrlException.class,
                () -> service.createShortUrl(originalUrl, expiresAt)
        );

        assertEquals(
                "Expiration time must be in the future",
                exception.getMessage()
        );

        verify(urlValidator).validate(originalUrl);
        verifyNoInteractions(shortCodeGenerator);
        verify(shortUrlRepository, never()).save(any());
    }

    @Test
    void shouldReturnNonExpiringShortUrl() {
        String shortCode = "abc1234";
        String originalUrl = "https://example.com";

        ShortUrl shortUrl = new ShortUrl(
                UUID.randomUUID(),
                shortCode,
                originalUrl,
                Instant.now()
        );

        when(shortUrlRepository.findByShortCode(shortCode))
                .thenReturn(Optional.of(shortUrl));

        ShortUrl result = service.getByShortCode(shortCode);

        assertEquals(shortCode, result.getShortCode());
        assertEquals(originalUrl, result.getOriginalUrl());
        assertNull(result.getExpiresAt());
    }

    @Test
    void shouldReturnActiveShortUrl() {
        String shortCode = "abc1234";
        String originalUrl = "https://example.com";
        Instant expiresAt = Instant.now().plusSeconds(3600);

        ShortUrl shortUrl = new ShortUrl(
                UUID.randomUUID(),
                shortCode,
                originalUrl,
                Instant.now(),
                expiresAt
        );

        when(shortUrlRepository.findByShortCode(shortCode))
                .thenReturn(Optional.of(shortUrl));

        ShortUrl result = service.getByShortCode(shortCode);

        assertEquals(shortCode, result.getShortCode());
        assertEquals(originalUrl, result.getOriginalUrl());
        assertEquals(expiresAt, result.getExpiresAt());
    }

    @Test
    void shouldRejectExpiredShortUrl() {
        String shortCode = "abc1234";
        String originalUrl = "https://example.com";
        Instant expiresAt = Instant.now().minusSeconds(1);

        ShortUrl shortUrl = new ShortUrl(
                UUID.randomUUID(),
                shortCode,
                originalUrl,
                Instant.now(),
                expiresAt
        );

        when(shortUrlRepository.findByShortCode(shortCode))
                .thenReturn(Optional.of(shortUrl));

        ShortUrlExpiredException exception = assertThrows(
                ShortUrlExpiredException.class,
                () -> service.getByShortCode(shortCode)
        );

        assertEquals(
                "Short URL has expired",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectShortCodeWithInvalidFormat() {
        assertThrows(
                ShortUrlNotFoundException.class,
                () -> service.getByShortCode("invalid!")
        );

        verify(shortUrlRepository, never()).findByShortCode(anyString());
    }

    @Test
    void shouldThrowWhenShortCodeDoesNotExist() {
        String shortCode = "abc1234";

        when(shortUrlRepository.findByShortCode(shortCode))
                .thenReturn(Optional.empty());

        assertThrows(
                ShortUrlNotFoundException.class,
                () -> service.getByShortCode(shortCode)
        );

        verify(shortUrlRepository).findByShortCode(shortCode);
    }

    @Test
    void shouldRetryWhenGeneratedShortCodeAlreadyExists() {
        String originalUrl = "https://example.com";

        when(shortCodeGenerator.generate())
                .thenReturn("abc1234")
                .thenReturn("xyz5678");

        when(shortUrlRepository.existsByShortCode("abc1234"))
                .thenReturn(true);

        when(shortUrlRepository.existsByShortCode("xyz5678"))
                .thenReturn(false);

        when(shortUrlRepository.save(any(ShortUrl.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrl result = service.createShortUrl(originalUrl);

        assertEquals("xyz5678", result.getShortCode());

        verify(shortCodeGenerator, times(2)).generate();
        verify(shortUrlRepository).save(any(ShortUrl.class));
    }

    @Test
    void shouldThrowWhenUnableToGenerateUniqueShortCode() {
        String originalUrl = "https://example.com";

        when(shortCodeGenerator.generate()).thenReturn("abc1234");
        when(shortUrlRepository.existsByShortCode("abc1234"))
                .thenReturn(true);

        assertThrows(
                ShortCodeGenerationException.class,
                () -> service.createShortUrl(originalUrl)
        );

        verify(shortCodeGenerator, times(5)).generate();
        verify(shortUrlRepository, never()).save(any());
    }

    @Test
    void shouldRetryWhenDatabaseReportsDuplicateShortCode() {
        String originalUrl = "https://example.com";

        when(shortCodeGenerator.generate())
                .thenReturn("abc1234")
                .thenReturn("xyz5678");

        when(shortUrlRepository.existsByShortCode("abc1234"))
                .thenReturn(false);

        when(shortUrlRepository.existsByShortCode("xyz5678"))
                .thenReturn(false);

        when(shortUrlRepository.save(any(ShortUrl.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrl result = service.createShortUrl(originalUrl);

        assertEquals("xyz5678", result.getShortCode());

        verify(shortCodeGenerator, times(2)).generate();
        verify(shortUrlRepository, times(2)).save(any(ShortUrl.class));
    }
}