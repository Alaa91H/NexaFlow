package com.nexaflow.core.execution.workflow

import com.nexaflow.core.common.EpochMillis
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowInterpreterTest {

    private fun action(id: String) =
        Action(type = ActionType.SYSTEM_WAIT, config = mapOf("id" to id, "seconds" to "0"))

    private fun recordingExecutor(log: MutableList<String>, failOn: Set<String> = emptySet()): ActionExecutor {
        return ActionExecutor { a ->
            log += a.config["id"] ?: "?"
            if (a.config["id"] in failOn) SystemControlResult.fail("boom:${a.config["id"]}")
            else SystemControlResult.ok("ok:${a.config["id"]}")
        }
    }

    @Test
    fun sequential_runsAllInOrder() = runBlocking {
        val log = mutableListOf<String>()
        val interpreter = WorkflowInterpreter(recordingExecutor(log))
        val workflow = WorkflowNode.SequenceNode(
            id = "seq",
            children = listOf(
                WorkflowNode.ActionNode("a", action("a")),
                WorkflowNode.ActionNode("b", action("b")),
                WorkflowNode.ActionNode("c", action("c"))
            )
        )
        val result = interpreter.execute(workflow)
        assertTrue(result.success)
        assertEquals(listOf("a", "b", "c"), log)
        assertEquals(3, result.nodeResults.size)
    }

    @Test
    fun sequential_stopsOnFirstFailure() = runBlocking {
        val log = mutableListOf<String>()
        val interpreter = WorkflowInterpreter(recordingExecutor(log, failOn = setOf("b")))
        val workflow = WorkflowNode.SequenceNode(
            id = "seq",
            children = listOf(
                WorkflowNode.ActionNode("a", action("a")),
                WorkflowNode.ActionNode("b", action("b")),
                WorkflowNode.ActionNode("c", action("c"))
            )
        )
        val result = interpreter.execute(workflow)
        assertFalse(result.success)
        assertEquals(listOf("a", "b"), log)
    }

    @Test
    fun parallel_runsAllChildren() = runBlocking {
        val log = java.util.Collections.synchronizedList(mutableListOf<String>())
        val interpreter = WorkflowInterpreter(recordingExecutor(log))
        val workflow = WorkflowNode.ParallelNode(
            id = "par",
            children = listOf(
                WorkflowNode.ActionNode("a", action("a")),
                WorkflowNode.ActionNode("b", action("b")),
                WorkflowNode.ActionNode("c", action("c"))
            )
        )
        val result = interpreter.execute(workflow)
        assertTrue(result.success)
        assertEquals(setOf("a", "b", "c"), log.toSet())
        assertEquals(3, result.nodeResults.size)
    }

    @Test
    fun parallel_failsWhenAnyChildFails() = runBlocking {
        val log = java.util.Collections.synchronizedList(mutableListOf<String>())
        val interpreter = WorkflowInterpreter(recordingExecutor(log, failOn = setOf("b")))
        val workflow = WorkflowNode.ParallelNode(
            id = "par",
            children = listOf(
                WorkflowNode.ActionNode("a", action("a")),
                WorkflowNode.ActionNode("b", action("b")),
                WorkflowNode.ActionNode("c", action("c"))
            )
        )
        val result = interpreter.execute(workflow)
        assertFalse(result.success)
        assertEquals(3, result.nodeResults.size)
    }

    @Test
    fun branch_takesTruePath() = runBlocking {
        val log = mutableListOf<String>()
        val interpreter = WorkflowInterpreter(recordingExecutor(log))
        val workflow = WorkflowNode.BranchNode(
            id = "branch",
            condition = WorkflowCondition { true },
            whenTrue = WorkflowNode.ActionNode("yes", action("yes")),
            whenFalse = WorkflowNode.ActionNode("no", action("no"))
        )
        val result = interpreter.execute(workflow)
        assertTrue(result.success)
        assertEquals(listOf("yes"), log)
    }

    @Test
    fun branch_takesFalsePath() = runBlocking {
        val log = mutableListOf<String>()
        val interpreter = WorkflowInterpreter(recordingExecutor(log))
        val workflow = WorkflowNode.BranchNode(
            id = "branch",
            condition = WorkflowCondition { false },
            whenTrue = WorkflowNode.ActionNode("yes", action("yes")),
            whenFalse = WorkflowNode.ActionNode("no", action("no"))
        )
        val result = interpreter.execute(workflow)
        assertTrue(result.success)
        assertEquals(listOf("no"), log)
    }

    @Test
    fun branch_conditionFailureStopsWithoutExecutingEitherPath() = runBlocking {
        val log = mutableListOf<String>()
        val interpreter = WorkflowInterpreter(recordingExecutor(log))
        val workflow = WorkflowNode.BranchNode(
            id = "branch",
            condition = WorkflowCondition { throw IllegalStateException("condition unavailable") },
            whenTrue = WorkflowNode.ActionNode("yes", action("yes")),
            whenFalse = WorkflowNode.ActionNode("no", action("no"))
        )

        val result = interpreter.execute(workflow)

        assertFalse(result.success)
        assertTrue(log.isEmpty())
        assertEquals("branch", result.nodeResults.single().nodeId)
        assertFalse(result.nodeResults.single().success)
        assertTrue(result.nodeResults.single().message.contains("condition unavailable"))
    }

    @Test
    fun branch_withoutElseSucceedsWhenFalse() = runBlocking {
        val log = mutableListOf<String>()
        val interpreter = WorkflowInterpreter(recordingExecutor(log))
        val workflow = WorkflowNode.BranchNode(
            id = "branch",
            condition = WorkflowCondition { false },
            whenTrue = WorkflowNode.ActionNode("yes", action("yes"))
        )
        val result = interpreter.execute(workflow)
        assertTrue(result.success)
        assertTrue(log.isEmpty())
    }

    @Test
    fun loop_repeatsBody() = runBlocking {
        val log = mutableListOf<String>()
        val interpreter = WorkflowInterpreter(recordingExecutor(log))
        val workflow = WorkflowNode.LoopNode(
            id = "loop",
            iterations = 4,
            body = WorkflowNode.ActionNode("tick", action("tick"))
        )
        val result = interpreter.execute(workflow)
        assertTrue(result.success)
        assertEquals(listOf("tick", "tick", "tick", "tick"), log)
    }

    @Test
    fun loop_stopsOnBodyFailure() = runBlocking {
        val log = mutableListOf<String>()
        val interpreter = WorkflowInterpreter(recordingExecutor(log, failOn = setOf("tick")))
        val workflow = WorkflowNode.LoopNode(
            id = "loop",
            iterations = 4,
            body = WorkflowNode.ActionNode("tick", action("tick"))
        )
        val result = interpreter.execute(workflow)
        assertFalse(result.success)
        assertEquals(1, log.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun loop_rejectsIterationsAboveBoundedLimit() {
        WorkflowNode.LoopNode(
            id = "oversized-loop",
            iterations = WorkflowNode.LoopNode.MAX_ITERATIONS + 1,
            body = WorkflowNode.ActionNode("tick", action("tick"))
        )
    }

    @Test(expected = CancellationException::class)
    fun cancellationPropagatesInsteadOfBecomingActionFailure() {
        runBlocking {
            val interpreter = WorkflowInterpreter(
                executor = ActionExecutor { throw CancellationException("cancelled by caller") }
            )

            interpreter.execute(WorkflowNode.ActionNode("cancel", action("cancel")))
        }
    }

    @Test
    fun rollback_invokesHandlerInReverseOrder() = runBlocking {
        val log = mutableListOf<String>()
        val rolledBack = mutableListOf<String>()
        val interpreter = WorkflowInterpreter(
            executor = recordingExecutor(log, failOn = setOf("c")),
            rollbackHandler = RollbackHandler { node ->
                rolledBack += node.action.config["id"] ?: "?"
            }
        )
        val workflow = WorkflowNode.SequenceNode(
            id = "seq",
            rollbackOnFailure = true,
            children = listOf(
                WorkflowNode.ActionNode("a", action("a")),
                WorkflowNode.ActionNode("b", action("b")),
                WorkflowNode.ActionNode("c", action("c")),
                WorkflowNode.ActionNode("d", action("d"))
            )
        )
        val result = interpreter.execute(workflow)
        assertFalse(result.success)
        // The failing action itself is still logged by the executor (it ran and failed).
        assertEquals(listOf("a", "b", "c"), log)
        // Rolled back in reverse order: b then a (c is the failing node, not rolled back).
        assertEquals(listOf("b", "a"), rolledBack)
    }

    @Test
    fun rollback_disabledWhenNotRequested() = runBlocking {
        val log = mutableListOf<String>()
        val rolledBack = mutableListOf<String>()
        val interpreter = WorkflowInterpreter(
            executor = recordingExecutor(log, failOn = setOf("c")),
            rollbackHandler = RollbackHandler { node ->
                rolledBack += node.action.config["id"] ?: "?"
            }
        )
        val workflow = WorkflowNode.SequenceNode(
            id = "seq",
            rollbackOnFailure = false,
            children = listOf(
                WorkflowNode.ActionNode("a", action("a")),
                WorkflowNode.ActionNode("b", action("b")),
                WorkflowNode.ActionNode("c", action("c"))
            )
        )
        interpreter.execute(workflow)
        assertTrue(rolledBack.isEmpty())
    }

    @Test
    fun timeout_marksNodeFailed() = runBlocking {
        val interpreter = WorkflowInterpreter(
            executor = ActionExecutor {
                delay(200)
                SystemControlResult.ok("late")
            },
            defaultNodeTimeoutMs = 50
        )
        val workflow = WorkflowNode.ActionNode("slow", action("slow"))
        val result = interpreter.execute(workflow)
        assertFalse(result.success)
        assertTrue(result.nodeResults.first().message.contains("timed out"))
    }

    @Test
    fun duration_isRecorded() = runBlocking {
        var now = 0L
        val clock = EpochMillis { now }
        val interpreter = WorkflowInterpreter(
            executor = ActionExecutor {
                now = 1000 // clock advances during the action execution
                SystemControlResult.ok("ok")
            },
            epochMillis = clock
        )
        val workflow = WorkflowNode.ActionNode("a", action("a"))
        val result = interpreter.execute(workflow)
        assertEquals(1000, result.durationMs)
    }
}
