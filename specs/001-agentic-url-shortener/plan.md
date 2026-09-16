# Technical Plan

## Boundary
Application plane: URL API, redirect, analytics, persistence. Control plane: workflow engine, agents, policy, approvals, audit, metrics.

## Runtime graph
`INGESTION -> NORMALIZATION -> AMBIGUITY_CHECK -> [CLARIFICATION -> REPLAN] -> [BROWNFIELD_IMPACT] -> DECOMPOSITION -> DESIGN -> ARCHITECTURE_APPROVAL -> (SECURITY || TEST_PLANNING) -> SYNCHRONIZE -> IMPLEMENTATION -> TESTING -> DOCUMENTATION -> VALIDATION -> RELEASE_READINESS -> RELEASE_APPROVAL -> COMPLETED`.

Failure edges: recoverable failure -> bounded RETRY; timeout/permanent failure -> FALLBACK -> COMPENSATION -> SAFE_STOP; any unsafe continuation -> SAFE_STOP; recoverable interruption -> RESUME -> COMPENSATION/active node. Upstream changes -> REPLAN -> invalidate downstream artifacts -> regenerate.

## Persistence
PostgreSQL/Flyway stores workflow runs, events, artifacts, approvals, policy evaluations and idempotency records. This is deliberately a modular monolith: the assessment requires production-grade discipline, not distributed-system scale.

## Agents
Requirement, architecture, security, test, documentation and release agents implement bounded role contracts. Agents propose/produce artifacts; the human owns material decisions and approvals.

## API contracts
See `contracts/openapi.yaml`. Workflow responses expose state, scenario, policy version and retry count. Inspection exposes graph, artifacts, approvals, policies and event history.

## Reliability
Maximum retry count = 3. Demonstration failure injection is explicitly labeled. Permanent failure enters compensation; compensation never claims to reverse an irreversible external side effect.

## Security
HTTP/HTTPS only, 2048-char URL limit, local/private destination rejection, rate limiting, no sensitive values in audit payloads, environment-based database credentials and dependency scanning.

## Traceability
Requirement -> scenario -> plan/ADR -> task -> source path -> test -> validation -> evidence.
