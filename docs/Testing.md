# Testing Strategy

## What I test

I keep the existing unit, integration and performance coverage for URL validation, short-code generation, URL creation, analytics and redirect behavior. I added orchestration-focused coverage for state transitions, approvals, parallel synchronization, bounded retry, timeout handling, compensation/safe-stop, resume, replanning, policy behavior, audit reconstruction and idempotency.

## Timeout validation

I use `ORCHESTRATION_AGENT_TIMEOUT_MILLIS` as the real deadline for the parallel security/test-planning stage. My orchestration tests reduce that deadline and use a deliberately slow branch so I can verify deterministic timeout handling, branch cancellation, fallback, compensation, safe-stop and preservation of the architecture-approval resume point.

## MTTR validation

I model failure events individually and pair each recovered failure with the specific source failure-event ID. This lets me exclude unrecovered failures from the MTTR denominator and prevents unrelated recovery events in the same workflow from being paired together.

## Evidence rule

I treat generated tests and documentation as plans until I execute them. I only describe test results and scenario outcomes as evidence when I actually observed them. I also do not rewrite the history of the project to make earlier implementation-first work look like TDD.
