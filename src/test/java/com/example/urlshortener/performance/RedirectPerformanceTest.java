package com.example.urlshortener.performance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RedirectPerformanceTest {

    private static final int WARMUP_REQUESTS = 10;
    private static final int PERFORMANCE_REQUESTS = 100;

    private static final long MAX_AVERAGE_LATENCY_MS = 200;
    private static final long MAX_P95_LATENCY_MS = 500;

    static {
        TimeZone.setDefault(
                TimeZone.getTimeZone("America/Chicago")
        );
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("urlshortener")
                    .withUsername("urlshortener")
                    .withPassword("urlshortener")
                    .withEnv("TZ", "America/Chicago")
                    .withCommand(
                            "postgres",
                            "-c",
                            "timezone=America/Chicago"
                    );

    @DynamicPropertySource
    static void configureDatasource(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                () -> POSTGRES.getJdbcUrl()
                        + "?options=-c%20TimeZone%3DAmerica%2FChicago"
        );

        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );

        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
    }

    @LocalServerPort
    private int port;

    @Test
    void shouldMaintainAcceptableRedirectLatency()
            throws Exception {

        String shortCode = createShortUrl();

        HttpClient httpClient =
                HttpClient.newBuilder()
                        .followRedirects(
                                HttpClient.Redirect.NEVER
                        )
                        .build();

        String redirectUrl =
                "http://localhost:"
                        + port
                        + "/"
                        + shortCode;

        // Warm-up requests are excluded from performance measurements.
        for (int i = 0; i < WARMUP_REQUESTS; i++) {

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(redirectUrl))
                            .GET()
                            .build();

            HttpResponse<Void> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.discarding()
                    );

            assertEquals(
                    302,
                    response.statusCode(),
                    "Warm-up redirect request did not return HTTP 302"
            );
        }

        List<Long> latenciesNanos =
                new ArrayList<>(PERFORMANCE_REQUESTS);

        // Measured requests.
        for (int i = 0; i < PERFORMANCE_REQUESTS; i++) {

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(redirectUrl))
                            .GET()
                            .build();

            long start =
                    System.nanoTime();

            HttpResponse<Void> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.discarding()
                    );

            long elapsed =
                    System.nanoTime() - start;

            latenciesNanos.add(elapsed);

            assertEquals(
                    302,
                    response.statusCode(),
                    "Redirect request "
                            + (i + 1)
                            + " did not return HTTP 302"
            );

            assertTrue(
                    response.headers()
                            .firstValue("Location")
                            .isPresent(),
                    "Redirect response must contain a Location header"
            );
        }

        Collections.sort(latenciesNanos);

        double averageLatencyMs =
                latenciesNanos.stream()
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0.0)
                        / 1_000_000.0;

        double p95LatencyMs =
                percentile(
                        latenciesNanos,
                        95
                ) / 1_000_000.0;

        System.out.printf(
                "Redirect performance: requests=%d, average=%.2f ms, p95=%.2f ms%n",
                PERFORMANCE_REQUESTS,
                averageLatencyMs,
                p95LatencyMs
        );

        assertTrue(
                averageLatencyMs < MAX_AVERAGE_LATENCY_MS,
                String.format(
                        "Average redirect latency %.2f ms exceeded threshold of %d ms",
                        averageLatencyMs,
                        MAX_AVERAGE_LATENCY_MS
                )
        );

        assertTrue(
                p95LatencyMs < MAX_P95_LATENCY_MS,
                String.format(
                        "P95 redirect latency %.2f ms exceeded threshold of %d ms",
                        p95LatencyMs,
                        MAX_P95_LATENCY_MS
                )
        );
    }

    private String createShortUrl()
            throws Exception {

        String url =
                "http://localhost:"
                        + port
                        + "/api/v1/urls";

        String requestBody =
                """
                {
                    "url": "https://example.com/performance-test"
                }
                """;

        HttpClient httpClient =
                HttpClient.newHttpClient();

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(requestBody)
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        assertEquals(
                201,
                response.statusCode(),
                "Create short URL request must return HTTP 201"
        );

        String responseBody =
                response.body();

        assertNotNull(
                responseBody,
                "Create response body must not be null"
        );

        return extractShortCode(responseBody);
    }

    private String extractShortCode(
            String responseBody) {

        String marker =
                "\"shortCode\":\"";

        int start =
                responseBody.indexOf(marker);

        assertTrue(
                start >= 0,
                "Create response must contain shortCode"
        );

        start += marker.length();

        int end =
                responseBody.indexOf(
                        "\"",
                        start
                );

        assertTrue(
                end > start,
                "Create response must contain a valid shortCode"
        );

        return responseBody.substring(
                start,
                end
        );
    }

    private long percentile(
            List<Long> sortedValues,
            double percentile) {

        if (sortedValues.isEmpty()) {
            return 0L;
        }

        int index =
                (int) Math.ceil(
                        (percentile / 100.0)
                                * sortedValues.size()
                ) - 1;

        index =
                Math.max(
                        0,
                        Math.min(
                                index,
                                sortedValues.size() - 1
                        )
                );

        return sortedValues.get(index);
    }
}