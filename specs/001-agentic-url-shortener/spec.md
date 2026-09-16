# Agentic Software Engineering System: URL Shortener

## Objective
Transform a software requirement into a reviewable engineering outcome using governed, stateful, non-linear orchestration over the existing URL shortener.

## Requirements
- FR-001 Requirement understanding: ingest, normalize and quality-check requirements.
- FR-002 Ambiguity: suspend unsafe execution and require explicit human clarification.
- FR-003 Brownfield: analyze impacted modules, interfaces, persistence, tests, telemetry, docs and regression risk before change.
- FR-004 Dependency graph: persist an explicit DAG with sequential, parallel and synchronized paths.
- FR-005 State: persist workflow state, context, artifact versions and terminal status.
- FR-006 Governance: enforce architecture, clarification, policy-exception and release approvals; rejection safe-stops.
- FR-007 Reliability: bounded retries (3), failure classification, a real deadline for the parallel security/test-planning stage, timeout cancellation/fallback, compensation and safe-stop.
- FR-008 Recovery: resume through a controlled recovery path without skipping gates.
- FR-009 Replanning: upstream requirement changes invalidate downstream artifacts and regenerate from the correct state.
- FR-010 Policy: record versioned policy outcomes; mandatory failures block progression; exceptions require rationale, scope, compensating control and expiry.
- FR-011 Audit: record run id, actor, action, node, timestamp, result, reason and payload/artifact provenance.
- FR-012 Metrics: expose success/failure, retry, compensation, per-failure-event MTTR inputs, recovered/unrecovered failure events and end-to-end latency; label prototype measurements and exclude unrecovered failures from the MTTR denominator.
- FR-013 URL domain: creation, unique 7-char Base62 codes, redirect, analytics, expiration, validation and persistence.
- FR-014 Idempotency: repeated create requests with the same key and same request return the canonical result; reuse with different input is rejected.
- FR-015 Operational health and rate limiting.

## Scenarios
- SC-GREENFIELD: complete requirement proceeds without artificial clarification.
- SC-BROWNFIELD: enhancement against the existing URL shortener with impact analysis.
- SC-AMBIGUOUS: incomplete/conflicting input blocks until human clarification and governed replan.

## Non-functional requirements
Security, reliability, maintainability, observability, auditability, performance, recoverability, testability and change safety are required. Numeric targets not supplied by the assignment are proposed validation assumptions, not confirmed client requirements.

## Acceptance criteria
1. A workflow can be created and inspected.
2. Architecture approval cannot be bypassed.
3. The graph visibly contains a parallel security/test branch and a synchronization point.
4. Workflow state survives process restart because it is persisted in PostgreSQL.
5. Ambiguous scenario stops at clarification and resumes only after explicit human approval.
6. Replanning invalidates downstream artifacts and records a replan event.
7. Retry never exceeds three attempts; permanent failure uses compensation/safe-stop; a parallel-stage deadline breach records `TIMEOUT`, cancels the timed-out branches and preserves architecture approval as the recovery gate.
8. Every run records policy version and auditable state transitions; recovered retries pair a specific failure-event ID to a `RECOVERY_COMPLETED` event for MTTR calculation.
9. Mandatory policy failure blocks release unless a human-approved exception includes required fields.
10. Existing URL APIs continue to operate.
11. Health, rate limiting and idempotency behavior are executable and testable.
