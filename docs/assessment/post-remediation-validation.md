# Post-Remediation Validation

I added real timeout enforcement and failure-event-based MTTR pairing in the final source. I have now executed the post-remediation Maven suite three times and observed all three results directly: `mvn clean test` and `mvn clean verify` each completed with 54/54 tests passing, and after adding four `OrchestrationServiceTest` governance cases, a further `mvn clean test` completed with **58/58 tests passing** (0 failures, 0 errors, 0 skipped) and `BUILD SUCCESS`. `verify` also produced the repackaged runnable jar. I captured the full console output for all three runs in `docs/assessment/test-execution-log.md`.

## 1. Full validation executed

```bash
mvn clean test
mvn clean verify
```

(The test suite provisions its own PostgreSQL 17 via Testcontainers, so it needs a running Docker daemon but not a manual `docker compose up -d`.)

Final observed result after I added the four governance tests:

```
Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

My first two validation captures were 54/54; after I added the four governance tests, my final run moved to 58/58 with the same 0/0/0 result. `verify` additionally reached packaging:

```
jar:3.4.2:jar -- Building jar: target\url-shortener-0.0.1-SNAPSHOT.jar
spring-boot:3.5.16:repackage -- Replacing main artifact with repackaged archive
```

The current source contains the 58-test suite represented by the final execution log, and all 58 tests ran and passed in my final run. The earlier figures of 50 and 54 predate, respectively, the two LLM-agent test classes and the four `OrchestrationServiceTest` governance cases, each of which accounts for its own +4 delta. (An earlier internal count of "52" was a miscount that matched `@Testcontainers` and `@TestInstance` as `@Test`; see `docs/assessment/test-execution-log.md` for how I reconciled the verified count against the executed result.)

## 2. Timeout validation I added

`realParallelStageTimeoutFallsBackToCompensationAndPreservesArchitectureGate` reduces `agentTimeoutMillis`, slows the security branch beyond the deadline, and verifies:

`TIMEOUT -> FALLBACK -> COMPENSATION -> SAFE_STOP`

The test also verifies that `ARCHITECTURE_APPROVAL` remains the resume point.

## 3. MTTR validation I added

`retryRecoveryRecordsSourceFailureEventForAccurateMttrPairing` and `metricsPairsRecoveredFailureEventsAndExcludesUnrecoveredEvents` verify that each recovery references a specific failure event and that unrecovered failures are excluded from the MTTR denominator.

## 4. Runtime metrics I will verify

After I start PostgreSQL and the application, I will call:

```bash
curl http://localhost:8080/api/v1/workflows/metrics
```

I expect the response to include:

- `mttrMillis`
- `totalRecoveryDurationMillis`
- `individualRecoveryDurationsMillis`
- `recoveredFailureEvents`
- `unrecoveredFailureEvents`
- `unrecoveredWorkflowCount`
- `unrecoveredExcludedFromMttrDenominator: true`

## 5. Timeout configuration

I configure the deadline with:

`ORCHESTRATION_AGENT_TIMEOUT_MILLIS`

Default: `2000` ms.

I apply the deadline to the parallel security/test-planning fan-out. A breach records `TIMEOUT`, cancels the branch futures, preserves `ARCHITECTURE_APPROVAL` as the recovery gate, and routes through fallback and compensation.
