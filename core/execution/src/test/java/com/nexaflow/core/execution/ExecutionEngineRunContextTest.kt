package com.nexaflow.core.execution

import android.content.Context
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.execution.handler.ActionExecutionContext
import com.nexaflow.core.execution.handler.ActionHandler
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies that [WorkflowRunContext] is threaded through
 * [ExecutionEngine.runAutomation]: a caller-provided context reaches the action
 * handler as the *same instance* (so handlers can read what earlier nodes wrote
 * and publish their own outputs), and a run without one gets a fresh context.
 */
@RunWith(RobolectricTestRunner::class)
class ExecutionEngineRunContextTest {

    private lateinit var context: Context

    private class CapturingHandler : ActionHandler {
        var received: WorkflowRunContext? = null
        override val supportedTypes: Set<ActionType> = setOf(ActionType.SYSTEM_SEND_NOTIFICATION)
        override suspend fun execute(
            action: Action,
            ctx: ActionExecutionContext
        ): SystemControlResult {
            received = ctx.runContext
            ctx.runContext?.put("\$.handler", "seen")
            return SystemControlResult.ok("ok")
        }
    }

    private class FakeHistoryRepository : HistoryRepository {
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(emptyList())
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> =
            emptyPagingSource()
        override suspend fun getExecutionById(id: String): ExecutionRecord? = null
        override suspend fun recordExecution(record: ExecutionRecord) = Unit
    }

    private fun automation(): Automation = Automation(
        id = "auto-ctx",
        name = "Context task",
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = emptyList(),
        actions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, emptyMap())),
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun engine(handler: ActionHandler): ExecutionEngine = ExecutionEngine(
        context = context,
        historyRepository = FakeHistoryRepository(),
        notificationPreferences = NotificationPreferences(context),
        actionRegistry = ActionRegistry.from(listOf(handler))
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun runAutomation_passesTheCallerContextToHandlersAsTheSameInstance() = runBlocking {
        val handler = CapturingHandler()
        val engine = engine(handler)
        val provided = WorkflowRunContext.create("auto-ctx", triggeredAt = 42L)

        engine.runAutomation(automation(), runContext = provided)

        // The handler received the very same instance — not a copy.
        assertSame(provided, handler.received)
        // The handler's write landed on the caller's context: the payload
        // delta is readable after the run completes.
        assertEquals("seen", provided.get("\$.handler"))
        assertTrue(provided.paths().contains("\$.handler"))
    }

    @Test
    fun runAutomation_withoutContext_createsAFreshOnePerRun() = runBlocking {
        val handler = CapturingHandler()
        val engine = engine(handler)

        engine.runAutomation(automation())
        val first = handler.received

        assertNotNull(first)
        assertEquals("auto-ctx", first!!.automationId)
        assertTrue(first.runId.isNotBlank())
        assertEquals("seen", first.get("\$.handler"))

        // A second run creates a new context (distinct run id).
        engine.runAutomation(automation())
        val second = handler.received
        assertNotNull(second)
        assertTrue(second!!.runId.isNotBlank())
        assertTrue(first.runId != second.runId)
    }
}
