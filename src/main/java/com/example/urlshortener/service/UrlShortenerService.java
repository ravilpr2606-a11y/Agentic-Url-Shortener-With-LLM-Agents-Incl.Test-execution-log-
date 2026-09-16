package com.example.urlshortener.service;

import com.example.urlshortener.domain.ShortUrl;
import com.example.urlshortener.exception.InvalidUrlException;
import com.example.urlshortener.exception.ShortCodeGenerationException;
import com.example.urlshortener.exception.ShortUrlExpiredException;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.repository.ShortUrlRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import com.example.urlshortener.domain.IdempotencyRecord;
import com.example.urlshortener.repository.IdempotencyRecordRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class UrlShortenerService {

    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final UrlValidator urlValidator;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public UrlShortenerService(ShortUrlRepository shortUrlRepository, ShortCodeGenerator shortCodeGenerator, UrlValidator urlValidator) {
        this(shortUrlRepository, shortCodeGenerator, urlValidator, null);
    }

    @Autowired
    public UrlShortenerService(
            ShortUrlRepository shortUrlRepository,
            ShortCodeGenerator shortCodeGenerator,
            UrlValidator urlValidator,
            IdempotencyRecordRepository idempotencyRecordRepository
    ) {
        this.shortUrlRepository = shortUrlRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.urlValidator = urlValidator;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    public ShortUrl createShortUrl(String originalUrl) {
        return createShortUrl(originalUrl, null);
    }

    public ShortUrl createShortUrl(String originalUrl, Instant expiresAt) {
        return createShortUrl(originalUrl, expiresAt, null);
    }

    public ShortUrl createShortUrl(String originalUrl, Instant expiresAt, String idempotencyKey) {
        urlValidator.validate(originalUrl);
        String fingerprint = fingerprint(originalUrl, expiresAt);
        if (idempotencyRecordRepository != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            IdempotencyRecord existing = idempotencyRecordRepository.findByKeyValue(idempotencyKey).orElse(null);
            if (existing != null) {
                if (!existing.getRequestFingerprint().equals(fingerprint)) throw new InvalidUrlException("Idempotency-Key was already used with a different request");
                return shortUrlRepository.findById(existing.getShortUrlId()).orElseThrow(() -> new ShortCodeGenerationException("Idempotency record points to missing short URL"));
            }
        }
        validateExpiration(expiresAt);

        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            String shortCode = shortCodeGenerator.generate();

            if (shortUrlRepository.existsByShortCode(shortCode)) {
                continue;
            }

            ShortUrl shortUrl = new ShortUrl(
                    UUID.randomUUID(),
                    shortCode,
                    originalUrl,
                    Instant.now(),
                    expiresAt
            );

            try {
                ShortUrl saved = shortUrlRepository.save(shortUrl);
                if (idempotencyRecordRepository != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
                    try { idempotencyRecordRepository.save(new IdempotencyRecord(idempotencyKey, saved.getId(), fingerprint)); }
                    catch (DataIntegrityViolationException ignored) { /* concurrent identical request; next lookup returns canonical record */ }
                }
                return saved;
            } catch (DataIntegrityViolationException exception) {
                // A concurrent request may have claimed the same code.
                // Retry with a newly generated code.
            }
        }

        throw new ShortCodeGenerationException(
                "Unable to generate a unique short code"
        );
    }

    public ShortUrl getByShortCode(String shortCode) {
        if (shortCode == null || !shortCode.matches("[a-zA-Z0-9]{7}")) {
            throw new ShortUrlNotFoundException("Short URL not found");
        }

        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() ->
                        new ShortUrlNotFoundException("Short URL not found")
                );

        if (isExpired(shortUrl)) {
            throw new ShortUrlExpiredException("Short URL has expired");
        }

        return shortUrl;
    }

    private String fingerprint(String url, Instant expiresAt) { try { MessageDigest md=MessageDigest.getInstance("SHA-256"); byte[] b=md.digest((url+"\n"+(expiresAt==null?"":expiresAt)).getBytes(StandardCharsets.UTF_8)); StringBuilder h=new StringBuilder(); for(byte x:b) h.append(String.format("%02x",x)); return h.toString(); } catch(Exception e){ throw new IllegalStateException("Fingerprint unavailable",e); } }

    private void validateExpiration(Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new InvalidUrlException(
                    "Expiration time must be in the future"
            );
        }
    }

    private boolean isExpired(ShortUrl shortUrl) {
        return shortUrl.getExpiresAt() != null
                && !shortUrl.getExpiresAt().isAfter(Instant.now());
    }
}