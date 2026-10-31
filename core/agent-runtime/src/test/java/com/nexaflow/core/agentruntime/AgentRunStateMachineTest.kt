package com.nexaflow.core.agentruntime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunStateMachineTest {

    @Test
    fun queuedRunOnlyStartsOrEndsWithoutRunning() {
        val queued = AgentRunStatus.QUEUED

        assertTrue(queued.canTransitionTo(AgentRunStatus.RUNNING))
        assertTrue(queued.canTransitionTo(AgentRunStatus.FAILED))
        assertTrue(queued.canTransitionTo(AgentRunStatus.CANCELLED))
        assertTrue(queued.canTransitionTo(AgentRunStatus.INTERRUPTED))

        // A run that never started cannot complete or wait for an approval.
        assertFalse(queued.canTransitionTo(AgentRunStatus.COMPLETED))
        assertFalse(queued.canTransitionTo(AgentRunStatus.WAITING_FOR_APPROVAL))
        assertFalse(queued.canTransitionTo(AgentRunStatus.QUEUED))
    }

    @Test
    fun runningRunCanPauseResumeOrTerminate() {
        val running = AgentRunStatus.RUNNING

        assertTrue(running.canTransitionTo(AgentRunStatus.WAITING_FOR_APPROVAL))
        assertTrue(running.canTransitionTo(AgentRunStatus.COMPLETED))
        assertTrue(running.canTransitionTo(AgentRunStatus.FAILED))
        assertTrue(running.canTransitionTo(AgentRunStatus.CANCELLED))
        assertTrue(running.canTransitionTo(AgentRunStatus.INTERRUPTED))

        assertFalse(running.canTransitionTo(AgentRunStatus.QUEUED))
        assertFalse(running.canTransitionTo(AgentRunStatus.RUNNING))
    }

    @Test
    fun approvalWaitOnlyResumesOrTerminates() {
        val waiting = AgentRunStatus.WAITING_FOR_APPROVAL

        assertTrue(waiting.canTransitionTo(AgentRunStatus.RUNNING))
        assertTrue(waiting.canTransitionTo(AgentRunStatus.COMPLETED))
        assertTrue(waiting.canTransitionTo(AgentRunStatus.FAILED))
        assertTrue(waiting.canTransitionTo(AgentRunStatus.CANCELLED))
        assertTrue(waiting.canTransitionTo(AgentRunStatus.INTERRUPTED))

        assertFalse(waiting.canTransitionTo(AgentRunStatus.QUEUED))
        assertFalse(waiting.canTransitionTo(AgentRunStatus.WAITING_FOR_APPROVAL))
    }

    @Test
    fun terminalStatesAreFrozen() {
        val terminal = listOf(
            AgentRunStatus.COMPLETED,
            AgentRunStatus.FAILED,
            AgentRunStatus.CANCELLED,
            AgentRunStatus.INTERRUPTED
        )

        for (status in terminal) {
            assertTrue(status.isTerminal)
            for (target in AgentRunStatus.entries) {
                assertFalse("$status -> $target must be rejected", status.canTransitionTo(target))
            }
        }
        assertFalse(AgentRunStatus.QUEUED.isTerminal)
        assertFalse(AgentRunStatus.RUNNING.isTerminal)
        assertFalse(AgentRunStatus.WAITING_FOR_APPROVAL.isTerminal)
    }
}
