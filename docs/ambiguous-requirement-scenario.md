# Ambiguous Requirement Scenario — Assessment Demonstration


I wrote this scenario to show how I handle uncertainty without letting an agent silently invent a requirement.
Create a workflow with `scenario=AMBIGUOUS` and an intentionally incomplete requirement. The orchestrator performs ingestion and normalization, then enters `CLARIFICATION` and waits.

No implementation path is allowed to continue while the workflow is waiting for clarification. An explicit human decision is recorded. Approval moves the workflow into governed replanning; rejection enters `SAFE_STOP`.

The replan path invalidates downstream artifacts, records the revised requirement and generates fresh decomposition/design/testing artifacts. Evidence must include the original input, ambiguity, blocked state, human decision, replan event, invalidated artifacts, resumed state and final validation.
