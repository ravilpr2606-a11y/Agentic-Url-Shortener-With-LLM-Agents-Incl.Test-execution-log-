package com.example.urlshortener.service;

import com.example.urlshortener.domain.IdempotencyRecord;
import com.example.urlshortener.domain.ShortUrl;
import com.example.urlshortener.exception.InvalidUrlException;
import com.example.urlshortener.repository.IdempotencyRecordRepository;
import com.example.urlshortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * I test the two idempotency-key behaviours that matter for safe retries:
 * replaying the same key with the same request must return the original
 * short URL without creating a second one, and replaying the same key with
 * a different request must be rejected rather than silently overwritten.
 */
class IdempotencyServiceTest {

    @Mock
    ShortUrlRepository urls;

    @Mock
    ShortCodeGenerator codes;

    @Mock
    UrlValidator validator;

    @Mock
    IdempotencyRecordRepository keys;

    UrlShortenerService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new UrlShortenerService(urls, codes, validator, keys);
    }

    @Test
    void sameKeyAndSameRequestReturnsTheCanonicalUrlWithoutCreatingAnother() {
        String key = "k1";
        String url = "https://example.com";

        ShortUrl existing = new ShortUrl(
                UUID.randomUUID(),
                "abc1234",
                url,
                Instant.now()
        );

        when(keys.findByKeyValue(key)).thenReturn(
                Optional.of(new IdempotencyRecord(key, existing.getId(), sha(url)))
        );
        when(urls.findById(existing.getId())).thenReturn(Optional.of(existing));

        ShortUrl result = service.createShortUrl(url, null, key);

        // A replayed request must resolve to the original record...
        assertEquals(existing.getId(), result.getId());

        // ...and must not write a duplicate. This is the assertion that makes
        // client-side retries safe.
        verify(urls, never()).save(any());
    }

    @Test
    void reusingAKeyWithADifferentRequestIsRejected() {
        String key = "k1";

        // The stored fingerprint is for a different URL than the one I submit
        // below, which is exactly the key-reuse collision I want to reject.
        when(keys.findByKeyValue(key)).thenReturn(
                Optional.of(new IdempotencyRecord(
                        key,
                        UUID.randomUUID(),
                        sha("https://example.com/other")
                ))
        );

        assertThrows(
                InvalidUrlException.class,
                () -> service.createShortUrl("https://example.com", null, key)
        );
    }

    /**
     * I mirror the service's request fingerprint here (SHA-256 over the URL
     * plus a newline separator) so the stubbed IdempotencyRecord matches what
     * the service computes.
     */
    private static String sha(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(
                    (url + "\n").getBytes(StandardCharsets.UTF_8)
            );

            StringBuilder hex = new StringBuilder();
            for (byte b : hashed) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
