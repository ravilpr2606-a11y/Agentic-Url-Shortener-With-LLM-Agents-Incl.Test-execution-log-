# Executed Evidence Manifest

I use this document to record what I actually observed during the assessment execution. I intentionally do not invent timestamps, approvals, metrics, Git history, or tool versions that are not available in the submitted workspace.

## Verification baseline

I executed `mvn clean test` successfully before the timeout/MTTR remediation: **47 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**. I also executed `mvn clean verify` successfully, started the PostgreSQL-backed application, and successfully created a short URL through `POST /api/v1/urls`.

I have since executed the post-remediation Maven suite, including the new timeout/MTTR tests: **58/58 tests passing — 0 failures, 0 errors, 0 skipped, BUILD SUCCESS** — against a Testcontainers-provisioned PostgreSQL 17 instance. I reproduced 54/54 under `mvn clean verify` (which also built the runnable jar), then, after adding four `OrchestrationServiceTest` governance cases, re-ran `mvn clean test` and observed 58/58. The full console output is in `docs/assessment/test-execution-log.md`.

## Greenfield execution

Workflow ID: `d5ae3b68-2db8-4540-a676-69de60de62cf`

I observed:

`WORKFLOW_CREATED -> REQUIREMENT_NORMALIZATION -> DECOMPOSITION -> DESIGN -> ARCHITECTURE_APPROVAL -> HUMAN APPROVE -> SECURITY || TEST_PLANNING -> SYNCHRONIZE -> IMPLEMENTATION -> TESTING -> DOCUMENTATION -> VALIDATION -> RELEASE_READINESS -> RELEASE_APPROVAL -> HUMAN APPROVE -> COMPLETED`

Terminal outcome: `COMPLETED`.

I did not manufacture a clarification step for the well-defined greenfield requirement.

## Brownfield execution

Workflow ID: `c4eaa60a-a2c5-487e-a212-1016e19e1117`

I created the workflow as `BROWNFIELD`, observed the impact-analysis path and advanced it to architecture approval. After explicit architecture approval it reached `RELEASE_APPROVAL`.

I did not fabricate the missing release approval.

## Ambiguous requirement / governed replan execution

Workflow ID: `675b4a8f-5158-4d47-805e-02469e82b1a1`

I observed:

1. Creation as `AMBIGUOUS`.
2. Stop at `WAITING_FOR_CLARIFICATION / CLARIFICATION`.
3. Explicit human clarification with a revised requirement.
4. Invalidation of the original normalized requirement artifact.
5. `REPLAN_APPROVED` recording the accepted revised requirement.
6. Regeneration of downstream decomposition/design/security/test/implementation/documentation/release artifacts.
7. Architecture rejection and `SAFE_STOP` with a persisted resume point.
8. `/resume` returning the workflow to the persisted architecture gate.
9. Explicit architecture approval.
10. Return to `WAITING_FOR_APPROVAL / RELEASE_APPROVAL` without bypassing the release gate.

## Transient failure / bounded retry execution

Workflow ID: `abafc7ea-4371-4152-ab41-287efed8d3b8`

I injected transient failure four times. I observed retry events at `1/3`, `2/3` and `3/3`; the fourth failure caused `SAFE_STOP`. The audit history retained the failure, retries, recovery completion and safe-stop events.

## Permanent failure / fallback / compensation / safe-stop execution

Workflow ID: `a68f41a5-72e2-4b59-8d9d-660a8aab4ef8`

I observed:

`FAILURE_INJECTED (permanent) -> FALLBACK -> FALLBACK_EXECUTED -> COMPENSATION -> COMPENSATION_EXECUTED -> SAFE_STOP`

The terminal state was `SAFE_STOP`, `retryCount` remained `0` for the permanent-failure path, and `resumeNode = RELEASE_APPROVAL` was preserved.
