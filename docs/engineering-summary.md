# Engineering Summary — Assessment Draft


I use this summary to explain what I built, why I chose the architecture, and what I still consider incomplete.
## Outcome
The existing URL shortener is retained as the application plane. The submission adds a persisted orchestration/control plane designed to demonstrate the assessment's critical differentiator: stateful, non-linear, governed agentic execution.

## Existing baseline
URL creation, redirect resolution, analytics, expiration, validation, PostgreSQL persistence, Flyway migrations and existing automated tests remain in place.

## Added control plane
Persisted workflow runs, state transitions, dependency graph, artifacts, approvals, policy evaluations, audit events, retry/recovery, compensation/safe-stop, dynamic replan and reliability metrics.

## Evidence status
This draft intentionally does not claim final readiness. Execute the test suite, scenario demonstrations, security/dependency checks and human gates before selecting READY, READY WITH ACCEPTED LIMITATIONS or NOT READY.

## Key trade-off
A PostgreSQL-backed modular monolith was selected instead of a distributed workflow platform because the assessment is timeboxed and explicitly values demonstrability, testability and clarity over unjustified distributed complexity.
