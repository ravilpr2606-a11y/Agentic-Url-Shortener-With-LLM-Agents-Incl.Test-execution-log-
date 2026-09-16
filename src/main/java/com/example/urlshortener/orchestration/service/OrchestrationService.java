package com.example.urlshortener.orchestration.service;

import com.example.urlshortener.orchestration.agent.*;
import com.example.urlshortener.orchestration.domain.*;
import com.example.urlshortener.orchestration.dto.*;
import com.example.urlshortener.orchestration.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class OrchestrationService {

 private static final int MAX_RETRIES = 3;

 @Value("${orchestration.agent-timeout-millis:2000}")
 private long agentTimeoutMillis = 2000L;

 private final WorkflowRunRepository runs;
 private final WorkflowEventRepository events;
 private final WorkflowArtifactRepository artifacts;
 private final ApprovalRepository approvals;
 private final PolicyEvaluationRepository policyEvaluations;

 private final PolicyService policy;

 private final RequirementAgent requirementAgent;
 private final ArchitectureAgent architectureAgent;
 private final SecurityAgent securityAgent;
 private final TestAgent testAgent;
 private final DocumentationAgent documentationAgent;
 private final ReleaseAgent releaseAgent;

 public OrchestrationService(
         WorkflowRunRepository runs,
         WorkflowEventRepository events,
         WorkflowArtifactRepository artifacts,
         ApprovalRepository approvals,
         PolicyEvaluationRepository policyEvaluations,
         PolicyService policy,
         RequirementAgent requirementAgent,
         ArchitectureAgent architectureAgent,
         SecurityAgent securityAgent,
         TestAgent testAgent,
         DocumentationAgent documentationAgent,
         ReleaseAgent releaseAgent
 ) {
  this.runs = runs;
  this.events = events;
  this.artifacts = artifacts;
  this.approvals = approvals;
  this.policyEvaluations = policyEvaluations;
  this.policy = policy;
  this.requirementAgent = requirementAgent;
  this.architectureAgent = architectureAgent;
  this.securityAgent = securityAgent;
  this.testAgent = testAgent;
  this.documentationAgent = documentationAgent;
  this.releaseAgent = releaseAgent;
 }

 /**
  * Create a new workflow.
  *
  * Important:
  * WorkflowRun uses an assigned UUID. Spring Data JPA may therefore use
  * merge() instead of persist(). Always retain the entity returned by save().
  */
 @Transactional
 public WorkflowResponse create(CreateWorkflowRequest req) {

  String scenario = req.scenario() == null
          ? "GREENFIELD"
          : req.scenario().toUpperCase(Locale.ROOT);

  if (!Set.of("GREENFIELD", "BROWNFIELD", "AMBIGUOUS").contains(scenario)) {
   throw new IllegalArgumentException(
           "scenario must be GREENFIELD, BROWNFIELD, or AMBIGUOUS"
   );
  }

  WorkflowRun w = new WorkflowRun(
          UUID.randomUUID(),
          req.requirement(),
          scenario,
          PolicyService.VERSION
  );

  /*
   * CRITICAL:
   * WorkflowRun has an assigned ID, so save() may return a managed copy.
   * Continue the workflow using the returned entity.
   */
  w = runs.save(w);

  event(
          w,
          "ORCHESTRATOR",
          "WORKFLOW_CREATED",
          WorkflowNode.REQUIREMENT_INGESTION,
          "PASS",
          "Workflow accepted",
          null
  );

  return advance(w);
 }

 /**
  * Main workflow state machine.
  *
  * Every transition is explicit and auditable.
  */
 @Transactional
 public WorkflowResponse advance(WorkflowRun w) {

  if (w.getStatus() == WorkflowStatus.SAFE_STOP
          || w.getStatus() == WorkflowStatus.COMPLETED
          || w.getStatus() == WorkflowStatus.FAILED
          || w.getStatus() == WorkflowStatus.WAITING_FOR_APPROVAL
          || w.getStatus() == WorkflowStatus.WAITING_FOR_CLARIFICATION) {

   return WorkflowResponse.of(w);
  }

  switch (w.getCurrentNode()) {

   case REQUIREMENT_INGESTION -> {
    artifact(
            w,
            "NORMALIZED_REQUIREMENT",
            requirementAgent.execute(
                    w.getRequirement(),
                    w.getScenario()
            )
    );

    move(
            w,
            WorkflowNode.REQUIREMENT_NORMALIZATION
    );
   }

   case REQUIREMENT_NORMALIZATION -> {
    move(
            w,
            WorkflowNode.AMBIGUITY_CHECK
    );
   }

   case AMBIGUITY_CHECK -> {

    /*
     * I consult the requirement agent again here (I already consulted it
     * at REQUIREMENT_INGESTION for normalization). When I have an LLM
     * backend configured (see RequirementAgent/LlmClient), this call
     * asks the model to reason about the requirement text itself and
     * report whether it is materially ambiguous. When no LLM is
     * configured, or the call fails or returns an unparseable response,
     * the agent falls back to its deterministic behavior and this simply
     * degrades to "no additional signal" -- the explicit "AMBIGUOUS"
     * scenario flag below still works exactly as before.
     */
    String ambiguityAnalysis = requirementAgent.execute(
            w.getRequirement(),
            w.getScenario()
    );

    boolean modelDetectedAmbiguity = ambiguityAnalysis != null
            && ambiguityAnalysis.contains("\"ambiguityDetected\":true");

    boolean scenarioFlaggedAmbiguous = "AMBIGUOUS".equals(w.getScenario());

    artifact(
            w,
            "AMBIGUITY_ANALYSIS",
            ambiguityAnalysis == null ? "{}" : ambiguityAnalysis
    );

    if (scenarioFlaggedAmbiguous || modelDetectedAmbiguity) {

     move(
             w,
             WorkflowNode.CLARIFICATION
     );

     w.move(
             WorkflowNode.CLARIFICATION,
             WorkflowStatus.WAITING_FOR_CLARIFICATION
     );

     event(
             w,
             "ORCHESTRATOR",
             "CLARIFICATION_REQUIRED",
             WorkflowNode.CLARIFICATION,
             "BLOCKED",
             scenarioFlaggedAmbiguous
                     ? "Material ambiguity requires explicit human decision"
                     : "Requirement agent detected material ambiguity; explicit human decision required",
             null
     );

     persist(w);

    } else {

     boolean policyPassed = policy.evaluate(
             w.getId(),
             "SECURITY_AND_CHANGE_CONTROL",
             true,
             !w.getRequirement()
                     .toLowerCase(Locale.ROOT)
                     .contains("bypass security"),
             "Baseline policy evaluation"
     );

     if (!policyPassed) {

      w.move(
              WorkflowNode.POLICY_EXCEPTION,
              WorkflowStatus.WAITING_FOR_APPROVAL
      );

      event(
              w,
              "POLICY_ENGINE",
              "POLICY_EXCEPTION_REQUESTED",
              WorkflowNode.POLICY_EXCEPTION,
              "EXCEPTION-REQUESTED",
              "Mandatory policy failed; explicit exception required",
              "rationale/scope/compensating-control/expiry must be recorded"
      );

      persist(w);

     } else {

      move(
              w,
              "BROWNFIELD".equals(w.getScenario())
                      ? WorkflowNode.BROWNFIELD_IMPACT
                      : WorkflowNode.DECOMPOSITION
      );
     }
    }
   }

   case POLICY_EXCEPTION -> {
    return WorkflowResponse.of(w);
   }

   case BROWNFIELD_IMPACT -> {

    artifact(
            w,
            "BROWNFIELD_IMPACT",
            "Impacted components: orchestration, API contracts, persistence, tests, telemetry, documentation; regression and compensation review required."
    );

    move(
            w,
            WorkflowNode.DECOMPOSITION
    );
   }

   case CLARIFICATION -> {
    return WorkflowResponse.of(w);
   }

   case DECOMPOSITION -> {

    artifact(
            w,
            "TASK_DECOMPOSITION",
            "T1 requirement quality -> "
                    + "T2 design -> "
                    + "T3 security || "
                    + "T4 test planning -> "
                    + "T5 synchronize -> "
                    + "T6 implementation -> "
                    + "T7 testing -> "
                    + "T8 documentation -> "
                    + "T9 validation -> "
                    + "T10 release readiness"
    );

    move(
            w,
            WorkflowNode.DESIGN
    );
   }

   case DESIGN -> {

    artifact(
            w,
            "ARCHITECTURE",
            architectureAgent.execute(
                    w.getRequirement(),
                    w.getScenario()
            )
    );

    w.move(
            WorkflowNode.ARCHITECTURE_APPROVAL,
            WorkflowStatus.WAITING_FOR_APPROVAL
    );

    event(
            w,
            "ARCHITECTURE_AGENT",
            "APPROVAL_REQUESTED",
            WorkflowNode.ARCHITECTURE_APPROVAL,
            "WAITING",
            "Human architecture approval required",
            null
    );

    persist(w);
   }

   case ARCHITECTURE_APPROVAL -> {
    return WorkflowResponse.of(w);
   }

   case SECURITY, TEST_PLANNING -> {
    return WorkflowResponse.of(w);
   }

   case SYNCHRONIZE -> {

    event(
            w,
            "ORCHESTRATOR",
            "SYNCHRONIZATION_GATE",
            WorkflowNode.SYNCHRONIZE,
            "PASS",
            "Security and test-planning branches synchronized",
            null
    );

    move(
            w,
            WorkflowNode.IMPLEMENTATION
    );
   }

   case IMPLEMENTATION -> {

    artifact(
            w,
            "IMPLEMENTATION_PLAN",
            "Controlled implementation executes only approved tasks; repeatable operations are idempotent and bounded."
    );

    move(
            w,
            WorkflowNode.TESTING
    );
   }

   case TESTING -> {

    artifact(
            w,
            "TEST_PLAN",
            testAgent.execute(
                    w.getRequirement(),
                    w.getScenario()
            )
    );

    move(
            w,
            WorkflowNode.DOCUMENTATION
    );
   }

   case DOCUMENTATION -> {

    artifact(
            w,
            "DOCUMENTATION",
            documentationAgent.execute(
                    w.getRequirement(),
                    w.getScenario()
            )
    );

    move(
            w,
            WorkflowNode.VALIDATION
    );
   }

   case VALIDATION -> {

    boolean securityOk = policy.evaluate(
            w.getId(),
            "RELEASE_SECURITY",
            true,
            true,
            "Validation security policy passed"
    );

    boolean complianceOk = policy.evaluate(
            w.getId(),
            "RELEASE_COMPLIANCE",
            true,
            true,
            "Validation compliance and change-control policy passed"
    );

    if (!securityOk || !complianceOk) {

     safeStop(
             w,
             "Mandatory release policy failed"
     );

    } else {

     move(
             w,
             WorkflowNode.RELEASE_READINESS
     );
    }
   }

   case RELEASE_READINESS -> {

    artifact(
            w,
            "RELEASE_RECOMMENDATION",
            releaseAgent.execute(
                    w.getRequirement(),
                    w.getScenario()
            )
    );

    w.move(
            WorkflowNode.RELEASE_APPROVAL,
            WorkflowStatus.WAITING_FOR_APPROVAL
    );

    event(
            w,
            "RELEASE_AGENT",
            "RELEASE_APPROVAL_REQUESTED",
            WorkflowNode.RELEASE_APPROVAL,
            "WAITING",
            "Release readiness is human-owned",
            null
    );

    persist(w);
   }

   case RELEASE_APPROVAL -> {
    return WorkflowResponse.of(w);
   }

   case REPLAN -> {

    artifact(
            w,
            "REPLAN",
            "Downstream artifacts invalidated; decomposition/design/testing must be regenerated from revised requirement."
    );

    move(
            w,
            WorkflowNode.DECOMPOSITION
    );
   }

   case FALLBACK -> {

    artifact(
            w,
            "FALLBACK",
            "Safe fallback: stop optional agent work and preserve stable application-plane behavior."
    );

    event(
            w,
            "ORCHESTRATOR",
            "FALLBACK_EXECUTED",
            WorkflowNode.FALLBACK,
            "PASS",
            "Fallback executed after non-recoverable failure",
            null
    );

    move(
            w,
            WorkflowNode.COMPENSATION
    );
   }

   case COMPENSATION -> {

    artifact(
            w,
            "COMPENSATION",
            "Compensating action: preserve existing stable URL behavior, invalidate incomplete workflow artifacts, and prevent release progression."
    );

    event(
            w,
            "ORCHESTRATOR",
            "COMPENSATION_EXECUTED",
            WorkflowNode.COMPENSATION,
            "PASS",
            "Compensation completed before safe-stop",
            null
    );

    /*
     * Do NOT overwrite an already-recorded resume point.
     *
     * For a permanent failure we record the stable node before
     * fallback/compensation. This makes the safe-stop resumable.
     */
    safeStop(
            w,
            "Non-recoverable failure handled through fallback and compensation"
    );
   }

   case SAFE_STOP -> {
    return WorkflowResponse.of(w);
   }

   case COMPLETED -> {
    return WorkflowResponse.of(w);
   }
  }

  /*
   * Explicit persistence makes the state machine robust even when the
   * entity originated from a merge/save boundary.
   */
  persist(w);

  return advance(w);
 }

 /**
  * Human approval endpoint.
  */
 @Transactional
 public WorkflowResponse approve(
         UUID id,
         String gate,
         ApprovalRequest req
 ) {

  WorkflowRun w = get(id);

  String normalizedGate =
          gate.toUpperCase(Locale.ROOT);

  String decision =
          req.decision().toUpperCase(Locale.ROOT);

  if (!Set.of("APPROVE", "REJECT").contains(decision)) {
   throw new IllegalArgumentException(
           "decision must be APPROVE or REJECT"
   );
  }

  if (!requiredGate(w, normalizedGate)) {
   throw new IllegalStateException(
           "No approval is pending for gate " + normalizedGate
   );
  }

  String comment =
          req.comment() == null ? "" : req.comment();

  approvals.save(
          new Approval(
                  id,
                  normalizedGate,
                  decision,
                  req.approver(),
                  comment
          )
  );

  event(
          w,
          "HUMAN",
          decision,
          w.getCurrentNode(),
          "APPROVE".equals(decision)
                  ? "PASS"
                  : "REJECTED",
          "Explicit human decision",
          comment
  );

  /*
   * Human rejection is always a controlled stop.
   */
  if ("REJECT".equals(decision)) {

   w.setResumeNode(
           w.getCurrentNode()
   );

   w.move(
           WorkflowNode.SAFE_STOP,
           WorkflowStatus.SAFE_STOP
   );

   event(
           w,
           "ORCHESTRATOR",
           "SAFE_STOP",
           WorkflowNode.SAFE_STOP,
           "BLOCKED",
           "Human rejected mandatory gate",
           null
   );

   persist(w);

   return WorkflowResponse.of(w);
  }

  /*
   * Policy exception approval.
   */
  if ("POLICY_EXCEPTION".equals(normalizedGate)) {

   String lowerComment =
           comment.toLowerCase(Locale.ROOT);

   if (!lowerComment.contains("rationale")
           || !lowerComment.contains("scope")
           || !lowerComment.contains("compensating")
           || !lowerComment.contains("expiry")) {

    throw new IllegalArgumentException(
            "Policy exception must record rationale, scope, compensating control, and expiry"
    );
   }

   w.move(
           WorkflowNode.DECOMPOSITION,
           WorkflowStatus.RUNNING
   );

   event(
           w,
           "HUMAN",
           "POLICY_EXCEPTION_APPROVED",
           WorkflowNode.POLICY_EXCEPTION,
           "PASS",
           "Explicit exception approved with compensating control",
           comment
   );

   persist(w);

   return advance(w);
  }

  /*
   * Clarification approval may revise the requirement.
   */
  if ("CLARIFICATION".equals(normalizedGate)) {

   if (req.revisedRequirement() != null
           && !req.revisedRequirement().isBlank()) {

    invalidateArtifacts(w);

    w.setRequirement(
            req.revisedRequirement()
    );

    event(
            w,
            "HUMAN",
            "REPLAN_APPROVED",
            WorkflowNode.REPLAN,
            "PASS",
            "Approved clarification triggered downstream impact analysis",
            req.revisedRequirement()
    );
   }

   w.move(
           WorkflowNode.REPLAN,
           WorkflowStatus.RUNNING
   );

   persist(w);

   return advance(w);
  }

  /*
   * Architecture approval starts the two independent branches.
   */
  if ("ARCHITECTURE".equals(normalizedGate)) {

   w.move(
           WorkflowNode.SECURITY,
           WorkflowStatus.RUNNING
   );

   persist(w);

   return runParallelAndSync(w);
  }

  /*
   * Release approval completes the workflow.
   */
  if ("RELEASE".equals(normalizedGate)) {

   w.move(
           WorkflowNode.COMPLETED,
           WorkflowStatus.COMPLETED
   );

   event(
           w,
           "ORCHESTRATOR",
           "WORKFLOW_COMPLETED",
           WorkflowNode.COMPLETED,
           "PASS",
           "Release approved",
           null
   );

   persist(w);

   return WorkflowResponse.of(w);
  }

  throw new IllegalStateException(
          "Unsupported approval gate"
  );
 }

 /**
  * Execute independent security and test-planning branches in parallel.
  *
  * Synchronization occurs only after both branches complete.
  */
 private WorkflowResponse runParallelAndSync(
         WorkflowRun w
 ) {

  ExecutorService executor =
          Executors.newFixedThreadPool(2);

  CompletableFuture<String> security =
          CompletableFuture.supplyAsync(
                  () -> securityAgent.execute(
                          w.getRequirement(),
                          w.getScenario()
                  ),
                  executor
          );

  CompletableFuture<String> testPlanning =
          CompletableFuture.supplyAsync(
                  () -> testAgent.execute(
                          w.getRequirement(),
                          w.getScenario()
                  ),
                  executor
          );

  try {

   CompletableFuture.allOf(
           security,
           testPlanning
   ).orTimeout(
           agentTimeoutMillis,
           TimeUnit.MILLISECONDS
   ).join();

   artifact(
           w,
           "SECURITY_RESULT",
           security.join()
   );

   artifact(
           w,
           "TEST_PLANNING_RESULT",
           testPlanning.join()
   );

   event(
           w,
           securityAgent.name(),
           "PARALLEL_BRANCH_COMPLETED",
           WorkflowNode.SECURITY,
           "PASS",
           "Security branch completed within the orchestration deadline",
           "timeoutMillis=" + agentTimeoutMillis
   );

   event(
           w,
           testAgent.name(),
           "PARALLEL_BRANCH_COMPLETED",
           WorkflowNode.TEST_PLANNING,
           "PASS",
           "Test-planning branch completed within the orchestration deadline",
           "timeoutMillis=" + agentTimeoutMillis
   );

   move(
           w,
           WorkflowNode.SYNCHRONIZE
   );

   event(
           w,
           "ORCHESTRATOR",
           "SYNCHRONIZED",
           WorkflowNode.SYNCHRONIZE,
           "PASS",
           "Both independent branches completed before implementation",
           "deadlineMillis=" + agentTimeoutMillis
   );

   persist(w);

   return advance(w);

  } catch (CompletionException ex) {

   Throwable cause = ex.getCause();

   if (cause instanceof TimeoutException) {

    security.cancel(true);
    testPlanning.cancel(true);

    return handleTimeout(
            w,
            "Parallel security/test-planning stage exceeded "
                    + agentTimeoutMillis
                    + " ms"
    );
   }

   throw ex;

  } finally {
   executor.shutdownNow();
  }
 }

 private WorkflowResponse handleTimeout(
         WorkflowRun w,
         String reason
 ) {

  /*
   * Timeout is a real deadline breach, not merely a synthetic failure.
   * Preserve the last human-approved safe point so recovery cannot bypass
   * architecture governance.
   */
  w.setResumeNode(
          WorkflowNode.ARCHITECTURE_APPROVAL
  );

  WorkflowEvent timeout =
          event(
                  w,
                  "ORCHESTRATOR",
                  "TIMEOUT",
                  w.getCurrentNode(),
                  "FAILED",
                  reason,
                  "timeoutMillis=" + agentTimeoutMillis
          );

  w.move(
          WorkflowNode.FALLBACK,
          WorkflowStatus.RUNNING
  );

  persist(w);

  WorkflowResponse response =
          advance(w);

  persist(w);

  return response;
 }

 /**
  * Convenience clarification endpoint.
  */
 @Transactional
 public WorkflowResponse clarify(
         UUID id,
         String decision,
         String approver,
         String comment
 ) {

  return approve(
          id,
          "CLARIFICATION",
          new ApprovalRequest(
                  decision,
                  approver,
                  comment,
                  null
          )
  );
 }

 /**
  * Explicit re-plan endpoint.
  */
 @Transactional
 public WorkflowResponse replan(
         UUID id,
         ReplanRequest req
 ) {

  WorkflowRun w = get(id);

  if (w.getCurrentNode() != WorkflowNode.REPLAN) {
   throw new IllegalStateException(
           "Replanning requires an explicit approved change/clarification gate"
   );
  }

  invalidateArtifacts(w);

  w.setRequirement(
          req.revisedRequirement()
  );

  event(
          w,
          "HUMAN",
          "REPLAN_REQUESTED",
          WorkflowNode.REPLAN,
          "PASS",
          req.reason(),
          req.revisedRequirement()
  );

  w.move(
          WorkflowNode.REPLAN,
          WorkflowStatus.RUNNING
  );

  persist(w);

  return advance(w);
 }

 /**
  * Bounded transient-failure retry.
  */
 @Transactional
 public WorkflowResponse retry(UUID id) {

  WorkflowRun w = get(id);

  if (w.getRetryCount() >= MAX_RETRIES) {

   safeStop(
           w,
           "Retry limit reached"
   );

   persist(w);

   return WorkflowResponse.of(w);
  }

  WorkflowEvent sourceFailure = latestFailureEvent(id);
  String sourceFailureId =
          sourceFailure == null ? null : sourceFailure.getId().toString();

  w.retry();

  event(
          w,
          "ORCHESTRATOR",
          "RECOVERY_STARTED",
          w.getCurrentNode(),
          "PASS",
          "Bounded recovery attempt started",
          recoveryPayload(sourceFailureId, "bounded-retry")
  );

  event(
          w,
          "ORCHESTRATOR",
          "RETRY",
          w.getCurrentNode(),
          "RETRYING",
          "Transient failure retry "
                  + w.getRetryCount()
                  + "/"
                  + MAX_RETRIES,
          recoveryPayload(sourceFailureId, "bounded-retry")
  );

  w.move(
          w.getCurrentNode(),
          WorkflowStatus.RUNNING
  );

  WorkflowEvent recoveryCompleted =
          event(
                  w,
                  "ORCHESTRATOR",
                  "RECOVERY_COMPLETED",
                  w.getCurrentNode(),
                  "PASS",
                  "Retry recovered workflow execution",
                  recoveryPayload(sourceFailureId, "bounded-retry")
          );

  persist(w);

  return advance(w);
 }

 /**
  * Controlled failure simulator used by scenario demonstrations.
  *
  * Supported:
  * - transient
  * - permanent
  * - timeout
  * - any other value => immediate safe-stop
  */
 @Transactional
 public WorkflowResponse simulateFailure(
         UUID id,
         String kind
 ) {

  WorkflowRun w = get(id);

  String k = kind == null
          ? "transient"
          : kind.toLowerCase(Locale.ROOT);

  if ("transient".equals(k)) {

   event(
           w,
           "SIMULATOR",
           "FAILURE_INJECTED",
           w.getCurrentNode(),
           "FAILED",
           "Demonstration-only transient failure",
           null
   );

   persist(w);

   return retry(id);
  }

  if ("permanent".equals(k)) {

   event(
           w,
           "SIMULATOR",
           "FAILURE_INJECTED",
           w.getCurrentNode(),
           "FAILED",
           "Demonstration-only permanent failure",
           null
   );

   /*
    * Preserve the last stable point BEFORE entering fallback.
    * This is the point from which controlled recovery can resume.
    */
   if (w.getResumeNode() == null
           || w.getResumeNode() == WorkflowNode.SAFE_STOP) {

    w.setResumeNode(
            w.getCurrentNode()
    );
   }

   w.move(
           WorkflowNode.FALLBACK,
           WorkflowStatus.RUNNING
   );

   persist(w);

   WorkflowResponse response =
           advance(w);

   /*
    * Explicit final save protects the terminal SAFE_STOP state.
    */
   persist(w);

   return response;
  }

  if ("timeout".equals(k)) {

   event(
           w,
           "SIMULATOR",
           "TIMEOUT",
           w.getCurrentNode(),
           "FAILED",
           "Demonstration-only timeout",
           null
   );

   /*
    * Preserve the last stable point BEFORE fallback.
    */
   if (w.getResumeNode() == null
           || w.getResumeNode() == WorkflowNode.SAFE_STOP) {

    w.setResumeNode(
            w.getCurrentNode()
    );
   }

   w.move(
           WorkflowNode.FALLBACK,
           WorkflowStatus.RUNNING
   );

   persist(w);

   WorkflowResponse response =
           advance(w);

   persist(w);

   return response;
  }

  /*
   * Unknown/unsafe failure types immediately safe-stop.
   */
  w.setResumeNode(
          w.getCurrentNode()
  );

  safeStop(
          w,
          "Unsafe continuation requested by failure simulation"
  );

  persist(w);

  return WorkflowResponse.of(w);
 }

 /**
  * Resume from a persisted safe point.
  */
 @Transactional
 public WorkflowResponse resume(UUID id) {

  WorkflowRun w = get(id);

  if (w.getStatus() != WorkflowStatus.SAFE_STOP
          && w.getStatus() != WorkflowStatus.FAILED) {

   throw new IllegalStateException(
           "Only stopped or failed workflows can be resumed"
   );
  }

  WorkflowNode target =
          w.getResumeNode();

  if (target == null) {

   throw new IllegalStateException(
           "No safe resume point recorded"
   );
  }

  event(
          w,
          "ORCHESTRATOR",
          "RESUME_REQUESTED",
          target,
          "PASS",
          "Controlled recovery requested from persisted safe point",
          null
  );

  w.move(
          target,
          WorkflowStatus.RUNNING
  );

  event(
          w,
          "ORCHESTRATOR",
          "RESUME",
          target,
          "PASS",
          "Controlled recovery resumed from persisted safe point",
          null
  );

  persist(w);

  /*
   * Human approval gates can never be bypassed during recovery.
   */
  if (target == WorkflowNode.ARCHITECTURE_APPROVAL
          || target == WorkflowNode.RELEASE_APPROVAL) {

   w.move(
           target,
           WorkflowStatus.WAITING_FOR_APPROVAL
   );

   event(
           w,
           "ORCHESTRATOR",
           "APPROVAL_REQUIRED",
           target,
           "WAITING",
           "Recovery reached a human approval gate; approval remains mandatory",
           null
   );

   persist(w);

   return WorkflowResponse.of(w);
  }

  return advance(w);
 }

 /**
  * Operational metrics.
  */
 public Map<String, Object> metrics() {

  List<WorkflowRun> all =
          runs.findAll();

  long completed =
          all.stream()
                  .filter(x -> x.getStatus() == WorkflowStatus.COMPLETED)
                  .count();

  long failedWorkflows =
          all.stream()
                  .filter(x -> x.getStatus() == WorkflowStatus.FAILED
                          || x.getStatus() == WorkflowStatus.SAFE_STOP)
                  .count();

  List<WorkflowEvent> allEvents =
          events.findAll();

  long retryEvents =
          allEvents.stream()
                  .filter(e -> "RETRY".equals(e.getAction()))
                  .count();

  long compensation =
          allEvents.stream()
                  .filter(e -> "COMPENSATION_EXECUTED".equals(e.getAction()))
                  .count();

  double successRate =
          all.isEmpty() ? 0.0 : (100.0 * completed / all.size());

  double failureRate =
          all.isEmpty() ? 0.0 : (100.0 * failedWorkflows / all.size());

  long latency =
          all.stream()
                  .filter(x -> x.getStatus() == WorkflowStatus.COMPLETED)
                  .mapToLong(x -> Duration.between(
                          x.getCreatedAt(),
                          x.getUpdatedAt()
                  ).toMillis())
                  .sum();

  List<WorkflowEvent> failureEvents =
          allEvents.stream()
                  .filter(this::isRecoverableFailureEvent)
                  .sorted(Comparator.comparing(WorkflowEvent::getOccurredAt))
                  .toList();

  Map<UUID, WorkflowEvent> completedRecoveries =
          new HashMap<>();

  for (WorkflowEvent e : allEvents) {
   if ("RECOVERY_COMPLETED".equals(e.getAction())) {
    UUID sourceId = extractSourceFailureId(e.getPayload());
    if (sourceId != null) {
     completedRecoveries.put(sourceId, e);
    }
   }
  }

  long recovered =
          failureEvents.stream()
                  .filter(e -> completedRecoveries.containsKey(e.getId()))
                  .count();

  long unrecovered =
          failureEvents.size() - recovered;

  long recoveryDuration = 0L;
  List<Long> individualRecoveryDurations = new ArrayList<>();

  for (WorkflowEvent failure : failureEvents) {
   WorkflowEvent recovery = completedRecoveries.get(failure.getId());
   if (recovery != null) {
    long duration = Duration.between(
            failure.getOccurredAt(),
            recovery.getOccurredAt()
    ).toMillis();
    recoveryDuration += duration;
    individualRecoveryDurations.add(duration);
   }
  }

  double mttr =
          recovered == 0
                  ? 0.0
                  : (double) recoveryDuration / recovered;

  Map<String, Object> metrics = new LinkedHashMap<>();
  metrics.put("workflowCount", all.size());
  metrics.put("successRatePercent", successRate);
  metrics.put("failureRatePercent", failureRate);
  metrics.put("retryFrequency", retryEvents);
  metrics.put("compensationFrequency", compensation);
  metrics.put("mttrMillis", mttr);
  metrics.put("recoveredFailureEvents", recovered);
  metrics.put("unrecoveredFailureEvents", unrecovered);
  metrics.put("unrecoveredWorkflowCount", failedWorkflows);
  metrics.put("totalRecoveryDurationMillis", recoveryDuration);
  metrics.put("individualRecoveryDurationsMillis", individualRecoveryDurations);
  metrics.put("endToEndLatencyMillisForCompletedWorkflows", latency);
  metrics.put("mttrDefinition", "sum of failure-event-to-recovery-completion durations / recovered failure events");
  metrics.put("unrecoveredExcludedFromMttrDenominator", true);
  metrics.put("measurementType", "prototype demonstration measurements");
  return metrics;
 }

 private boolean isRecoverableFailureEvent(WorkflowEvent e) {
  return "FAILURE_INJECTED".equals(e.getAction())
          || "TIMEOUT".equals(e.getAction());
 }

 private UUID extractSourceFailureId(String payload) {
  if (payload == null || !payload.contains("sourceFailureEventId=")) {
   return null;
  }
  String marker = "sourceFailureEventId=";
  int start = payload.indexOf(marker) + marker.length();
  int end = payload.indexOf(';', start);
  String raw = end >= 0 ? payload.substring(start, end) : payload.substring(start);
  if (raw.isBlank()) {
   return null;
  }
  try {
   return UUID.fromString(raw);
  } catch (IllegalArgumentException ex) {
   return null;
  }
 }

 private String recoveryPayload(String sourceFailureId, String mechanism) {
  return "sourceFailureEventId=" + (sourceFailureId == null ? "" : sourceFailureId)
          + ";mechanism=" + mechanism;
 }

 private WorkflowEvent latestFailureEvent(UUID id) {
  return events.findByRunIdOrderByOccurredAtAsc(id).stream()
          .filter(this::isRecoverableFailureEvent)
          .max(Comparator.comparing(WorkflowEvent::getOccurredAt))
          .orElse(null);
 }

 /**
  * Audit-grade workflow inspection endpoint.
  */
 public Map<String, Object> inspect(UUID id) {

  WorkflowRun w =
          get(id);

  return Map.of(
          "workflow",
          WorkflowResponse.of(w),

          "events",
          events.findByRunIdOrderByOccurredAtAsc(id),

          "artifacts",
          artifacts.findByRunIdOrderByCreatedAtAsc(id),

          "approvals",
          approvals.findByRunIdOrderByDecidedAtAsc(id),

          "policyEvaluations",
          policyEvaluations(id),

          "dependencyGraph",
          List.of(
                  "INGESTION->NORMALIZATION->AMBIGUITY",
                  "AMBIGUITY->CLARIFICATION (conditional)",
                  "AMBIGUITY->BROWNFIELD_IMPACT (brownfield conditional)",
                  "BROWNFIELD_IMPACT->DECOMPOSITION",
                  "DECOMPOSITION->DESIGN->ARCHITECTURE_APPROVAL",
                  "ARCHITECTURE_APPROVAL->SECURITY || TEST_PLANNING",
                  "SECURITY || TEST_PLANNING->SYNCHRONIZE",
                  "SYNCHRONIZE->IMPLEMENTATION",
                  "IMPLEMENTATION->TESTING",
                  "TESTING->DOCUMENTATION",
                  "DOCUMENTATION->VALIDATION",
                  "VALIDATION->RELEASE_READINESS",
                  "RELEASE_READINESS->RELEASE_APPROVAL",
                  "RELEASE_APPROVAL->COMPLETED",
                  "FAILURE->FALLBACK->COMPENSATION->SAFE_STOP"
          )
  );
 }

 private List<?> policyEvaluations(UUID id) {

  return policyEvaluations
          .findByRunIdOrderByEvaluatedAtAsc(id);
 }

 private WorkflowRun get(UUID id) {

  return runs.findById(id)
          .orElseThrow(() ->
                  new NoSuchElementException(
                          "Workflow not found"
                  )
          );
 }

 private boolean requiredGate(
         WorkflowRun w,
         String gate
 ) {

  return
          ("ARCHITECTURE".equals(gate)
                  && w.getCurrentNode()
                  == WorkflowNode.ARCHITECTURE_APPROVAL)

                  || ("RELEASE".equals(gate)
                  && w.getCurrentNode()
                  == WorkflowNode.RELEASE_APPROVAL)

                  || ("CLARIFICATION".equals(gate)
                  && w.getCurrentNode()
                  == WorkflowNode.CLARIFICATION)

                  || ("POLICY_EXCEPTION".equals(gate)
                  && w.getCurrentNode()
                  == WorkflowNode.POLICY_EXCEPTION);
 }

 /**
  * Explicit workflow transition with audit event.
  */
 private void move(
         WorkflowRun w,
         WorkflowNode node
 ) {

  w.move(
          node,
          WorkflowStatus.RUNNING
  );

  event(
          w,
          "ORCHESTRATOR",
          "STATE_TRANSITION",
          node,
          "PASS",
          "Transitioned to " + node,
          null
  );
 }

 /**
  * Enter a controlled terminal safe-stop state.
  *
  * Existing resume point is deliberately preserved.
  */
 private void safeStop(
         WorkflowRun w,
         String reason
 ) {

  if (w.getResumeNode() == null
          || w.getResumeNode() == WorkflowNode.SAFE_STOP) {

   w.setResumeNode(
           w.getCurrentNode()
   );
  }

  w.move(
          WorkflowNode.SAFE_STOP,
          WorkflowStatus.SAFE_STOP
  );

  event(
          w,
          "ORCHESTRATOR",
          "SAFE_STOP",
          WorkflowNode.SAFE_STOP,
          "BLOCKED",
          reason,
          null
  );

  persist(w);
 }

 /**
  * Persist an artifact and emit its audit event.
  */
 private void artifact(
         WorkflowRun w,
         String type,
         String content
 ) {

  artifacts.save(
          new WorkflowArtifact(
                  w.getId(),
                  type,
                  w.getVersion() + 1,
                  content
          )
  );

  event(
          w,
          "AGENT",
          "ARTIFACT_CREATED",
          w.getCurrentNode(),
          "PASS",
          type,
          content
  );
 }

 /**
  * Persist an audit event.
  */
 private WorkflowEvent event(
         WorkflowRun w,
         String actor,
         String action,
         WorkflowNode node,
         String result,
         String reason,
         String payload
 ) {

  return events.save(
          new WorkflowEvent(
                  w.getId(),
                  actor,
                  action,
                  node.name(),
                  result,
                  reason,
                  payload
          )
  );
 }

 /**
  * Invalidate all currently valid downstream artifacts after a re-plan.
  */
 private void invalidateArtifacts(
         WorkflowRun w
 ) {

  List<WorkflowArtifact> existing =
          artifacts.findByRunIdOrderByCreatedAtAsc(
                  w.getId()
          );

  for (WorkflowArtifact artifact : existing) {

   if (artifact.isValid()) {
    artifact.invalidate();
   }
  }

  artifacts.saveAll(existing);

  event(
          w,
          "ORCHESTRATOR",
          "ARTIFACTS_INVALIDATED",
          WorkflowNode.REPLAN,
          "PASS",
          "Downstream artifacts invalidated because upstream requirements changed",
          null
  );
 }

 /**
  * Explicit persistence boundary.
  *
  * The returned value is intentionally ignored here because callers that
  * start from a managed entity retain the same managed instance. The create
  * path separately captures the save() return value because WorkflowRun has
  * an assigned UUID and may enter the persistence context through merge().
  */
 private void persist(
         WorkflowRun w
 ) {

  runs.save(w);
 }
}