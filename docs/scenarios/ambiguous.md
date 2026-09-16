# Ambiguous Requirement Scenario


I use this scenario as the strongest demonstration of governed replanning and preserved human ownership.
Input: intentionally incomplete/conflicting requirement submitted with scenario=AMBIGUOUS.

The workflow stops at CLARIFICATION. No implementation occurs until an explicit human decision. Approval moves to REPLAN; downstream artifacts are invalidated, the revised requirement is recorded, and the workflow regenerates decomposition/design/test evidence before continuing.


Recovery demonstration: inject `kind=interrupt` to enter `SAFE_STOP` with a persisted resume point, then call `/resume`. The workflow returns to the recorded point rather than skipping governance.

## Executed evidence

Workflow: `675b4a8f-5158-4d47-805e-02469e82b1a1`

Observed sequence: `CLARIFICATION` -> explicit human clarification -> `REPLAN_APPROVED` -> artifact invalidation/regeneration -> architecture rejection -> `SAFE_STOP` -> `/resume` -> architecture approval -> release approval gate.

This is the primary dynamic-replanning evidence.
