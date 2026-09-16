# LLM Integration (RequirementAgent, ArchitectureAgent)

## What I changed

`RequirementAgent` and `ArchitectureAgent` previously returned a fixed,
deterministic string regardless of the actual requirement text — they
demonstrated the orchestration *shape* (an agent role in the DAG) but not
real reasoning. I made them call a real LLM through a new `LlmClient`
when configured, and fall back to the original deterministic behavior
when it is not.

## Why I chose this design, not something more invasive

I deliberately did not:
- Convert all six agents to LLM calls. I focused on requirement
  understanding and architecture proposal because that is where
  "reasoning about ambiguous text" actually matters for the assessment's
  evaluation criteria. I kept the Security, Test, Documentation, and
  Release agents producing structured, predictable artifacts from
  already-decided upstream state; converting them would have added LLM
  latency/cost/nondeterminism to stages where I don't think it adds much
  evaluative value.
- Make the LLM call mandatory. I default `llm.enabled` to `false` (see
  `application.yml`). This is what keeps the whole orchestration test
  suite deterministic, offline, and free for me (or a reviewer) to run
  without an API key. I made enabling it an explicit opt-in
  (`LLM_ENABLED=true`, `ANTHROPIC_API_KEY=<key>`).
- Retry inside the LLM client. The orchestration layer already owns
  bounded retry, fallback, compensation, and safe-stop as first-class,
  audited workflow behavior. I didn't want to duplicate that machinery
  or hide failures from the governance layer that is supposed to observe
  them, so I left `LlmClient` retry-free.

## The fallback contract I built

`LlmClient.complete(...)` returns `Optional<String>` and I designed it to
**never throw**. I return `Optional.empty()` when:
- the integration is disabled,
- no API key is configured,
- the network call fails or times out,
- the response is not a 2xx, or
- the response body cannot be parsed.

I also made `RequirementAgent` and `ArchitectureAgent` each validate that
a present LLM response is well-formed JSON containing the fields they
require. An `Optional.empty()` or a response that fails my validation
always resolves to the agent's deterministic fallback — I made sure the
workflow state machine never sees a null, an exception, or a malformed
artifact.

I tag every agent response with `"source":"llm"` or
`"source":"deterministic-fallback"` so the audit trail
(`WorkflowArtifact.content`) always shows which path actually produced a
given artifact, rather than me asserting reasoning happened when it did
not.

## What this changes in orchestration

I made `AMBIGUITY_CHECK` consult `RequirementAgent` a second time and read
its `ambiguityDetected` field as an additional signal alongside the
existing explicit `scenario == "AMBIGUOUS"` flag. Either signal routes the
workflow to the human `CLARIFICATION` gate. When the LLM integration is
disabled, `ambiguityDetected` only ever reflects the same deterministic
scenario check as before, so I kept this backward compatible with every
existing scenario and test.

## What I have and have not verified

I have executed `mvn clean test`, `mvn clean verify`, and a further
`mvn clean test` with this change in place. My first two runs were 54/54,
and my final run was 58/58 after I added the four orchestration governance
tests. All three runs reached `BUILD SUCCESS` with zero failures.
`RequirementAgentTest` and `ArchitectureAgentTest` (4 tests) pass in every
run, and no pre-existing test regressed — which is my concrete evidence
that this integration is backward compatible with the disabled path the
rest of the suite relies on. The full capture for all three runs is in
`docs/assessment/test-execution-log.md`.

What I have **not** verified is the live-model path. I have never executed
this against a real Anthropic API response, so prompt quality, response
parsing against real output, timeout behaviour under real latency, and
error handling for real API failures are all unvalidated. The automated
coverage proves only that the fallback is correct and that enabling the
feature flag is the sole thing standing between the two paths.

## How I enable it

```bash
export LLM_ENABLED=true
export ANTHROPIC_API_KEY=<key>
mvn spring-boot:run
```

Optional overrides I support: `LLM_MODEL` (default
`claude-3-5-haiku-20241022`), `LLM_TIMEOUT_MILLIS` (default `4000`),
`LLM_MAX_TOKENS` (default `512`), `LLM_BASE_URL`.
