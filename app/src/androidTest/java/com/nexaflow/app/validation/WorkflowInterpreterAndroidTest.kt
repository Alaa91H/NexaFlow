package com.nexaflow.app.validation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexaflow.core.execution.workflow.ActionExecutor
import com.nexaflow.core.execution.workflow.WorkflowInterpreter
import com.nexaflow.core.execution.workflow.WorkflowNode
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected-device coverage for deterministic control-flow semantics.
 *
 * This deliberately exercises the production interpreter with a bounded, cooperative executor.
 * It does not represent verification of a privileged Android action or an external side effect.
 */
@RunWith(AndroidJUnit4::class)
class WorkflowInterpreterAndroidTest {

    @Test
    fun retryUsesBoundedAttemptsUntilTheRealInterpreterSeesSuccess() = runBlocking {
        var attempts = 0
        val interpreter = WorkflowInterpreter(
            ActionExecutor {
                attempts += 1
                if (attempts < 3) SystemControlResult.fail("temporary") else SystemControlResult.ok("ok")
            }
        )

        val result = interpreter.execute(
            WorkflowNode.RetryNode(
                id = "retry",
                body = WorkflowNode.ActionNode("attempt", action("retry")),
                maxAttempts = 3,
                backoffMs = 10L
            )
        )

        assertTrue(result.success)
        assertEquals(3, attempts)
        assertTrue(result.nodeResults.any { it.nodeId == "retry" && it.success })
    }

    @Test
    fun timeoutCancelsSlowChildAndReturnsFailedWorkflow() = runBlocking {
        val interpreter = WorkflowInterpreter(ActionExecutor { SystemControlResult.ok("unused") })

        val result = interpreter.execute(
            WorkflowNode.TimeoutNode(
                id = "timeout",
                body = WorkflowNode.DelayNode("slow", delayMs = 2_000L),
                timeoutMs = 200L
            )
        )

        assertFalse(result.success)
        assertTrue(result.nodeResults.any { it.nodeId == "timeout" && !it.success })
    }

    @Test
    fun cancellationPropagatesToRunningExecutorWithoutProducingSuccess() = runBlocking {
        val started = Channel<Unit>(capacity = 1)
        val cancelled = Channel<Unit>(capacity = 1)
        val interpreter = WorkflowInterpreter(
            ActionExecutor {
                started.trySend(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.trySend(Unit)
                }
            }
        )

        coroutineScope {
            val running = async {
                interpreter.execute(WorkflowNode.ActionNode("blocking", action("blocking")))
            }
            withTimeout(5_000L) { started.receive() }
            running.cancelAndJoin()
            withTimeout(5_000L) { cancelled.receive() }
            assertTrue(running.isCancelled)
        }
    }

    private fun action(id: String): Action = Action(
        type = ActionType.SYSTEM_WAIT,
        config = mapOf("id" to id, "seconds" to "0")
    )
}
