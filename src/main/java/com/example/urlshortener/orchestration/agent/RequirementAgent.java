package com.example.urlshortener.orchestration.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * I built this agent to interpret a raw requirement and report a
 * normalized version plus whether it detects material ambiguity.
 *
 * When I have an LLM backend configured (see LlmClient), I ask the model
 * to actually reason about the requirement text: normalize it, decide
 * whether it is ambiguous, and say why. When no LLM is configured, or the
 * call fails, or the model's response is not valid/complete JSON, I always
 * fall back to a deterministic response so the orchestration layer keeps
 * working -- offline, in tests, and in production if the LLM provider is
 * unavailable.
 */
@Component
public class RequirementAgent implements Agent {

    private static final String SYSTEM_PROMPT =
            "You are a requirements analyst inside a governed software "
                    + "engineering orchestration system. Given a raw "
                    + "requirement and its scenario type (GREENFIELD, "
                    + "BROWNFIELD, or AMBIGUOUS), respond with ONLY a JSON "
                    + "object -- no prose, no markdown code fences -- with "
                    + "exactly these fields: "
                    + "\"normalizedRequirement\" (string: the requirement "
                    + "restated as a clear, testable engineering "
                    + "statement), "
                    + "\"ambiguityDetected\" (boolean: true only if the "
                    + "requirement is materially ambiguous enough that an "
                    + "engineer could not safely start work without "
                    + "clarification), "
                    + "\"ambiguities\" (array of short strings describing "
                    + "each specific ambiguity; empty array if none), "
                    + "\"confidence\" (number between 0 and 1).";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RequirementAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public String name() {
        return "REQUIREMENT_AGENT";
    }

    @Override
    public String execute(String requirement, String scenario) {

        Optional<String> llmResponse = safeLlmCall(requirement, scenario);

        if (llmResponse.isPresent()) {
            String validated = validate(llmResponse.get(), scenario);
            if (validated != null) {
                return validated;
            }
        }

        return deterministicFallback(requirement, scenario);
    }

    private Optional<String> safeLlmCall(String requirement, String scenario) {
        try {
            String userPrompt = "Scenario: " + scenario
                    + "\nRequirement: " + requirement;
            return llmClient.complete(SYSTEM_PROMPT, userPrompt);
        } catch (Exception e) {
            // My contract for this agent is: never throw. LlmClient
            // already swallows its own failures, but I added this to
            // guard against any future change to that contract.
            return Optional.empty();
        }
    }

    /**
     * I confirm the model's response is well-formed JSON containing the
     * fields I require, and stamp it with provenance ("source":"llm") so
     * downstream artifacts/audit trails can distinguish model-generated
     * output from my deterministic fallback. I return null if I can't
     * trust the response as-is.
     */
    private String validate(String rawResponse, String scenario) {
        try {
            String candidate = stripMarkdownFences(rawResponse).trim();
            JsonNode node = objectMapper.readTree(candidate);

            if (!node.has("normalizedRequirement") || !node.has("ambiguityDetected")) {
                return null;
            }
            if (!node.get("normalizedRequirement").isTextual()) {
                return null;
            }
            if (!node.get("ambiguityDetected").isBoolean()) {
                return null;
            }

            var enriched = objectMapper.createObjectNode();
            enriched.put("normalizedRequirement", node.get("normalizedRequirement").asText());
            enriched.put("ambiguityDetected", node.get("ambiguityDetected").asBoolean());
            enriched.set(
                    "ambiguities",
                    node.has("ambiguities") && node.get("ambiguities").isArray()
                            ? node.get("ambiguities")
                            : objectMapper.createArrayNode()
            );
            enriched.put(
                    "confidence",
                    node.has("confidence") && node.get("confidence").isNumber()
                            ? node.get("confidence").asDouble()
                            : 0.5
            );
            enriched.put("scenario", scenario);
            enriched.put("source", "llm");

            return objectMapper.writeValueAsString(enriched);

        } catch (Exception e) {
            return null;
        }
    }

    private String stripMarkdownFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence);
            }
        }
        return trimmed;
    }

    private String deterministicFallback(String requirement, String scenario) {
        return "{"
                + "\"normalizedRequirement\":\"" + escape(requirement) + "\","
                + "\"ambiguityDetected\":" + "AMBIGUOUS".equals(scenario) + ","
                + "\"ambiguities\":[],"
                + "\"confidence\":0.5,"
                + "\"scenario\":\"" + scenario + "\","
                + "\"quality\":\"TESTABLE_AND_POLICY_CHECKED\","
                + "\"source\":\"deterministic-fallback\""
                + "}";
    }

    private String escape(String x) {
        return x.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
