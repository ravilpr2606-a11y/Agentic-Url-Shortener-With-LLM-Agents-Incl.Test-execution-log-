package com.example.urlshortener.orchestration;

import com.example.urlshortener.orchestration.domain.WorkflowNode;
import com.example.urlshortener.orchestration.domain.WorkflowRun;
import com.example.urlshortener.orchestration.domain.WorkflowStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I test the WorkflowRun aggregate in isolation here, with no orchestration
 * service and no persistence. The point is to pin down the invariants the
 * state machine depends on: a new run starts RUNNING at the ingestion node,
 * and every move() both changes state and bumps the optimistic version
 * counter that guards concurrent writes.
 */
class WorkflowRunStateTest {

    @Test
    void workflowStartsAtIngestionAndCanEnterAnApprovalGate() {
        WorkflowRun run = new WorkflowRun(
                UUID.randomUUID(),
                "Add reporting",
                "GREENFIELD",
                "POLICY-1.0"
        );

        // A newly created run must be live and sitting at the entry node.
        assertEquals(WorkflowStatus.RUNNING, run.getStatus());
        assertEquals(WorkflowNode.REQUIREMENT_INGESTION, run.getCurrentNode());

        run.move(
                WorkflowNode.ARCHITECTURE_APPROVAL,
                WorkflowStatus.WAITING_FOR_APPROVAL
        );

        // Moving to a human gate must park the run rather than leave it running.
        assertEquals(WorkflowStatus.WAITING_FOR_APPROVAL, run.getStatus());
        assertEquals(WorkflowNode.ARCHITECTURE_APPROVAL, run.getCurrentNode());

        // I rely on this counter for optimistic concurrency, so a move that
        // did not bump it would silently break my concurrent-write guard.
        assertTrue(run.getVersion() > 0);
    }
}
