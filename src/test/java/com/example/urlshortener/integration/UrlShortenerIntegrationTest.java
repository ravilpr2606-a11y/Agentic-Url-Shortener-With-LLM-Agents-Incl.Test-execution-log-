package com.example.urlshortener.integration;

import com.example.urlshortener.domain.ClickEvent;
import com.example.urlshortener.domain.ShortUrl;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UrlShortenerIntegrationTest {

    static {
        TimeZone.setDefault(
                TimeZone.getTimeZone("America/Chicago")
        );
    }

    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("urlshortener")
                    .withUsername("urlshortener")
                    .withPassword("urlshortener")
                    .withCommand(
                            "postgres",
                            "-c",
                            "timezone=America/Chicago"
                    );

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureDatabase(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                () -> postgres.getJdbcUrl()
                        + "?options=-c%20TimeZone%3DAmerica%2FChicago"
        );

        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );

        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );

        registry.add(
                "spring.flyway.enabled",
                () -> true
        );
    }

    @AfterAll
    static void stopContainer() {
        postgres.stop();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @BeforeEach
    void cleanDatabase() {
        clickEventRepository.deleteAll();
        shortUrlRepository.deleteAll();
    }

    @Test
    void shouldCreateShortUrl() {

        Map<String, String> request = Map.of(
                "url",
                "https://example.com/integration-test"
        );

        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        "/api/v1/urls",
                        request,
                        Map.class
                );

        assertEquals(
                HttpStatus.CREATED,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertNotNull(
                response.getBody().get("shortCode")
        );

        assertNotNull(
                response.getBody().get("shortUrl")
        );

        assertEquals(
                "https://example.com/integration-test",
                response.getBody().get("originalUrl")
        );

        String shortCode =
                (String) response.getBody().get("shortCode");

        assertEquals(
                7,
                shortCode.length()
        );

        assertTrue(
                shortCode.matches("[a-zA-Z0-9]{7}")
        );

        assertTrue(
                shortUrlRepository
                        .findByShortCode(shortCode)
                        .isPresent()
        );
    }

    @Test
    void shouldRedirectToOriginalUrl() {

        Map<String, String> request = Map.of(
                "url",
                "https://example.com/redirect-test"
        );

        ResponseEntity<Map> createResponse =
                restTemplate.postForEntity(
                        "/api/v1/urls",
                        request,
                        Map.class
                );

        assertEquals(
                HttpStatus.CREATED,
                createResponse.getStatusCode()
        );

        assertNotNull(createResponse.getBody());

        String shortCode =
                (String) createResponse.getBody().get("shortCode");

        assertNotNull(shortCode);

        assertEquals(
                7,
                shortCode.length()
        );

        assertTrue(
                shortCode.matches("[a-zA-Z0-9]{7}")
        );

        ShortUrl savedShortUrl =
                shortUrlRepository
                        .findByShortCode(shortCode)
                        .orElseThrow();

        HttpHeaders headers = new HttpHeaders();

        headers.set(
                "User-Agent",
                "integration-test-agent"
        );

        headers.set(
                "Referer",
                "https://google.com"
        );

        HttpEntity<Void> redirectRequest =
                new HttpEntity<>(headers);

        String redirectUrl =
                "http://127.0.0.1:"
                        + port
                        + "/"
                        + shortCode;

        RestTemplate directRestTemplate =
                new RestTemplate();

        ResponseEntity<Void> redirectResponse =
                directRestTemplate.exchange(
                        redirectUrl,
                        HttpMethod.GET,
                        redirectRequest,
                        Void.class
                );

        assertEquals(
                HttpStatus.FOUND,
                redirectResponse.getStatusCode(),
                "Redirect failed. URL="
                        + redirectUrl
                        + ", Status="
                        + redirectResponse.getStatusCode()
                        + ", Headers="
                        + redirectResponse.getHeaders()
        );

        assertEquals(
                URI.create(
                        "https://example.com/redirect-test"
                ),
                redirectResponse.getHeaders().getLocation()
        );

        assertEquals(
                1,
                clickEventRepository.countByShortUrlId(
                        savedShortUrl.getId()
                )
        );
    }

    @Test
    void shouldRecordAnalyticsForRedirect() {

        ShortUrl shortUrl = new ShortUrl(
                UUID.randomUUID(),
                "An45Bc7",
                "https://example.com/analytics-test",
                Instant.now()
        );

        shortUrlRepository.save(shortUrl);

        HttpHeaders headers = new HttpHeaders();

        headers.set(
                "User-Agent",
                "integration-test-agent"
        );

        headers.set(
                "Referer",
                "https://example.com/source"
        );

        HttpEntity<Void> request =
                new HttpEntity<>(headers);

        restTemplate.exchange(
                "/An45Bc7",
                HttpMethod.GET,
                request,
                Void.class
        );

        ResponseEntity<Map> response =
                restTemplate.getForEntity(
                        "/api/v1/urls/An45Bc7/analytics",
                        Map.class
                );

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "An45Bc7",
                response.getBody().get("shortCode")
        );

        assertEquals(
                1,
                response.getBody().get("totalClicks")
        );

        assertNotNull(
                response.getBody().get("lastAccessedAt")
        );

        ClickEvent clickEvent =
                clickEventRepository.findAll()
                        .stream()
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                "https://example.com/source",
                clickEvent.getReferrer()
        );

        assertEquals(
                "integration-test-agent",
                clickEvent.getUserAgent()
        );
    }

    @Test
    void shouldReturnZeroAnalyticsForUnusedShortUrl() {

        ShortUrl shortUrl = new ShortUrl(
                UUID.randomUUID(),
                "Zero123",
                "https://example.com/no-clicks",
                Instant.now()
        );

        shortUrlRepository.save(shortUrl);

        ResponseEntity<Map> response =
                restTemplate.getForEntity(
                        "/api/v1/urls/Zero123/analytics",
                        Map.class
                );

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "Zero123",
                response.getBody().get("shortCode")
        );

        assertEquals(
                0,
                response.getBody().get("totalClicks")
        );

        assertNull(
                response.getBody().get("lastAccessedAt")
        );
    }

    @Test
    void shouldReturnBadRequestForInvalidUrl() {

        Map<String, String> request = Map.of(
                "url",
                "ftp://example.com/file"
        );

        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        "/api/v1/urls",
                        request,
                        Map.class
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "INVALID_URL",
                response.getBody().get("code")
        );
    }

    @Test
    void shouldReturnBadRequestForBlankUrl() {

        Map<String, String> request = Map.of(
                "url",
                " "
        );

        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        "/api/v1/urls",
                        request,
                        Map.class
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "INVALID_REQUEST",
                response.getBody().get("code")
        );
    }

    @Test
    void shouldReturnNotFoundForUnknownShortCode() {

        ResponseEntity<Map> response =
                restTemplate.getForEntity(
                        "/DOESNOTEXIST",
                        Map.class
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "SHORT_URL_NOT_FOUND",
                response.getBody().get("code")
        );
    }

    @Test
    void shouldCreateDifferentShortCodesForDuplicateUrls() {

        Map<String, String> request = Map.of(
                "url",
                "https://example.com/duplicate"
        );

        ResponseEntity<Map> first =
                restTemplate.postForEntity(
                        "/api/v1/urls",
                        request,
                        Map.class
                );

        ResponseEntity<Map> second =
                restTemplate.postForEntity(
                        "/api/v1/urls",
                        request,
                        Map.class
                );

        assertEquals(
                HttpStatus.CREATED,
                first.getStatusCode()
        );

        assertEquals(
                HttpStatus.CREATED,
                second.getStatusCode()
        );

        assertNotNull(first.getBody());
        assertNotNull(second.getBody());

        String firstCode =
                (String) first.getBody().get("shortCode");

        String secondCode =
                (String) second.getBody().get("shortCode");

        assertNotEquals(
                firstCode,
                secondCode
        );

        assertEquals(
                2,
                shortUrlRepository.count()
        );
    }

    @Test
    void shouldRedirectWhenExpirationIsInTheFuture() {

        Instant expiresAt =
                Instant.now().plusSeconds(3600);

        Map<String, Object> request = Map.of(
                "url",
                "https://example.com/future-expiration",
                "expiresAt",
                expiresAt.toString()
        );

        ResponseEntity<Map> createResponse =
                restTemplate.postForEntity(
                        "/api/v1/urls",
                        request,
                        Map.class
                );

        assertEquals(
                HttpStatus.CREATED,
                createResponse.getStatusCode()
        );

        assertNotNull(createResponse.getBody());

        String shortCode =
                (String) createResponse.getBody().get("shortCode");

        assertNotNull(shortCode);

        String redirectUrl =
                "http://127.0.0.1:"
                        + port
                        + "/"
                        + shortCode;

        RestTemplate directRestTemplate =
                new RestTemplate();

        ResponseEntity<Void> redirectResponse =
                directRestTemplate.getForEntity(
                        redirectUrl,
                        Void.class
                );

        assertEquals(
                HttpStatus.FOUND,
                redirectResponse.getStatusCode()
        );

        assertEquals(
                URI.create(
                        "https://example.com/future-expiration"
                ),
                redirectResponse.getHeaders().getLocation()
        );

        ShortUrl savedShortUrl =
                shortUrlRepository
                        .findByShortCode(shortCode)
                        .orElseThrow();

        assertEquals(
                1,
                clickEventRepository.countByShortUrlId(
                        savedShortUrl.getId()
                )
        );
    }

    @Test
    void shouldReturnNotFoundWhenShortUrlHasExpired() {

        Instant expiresAt =
                Instant.now().minusSeconds(60);

        Map<String, Object> request = Map.of(
                "url",
                "https://example.com/expired",
                "expiresAt",
                expiresAt.toString()
        );

        ResponseEntity<Map> createResponse =
                restTemplate.postForEntity(
                        "/api/v1/urls",
                        request,
                        Map.class
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                createResponse.getStatusCode()
        );

        ShortUrl expiredShortUrl = new ShortUrl(
                UUID.randomUUID(),
                "Exp1234",
                "https://example.com/expired",
                Instant.now().minusSeconds(120),
                expiresAt
        );

        shortUrlRepository.save(expiredShortUrl);

        String redirectUrl =
                "http://127.0.0.1:"
                        + port
                        + "/Exp1234";

        RestTemplate directRestTemplate =
                createNonThrowingRestTemplate();

        ResponseEntity<Map> redirectResponse =
                directRestTemplate.getForEntity(
                        redirectUrl,
                        Map.class
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                redirectResponse.getStatusCode()
        );

        assertNotNull(
                redirectResponse.getBody()
        );

        assertEquals(
                "SHORT_URL_EXPIRED",
                redirectResponse.getBody().get("code")
        );

        assertEquals(
                "Short URL has expired",
                redirectResponse.getBody().get("message")
        );
    }

    @Test
    void shouldNotRecordAnalyticsForExpiredShortUrl() {

        Instant expiresAt =
                Instant.now().minusSeconds(60);

        ShortUrl expiredShortUrl = new ShortUrl(
                UUID.randomUUID(),
                "NoClik1",
                "https://example.com/expired-no-click",
                Instant.now().minusSeconds(120),
                expiresAt
        );

        shortUrlRepository.save(expiredShortUrl);

        String redirectUrl =
                "http://127.0.0.1:"
                        + port
                        + "/NoClik1";

        RestTemplate directRestTemplate =
                createNonThrowingRestTemplate();

        ResponseEntity<Map> redirectResponse =
                directRestTemplate.getForEntity(
                        redirectUrl,
                        Map.class
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                redirectResponse.getStatusCode()
        );

        assertEquals(
                0,
                clickEventRepository.countByShortUrlId(
                        expiredShortUrl.getId()
                )
        );
    }

    @Test
    void shouldReturnBadRequestForMalformedExpiration() {

        Map<String, String> request = Map.of(
                "url",
                "https://example.com/malformed-expiration",
                "expiresAt",
                "not-a-date"
        );

        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        "/api/v1/urls",
                        request,
                        Map.class
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );

        assertNotNull(
                response.getBody()
        );

        assertEquals(
                "INVALID_REQUEST",
                response.getBody().get("code")
        );

        assertEquals(
                "Request body contains invalid or malformed data",
                response.getBody().get("message")
        );
    }

    private RestTemplate createNonThrowingRestTemplate() {

        RestTemplate template =
                new RestTemplate();

        template.setErrorHandler(
                new DefaultResponseErrorHandler() {

                    @Override
                    public boolean hasError(
                            ClientHttpResponse response
                    ) throws IOException {
                        return false;
                    }
                }
        );

        return template;
    }
}