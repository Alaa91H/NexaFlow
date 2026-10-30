package com.nexaflow.core.execution.handler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.NotificationSettings
import com.nexaflow.core.execution.WorkflowRunContext
import com.nexaflow.core.rom.RomCapabilityProvider
import com.nexaflow.core.rom.SystemController
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AgentActionsHandlerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun context(runContext: WorkflowRunContext? = null) = ActionExecutionContext(
        appContext = context,
        controller = SystemController(
            context = context,
            capabilityProvider = RomCapabilityProvider(context, IntegrationLevel.NORMAL, RomFamily.AOSP)
        ),
        notificationSettings = NotificationSettings(),
        executionId = "exec-1",
        nodeId = "action:0",
        runContext = runContext,
    )

    private fun action(vararg config: Pair<String, String>) = Action(
        type = ActionType.AGENT_RUN,
        config = mapOf(*config),
    )

    @Test
    fun missingAgentIdFailsWithoutTouchingTheGateway() = runBlocking {
        val calls = AtomicInteger()
        val handler = AgentActionsHandler().apply {
            gateway = AgentRunGateway { calls.incrementAndGet(); AgentRunOutcome.Completed("ok") }
        }

        val result = handler.execute(action("prompt" to "hello"), context())

        assertFalse(result.success)
        assertEquals("agent_config_invalid", result.errorCode)
        assertEquals(0, calls.get())
    }

    @Test
    fun missingGatewayFailsClosed() = runBlocking {
        val result = AgentActionsHandler().execute(
            action("agentId" to "agent-1", "prompt" to "hello"),
            context()
        )

        assertFalse(result.success)
        assertEquals("agent_runtime_unavailable", result.errorCode)
    }

    @Test
    fun approvalPolicyDefaultsToFailAndSkipIsForwarded() = runBlocking {
        val seen = mutableListOf<AgentRunRequest>()
        val handler = AgentActionsHandler().apply {
            gateway = AgentRunGateway { request -> seen += request; AgentRunOutcome.Completed("done") }
        }

        handler.execute(action("agentId" to "agent-1", "prompt" to "one"), context())
        handler.execute(
            action("agentId" to "agent-1", "prompt" to "two", "onApprovalRequired" to "skip"),
            context()
        )

        assertEquals(AgentApprovalPolicy.FAIL, seen[0].onApprovalRequired)
        assertEquals(AgentApprovalPolicy.SKIP, seen[1].onApprovalRequired)
    }

    @Test
    fun unknownApprovalPolicyIsRejected() = runBlocking {
        val handler = AgentActionsHandler().apply {
            gateway = AgentRunGateway { AgentRunOutcome.Completed("done") }
        }

        val result = handler.execute(
            action("agentId" to "agent-1", "prompt" to "hello", "onApprovalRequired" to "WAIT"),
            context()
        )

        assertFalse(result.success)
        assertEquals("agent_policy_invalid", result.errorCode)
    }

    @Test
    fun outOfRangeDurationIsRejected() = runBlocking {
        val handler = AgentActionsHandler().apply {
            gateway = AgentRunGateway { AgentRunOutcome.Completed("done") }
        }

        val result = handler.execute(
            action("agentId" to "agent-1", "prompt" to "hello", "maxDurationSeconds" to "3601"),
            context()
        )

        assertFalse(result.success)
        assertEquals("agent_duration_invalid", result.errorCode)
    }

    @Test
    fun completedRunPublishesTheBoundedSummaryToTheResultVariable() = runBlocking {
        val longSummary = "x".repeat(2_500)
        val handler = AgentActionsHandler().apply {
            gateway = AgentRunGateway { AgentRunOutcome.Completed(longSummary) }
        }
        val runContext = WorkflowRunContext.create("automation-1", 1_000L)

        val result = handler.execute(
            action(
                "agentId" to "agent-1",
                "prompt" to "hello",
                "resultVariable" to "agentResult"
            ),
            context(runContext)
        )

        assertTrue(result.message, result.success)
        assertEquals("agent", result.executionChannel)
        val stored = runContext.get("$.agentResult.summary") as String
        assertEquals(2_000, stored.length)
    }

    @Test
    fun failedOutcomeKeepsTheSafeCodeAndRejectsUnsafeCodes() = runBlocking {
        var code = "agent_disabled"
        val handler = AgentActionsHandler().apply {
            gateway = AgentRunGateway { AgentRunOutcome.Failed(code) }
        }

        val safe = handler.execute(
            action("agentId" to "agent-1", "prompt" to "hello"),
            context()
        )
        assertEquals("agent_disabled", safe.errorCode)

        code = "UPPERCASE UNTRUSTED"
        val unsafe = handler.execute(
            action("agentId" to "agent-1", "prompt" to "hello"),
            context()
        )
        assertEquals("agent_run_failed", unsafe.errorCode)
    }

    @Test
    fun gatewayExceptionBecomesAFailedResult() = runBlocking {
        val handler = AgentActionsHandler().apply {
            gateway = AgentRunGateway { throw IllegalStateException("boom") }
        }

        val result = handler.execute(
            action("agentId" to "agent-1", "prompt" to "hello"),
            context()
        )

        assertFalse(result.success)
        assertEquals("agent_run_failed", result.errorCode)
    }

    @Test
    fun idempotencyKeyBindsTheExecutionNodeAndAgent() = runBlocking {
        var seen: AgentRunRequest? = null
        val handler = AgentActionsHandler().apply {
            gateway = AgentRunGateway { seen = it; AgentRunOutcome.Completed("ok") }
        }

        handler.execute(
            action("agentId" to "agent-1", "prompt" to "hello"),
            context()
        )

        assertEquals("exec-1:action:0:agent-1", seen?.idempotencyKey)
        assertEquals(60_000L, seen?.maxDurationMillis)
    }
}
