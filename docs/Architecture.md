# Architecture

I keep the authoritative architecture in `docs/architecture/architecture-overview.md`, with the material decisions recorded under `docs/adr/`.

I use two clear boundaries:

- **Application plane:** URL creation, redirect resolution, analytics, expiration, validation and persistence.
- **Control plane:** workflow state, dependency/state transitions, bounded agents, policy evaluation, approvals, audit evidence, reliability handling and replanning.

I made this separation so the orchestration requirement remains a real control-plane capability instead of becoming a set of URL-shortener controllers with hidden workflow behavior.
