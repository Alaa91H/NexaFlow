package com.nexaflow.app.agent

import com.nexaflow.core.agentruntime.AgentRun
import com.nexaflow.core.agentruntime.ManagedAgentRunEvent
import com.nexaflow.core.agentruntime.ManagedAgentRunUseCase
import com.nexaflow.core.execution.handler.AgentApprovalPolicy
import com.nexaflow.core.execution.handler.AgentRunOutcome
import com.nexaflow.core.execution.handler.AgentRunRequest
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedAgentRunGatewayTest {

    private class FakeRuns(
        private val events: List<ManagedAgentRunEvent>,
    ) : ManagedAgentRunUseCase {
        val resolved = mutableListOf<Pair<String, Boolean>>()
        var lastKey: String? = null

        override fun observeRuns(agentId: String): Flow<List<AgentRun>> = flow { emit(emptyList()) }

        override fun runAgent(
            agentId: String,
            prompt: String,
            idempotencyKey: String
        ): Flow<ManagedAgentRunEvent> = flow {
            lastKey = idempotencyKey
            events.forEach { emit(it) }
        }

        override suspend fun resolveApproval(approvalId: String, approved: Boolean): Boolean {
            resolved += approvalId to approved
            return true
        }
    }

    private fun gateway(runs: ManagedAgentRunUseCase) = ManagedAgentRunGateway(Lazy { runs })

    private fun request(policy: AgentApprovalPolicy, millis: Long = 5_000L) = AgentRunRequest(
        agentId = "agent-1",
        prompt = "hello",
        maxDurationMillis = millis,
        onApprovalRequired = policy,
        idempotencyKey = "exec:node:agent-1",
    )

    @Test
    fun aggregatesDeltasIntoTheCompletedSummary() = runTest {
        val runs = FakeRuns(
            listOf(
                ManagedAgentRunEvent.Queued("run-1"),
                ManagedAgentRunEvent.AssistantDelta("Hello "),
                ManagedAgentRunEvent.AssistantDelta("world"),
                ManagedAgentRunEvent.Completed("run-1"),
            )
        )

        val outcome = gateway(runs).run(request(AgentApprovalPolicy.FAIL))

        assertEquals(AgentRunOutcome.Completed("Hello world"), outcome)
        assertEquals("exec:node:agent-1", runs.lastKey)
    }

    @Test
    fun approvalUnderFailPolicyDeniesAndAbortsTheOutcome() = runTest {
        val runs = FakeRuns(
            listOf(
                ManagedAgentRunEvent.Queued("run-1"),
                ManagedAgentRunEvent.ApprovalRequired("approval-1", "delete_task", "{}"),
                ManagedAgentRunEvent.Completed("run-1"),
            )
        )

        val outcome = gateway(runs).run(request(AgentApprovalPolicy.FAIL))

        assertEquals(AgentRunOutcome.Failed("approval_required"), outcome)
        assertEquals(listOf("approval-1" to false), runs.resolved)
    }

    @Test
    fun approvalUnderSkipPolicyIsDeniedAndTheRunContinues() = runTest {
        val runs = FakeRuns(
            listOf(
                ManagedAgentRunEvent.ApprovalRequired("approval-1", "delete_task", "{}"),
                ManagedAgentRunEvent.AssistantDelta("continued"),
                ManagedAgentRunEvent.Completed("run-1"),
            )
        )

        val outcome = gateway(runs).run(request(AgentApprovalPolicy.SKIP))

        assertEquals(AgentRunOutcome.Completed("continued"), outcome)
        assertEquals(listOf("approval-1" to false), runs.resolved)
    }

    @Test
    fun failedAndRejectedEventsKeepTheirSafeCodes() = runTest {
        val failed = gateway(
            FakeRuns(listOf(ManagedAgentRunEvent.Failed("run-1", "provider_unavailable")))
        ).run(request(AgentApprovalPolicy.FAIL))
        assertEquals(AgentRunOutcome.Failed("provider_unavailable"), failed)

        val rejected = gateway(
            FakeRuns(listOf(ManagedAgentRunEvent.Rejected("agent_unavailable")))
        ).run(request(AgentApprovalPolicy.FAIL))
        assertEquals(AgentRunOutcome.Failed("agent_unavailable"), rejected)
    }

    @Test
    fun anExistingIdempotentRunIsNotReportedAsSuccess() = runTest {
        val outcome = gateway(
            FakeRuns(listOf(ManagedAgentRunEvent.Existing("run-1")))
        ).run(request(AgentApprovalPolicy.FAIL))

        assertEquals(AgentRunOutcome.Failed("existing_run"), outcome)
    }

    @Test
    fun aRunThatEndsWithoutATerminalEventFailsClosed() = runTest {
        val outcome = gateway(
            FakeRuns(listOf(ManagedAgentRunEvent.AssistantDelta("partial")))
        ).run(request(AgentApprovalPolicy.FAIL))

        assertTrue(outcome is AgentRunOutcome.Failed)
        assertEquals("run_incomplete", (outcome as AgentRunOutcome.Failed).safeCode)
    }

    @Test
    fun theGatewayHonoursTheActionDeadline() = runTest {
        val slow = object : ManagedAgentRunUseCase {
            override fun observeRuns(agentId: String): Flow<List<AgentRun>> = flow { emit(emptyList()) }

            override fun runAgent(
                agentId: String,
                prompt: String,
                idempotencyKey: String
            ): Flow<ManagedAgentRunEvent> = flow {
                emit(ManagedAgentRunEvent.AssistantDelta("slow"))
                kotlinx.coroutines.delay(2_000)
                emit(ManagedAgentRunEvent.Completed("run-1"))
            }

            override suspend fun resolveApproval(approvalId: String, approved: Boolean): Boolean = true
        }

        val outcome = gateway(slow).run(
            request(AgentApprovalPolicy.FAIL, millis = 50L)
        )

        assertEquals(AgentRunOutcome.Failed("deadline_exceeded"), outcome)
    }
}
