package com.example.urlshortener.service;

import com.example.urlshortener.domain.ClickEvent;
import com.example.urlshortener.domain.ShortUrl;
import com.example.urlshortener.repository.ClickEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AnalyticsService {

    private static final Logger log =
            LoggerFactory.getLogger(AnalyticsService.class);

    private final ClickEventRepository clickEventRepository;

    public AnalyticsService(ClickEventRepository clickEventRepository) {
        this.clickEventRepository = clickEventRepository;
    }

    public void recordClick(
            ShortUrl shortUrl,
            String referrer,
            String userAgent
    ) {
        try {
            ClickEvent clickEvent = new ClickEvent(
                    UUID.randomUUID(),
                    shortUrl,
                    Instant.now(),
                    referrer,
                    userAgent
            );

            clickEventRepository.save(clickEvent);

        } catch (RuntimeException exception) {
            log.error(
                    "ANALYTICS_WRITE_FAILURE shortCode={}",
                    shortUrl.getShortCode(),
                    exception
            );
        }
    }

    public long getClickCount(UUID shortUrlId) {
        return clickEventRepository.countByShortUrlId(shortUrlId);
    }

    public Instant getLastAccessedAt(UUID shortUrlId) {
        return clickEventRepository.findLastAccessedAt(shortUrlId);
    }
}