# Reliability and Recovery Model


I designed the reliability model around bounded retry, real deadlines, failure-event lineage, compensation and deterministic safe-stop.
| Failure | Action | Terminal behavior | Recovery evidence |
|---|---|---|---|
| transient | bounded retry up to 3 | resume normal path | `RECOVERY_STARTED` + `RECOVERY_COMPLETED` paired to the source failure event |
| parallel-stage timeout | cancel timed-out branches, fallback | compensation -> safe-stop; resume returns to architecture approval | `TIMEOUT` event with configured deadline; no MTTR credit unless a later recovery event explicitly references that timeout |
| permanent | fallback | compensation -> safe-stop | `FAILURE_INJECTED` remains unrecovered unless an explicit recovery event references it |
| unsafe continuation | safe-stop | human-controlled resume only | terminal `SAFE_STOP` audit event |
| human rejection | safe-stop | human-controlled resume/decision | approval/rejection + safe-stop evidence |

## Timeout handling

The security/test-planning fan-out uses a real overall execution deadline (`orchestration.agent-timeout-millis`, default 2000 ms). The two independent branches execute concurrently, and the orchestration gate waits for both only within that deadline. A deadline breach cancels both futures, records a `TIMEOUT` audit event with the configured deadline, preserves `ARCHITECTURE_APPROVAL` as the safe resume point, and routes through fallback and compensation to safe-stop. Recovery cannot bypass the human architecture gate.

Synthetic failure injection remains available for deterministic assessment demonstrations, but actual deadline enforcement is separate from the simulator.

## MTTR measurement

MTTR is calculated from **failure events**, not workflows:

`MTTR = total duration from each recovered failure event to its paired RECOVERY_COMPLETED event / number of recovered failure events`

Each recovered retry records `sourceFailureEventId=<UUID>` in its recovery events. Unrecovered failure events are counted separately and are excluded from the MTTR denominator. The metrics endpoint also returns total recovery duration, individual recovery durations, recovered failure-event count, unrecovered failure-event count, and the calculation definition. Measurements are labeled prototype demonstration measurements.
