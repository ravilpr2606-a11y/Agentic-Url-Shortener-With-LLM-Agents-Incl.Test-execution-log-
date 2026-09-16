# Agentic Software Engineering System — URL Shortener

**Test status: 58/58 passing — 0 failures, 0 errors, 0 skipped.** I
verified this across three independent clean runs: `mvn clean test`,
`mvn clean verify` (which also built the runnable
`target/url-shortener-0.0.1-SNAPSHOT.jar`), and a second `mvn clean test`
after adding four `OrchestrationServiceTest` cases covering
concurrent/out-of-order approval and replan governance. All three runs
reached `BUILD SUCCESS` against a Testcontainers-provisioned PostgreSQL 17
instance. Full console output for all three runs is in
`docs/assessment/test-execution-log.md`.

## What I built

I started from the existing URL shortener and kept it as the application plane. I then added a persisted orchestration/control plane around it so I could demonstrate the SDLC behaviors the assessment asks for: requirement understanding, decomposition, human gates, sequential and parallel execution, synchronization, recovery, replanning, auditability, policy checks and release readiness.

I deliberately kept the design as a modular PostgreSQL-backed monolith. The assessment is timeboxed, so I chose demonstrability and testability over introducing a distributed workflow platform that would add infrastructure without improving the evidence I could demonstrate locally.

## Architecture I use

```text
CONTROL PLANE
Requirement -> Normalize -> Quality -> [Clarify | Brownfield] -> Decompose -> Design
                                                       |
                                             Human Architecture Gate
                                                       |
                                      +----------------+----------------+
                                      |                                 |
                               Security Agent                    Test Agent
                                      |                                 |
                                      +------------ Synchronize --------+
                                                       |
                                             Implementation
                                                       |
                                          Testing -> Documentation
                                                       |
                                           Validation -> Release Gate

APPLICATION PLANE
REST API -> URL services -> PostgreSQL
```

I kept the two planes separate so the orchestration requirement does not get buried inside URL-shortener controllers.

## What the application does

I retained the original URL-shortener behaviors:

- `POST /api/v1/urls`
- `GET /{shortCode}`
- `GET /api/v1/urls/{shortCode}/analytics`
- expiration
- Base62 7-character secure code generation
- collision protection
- PostgreSQL persistence with Flyway
- unit, integration and performance tests

I added these orchestration and operational endpoints:

- `POST /api/v1/workflows`
- `GET /api/v1/workflows/{id}`
- `POST /api/v1/workflows/{id}/approvals/{gate}`
- `POST /api/v1/workflows/{id}/clarification` (where applicable)
- `POST /api/v1/workflows/{id}/replan`
- `POST /api/v1/workflows/{id}/retry`
- `POST /api/v1/workflows/{id}/simulate-failure?kind=transient|timeout|permanent|interrupt`
- `POST /api/v1/workflows/{id}/resume`
- `GET /api/v1/workflows/metrics`
- `GET /api/v1/health`

I also added `Idempotency-Key` handling for URL creation and API rate limiting.

## The workflow states I persist

`REQUIREMENT_INGESTION`, `REQUIREMENT_NORMALIZATION`, `AMBIGUITY_CHECK`, `CLARIFICATION`, `BROWNFIELD_IMPACT`, `POLICY_EXCEPTION`, `DECOMPOSITION`, `DESIGN`, `ARCHITECTURE_APPROVAL`, `SECURITY`, `TEST_PLANNING`, `SYNCHRONIZE`, `IMPLEMENTATION`, `TESTING`, `DOCUMENTATION`, `VALIDATION`, `RELEASE_READINESS`, `RELEASE_APPROVAL`, `REPLAN`, `COMPENSATION`, `SAFE_STOP`, `COMPLETED`.

## LLM-backed agents

`RequirementAgent` and `ArchitectureAgent` can call a real LLM to reason about the actual requirement text — normalize it, detect material ambiguity, propose components/risks — instead of returning a fixed string. This is opt-in and off by default (`LLM_ENABLED=false`), so the full test suite and the orchestration flow run deterministically and offline unless you explicitly enable it. On any failure (no key, network error, timeout, malformed response) it always falls back to the original deterministic behavior; nothing about the workflow state machine depends on the LLM call succeeding. Full design and the exact fallback contract are in `docs/architecture/llm-integration.md`.

## How I handle human ownership

I do not treat agent output as approval. I keep architecture, material ambiguity, security-sensitive exceptions, destructive changes, release readiness and final submission under explicit human control. Silence is never treated as approval.

## Reliability behavior

I use bounded retries for transient failures. Permanent failures take the fallback/compensation path and then safe-stop. The parallel security/test-planning fan-out has a real configurable deadline through `ORCHESTRATION_AGENT_TIMEOUT_MILLIS`; a timeout is treated as a recoverable control failure first and then routed through fallback, compensation and safe-stop when the workflow cannot safely continue.

For MTTR, I pair each recovered failure with its originating failure-event ID. I exclude unrecovered failure events from the MTTR denominator and report them separately.

## How I validated the workflow

The repository contains the executed evidence I captured during the assessment. The key scenarios are documented in `docs/assessment/executed-evidence.md`, including:

- a clean greenfield workflow that reached `COMPLETED` after architecture and release approvals;
- a brownfield workflow that reached the release-approval gate after impact analysis;
- an ambiguous workflow that went through clarification, governed replanning, artifact invalidation/regeneration, rejection, safe-stop, resume and renewed approval;
- a transient-failure run that exhausted the bounded retry budget;
- a permanent-failure run that demonstrated fallback -> compensation -> safe-stop.

I have intentionally not invented Git history, historical SpecKit CLI output, or approvals that were not actually recorded.

## Run locally

I use Java 21, Maven and Docker.

```bash
docker compose up -d
mvn clean test
mvn clean verify
mvn spring-boot:run
```

The test suite provisions its own PostgreSQL 17 via Testcontainers, so
`mvn clean test` needs a running Docker daemon but not the compose
stack. `docker compose up -d` is what backs `mvn spring-boot:run`.

Health:

```bash
curl http://localhost:8080/api/v1/health
```

Create a short URL:

```bash
curl -i -X POST http://localhost:8080/api/v1/urls \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-001' \
  -d '{"url":"https://example.com"}'
```

## Important limitations I am carrying forward

I have documented the remaining production hardening items instead of hiding them: there is no authentication/authorization layer, rate limiting is single-node/in-memory, and SSRF protection is a prototype control rather than network-level egress enforcement. `RequirementAgent` and `ArchitectureAgent` can call a real LLM for genuine reasoning (see "LLM-backed agents" below); it is opt-in and off by default, and every call falls back to deterministic behavior on failure. The Security, Test, Documentation, and Release agents remain deterministic.

I also do not claim historical Git/SpecKit/TDD evidence that was not preserved in the submitted archive. Those limitations are called out explicitly in the assessment documents.
