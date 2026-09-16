# Architecture Overview


I chose this architecture because it gives me explicit workflow state, persistence and governance without adding distributed-system complexity.
```text
                    CONTROL PLANE
 Requirement -> Requirement Agent -> Quality/Policy
                              |
                        explicit DAG/state
                              |
        +---------------------+----------------------+
        |                                            |
 Security Agent                              Test Agent
        |                                            |
        +------------------- SYNC ------------------+
                              |
                      Implementation
                              |
                 Testing -> Documentation
                              |
                   Validation/Release Gate

                    APPLICATION PLANE
        REST -> URL Domain -> Persistence -> PostgreSQL
```

The control plane orchestrates the existing URL shortener rather than replacing it. Workflow state, audit events, artifacts, approvals and policy evaluations are persisted. The graph contains sequential edges, a conditional clarification/brownfield branch, a parallel security/test branch, synchronization, approval gates and recovery/replan edges.
