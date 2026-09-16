package com.example.urlshortener.orchestration.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I wrote this to exercise RequirementAgent with a plain, unconfigured
 * LlmClient (no Spring context, no network). LlmClient's @Value fields
 * are never populated here, so "enabled" keeps its Java default of false
 * and every call short-circuits to Optional.empty() -- meaning these
 * tests verify exactly the offline/no-API-key path the rest of my suite
 * relies on.
 */
class RequirementAgentTest {

    private final RequirementAgent agent = new RequirementAgent(new LlmClient());

    @Test
    void fallsBackToDeterministicResponseWhenLlmIsNotConfigured() {
        String result = agent.execute("Add click analytics", "GREENFIELD");

        assertTrue(result.contains("\"source\":\"deterministic-fallback\""));
        assertTrue(result.contains("\"normalizedRequirement\""));
        assertTrue(result.contains("\"ambiguityDetected\":false"));
    }

    @Test
    void deterministicFallbackFlagsAmbiguousScenario() {
        String result = agent.execute("Make it better somehow", "AMBIGUOUS");

        assertTrue(result.contains("\"ambiguityDetected\":true"));
    }
}
