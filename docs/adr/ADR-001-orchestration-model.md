# ADR-001: Stateful modular-monolith orchestration
Status: Proposed — human approval required

## Context
The assessment requires explicit dependencies, persisted state, parallel execution, synchronization, branching, recovery, replanning and governance. A linear agent chain is insufficient.

## Options
1. Distributed workflow engine — strong durability, excessive setup and infrastructure for a 2–3 day prototype.
2. In-memory coordinator — simple, fails persistence/restart requirements.
3. PostgreSQL-backed orchestration in the existing Spring application — durable, testable, locally runnable and aligned with the existing brownfield codebase.

## Decision
Use a PostgreSQL-backed orchestration control plane in the existing modular monolith.

## Consequences
Positive: one deployable unit, clear boundaries, durable state, easy reviewer setup. Negative: not intended as a horizontally scaled workflow platform.

## Validation
Workflow restart, transition, parallelism, approval and recovery tests plus inspection API.
