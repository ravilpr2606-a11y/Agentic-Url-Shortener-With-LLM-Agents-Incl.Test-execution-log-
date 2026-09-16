# My Reviewer Navigation Guide

## 1. Where I would start

- `README.md` — how I built the system, how I run it and the main endpoints.
- `docs/assessment/final-engineering-summary.md` — my final engineering summary.
- `docs/assessment/executed-evidence.md` — the workflow executions I actually observed.
- `docs/assessment/risk-register.md` — the limitations and residual risks I am carrying forward.

## 2. My requirements and lifecycle artifacts

- `specs/001-agentic-url-shortener/spec.md` — the requirements and acceptance criteria I used.
- `specs/001-agentic-url-shortener/plan.md` — the implementation and architecture plan I followed.
- `specs/001-agentic-url-shortener/tasks.md` — my dependency-aware task decomposition.
- `docs/governance/constitution.md` — the governance rules I used.
- `docs/adr/` — the material architecture decisions I recorded.

## 3. Where I implemented orchestration

I keep the orchestration implementation under `src/main/java/com/example/urlshortener/orchestration/`.

Main entry points:

```text
POST /api/v1/workflows
GET  /api/v1/workflows/{id}
POST /api/v1/workflows/{id}/approvals/{gate}
POST /api/v1/workflows/{id}/clarification
POST /api/v1/workflows/{id}/replan
POST /api/v1/workflows/{id}/retry
POST /api/v1/workflows/{id}/simulate-failure?kind=transient|timeout|permanent|interrupt
POST /api/v1/workflows/{id}/resume
GET  /api/v1/workflows/metrics
```

## 4. Demonstrations I actually captured

### Greenfield
I create a workflow with `scenario=GREENFIELD`. A complete requirement advances to architecture approval. After explicit architecture approval, I run Security and Test Planning in parallel, synchronize them, continue through implementation/testing/documentation/validation and request release approval.

Executed evidence: workflow `d5ae3b68-2db8-4540-a676-69de60de62cf`, terminal `COMPLETED`.

### Brownfield
I create a workflow with `scenario=BROWNFIELD` and review the brownfield impact artifact before approving architecture.

Executed evidence: workflow `c4eaa60a-a2c5-487e-a212-1016e19e1117`, which advanced through brownfield impact and architecture approval to the release gate.

### Ambiguous requirement
I create a workflow with `scenario=AMBIGUOUS`. I let it stop at `CLARIFICATION`, submit an explicit human decision and revised requirement, and then inspect the `REPLAN_APPROVED` event and invalidated/regenerated artifacts.

Executed evidence: workflow `675b4a8f-5158-4d47-805e-02469e82b1a1`.

### Transient retry
I use `simulate-failure?kind=transient`. My retry counter is bounded at 3; the fourth failure causes safe-stop.

Executed evidence: workflow `abafc7ea-4371-4152-ab41-287efed8d3b8`.

### Permanent failure / compensation
I use `simulate-failure?kind=permanent` and review the audit sequence `FAILURE_INJECTED -> FALLBACK -> COMPENSATION -> SAFE_STOP`.

Executed evidence: workflow `a68f41a5-72e2-4b59-8d9d-660a8aab4ef8`.

## 5. Timeout handling I added

I configure `ORCHESTRATION_AGENT_TIMEOUT_MILLIS` (or the corresponding `orchestration.agent-timeout-millis` property) to set the real deadline for my parallel security/test-planning stage. When the deadline is exceeded, I cancel the branch futures, record `TIMEOUT`, preserve `ARCHITECTURE_APPROVAL` as the recovery point and route the workflow through fallback and compensation.

I have added dedicated tests for this path, and my final 58/58 Maven run passed them successfully.

## 6. MTTR metrics I added

I expose `GET /api/v1/workflows/metrics` with success/failure rate, retry frequency, compensation frequency, per-failure-event MTTR, total and individual recovery durations, recovered/unrecovered failure-event counts, unrecovered workflow count and completed-workflow latency.

I pair each recovery with a specific failure-event UUID. I exclude unrecovered failure events from the MTTR denominator.

## 7. Security

I want reviewers to read `SECURITY.md` and `docs/security-assessment.md` before treating the prototype as production deployment-ready.

## 8. Traceability

I maintain the requirement-to-code matrix in `docs/traceability/requirements-to-code.md`.

## 9. Evidence limits I am deliberately disclosing

I did not preserve a historical Git commit sequence, a recorded SpecKit CLI version or a generated `.claude/skills` installation in this archive. I am disclosing those gaps rather than manufacturing evidence.
