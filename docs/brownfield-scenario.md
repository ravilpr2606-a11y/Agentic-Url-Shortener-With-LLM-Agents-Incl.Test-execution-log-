# Brownfield Scenario — Assessment Demonstration


I use this scenario to show how I reason about an existing codebase before allowing a material change.
The existing URL shortener is the brownfield baseline. A new enhancement is evaluated before code changes.

## Required pre-change analysis
The workflow must identify impacted modules, interfaces, API contracts, domain rules, persistence, orchestration states, telemetry, documentation, direct tests, regression tests, security risks, reliability risks, data compatibility, compensation implications, affected ADRs and downstream tasks.

## Current prototype evidence path
`BROWNFIELD_IMPACT -> DECOMPOSITION -> DESIGN -> ARCHITECTURE_APPROVAL -> parallel security/test planning -> synchronization -> implementation ...`

The analysis is stored as a workflow artifact and audit event so a reviewer can inspect the actual run instead of relying on prose alone.
