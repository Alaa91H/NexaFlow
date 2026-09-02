package com.nexaflow.core.execution.workflow

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowInterpreterFlowControlTest {

    @Test
    fun retryRetriesFailedBodyUntilItSucceeds() = runBlocking {
        var attempts = 0
        val interpreter = WorkflowInterpreter(ActionExecutor {
            attempts++
            if (attempts < 3) SystemControlResult.fail("temporary") else SystemControlResult.ok("ok")
        })

        val result = interpreter.execute(
            WorkflowNode.RetryNode(
                id = "retry",
                body = WorkflowNode.ActionNode("action", action("retry")),
                maxAttempts = 3
            )
        )

        assertTrue(result.success)
        assertEquals(3, attempts)
        assertTrue(result.nodeResults.any { it.nodeId == "retry" && it.success })
    }

    @Test
    fun whileStopsWhenConditionBecomesFalse() = runBlocking {
        var remaining = 3
        val calls = mutableListOf<String>()
        val interpreter = WorkflowInterpreter(ActionExecutor {
            calls += it.config.getValue("id")
            remaining--
            SystemControlResult.ok("ok")
        })

        val result = interpreter.execute(
            WorkflowNode.WhileNode(
                id = "while",
                condition = WorkflowCondition {
                    delay(0L)
                    remaining > 0
                },
                body = WorkflowNode.ActionNode("tick", action("tick"))
            )
        )

        assertTrue(result.success)
        assertEquals(listOf("tick", "tick", "tick"), calls)
    }

    @Test
    fun tryRunsCatchAndFinallyAfterBodyFailure() = runBlocking {
        val calls = mutableListOf<String>()
        val interpreter = WorkflowInterpreter(ActionExecutor { action ->
            calls += action.config.getValue("id")
            if (action.config.getValue("id") == "body") SystemControlResult.fail("body failed")
            else SystemControlResult.ok("ok")
        })

        val result = interpreter.execute(
            WorkflowNode.TryNode(
                id = "try",
                body = WorkflowNode.ActionNode("body", action("body")),
                catchNode = WorkflowNode.ActionNode("catch", action("catch")),
                finallyNode = WorkflowNode.ActionNode("finally", action("finally"))
            )
        )

        assertTrue(result.success)
        assertEquals(listOf("body", "catch", "finally"), calls)
    }

    @Test
    fun timeoutCancelsSlowChildAndReturnsFailure() = runBlocking {
        val interpreter = WorkflowInterpreter(ActionExecutor { SystemControlResult.ok("unused") })

        val result = interpreter.execute(
            WorkflowNode.TimeoutNode(
                id = "timeout",
                body = WorkflowNode.DelayNode("slow", 100L),
                timeoutMs = 10L
            )
        )

        assertFalse(result.success)
        assertTrue(result.nodeResults.any { it.nodeId == "timeout" && !it.success })
    }

    @Test
    fun raceCompletesWithFirstFinishedBranch() = runBlocking {
        val interpreter = WorkflowInterpreter(ActionExecutor { SystemControlResult.ok("unused") })

        val result = interpreter.execute(
            WorkflowNode.RaceNode(
                id = "race",
                children = listOf(
                    WorkflowNode.DelayNode("fast", 1L),
                    WorkflowNode.DelayNode("slow", 100L)
                )
            )
        )

        assertTrue(result.success)
        assertTrue(result.nodeResults.any { it.nodeId == "fast" })
    }

    @Test
    fun waitUntilUsesBoundedPollingAndCondition() = runBlocking {
        var checks = 0
        val interpreter = WorkflowInterpreter(ActionExecutor { SystemControlResult.ok("unused") })

        val result = interpreter.execute(
            WorkflowNode.WaitUntilNode(
                id = "wait",
                condition = WorkflowCondition {
                    delay(0L),
                    ++checks == 3
                },
                timeoutMs = 1000L,
                pollIntervalMs = 10L
            )
        )

        assertTrue(result.success)
        assertEquals(3, checks)
    }

    @Test
    fun strictWhileConditionFailureStopsWithoutHidingRuntimeErrors() = runBlocking {
        val interpreter = WorkflowInterpreter(ActionExecutor { SystemControlResult.ok("unused") })

        val result = interpreter.execute(
            WorkflowNode.WhileNode(
                id = "while",
                condition = WorkflowCondition {
                    delay(0L),
                    error("while condition is unavailable")
                },
                body = WorkflowNode.ActionNode("tick", action("tick"))
            )
        )

        assertFalse(result.success)
        assertTrue(result.nodeResults.single().message.contains("while condition is unavailable"))
    }

    @Test
    fun branchConditionFailureStopsWithoutExecutingEitherPath() = runBlocking {
        val interpreter = WorkflowInterpreter(ActionExecutor { SystemControlResult.ok("unused") })

        val result = interpreter.execute(
            WorkflowNode.BranchNode(
                id = "branch",
                condition = WorkflowCondition {
                    delay(0L),
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

    private fun action(id: String): Action = Action(
        type = ActionType.SYSTEM_WAIT,
        config = mapOf("id" to id, "seconds" to "0")
    )
}
