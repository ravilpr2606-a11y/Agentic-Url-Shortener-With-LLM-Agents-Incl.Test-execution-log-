# Live Model Validation

## Status: NOT YET EXECUTED

I have not run this. I am writing the plan and the harness down first so
that when I do run it, the evidence is captured in a fixed shape rather
than assembled after the fact — and so a reviewer can see exactly what I
intend to prove before I prove it.

**Do not read this document as evidence.** The evidence file it refers to,
`docs/assessment/live-model-capture-output.txt`, does not exist until
`scripts/live-model-capture.ps1` has been run successfully.

## Why this gap matters more than its size suggests

My suite proves 58/58 passing, but every one of those tests exercises the
LLM integration with `enabled=false`. `RequirementAgentTest` and
`ArchitectureAgentTest` construct a bare `new LlmClient()` outside Spring,
so the `@Value` fields are never populated and `enabled` keeps its Java
default of `false`. What I have therefore proven is that the *fallback* is
correct. I have proven nothing about the path that runs when someone sets
`LLM_ENABLED=true`.

Specifically, these remain unvalidated:

- whether my system prompt reliably produces parseable JSON from a real
  model,
- whether `RequirementAgent.validate` accepts real model output rather than
  rejecting it and silently falling back,
- whether `stripMarkdownFences` handles how the model actually wraps JSON,
- whether `llm.timeout-millis: 4000` is realistic for live latency,
- whether a real API error (401, 429, 529) degrades the way I designed.

The failure mode I am most concerned about is the quiet one: if
`validate()` rejects every real response, the system falls back
successfully, logs a warning, and looks like it is working. Nothing fails.
I would not know the integration is inert unless I check provenance.

## What the harness proves

`scripts/live-model-capture.ps1` is built around one decisive test.

I submit a deliberately vague requirement with scenario `GREENFIELD` —
**not** `AMBIGUOUS`. In the deterministic path,
`deterministicFallback` sets `ambiguityDetected` to
`"AMBIGUOUS".equals(scenario)`, which is `false` here, and
`OrchestrationService.AMBIGUITY_CHECK` would route straight past
clarification to `DECOMPOSITION`. So if the run lands at the
`CLARIFICATION` gate, the only thing that could have put it there is the
model's own verdict.

That single observation demonstrates, in one step:

1. the live call executed (provenance `"source":"llm"`, not
   `"source":"deterministic-fallback"`),
2. the response parsed and passed my validation,
3. model reasoning drove a real governance decision,
4. and the human gate still held — the model can *raise* a gate but never
   bypass one.

The harness also captures a control case (a crisp, precisely-specified
`GREENFIELD` requirement) to show the model discriminates rather than
flagging everything. A model that flags every requirement would produce the
same result as case 1 while being useless.

## Running it

```powershell
docker compose up -d                 # the app needs this; the test suite does not
$env:ANTHROPIC_API_KEY = "<key>"
.\scripts\live-model-capture.ps1
```

Output lands in `docs/assessment/live-model-capture-output.txt`.

## How to read the result

| Observation | Meaning |
|---|---|
| `"source":"llm"` in the artifact | The live path executed end to end. |
| `"source":"deterministic-fallback"` | The live path did **not** execute. Check the log lines the harness extracts. |
| Case 1 at `CLARIFICATION` | Model reasoning drove a governance decision. |
| Case 2 at `ARCHITECTURE_APPROVAL` | The model discriminates; the signal is meaningful. |
| Both cases flagged | The signal is noise, even if the call worked. |

If provenance comes back as `deterministic-fallback`, the integration is
inert and I should say so plainly rather than presenting a passing suite as
though it covered this.

## Secondary gap this also closes

The harness captures the live `GET /api/v1/workflows/metrics` response
body, which is the other item I had listed as unconfirmed in
`docs/assessment/test-execution-log.md`. That endpoint is exercised
indirectly by `OrchestrationServiceTest.metricsPairsRecoveredFailureEventsAndExcludesUnrecoveredEvents`,
but I had never captured a live response shape.

## What this will still not prove

Even a clean capture is a single observation, not coverage. It will not
establish prompt stability across model versions, behaviour under rate
limiting, cost per workflow, or determinism of the model's ambiguity
verdict across repeated runs on the same input. Treating one successful
capture as validation of the live path would overstate it, and I would
rather name that limit than have a reviewer find it.
