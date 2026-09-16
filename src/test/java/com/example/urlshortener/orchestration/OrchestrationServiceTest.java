package com.example.urlshortener.orchestration;

import com.example.urlshortener.orchestration.agent.*;
import com.example.urlshortener.orchestration.domain.*;
import com.example.urlshortener.orchestration.dto.*;
import com.example.urlshortener.orchestration.repository.*;
import com.example.urlshortener.orchestration.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class OrchestrationServiceTest {
    @Mock WorkflowRunRepository runs;
    @Mock WorkflowEventRepository events;
    @Mock WorkflowArtifactRepository artifacts;
    @Mock ApprovalRepository approvals;
    @Mock PolicyEvaluationRepository policyEvaluations;
    @Mock PolicyService policy;
    @Mock RequirementAgent requirementAgent;
    @Mock ArchitectureAgent architectureAgent;
    @Mock SecurityAgent securityAgent;
    @Mock TestAgent testAgent;
    @Mock DocumentationAgent documentationAgent;
    @Mock ReleaseAgent releaseAgent;

    OrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new OrchestrationService(runs, events, artifacts, approvals, policyEvaluations, policy,
                requirementAgent, architectureAgent, securityAgent, testAgent, documentationAgent, releaseAgent);
        lenient().when(runs.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(artifacts.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(artifacts.findByRunIdOrderByCreatedAtAsc(any())).thenReturn(new ArrayList<>());
        lenient().when(events.findByRunIdOrderByOccurredAtAsc(any())).thenReturn(new ArrayList<>());
        lenient().when(policy.evaluate(any(), anyString(), anyBoolean(), anyBoolean(), anyString())).thenReturn(true);
        lenient().when(requirementAgent.execute(anyString(), anyString())).thenReturn("normalized");
        lenient().when(architectureAgent.execute(anyString(), anyString())).thenReturn("architecture");
        lenient().when(securityAgent.execute(anyString(), anyString())).thenReturn("security");
        lenient().when(testAgent.execute(anyString(), anyString())).thenReturn("tests");
        lenient().when(documentationAgent.execute(anyString(), anyString())).thenReturn("docs");
        lenient().when(releaseAgent.execute(anyString(), anyString())).thenReturn("release");
    }

    @Test
    void greenfieldStopsAtArchitectureApprovalAndCannotBypassIt() {
        CreateWorkflowRequest request = new CreateWorkflowRequest("Add reporting with API, tests and docs", "GREENFIELD");
        WorkflowResponse response = service.create(request);

        assertEquals(WorkflowStatus.WAITING_FOR_APPROVAL, response.status());
        assertEquals(WorkflowNode.ARCHITECTURE_APPROVAL, response.currentNode());
        verify(architectureAgent).execute(anyString(), eq("GREENFIELD"));
        verify(securityAgent, never()).execute(anyString(), anyString());
    }

    @Test
    void architectureApprovalRunsSecurityAndTestBranchesThenSynchronizes() {
        WorkflowRun run = new WorkflowRun(UUID.randomUUID(), "Add reporting", "GREENFIELD", PolicyService.VERSION);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        run.move(WorkflowNode.ARCHITECTURE_APPROVAL, WorkflowStatus.WAITING_FOR_APPROVAL);

        WorkflowResponse response = service.approve(run.getId(), "ARCHITECTURE",
                new ApprovalRequest("APPROVE", "human", "Architecture approved", null));

        assertEquals(WorkflowStatus.WAITING_FOR_APPROVAL, response.status());
        assertEquals(WorkflowNode.RELEASE_APPROVAL, response.currentNode());
        verify(securityAgent).execute("Add reporting", "GREENFIELD");
        verify(testAgent, times(2)).execute("Add reporting", "GREENFIELD");
        verify(events, atLeastOnce()).save(argThat(e -> "SYNCHRONIZED".equals(e.getAction())));
    }

    @Test
    void rejectedArchitectureGateSafeStopsAndRecordsDecision() {
        WorkflowRun run = new WorkflowRun(UUID.randomUUID(), "Add reporting", "GREENFIELD", PolicyService.VERSION);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        run.move(WorkflowNode.ARCHITECTURE_APPROVAL, WorkflowStatus.WAITING_FOR_APPROVAL);

        WorkflowResponse response = service.approve(run.getId(), "ARCHITECTURE",
                new ApprovalRequest("REJECT", "human", "Risk too high", null));

        assertEquals(WorkflowStatus.SAFE_STOP, response.status());
        assertEquals(WorkflowNode.SAFE_STOP, response.currentNode());
        assertEquals(WorkflowNode.ARCHITECTURE_APPROVAL, response.resumeNode());
        verify(approvals).save(any(Approval.class));
    }

    @Test
    void ambiguousScenarioBlocksUntilClarificationThenReplans() {
        CreateWorkflowRequest request = new CreateWorkflowRequest("Make it faster and change analytics as needed", "AMBIGUOUS");
        WorkflowResponse response = service.create(request);
        assertEquals(WorkflowStatus.WAITING_FOR_CLARIFICATION, response.status());
        assertEquals(WorkflowNode.CLARIFICATION, response.currentNode());

        UUID id = response.id();
        WorkflowRun run = new WorkflowRun(id, response.requirement(), "AMBIGUOUS", PolicyService.VERSION);
        run.move(WorkflowNode.CLARIFICATION, WorkflowStatus.WAITING_FOR_CLARIFICATION);
        when(runs.findById(id)).thenReturn(Optional.of(run));

        WorkflowResponse resumed = service.approve(id, "CLARIFICATION",
                new ApprovalRequest("APPROVE", "human", "Clarified scope and constraints", "Add analytics latency dashboard with p95 under 250ms"));

        assertNotEquals(WorkflowStatus.WAITING_FOR_CLARIFICATION, resumed.status());
        assertEquals("Add analytics latency dashboard with p95 under 250ms", resumed.requirement());
        verify(artifacts, atLeastOnce()).findByRunIdOrderByCreatedAtAsc(id);
        verify(events, atLeastOnce()).save(argThat(e -> "REPLAN_APPROVED".equals(e.getAction())));
    }

    @Test
    void interruptSafeStopPreservesResumePoint() {
        WorkflowRun run = new WorkflowRun(UUID.randomUUID(), "Add reporting", "GREENFIELD", PolicyService.VERSION);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        run.move(WorkflowNode.DESIGN, WorkflowStatus.RUNNING);

        WorkflowResponse stopped = service.simulateFailure(run.getId(), "interrupt");
        assertEquals(WorkflowStatus.SAFE_STOP, stopped.status());
        assertEquals(WorkflowNode.DESIGN, stopped.resumeNode());

        WorkflowResponse resumed = service.resume(run.getId());
        assertEquals(WorkflowStatus.WAITING_FOR_APPROVAL, resumed.status());
        assertEquals(WorkflowNode.ARCHITECTURE_APPROVAL, resumed.currentNode());
    }

    @Test
    void retryIsBoundedAtThreeAttempts() {
        WorkflowRun run = new WorkflowRun(UUID.randomUUID(), "Add reporting", "GREENFIELD", PolicyService.VERSION);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        run.move(WorkflowNode.TESTING, WorkflowStatus.RUNNING);
        run.retry(); run.retry(); run.retry();

        WorkflowResponse response = service.retry(run.getId());
        assertEquals(WorkflowStatus.SAFE_STOP, response.status());
        assertEquals(3, response.retryCount());
    }


    @Test
    void realParallelStageTimeoutFallsBackToCompensationAndPreservesArchitectureGate() {
        ReflectionTestUtils.setField(service, "agentTimeoutMillis", 25L);

        WorkflowRun run = new WorkflowRun(UUID.randomUUID(), "Add reporting", "GREENFIELD", PolicyService.VERSION);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        run.move(WorkflowNode.ARCHITECTURE_APPROVAL, WorkflowStatus.WAITING_FOR_APPROVAL);

        doAnswer(invocation -> {
            Thread.sleep(150L);
            return "slow-security";
        }).when(securityAgent).execute(anyString(), anyString());
        when(testAgent.execute(anyString(), anyString())).thenReturn("tests");

        WorkflowResponse response = service.approve(run.getId(), "ARCHITECTURE",
                new ApprovalRequest("APPROVE", "human", "Architecture approved", null));

        assertEquals(WorkflowStatus.SAFE_STOP, response.status());
        assertEquals(WorkflowNode.SAFE_STOP, response.currentNode());
        assertEquals(WorkflowNode.ARCHITECTURE_APPROVAL, response.resumeNode());
        verify(events, atLeastOnce()).save(argThat(e -> "TIMEOUT".equals(e.getAction())));
        verify(events, atLeastOnce()).save(argThat(e -> "COMPENSATION_EXECUTED".equals(e.getAction())));
    }

    @Test
    void retryRecoveryRecordsSourceFailureEventForAccurateMttrPairing() {
        WorkflowRun run = new WorkflowRun(UUID.randomUUID(), "Add reporting", "GREENFIELD", PolicyService.VERSION);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));

        WorkflowEvent failure = new WorkflowEvent(
                run.getId(),
                "SIMULATOR",
                "FAILURE_INJECTED",
                WorkflowNode.TESTING.name(),
                "FAILED",
                "synthetic transient failure",
                null
        );
        when(events.findByRunIdOrderByOccurredAtAsc(run.getId()))
                .thenReturn(List.of(failure));
        run.move(WorkflowNode.TESTING, WorkflowStatus.RUNNING);

        service.retry(run.getId());

        verify(events, atLeastOnce()).save(argThat(e ->
                "RECOVERY_COMPLETED".equals(e.getAction())
                        && e.getPayload() != null
                        && e.getPayload().contains(failure.getId().toString())
        ));
    }

    @Test
    void metricsPairsRecoveredFailureEventsAndExcludesUnrecoveredEvents() {
        WorkflowRun completedRun = new WorkflowRun(UUID.randomUUID(), "Completed", "GREENFIELD", PolicyService.VERSION);
        completedRun.move(WorkflowNode.COMPLETED, WorkflowStatus.COMPLETED);
        WorkflowRun stoppedRun = new WorkflowRun(UUID.randomUUID(), "Stopped", "GREENFIELD", PolicyService.VERSION);
        stoppedRun.move(WorkflowNode.SAFE_STOP, WorkflowStatus.SAFE_STOP);

        WorkflowEvent recoveredFailure = new WorkflowEvent(
                completedRun.getId(), "SIMULATOR", "FAILURE_INJECTED",
                WorkflowNode.TESTING.name(), "FAILED", "transient failure", null
        );
        WorkflowEvent recovery = new WorkflowEvent(
                completedRun.getId(), "ORCHESTRATOR", "RECOVERY_COMPLETED",
                WorkflowNode.TESTING.name(), "PASS", "Recovered",
                "sourceFailureEventId=" + recoveredFailure.getId() + ";mechanism=bounded-retry"
        );
        WorkflowEvent unrecoveredTimeout = new WorkflowEvent(
                stoppedRun.getId(), "ORCHESTRATOR", "TIMEOUT",
                WorkflowNode.SECURITY.name(), "FAILED", "deadline exceeded", "timeoutMillis=25"
        );

        when(runs.findAll()).thenReturn(List.of(completedRun, stoppedRun));
        when(events.findAll()).thenReturn(List.of(recoveredFailure, recovery, unrecoveredTimeout));

        Map<String, Object> metrics = service.metrics();

        assertEquals(1L, metrics.get("recoveredFailureEvents"));
        assertEquals(1L, metrics.get("unrecoveredFailureEvents"));
        assertEquals(true, metrics.get("unrecoveredExcludedFromMttrDenominator"));
        assertTrue(((String) metrics.get("mttrDefinition")).contains("failure-event-to-recovery-completion"));
    }

    /*
     * The four tests below cover the two governance properties I consider
     * most load-bearing and least covered: what happens when approvals race
     * or arrive out of order, and whether a re-plan on changed upstream
     * input actually invalidates and regenerates downstream work instead of
     * quietly reusing stale artifacts.
     */

    @Test
    void secondApprovalAtAnAlreadyPassedGateIsRejectedAndRecordsNoDuplicate() {
        WorkflowRun run = new WorkflowRun(UUID.randomUUID(), "Add reporting", "GREENFIELD", PolicyService.VERSION);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        run.move(WorkflowNode.ARCHITECTURE_APPROVAL, WorkflowStatus.WAITING_FOR_APPROVAL);

        // First reviewer approves; the gate is consumed and the run advances.
        WorkflowResponse first = service.approve(run.getId(), "ARCHITECTURE",
                new ApprovalRequest("APPROVE", "reviewer-a", "Architecture approved", null));
        assertEquals(WorkflowNode.RELEASE_APPROVAL, first.currentNode());

        /*
         * A second reviewer submits the same architecture approval -- the
         * realistic double-click / two-reviewers-at-once case. The gate is no
         * longer pending, so this must be refused rather than re-run the
         * parallel stage or double-count the decision.
         */
        IllegalStateException rejected = assertThrows(IllegalStateException.class, () ->
                service.approve(run.getId(), "ARCHITECTURE",
                        new ApprovalRequest("APPROVE", "reviewer-b", "Also approved", null)));
        assertTrue(rejected.getMessage().contains("ARCHITECTURE"));

        // Exactly one approval is on the audit record, and the run did not move.
        verify(approvals, times(1)).save(any(Approval.class));
        assertEquals(WorkflowNode.RELEASE_APPROVAL, run.getCurrentNode());
        assertEquals(WorkflowStatus.WAITING_FOR_APPROVAL, run.getStatus());
    }

    @Test
    void approvalForADownstreamGateCannotSkipThePendingUpstreamGate() {
        WorkflowRun run = new WorkflowRun(UUID.randomUUID(), "Add reporting", "GREENFIELD", PolicyService.VERSION);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        run.move(WorkflowNode.ARCHITECTURE_APPROVAL, WorkflowStatus.WAITING_FOR_APPROVAL);

        /*
         * Someone submits a RELEASE approval while the run is still parked at
         * the architecture gate. If gate matching were loose, this would jump
         * the workflow straight to COMPLETED and bypass security, test
         * planning, implementation and validation entirely.
         */
        assertThrows(IllegalStateException.class, () ->
                service.approve(run.getId(), "RELEASE",
                        new ApprovalRequest("APPROVE", "human", "Ship it", null)));

        // Nothing recorded, nothing moved, no agent work triggered.
        verify(approvals, never()).save(any(Approval.class));
        verify(securityAgent, never()).execute(anyString(), anyString());
        verify(releaseAgent, never()).execute(anyString(), anyString());
        assertEquals(WorkflowNode.ARCHITECTURE_APPROVAL, run.getCurrentNode());
        assertEquals(WorkflowStatus.WAITING_FOR_APPROVAL, run.getStatus());
    }

    @Test
    void replanOnChangedUpstreamInvalidatesDownstreamArtifactsAndRegeneratesDesign() {
        WorkflowRun run = new WorkflowRun(UUID.randomUUID(), "Add reporting", "GREENFIELD", PolicyService.VERSION);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        run.move(WorkflowNode.REPLAN, WorkflowStatus.RUNNING);

        // Downstream artifacts produced under the *old* requirement.
        WorkflowArtifact staleDecomposition =
                new WorkflowArtifact(run.getId(), "TASK_DECOMPOSITION", 1, "old tasks");
        WorkflowArtifact staleArchitecture =
                new WorkflowArtifact(run.getId(), "ARCHITECTURE", 1, "old architecture");
        List<WorkflowArtifact> existing =
                new ArrayList<>(List.of(staleDecomposition, staleArchitecture));
        when(artifacts.findByRunIdOrderByCreatedAtAsc(run.getId())).thenReturn(existing);

        String revised = "Add reporting with a p95 latency budget of 250ms";
        WorkflowResponse response = service.replan(run.getId(),
                new ReplanRequest(revised, "Upstream requirement changed after stakeholder review"));

        // The revised requirement is what the run now carries.
        assertEquals(revised, response.requirement());

        /*
         * Every artifact built from the superseded requirement must be marked
         * invalid. Leaving these valid is the failure mode I care about: a
         * reviewer would otherwise read stale design output as current.
         */
        assertFalse(staleDecomposition.isValid());
        assertFalse(staleArchitecture.isValid());
        verify(events, atLeastOnce()).save(argThat(e -> "ARTIFACTS_INVALIDATED".equals(e.getAction())));
        verify(events, atLeastOnce()).save(argThat(e -> "REPLAN_REQUESTED".equals(e.getAction())));

        /*
         * Re-planning must actually re-derive design from the new text, not
         * reuse the cached architecture -- so the architecture agent is called
         * with the revised requirement.
         */
        verify(architectureAgent).execute(revised, "GREENFIELD");

        // And re-planning cannot launder its way past the human gate.
        assertEquals(WorkflowStatus.WAITING_FOR_APPROVAL, response.status());
        assertEquals(WorkflowNode.ARCHITECTURE_APPROVAL, response.currentNode());
    }

    @Test
    void replanWithoutAnApprovedReplanGateIsRefusedAndTouchesNothing() {
        WorkflowRun run = new WorkflowRun(UUID.randomUUID(), "Add reporting", "GREENFIELD", PolicyService.VERSION);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        run.move(WorkflowNode.DESIGN, WorkflowStatus.RUNNING);

        /*
         * Re-planning mid-flight without passing an explicit
         * change/clarification gate would let a requirement be rewritten with
         * no human decision on record, which is the governance hole this
         * guards.
         */
        assertThrows(IllegalStateException.class, () ->
                service.replan(run.getId(),
                        new ReplanRequest("Something entirely different", "No gate passed")));

        // Requirement untouched, no artifacts invalidated, run still at DESIGN.
        assertEquals("Add reporting", run.getRequirement());
        verify(artifacts, never()).saveAll(any());
        assertEquals(WorkflowNode.DESIGN, run.getCurrentNode());
    }

    @Test
    void permanentFailureFallsBackToCompensationAndSafeStop() {
        WorkflowRun run = new WorkflowRun(UUID.randomUUID(), "Add reporting", "GREENFIELD", PolicyService.VERSION);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        run.move(WorkflowNode.IMPLEMENTATION, WorkflowStatus.RUNNING);

        WorkflowResponse response = service.simulateFailure(run.getId(), "permanent");
        assertEquals(WorkflowStatus.SAFE_STOP, response.status());
        assertEquals(WorkflowNode.SAFE_STOP, response.currentNode());
        assertEquals(WorkflowNode.IMPLEMENTATION, response.resumeNode());
        verify(events, atLeastOnce()).save(argThat(e -> "SAFE_STOP".equals(e.getAction())));
        verify(events, atLeastOnce()).save(argThat(e -> "STATE_TRANSITION".equals(e.getAction()) && "COMPENSATION".equals(e.getNode())));
    }
}
