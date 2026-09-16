package com.example.urlshortener.orchestration.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I gave this the same offline/no-API-key contract as
 * RequirementAgentTest: LlmClient is unconfigured (Java default
 * enabled=false), so execute() must always resolve through the
 * deterministic fallback path I built.
 */
class ArchitectureAgentTest {

    private final ArchitectureAgent agent = new ArchitectureAgent(new LlmClient());

    @Test
    void fallsBackToDeterministicResponseWhenLlmIsNotConfigured() {
        String result = agent.execute("Add click analytics", "GREENFIELD");

        assertTrue(result.contains("\"source\":\"deterministic-fallback\""));
        assertTrue(result.contains("\"components\""));
        assertTrue(result.contains("\"dependencyGraph\""));
    }

    @Test
    void includesScenarioInFallbackResponse() {
        String result = agent.execute("Refactor the analytics module", "BROWNFIELD");

        assertTrue(result.contains("\"scenario\":\"BROWNFIELD\""));
    }
}
