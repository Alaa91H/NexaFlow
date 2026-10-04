package com.nexaflow.core.agentruntime

import com.nexaflow.core.airuntime.AiToolCall
import com.nexaflow.core.airuntime.AiToolDefinition
import com.nexaflow.core.airuntime.AiToolExecutor
import com.nexaflow.core.airuntime.AiToolResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRuntimePolicyTest {

    @Test
    fun policyExecutorHidesAndRejectsToolsOutsideAllowlist() = runTest {
        val source = FakeExecutor("tasks.read", "device.lock")
        val executor = PolicyFilteredAgentToolExecutor(
            agentId = "assistant",
            source = source,
            policy = AgentPolicy(allowedToolNames = setOf("tasks.read")),
            approvalGate = AgentToolApprovalGate { _, _, _ -> true }
        )

        assertEquals(listOf("tasks.read"), executor.tools.value.map { it.name })
        val result = executor.execute(call("device.lock"))

        assertTrue(result.isError)
        assertEquals("tool_not_allowed", result.output.jsonObject["error"]?.jsonPrimitive?.content)
        assertEquals(0, source.executions)
    }

    @Test
    fun configuredApprovalIsRequiredBeforeExecutingTool() = runTest {
        val source = FakeExecutor("tasks.read", "device.lock")
        val executor = PolicyFilteredAgentToolExecutor(
            agentId = "assistant",
            source = source,
            policy = AgentPolicy(
                allowedToolNames = setOf("tasks.read", "device.lock"),
                approvalRequiredToolNames = setOf("device.lock")
            ),
            approvalGate = AgentToolApprovalGate { _, _, _ -> false }
        )

        val result = executor.execute(call("device.lock"))

        assertTrue(result.isError)
        assertEquals("approval_denied", result.output.jsonObject["error"]?.jsonPrimitive?.content)
        assertEquals(0, source.executions)
    }

    @Test
    fun toolCatalogUsesTheRunStartSnapshotForRuntimeRegistry() = runTest {
        val source = FakeExecutor("tasks.read")
        val executor = PolicyFilteredAgentToolExecutor(
            agentId = "assistant",
            source = source,
            policy = AgentPolicy(allowedToolNames = setOf("tasks.read", "device.lock")),
            approvalGate = AgentToolApprovalGate { _, _, _ -> true }
        )

        source.tools.value += AiToolDefinition(
            name = "device.lock",
            description = "device.lock",
            inputSchema = buildJsonObject { put("type", "object") }
        )
        assertEquals(setOf("tasks.read"), executor.tools.value.map { it.name }.toSet())
    }

    @Test
    fun allowedToolExecutesWithoutRequiringUnconfiguredApproval() = runTest {
        val source = FakeExecutor("tasks.read")
        source.tools.value = source.tools.value.map { it.copy(readOnly = true) }
        val executor = PolicyFilteredAgentToolExecutor(
            agentId = "assistant",
            source = source,
            policy = AgentPolicy(allowedToolNames = setOf("tasks.read")),
            approvalGate = AgentToolApprovalGate { _, _, _ -> error("approval was not required") }
        )

        val result = executor.execute(call("tasks.read"))

        assertFalse(result.isError)
        assertEquals(1, source.executions)
    }

    @Test
    fun allowedWriteToolRequiresApprovalEvenWhenSavedPolicyHasNoApprovalEntry() = runTest {
        val source = FakeExecutor("nexaflow.create_task")
        var approvalRequested = false
        val executor = PolicyFilteredAgentToolExecutor(
            agentId = "assistant",
            source = source,
            policy = AgentPolicy(allowedToolNames = setOf("nexaflow.create_task")),
            approvalGate = AgentToolApprovalGate { _, _, _ ->
                approvalRequested = true
                false
            }
        )

        val result = executor.execute(call("nexaflow.create_task"))

        assertTrue(approvalRequested)
        assertTrue(result.isError)
        assertEquals("approval_denied", result.output.jsonObject["error"]?.jsonPrimitive?.content)
        assertEquals(0, source.executions)
    }

    @Test
    fun aPersistedExplicitApprovalOptOutIsHonoredForAnAllowedWriteTool() = runTest {
        val source = FakeExecutor("nexaflow.create_task")
        val executor = PolicyFilteredAgentToolExecutor(
            agentId = "assistant",
            source = source,
            policy = AgentPolicy(
                allowedToolNames = setOf("nexaflow.create_task"),
                approvalOptionalToolNames = setOf("nexaflow.create_task")
            ),
            approvalGate = AgentToolApprovalGate { _, _, _ -> error("explicit opt-out should not request approval") }
        )

        val result = executor.execute(call("nexaflow.create_task"))

        assertFalse(result.isError)
        assertEquals(1, source.executions)
    }

    @Test
    fun explicitlyElevatedWriteToolCannotOptOutOfApproval() = runTest {
        val source = FakeExecutor("nexaflow.run_root_action")
        var approvalRequested = false
        source.tools.value = source.tools.value.map { it.copy(destructive = true) }
        val approvedPolicy = AgentPolicy(
            allowedToolNames = setOf("nexaflow.run_root_action"),
            approvalOptionalToolNames = setOf("nexaflow.run_root_action")
        )
        val effectivePolicy = approvedPolicy.copy(
            approvalOptionalToolNames = approvedPolicy.approvalOptionalToolNames -
                source.tools.value.filter { it.destructive || !it.readOnly }.map { it.name }.toSet()
        )
        val executor = PolicyFilteredAgentToolExecutor(
            agentId = "assistant",
            source = source,
            policy = effectivePolicy,
            approvalGate = AgentToolApprovalGate { _, _, _ ->
                approvalRequested = true
                false
            }
        )

        val result = executor.execute(call("nexaflow.run_root_action"))

        assertTrue(approvalRequested)
        assertTrue(result.isError)
        assertEquals("approval_denied", result.output.jsonObject["error"]?.jsonPrimitive?.content)
        assertEquals(0, source.executions)
    }

    @Test
    fun liveExecutionGuardRevokesAnAlreadySnapshottedToolBeforeItsSideEffect() = runTest {
        val source = FakeExecutor("nexaflow.create_task")
        var definitionStillCurrent = true
        val executor = PolicyFilteredAgentToolExecutor(
            agentId = "assistant",
            source = source,
            policy = AgentPolicy(allowedToolNames = setOf("nexaflow.create_task")),
            approvalGate = AgentToolApprovalGate { _, _, _ -> true },
            executionGuard = AgentToolExecutionGuard {
                if (definitionStillCurrent) null else "agent_definition_changed"
            }
        )

        definitionStillCurrent = false
        val result = executor.execute(call("nexaflow.create_task"))

        assertTrue(result.isError)
        assertEquals("agent_definition_changed", result.output.jsonObject["error"]?.jsonPrimitive?.content)
        assertEquals(0, source.executions)
    }

    @Test
    fun budgetLedgerEnforcesTurnsCallsOutputDeadlineAndUnknownCost() {
        var now = 100L
        val ledger = AgentBudgetLedger(
            AgentBudget(
                maxDurationMillis = 50,
                maxTurns = 1,
                maxToolCalls = 1,
                maxOutputCharacters = 4,
                maxCostMicros = 10
            ),
            monotonicClock = { now }
        )

        assertEquals(AgentBudgetDecision.ALLOWED, ledger.beginTurn())
        assertEquals(AgentBudgetDecision.TURN_LIMIT, ledger.beginTurn())
        assertEquals(AgentBudgetDecision.ALLOWED, ledger.beginToolCall())
        assertEquals(AgentBudgetDecision.TOOL_CALL_LIMIT, ledger.beginToolCall())
        assertEquals(AgentBudgetDecision.ALLOWED, ledger.reserveOutput(4))
        assertEquals(AgentBudgetDecision.OUTPUT_LIMIT, ledger.reserveOutput(1))
        assertEquals(AgentBudgetDecision.COST_UNKNOWN, ledger.recordCost(null))
        assertEquals(AgentBudgetDecision.ALLOWED, ledger.recordCost(10))
        assertEquals(AgentBudgetDecision.COST_LIMIT, ledger.recordCost(1))
        now = 151L
        assertEquals(AgentBudgetDecision.DEADLINE, ledger.beginToolCall())
    }

    @Test
    fun definitionRequiresAValidStableIdentityAndApprovalSubset() {
        assertTrue(runCatching { AgentDefinition(id = "bad id", name = "Bad") }.isFailure)
        assertTrue(
            runCatching {
                AgentPolicy(
                    allowedToolNames = setOf("tasks.read"),
                    approvalRequiredToolNames = setOf("device.lock")
                )
            }.isFailure
        )
        assertFalse(AgentDefinition(id = "assistant", name = "Assistant").enabled)
    }

    private fun call(name: String) = AiToolCall(
        id = "call-$name",
        name = name,
        arguments = buildJsonObject {}
    )

    private class FakeExecutor(vararg names: String) : AiToolExecutor {
        override val tools = MutableStateFlow(
            names.map { name ->
                AiToolDefinition(
                    name = name,
                    description = name,
                    inputSchema = buildJsonObject { put("type", "object") }
                )
            }
        )
        var executions = 0

        override suspend fun execute(call: AiToolCall): AiToolResult {
            executions += 1
            return AiToolResult(call.id, call.name, buildJsonObject {})
        }
    }
}
