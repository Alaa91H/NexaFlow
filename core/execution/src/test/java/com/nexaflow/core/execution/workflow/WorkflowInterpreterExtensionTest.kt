package com.nexaflow.core.execution.workflow

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowInterpreterExtensionTest {

    @Test
    fun subworkflowPassesInputsAndPublishesOnlyRequestedOutputs() = runBlocking {
        var receivedInputs: Map<String, String>? = null
        var receivedDepth: Int? = null
        var receivedBudgetConsumed: Int? = null
        val interpreter = WorkflowInterpreter(
            executor = ActionExecutor { SystemControlResult.ok("unused") },
            subworkflowProvider = object : SubworkflowProvider {
                override suspend fun executeSubworkflow(
                    workflowId: String,
                    inputParameters: Map<String, String>,
                    depth: Int
                ): WorkflowExecutionResult = executeSubworkflow(
                    workflowId,
                    inputParameters,
                    depth,
                    WorkflowExecutionBudget.fromPolicy(WorkflowExecutionPolicy())
                )

                override suspend fun executeSubworkflow(
                    workflowId: String,
                    inputParameters: Map<String, String>,
                    depth: Int,
                    executionBudget: WorkflowExecutionBudget
                ): WorkflowExecutionResult {
                    assertEquals("child", workflowId)
                    assertEquals(1, depth)
                    receivedInputs = inputParameters
                    receivedDepth = depth
                    receivedBudgetConsumed = executionBudget.consumedNodeVisits
                    return WorkflowExecutionResult(
                        success = true,
                        message = "child complete",
                        nodeResults = emptyList(),
                        durationMs = 1,
                        outputs = mapOf("approved" to "yes", "secret" to "hidden")
                    )
                }
            }
        )

        val result = interpreter.execute(
            SubworkflowNode(
                id = "invoke",
                workflowId = "child",
                inputParameters = mapOf("request" to "%name"),
                outputParameters = listOf("approved")
            )
        )

        assertTrue(result.success)
        assertEquals(mapOf("request" to "%name"), receivedInputs)
        assertEquals(mapOf("approved" to "yes"), result.outputs)
        assertEquals(1, receivedDepth)
        assertTrue(receivedBudgetConsumed!! >= 1)
    }

    @Test
    fun subworkflowFailsClosedWhenProviderIsMissing() = runBlocking {
        val result = WorkflowInterpreter(
            executor = ActionExecutor { SystemControlResult.ok("unused") }
        ).execute(
            SubworkflowNode(id = "invoke", workflowId = "missing")
        )

        assertFalse(result.success)
        assertTrue(result.nodeResults.single().message.contains("no provider"))
    }

    @Test
    fun subworkflowStopsAtConfiguredRecursionDepth() = runBlocking {
        val interpreter = WorkflowInterpreter(
            executor = ActionExecutor { SystemControlResult.ok("unused") },
            maxSubworkflowDepth = 1,
            subworkflowProvider = object : SubworkflowProvider {
                override suspend fun executeSubworkflow(
                    workflowId: String,
                    inputParameters: Map<String, String>,
                    depth: Int
                ): WorkflowExecutionResult = executeSubworkflow(
                    workflowId,
                    inputParameters,
                    depth,
                    WorkflowExecutionBudget.fromPolicy(WorkflowExecutionPolicy())
                )

                override suspend fun executeSubworkflow(
                    workflowId: String,
                    inputParameters: Map<String, String>,
                    depth: Int,
                    executionBudget: WorkflowExecutionBudget
                ): WorkflowExecutionResult = WorkflowExecutionResult(
                    success = true,
                    message = "provider does not recurse",
                    nodeResults = emptyList(),
                    durationMs = 0
                )
            }
        )

        val result = interpreter.execute(
            SubworkflowNode(id = "root", workflowId = "child")
        )

        assertTrue(result.success)
        assertTrue(result.nodeResults.single().message.contains("Subworkflow child"))
    }

    @Test
    fun approvalUsesGatewayDecision() = runBlocking {
        val requested = mutableListOf<String>()
        val interpreter = WorkflowInterpreter(
            executor = ActionExecutor { SystemControlResult.ok("unused") },
            approvalGateway = object : ApprovalGateway {
                override suspend fun requestApproval(
                    nodeId: String,
                    prompt: String,
                    timeoutMs: Long,
                    onTimeout: HumanApprovalNode.TimeoutOutcome
                ): Boolean {
                    requested += "$nodeId:$prompt"
                    return true
                }
            }
        )

        val result = interpreter.execute(
            HumanApprovalNode(
                id = "approve",
                prompt = "Run the operation?",
                timeoutMs = 5000L
            )
        )

        assertTrue(result.success)
        assertEquals(listOf("approve:Run the operation?"), requested)
        assertTrue(result.nodeResults.single().message.contains("granted"))
    }

    @Test
    fun approvalTimeoutFollowsExplicitRejectPolicy() = runBlocking {
        val interpreter = WorkflowInterpreter(
            executor = ActionExecutor { SystemControlResult.ok("unused") },
            approvalGateway = object : ApprovalGateway {
                override suspend fun requestApproval(
                    nodeId: String,
                    prompt: String,
                    timeoutMs: Long,
                    onTimeout: HumanApprovalNode.TimeoutOutcome
                ): Boolean = kotlinx.coroutines.awaitCancellation()
            }
        )

        val result = interpreter.execute(
            HumanApprovalNode(
                id = "approve",
                prompt = "Approve?",
                timeoutMs = 5000L,
                onTimeout = HumanApprovalNode.TimeoutOutcome.REJECT
            )
        )

        assertFalse(result.success)
        assertTrue(result.nodeResults.single().message.contains("timed out"))
    }

    @Test
    fun forEachBindsItemAndRestoresTheOuterScope() = runBlocking {
        val seen = mutableListOf<String>()
        val interpreter = WorkflowInterpreter(
            executor = ActionExecutor { action ->
                seen += action.config.getValue("value")
                SystemControlResult.ok("ok")
            }
        )

        val result = interpreter.execute(
            WorkflowNode.SequenceNode(
                id = "root",
                children = listOf(
                    ForEachNode(
                        id = "items",
                        items = listOf("red", "green", "blue"),
                        itemVariable = "item",
                        body = WorkflowNode.ActionNode(
                            id = "emit",
                            action = action("%item")
                        )
                    ),
                    WorkflowNode.ActionNode("after", action("%item"))
                )
            )
        )

        assertTrue(result.success)
        assertEquals(listOf("red", "green", "blue", "%item"), seen)
        assertFalse(result.outputs.containsKey("item"))
    }

    @Test
    fun sagaCompensatesAfterSequenceFailureInReverseOrder() = runBlocking {
        val executed = mutableListOf<String>()
        val compensated = mutableListOf<String>()
        val interpreter = WorkflowInterpreter(
            executor = ActionExecutor { action ->
                val id = action.config.getValue("id")
                executed += id
                if (id == "fail") SystemControlResult.fail("expected failure")
                else SystemControlResult.ok("ok")
            },
            rollbackHandler = RollbackHandler { node ->
                compensated += node.action.config.getValue("id")
            }
        )

        val result = interpreter.execute(
            WorkflowNode.SequenceNode(
                id = "root",
                rollbackOnFailure = true,
                children = listOf(
                    SagaNode(
                        id = "saga",
                        body = WorkflowNode.ActionNode("create", action("create")),
                        compensation = WorkflowNode.ActionNode("delete", action("delete"))
                    ),
                    WorkflowNode.ActionNode("fail", action("fail"))
                )
            )
        )

        assertFalse(result.success)
        val createIndex = executed.indexOf("create")
        val deleteIndex = executed.indexOf("delete")
        assertTrue(createIndex >= 0)
        assertTrue(deleteIndex > createIndex)
    }

    @Test
    fun sharedBudgetIsPropagatedToNestedExecution() = runBlocking {
        val exchanges = mutableListOf<Int>()
        val interpreter = WorkflowInterpreter(
            executor = ActionExecutor { SystemControlResult.ok("unused") },
            subworkflowProvider = object : SubworkflowProvider {
                override suspend fun executeSubworkflow(
                    workflowId: String,
                    inputParameters: Map<String, String>,
                    depth: Int
                ): WorkflowExecutionResult = executeSubworkflow(
                    workflowId,
                    inputParameters,
                    depth,
                    WorkflowExecutionBudget.fromPolicy(WorkflowExecutionPolicy())
                )

                override suspend fun executeSubworkflow(
                    workflowId: String,
                    inputParameters: Map<String, String>,
                    depth: Int,
                    executionBudget: WorkflowExecutionBudget
                ): WorkflowExecutionResult {
                    exchanges += executionBudget.consumedNodeVisits
                    return WorkflowExecutionResult(
                        success = true,
                        message = "child complete",
                        nodeResults = emptyList(),
                        durationMs = 0
                    )
                }
            }
        )

        interpreter.execute(
            SubworkflowNode(
                id = "invoke",
                workflowId = "child",
                inputParameters = emptyMap(),
                outputParameters = emptyList()
            )
        )

        assertEquals(1, exchanges.size)
        assertTrue(exchanges[0] >= 1)
    }

    @Test
    fun strictConditionFailureStopsWithoutExecutingEitherBranch() = runBlocking {
        val interpreter = WorkflowInterpreter(
            executor = ActionExecutor { SystemControlResult.ok("unused") }
        )

        val result = interpreter.execute(
            WorkflowNode.BranchNode(
                id = "branch",
                condition = WorkflowCondition {
                    delay(0L)
                    error("branch condition is unavailable")
                },
                whenTrue = WorkflowNode.ActionNode("yes", action("yes")),
                whenFalse = WorkflowNode.ActionNode("no", action("no"))
            )
        )

        assertFalse(result.success)
        assertTrue(result.nodeResults.single().message.contains("branch condition is unavailable"))
        val executed = result.nodeResults.any { it.nodeId in listOf("yes", "no") }
        assertFalse(executed)
    }

    @Test
    fun nodeVisitLimitProducesAFailedNodeResult() = runBlocking {
        val interpreter = WorkflowInterpreter(
            executor = ActionExecutor { SystemControlResult.ok("unused") },
            executionPolicy = WorkflowExecutionPolicy(
                maxExecutionTimeMs = 15 * 60 * 1000L,
                maxNodeVisits = 1
            )
        )

        val result = interpreter.execute(
            WorkflowNode.SequenceNode(
                id = "seq",
                children = listOf(
                    WorkflowNode.ActionNode("a", action("a")),
                    WorkflowNode.ActionNode("b", action("b"))
                )
            )
        )

        assertFalse(result.success)
        assertTrue(
            result.nodeResults.any {
                it.message.contains("node-visit")
            }
        )
    }

    private fun action(id: String): Action = Action(
        type = ActionType.SYSTEM_WAIT,
        config = mapOf("id" to id, "value" to id, "seconds" to "0")
    )
}
