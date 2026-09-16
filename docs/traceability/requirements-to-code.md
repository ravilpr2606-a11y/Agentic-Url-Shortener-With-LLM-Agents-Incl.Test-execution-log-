# Traceability Matrix

| Requirement | Design / Task | Implementation | Tests / Executed Evidence |
|---|---|---|---|
| FR-001 Requirement understanding | T02, T12, T14 | `OrchestrationService`, `RequirementAgent` | greenfield + ambiguous evidence |
| FR-002 Ambiguity | T14 | `AMBIGUITY_CHECK`, `CLARIFICATION` | `675b4a8f-5158-4d47-805e-02469e82b1a1` |
| FR-003 Brownfield | T13 | `BROWNFIELD_IMPACT` | `c4eaa60a-a2c5-487e-a212-1016e19e1117` |
| FR-004 Dependency graph | T05, T07 | `WorkflowNode`, `OrchestrationService` | orchestration tests + workflow audit |
| FR-005 Persistent state | T03, T05 | workflow entities + V3/V5/V6 | workflow inspection and restart-oriented design |
| FR-006 Governance | T06 | approvals + safe-stop | human approval events |
| FR-007 Reliability | T08 | retry/fallback/compensation/safe-stop | transient + permanent failure evidence |
| FR-008 Recovery | T08 | `/resume`, persisted `resumeNode` | ambiguous rejection/resume evidence |
| FR-009 Replanning | T09 | artifact invalidation + `REPLAN_APPROVED` | ambiguous workflow evidence |
| FR-010 Policy | T04 | `PolicyService`, `PolicyEvaluation`, `POLICY-1.0` | workflow policy evaluation records |
| FR-011 Audit | T10 | `WorkflowEvent`, inspection endpoint | executed workflow audit trails |
| FR-012 Metrics | T10 | `/api/v1/workflows/metrics` | metrics implementation; demonstration data labeled |
| FR-013 URL domain | T01 | URL service/domain/repositories | 47-test executed baseline |
| FR-014 Idempotency | T11 | `IdempotencyRecord`, service logic | `IdempotencyServiceTest` |
| FR-015 Health/rate limiting | T11 | `HealthController`, `RateLimitFilter` | API/security test coverage |

## Core scenario evidence

- Greenfield: `d5ae3b68-2db8-4540-a676-69de60de62cf`
- Brownfield: `c4eaa60a-a2c5-487e-a212-1016e19e1117`
- Ambiguous/replan: `675b4a8f-5158-4d47-805e-02469e82b1a1`
- Transient retry: `abafc7ea-4371-4152-ab41-287efed8d3b8`
- Permanent/fallback/compensation: `a68f41a5-72e2-4b59-8d9d-660a8aab4ef8`

Where an evidence ID is given, a reviewer can reproduce the same path from the API and inspect the persisted audit trail. No synthetic workflow IDs are used in this document.
