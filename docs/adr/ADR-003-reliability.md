# ADR-003: Bounded retry and compensation
Status: Proposed — human approval required

Retry is bounded to 3 attempts. Transient failure may retry; permanent/unsafe failure uses compensation and SAFE_STOP. The prototype never claims rollback of an irreversible external side effect.
