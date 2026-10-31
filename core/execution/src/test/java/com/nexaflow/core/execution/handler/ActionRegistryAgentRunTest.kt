package com.nexaflow.core.execution.handler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.NotificationSettings
import com.nexaflow.core.execution.capability.semantic.CapabilityEvidenceStore
import com.nexaflow.core.execution.capability.semantic.CapabilityRouter
import com.nexaflow.core.execution.capability.semantic.DeviceFingerprint
import com.nexaflow.core.execution.capability.semantic.OperationRegistry
import com.nexaflow.core.execution.capability.semantic.SemanticActionRouter
import com.nexaflow.core.execution.capability.semantic.StrategyHealthTracker
import com.nexaflow.core.rom.RomCapabilityProvider
import com.nexaflow.core.rom.SystemController
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Both registry factories must resolve the new AGENT_RUN action, and the
 * semantic factory must forward the supplied gateway rather than silently
 * dropping it — a dropped gateway would turn every agent step into
 * `agent_runtime_unavailable` in production.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActionRegistryAgentRunTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun executionContext() = ActionExecutionContext(
        appContext = context,
        controller = SystemController(
            context = context,
            capabilityProvider = RomCapabilityProvider(context, IntegrationLevel.NORMAL, RomFamily.AOSP)
        ),
        notificationSettings = NotificationSettings(),
        executionId = "exec-1",
        nodeId = "action:0"
    )

    private fun action() = Action(
        type = ActionType.AGENT_RUN,
        config = mapOf("agentId" to "agent-a", "prompt" to "Summarise")
    )

    private fun semanticRouter(): SemanticActionRouter = SemanticActionRouter(
        CapabilityRouter(
            registry = OperationRegistry.default(),
            strategies = emptyList(),
            evidenceStore = CapabilityEvidenceStore(),
            healthTracker = StrategyHealthTracker(),
            fingerprint = DeviceFingerprint(
                manufacturer = "test",
                model = "test",
                device = "test",
                androidApi = 35,
                securityPatch = "2026-10-01",
                romFamily = RomFamily.AOSP
            )
        )
    )

    @Test
    fun defaultRegistryResolvesTheAgentHandlerAndFailsClosedWithoutARuntime() = runBlocking {
        val handler = ActionRegistry.default().handlerFor(ActionType.AGENT_RUN)

        assertTrue(handler is AgentActionsHandler)
        val result = handler!!.execute(action(), executionContext())
        assertFalse(result.success)
        assertEquals("agent_runtime_unavailable", result.errorCode)
    }

    @Test
    fun semanticRegistryForwardsTheProvidedGateway() = runBlocking {
        val registry = ActionRegistry.withSemanticRouter(
            semanticRouter(),
            AgentRunGateway { AgentRunOutcome.Completed("done") }
        )

        val result = registry.handlerFor(ActionType.AGENT_RUN)!!.execute(action(), executionContext())
        assertTrue(result.success)
        assertEquals("Agent run completed: done", result.message)
    }

    @Test
    fun semanticRegistryWithoutAGatewayStaysFailClosed() = runBlocking {
        val registry = ActionRegistry.withSemanticRouter(semanticRouter())

        val result = registry.handlerFor(ActionType.AGENT_RUN)!!.execute(action(), executionContext())
        assertFalse(result.success)
        assertEquals("agent_runtime_unavailable", result.errorCode)
    }
}
