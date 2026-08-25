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
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies [ExecutionEngine.runExit]: the task's end behavior actually executes.
 *
 * The regression this pins: runExit used to early-return "No exit behavior
 * configured" whenever `exitActions` was empty — silently ignoring per-action
 * end behaviors (leave / restore / set value), which is the unified builder
 * model. A task configured only with per-action end options never ran them.
 */
@RunWith(RobolectricTestRunner::class)
class ExecutionEngineExitBehaviorTest {

    private lateinit var context: Context

    private class RecordingHandler(
        private val failFirstCall: Boolean = false
    ) : ActionHandler {
        var calls = 0
        override val supportedTypes: Set<ActionType> = setOf(ActionType.SYSTEM_SEND_NOTIFICATION)
        override suspend fun execute(
            action: Action,
            ctx: ActionExecutionContext
        ): SystemControlResult {
            calls++
            return if (failFirstCall && calls == 1) {
                SystemControlResult.fail("main action failed")
            } else {
                SystemControlResult.ok("ok")
            }
        }
    }

    private class RecordingHistory : HistoryRepository {
        val messages = mutableListOf<String>()
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(emptyList())
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> =
            emptyPagingSource()
        override suspend fun getExecutionById(id: String): ExecutionRecord? = null
        override suspend fun recordExecution(record: ExecutionRecord) {
            messages += record.message
        }
    }

    private val action = Action(
        type = ActionType.SYSTEM_SEND_NOTIFICATION,
        config = mapOf("title" to "hi")
    )

    private fun automation(
        actions: List<Action> = listOf(action),
        exitActions: List<Action> = emptyList(),
        revertOnExit: Boolean = false,
        triggers: List<Trigger> = emptyList()
    ): Automation = Automation(
        id = "auto-exit",
        name = "Exit task",
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = triggers,
        actions = actions,
        exitActions = exitActions,
        revertOnExit = revertOnExit,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun engine(handler: RecordingHandler, history: RecordingHistory): ExecutionEngine =
        ExecutionEngine(
            context = context,
            historyRepository = history,
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(listOf(handler))
        )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `per-action end behavior runs on exit when no exit actions are configured`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(handler, history)
        val automation = automation(
            actions = listOf(
                action.copy(endBehavior = EndBehavior(EndMode.SET_VALUE, mapOf("enabled" to "true")))
            )
        )

        engine.runAutomation(automation)
        assertEquals(1, handler.calls)

        val record = engine.runExit(automation)

        // Regression: this used to early-return and never run the end behavior.
        assertEquals("end behavior must execute on exit", 2, handler.calls)
        assertTrue("run was recorded", history.messages.isNotEmpty())
        assertTrue(record.success)
    }

    @Test
    fun `per-action revert end behavior runs on exit`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(handler, history)
        val automation = automation(
            actions = listOf(action.copy(endBehavior = EndBehavior(EndMode.REVERT)))
        )

        engine.runAutomation(automation)
        val record = engine.runExit(automation)

        assertTrue("revert end must not early-return", record.success)
        assertTrue(record.message.contains("Nothing to restore"))
    }

    @Test
    fun `exit actions run when the task ends`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(handler, history)
        val automation = automation(exitActions = listOf(action))

        engine.runAutomation(automation)
        assertEquals(1, handler.calls)

        engine.runExit(automation)

        assertEquals(2, handler.calls)
    }

    @Test
    fun `one-shot completion runs configured exit actions immediately`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(handler, history)
        val automation = automation(exitActions = listOf(action))

        val record = engine.runAutomation(
            automation = automation,
            completeExitOnFinish = true
        )

        assertTrue(record.success)
        assertEquals("main action and end action must both run", 2, handler.calls)
        assertEquals("main and exit records must both be durable", 2, history.messages.size)

        // The automatic exit consumes the lifecycle, so a later duplicate end
        // signal cannot repeat the configured action.
        engine.runExit(automation)
        assertEquals("end action must run exactly once", 2, handler.calls)
    }

    @Test
    fun `one-shot completion still runs end behavior after main action failure`() = runBlocking {
        val handler = RecordingHandler(failFirstCall = true)
        val history = RecordingHistory()
        val engine = engine(handler, history)
        val automation = automation(
            actions = listOf(action.copy(endBehavior = EndBehavior(EndMode.SET_VALUE, mapOf("enabled" to "true"))))
        )

        val record = engine.runAutomation(
            automation = automation,
            completeExitOnFinish = true
        )

        assertFalse("main failure must remain visible in its execution record", record.success)
        assertEquals("end behavior must still run after the failed main action", 2, handler.calls)
        assertEquals("main and end records must both be durable", 2, history.messages.size)
    }

    @Test
    fun `momentary trigger automatically runs end actions after the main chain`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(handler, history)
        val automation = automation(
            triggers = listOf(Trigger(TriggerType.APP_INSTALLED, mapOf("event" to "INSTALLED"))),
            exitActions = listOf(action)
        )

        val record = engine.runAutomation(automation)

        assertTrue(record.success)
        assertEquals("main action and automatic end action must both run", 2, handler.calls)
        assertEquals("main and end records must both be durable", 2, history.messages.size)
    }

    @Test
    fun `stateful trigger does not auto run end actions before its condition ends`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(handler, history)
        val automation = automation(
            triggers = listOf(Trigger(TriggerType.CONNECTIVITY, mapOf("state" to "CONNECTED"))),
            exitActions = listOf(action)
        )

        engine.runAutomation(automation)
        assertEquals("stateful task must remain active until its opposite condition", 1, handler.calls)

        engine.runExit(automation)
        assertEquals("end action must run when the stateful task ends", 2, handler.calls)
    }

    @Test
    fun `no end behavior configured records nothing and executes nothing on exit`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(handler, history)
        val automation = automation()

        engine.runAutomation(automation)
        assertEquals(1, handler.calls)

        val record = engine.runExit(automation)

        assertEquals(1, handler.calls)
        assertTrue("expected a no-exit-behavior message", record.message.contains("No exit"))
    }
}
