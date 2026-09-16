CREATE TABLE workflow_runs (
 id UUID PRIMARY KEY, status VARCHAR(30) NOT NULL, current_node VARCHAR(60) NOT NULL,
 requirement TEXT NOT NULL, scenario VARCHAR(30) NOT NULL, policy_version VARCHAR(30) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, retry_count INTEGER NOT NULL DEFAULT 0, version INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE workflow_events (
 id UUID PRIMARY KEY, run_id UUID NOT NULL REFERENCES workflow_runs(id) ON DELETE CASCADE,
 occurred_at TIMESTAMPTZ NOT NULL, actor_type VARCHAR(40) NOT NULL, action VARCHAR(100) NOT NULL,
 node VARCHAR(80) NOT NULL, result VARCHAR(30) NOT NULL, reason TEXT NOT NULL, payload TEXT
);
CREATE INDEX idx_workflow_events_run_time ON workflow_events(run_id, occurred_at);
CREATE TABLE workflow_artifacts (
 id UUID PRIMARY KEY, run_id UUID NOT NULL REFERENCES workflow_runs(id) ON DELETE CASCADE,
 type VARCHAR(80) NOT NULL, version INTEGER NOT NULL, content TEXT NOT NULL, valid BOOLEAN NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE workflow_approvals (
 id UUID PRIMARY KEY, run_id UUID NOT NULL REFERENCES workflow_runs(id) ON DELETE CASCADE,
 gate VARCHAR(50) NOT NULL, decision VARCHAR(20) NOT NULL, approver VARCHAR(100) NOT NULL, decided_at TIMESTAMPTZ NOT NULL, comment TEXT
);
CREATE TABLE policy_evaluations (
 id UUID PRIMARY KEY, run_id UUID NOT NULL REFERENCES workflow_runs(id) ON DELETE CASCADE,
 policy_version VARCHAR(30) NOT NULL, policy VARCHAR(80) NOT NULL, outcome VARCHAR(30) NOT NULL,
 mandatory BOOLEAN NOT NULL, evaluated_at TIMESTAMPTZ NOT NULL, reason TEXT
);
CREATE INDEX idx_policy_eval_run ON policy_evaluations(run_id, evaluated_at);
