package com.nexaflow.core.execution.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowExecutionStateTest {
    @Test
    fun workflowContractAllowsOnlyForwardOperationalTransitions() {
        assertTrue(WorkflowExecutionState.ADMITTED.canTransitionTo(WorkflowExecutionState.RUNNING))
        assertTrue(WorkflowExecutionState.RUNNING.canTransitionTo(WorkflowExecutionState.WAITING))
        assertTrue(WorkflowExecutionState.WAITING.canTransitionTo(WorkflowExecutionState.RUNNING))
        assertTrue(WorkflowExecutionState.RUNNING.canTransitionTo(WorkflowExecutionState.UNKNOWN))
        assertTrue(WorkflowExecutionState.UNKNOWN.canTransitionTo(WorkflowExecutionState.RECOVERY_REQUIRED))
        assertFalse(WorkflowExecutionState.ADMITTED.canTransitionTo(WorkflowExecutionState.SUCCEEDED))
        assertFalse(WorkflowExecutionState.FAILED.canTransitionTo(WorkflowExecutionState.RUNNING))
        assertFalse(WorkflowExecutionState.SUCCEEDED.canTransitionTo(WorkflowExecutionState.FAILED))
    }

    @Test
    fun nodeContractRequiresExplicitCompensationAndCannotReopenTerminalStates() {
        assertTrue(NodeExecutionState.PENDING.canTransitionTo(NodeExecutionState.RUNNING))
        assertTrue(NodeExecutionState.RUNNING.canTransitionTo(NodeExecutionState.UNKNOWN))
        assertTrue(NodeExecutionState.FAILED.canTransitionTo(NodeExecutionState.COMPENSATING))
        assertTrue(NodeExecutionState.COMPENSATING.canTransitionTo(NodeExecutionState.COMPENSATED))
        assertFalse(NodeExecutionState.FAILED.canTransitionTo(NodeExecutionState.SUCCEEDED))
        assertFalse(NodeExecutionState.COMPENSATED.canTransitionTo(NodeExecutionState.RUNNING))
        assertFalse(NodeExecutionState.UNKNOWN.canTransitionTo(NodeExecutionState.SUCCEEDED))
    }

    @Test
    fun resultAdaptersNeverReportFailureAsSuccess() {
        val successfulNode = NodeResult("node-ok", true, "completed", 1L)
        val failedNode = NodeResult("node-fail", false, "operation failed", 1L)
        val unknownNode = NodeResult("node-unknown", false, "Outcome unknown after timeout", 1L)

        assertEquals(NodeExecutionState.SUCCEEDED, successfulNode.canonicalState())
        assertEquals(NodeExecutionState.FAILED, failedNode.canonicalState())
        assertEquals(NodeExecutionState.UNKNOWN, unknownNode.canonicalState())
        assertEquals(
            WorkflowExecutionState.SUCCEEDED,
            WorkflowExecutionResult(true, "done", listOf(successfulNode), 1L).canonicalState()
        )
        assertEquals(
            WorkflowExecutionState.FAILED,
            WorkflowExecutionResult(false, "failed", listOf(failedNode), 1L).canonicalState()
        )
        assertEquals(
            WorkflowExecutionState.UNKNOWN,
            WorkflowExecutionResult(false, "failed", listOf(unknownNode), 1L).canonicalState()
        )
    }
}
