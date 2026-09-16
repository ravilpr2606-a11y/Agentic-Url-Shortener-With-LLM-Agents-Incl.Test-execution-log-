package com.example.urlshortener.orchestration.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * I built this agent to propose a component/dependency view of the
 * change for the human architecture-approval gate.
 *
 * I gave it the same fallback contract as RequirementAgent: when I have
 * an LLM backend configured, it asks the model to reason about the
 * requirement and propose real components/risks; otherwise (or on any
 * failure) it falls back to a deterministic response so the workflow
 * never stalls on an unavailable model.
 */
@Component
public class ArchitectureAgent implements Agent {

    private static final String SYSTEM_PROMPT =
            "You are a software architect inside a governed software "
                    + "engineering orchestration system. Given a "
                    + "normalized requirement and its scenario type "
                    + "(GREENFIELD, BROWNFIELD, or AMBIGUOUS), respond "
                    + "with ONLY a JSON object -- no prose, no markdown "
                    + "code fences -- with exactly these fields: "
                    + "\"components\" (array of short strings naming the "
                    + "components/services/modules this change touches "
                    + "or introduces), "
                    + "\"dependencyGraph\" (short string describing the "
                    + "shape of the dependency graph, e.g. "
                    + "\"linear\" or \"fan-out-then-sync\"), "
                    + "\"risks\" (array of short strings naming concrete "
                    + "architectural or delivery risks; empty array if "
                    + "none).";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ArchitectureAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public String name() {
        return "ARCHITECTURE_AGENT";
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

        return deterministicFallback(scenario);
    }

    private Optional<String> safeLlmCall(String requirement, String scenario) {
        try {
            String userPrompt = "Scenario: " + scenario
                    + "\nRequirement: " + requirement;
            return llmClient.complete(SYSTEM_PROMPT, userPrompt);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String validate(String rawResponse, String scenario) {
        try {
            String candidate = stripMarkdownFences(rawResponse).trim();
            JsonNode node = objectMapper.readTree(candidate);

            if (!node.has("components") || !node.get("components").isArray()) {
                return null;
            }
            if (node.get("components").isEmpty()) {
                return null;
            }

            var enriched = objectMapper.createObjectNode();
            enriched.set("components", node.get("components"));
            enriched.put(
                    "dependencyGraph",
                    node.has("dependencyGraph") && node.get("dependencyGraph").isTextual()
                            ? node.get("dependencyGraph").asText()
                            : "explicit-DAG"
            );
            enriched.set(
                    "risks",
                    node.has("risks") && node.get("risks").isArray()
                            ? node.get("risks")
                            : objectMapper.createArrayNode()
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

    private String deterministicFallback(String scenario) {
        return "{"
                + "\"components\":[\"application-plane\",\"orchestration-plane\",\"policy\",\"audit\"],"
                + "\"dependencyGraph\":\"explicit-DAG\","
                + "\"risks\":[],"
                + "\"scenario\":\"" + scenario + "\","
                + "\"source\":\"deterministic-fallback\""
                + "}";
    }
}
