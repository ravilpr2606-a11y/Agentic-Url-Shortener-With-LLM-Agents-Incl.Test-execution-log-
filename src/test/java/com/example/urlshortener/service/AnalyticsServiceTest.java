package com.example.urlshortener.service;

import com.example.urlshortener.domain.ClickEvent;
import com.example.urlshortener.domain.ShortUrl;
import com.example.urlshortener.repository.ClickEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private ClickEventRepository clickEventRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(clickEventRepository);
    }

    @Test
    void shouldNotThrowWhenAnalyticsPersistenceFails() {
        ShortUrl shortUrl = new ShortUrl(
                UUID.randomUUID(),
                "abc1234",
                "https://example.com",
                Instant.now()
        );

        doThrow(new RuntimeException("Database unavailable"))
                .when(clickEventRepository)
                .save(any(ClickEvent.class));

        assertDoesNotThrow(() ->
                analyticsService.recordClick(
                        shortUrl,
                        null,
                        "test-agent"
                )
        );

        verify(clickEventRepository).save(any(ClickEvent.class));
    }
}