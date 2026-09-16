# Dependency-aware task plan

| ID | Objective | Depends on | Parallel | Evidence |
|---|---|---|---|---|
| T01 | Preserve existing URL baseline | - | no | existing tests |
| T02 | Add versioned API/schema contracts | T01 | yes | contracts/openapi.yaml |
| T03 | Add orchestration persistence | T01 | no | V3 migration + entities |
| T04 | Add policy model/versioning | T03 | yes | policy evaluations |
| T05 | Add workflow state/dependency graph | T03 | no | WorkflowNode + service |
| T06 | Add approval gates | T05,T04 | no | approval API + tests |
| T07 | Add parallel branches/synchronization | T05 | yes | security/test branches |
| T08 | Add retry/real timeout/compensation/safe-stop/resume | T05 | yes | bounded retry + deadline enforcement + failure-path tests |
| T09 | Add governed dynamic replanning | T05,T06 | no | invalidation + replan event |
| T10 | Add audit/metrics | T03 | yes | event-linked MTTR + metrics endpoint |
| T11 | Add idempotency/health/rate limiting | T01 | yes | API/security tests |
| T12 | Greenfield scenario | T05-T10 | no | `d5ae3b68-2db8-4540-a676-69de60de62cf` |
| T13 | Brownfield scenario | T05,T09 | no | `c4eaa60a-a2c5-487e-a212-1016e19e1117` |
| T14 | Ambiguous scenario | T06,T09 | no | `675b4a8f-5158-4d47-805e-02469e82b1a1` |
| T15 | Final test/release readiness | T11-T14 | no | executed-evidence + release-readiness |

TDD obligation: for new behavior, create a failing test, execute it, implement the smallest compliant behavior, rerun, refactor and rerun regression tests. Existing implementation-first history is not retroactively represented as TDD.
