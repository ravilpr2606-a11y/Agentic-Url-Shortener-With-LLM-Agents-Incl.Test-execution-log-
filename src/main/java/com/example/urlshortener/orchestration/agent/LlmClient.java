package com.example.urlshortener.orchestration.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * I built this as a thin client around a real LLM API call, used by
 * orchestration agents that want to reason about requirement/architecture
 * text instead of returning a fixed deterministic string.
 *
 * My design intent:
 * - I disabled this by default (llm.enabled=false). Every caller must be
 *   able to run correctly, deterministically and offline with this
 *   disabled -- that is what keeps my existing unit/integration test
 *   suite passing without network access or an API key.
 * - When enabled, I made sure a failure (missing key, network error,
 *   timeout, unexpected response shape) NEVER propagates as an exception.
 *   Callers always get an empty Optional and are expected to fall back to
 *   their own deterministic behavior. I don't want an orchestration agent
 *   to crash a workflow because an upstream model call failed.
 * - I intentionally did not add retry here. The orchestration layer
 *   already has its own bounded-retry/fallback/compensation machinery for
 *   workflow nodes; retrying silently inside the LLM client would hide
 *   failures from that governance layer instead of surfacing them.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    @Value("${llm.enabled:false}")
    private boolean enabled;

    @Value("${llm.api-key:${ANTHROPIC_API_KEY:}}")
    private String apiKey;

    @Value("${llm.model:claude-3-5-haiku-20241022}")
    private String model;

    @Value("${llm.base-url:https://api.anthropic.com/v1/messages}")
    private String baseUrl;

    @Value("${llm.timeout-millis:4000}")
    private long timeoutMillis;

    @Value("${llm.max-tokens:512}")
    private int maxTokens;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LlmClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * I call the configured LLM with a system prompt and a user prompt and
     * return the raw text of its response.
     *
     * I return Optional.empty() -- never throw -- when:
     * - the LLM integration is disabled,
     * - no API key is configured,
     * - the request times out or the network call fails,
     * - the response is not a 2xx, or
     * - the response body cannot be parsed into the expected shape.
     */
    public Optional<String> complete(String systemPrompt, String userPrompt) {

        if (!enabled) {
            return Optional.empty();
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("LLM integration is enabled but no API key is configured; falling back to deterministic agent behavior.");
            return Optional.empty();
        }

        try {
            String requestBody = buildRequestBody(systemPrompt, userPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .header("content-type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("LLM call returned non-2xx status {}; falling back to deterministic agent behavior.", response.statusCode());
                return Optional.empty();
            }

            return extractText(response.body());

        } catch (Exception e) {
            // I catch broadly here on purpose: this covers IOException,
            // InterruptedException, HttpTimeoutException and any JSON
            // parsing failure. I want an agent's LLM call failing to
            // degrade to the deterministic fallback, not fail the
            // workflow node.
            log.warn("LLM call failed ({}); falling back to deterministic agent behavior.", e.toString());
            return Optional.empty();
        }
    }

    private String buildRequestBody(String systemPrompt, String userPrompt) throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", maxTokens);
        root.put("system", systemPrompt);

        var messages = objectMapper.createArrayNode();
        var userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);
        messages.add(userMessage);

        root.set("messages", messages);

        return objectMapper.writeValueAsString(root);
    }

    private Optional<String> extractText(String responseBody) {
        try {
            JsonNode parsed = objectMapper.readTree(responseBody);
            JsonNode content = parsed.get("content");

            if (content == null || !content.isArray() || content.isEmpty()) {
                return Optional.empty();
            }

            JsonNode firstBlock = content.get(0);
            JsonNode text = firstBlock.get("text");

            if (text == null || !text.isTextual()) {
                return Optional.empty();
            }

            return Optional.of(text.asText());

        } catch (Exception e) {
            log.warn("Could not parse LLM response body: {}", e.toString());
            return Optional.empty();
        }
    }
}
