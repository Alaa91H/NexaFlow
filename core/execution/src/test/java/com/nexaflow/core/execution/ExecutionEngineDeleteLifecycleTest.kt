package com.nexaflow.core.execution

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.execution.handler.ActionExecutionContext
import com.nexaflow.core.execution.handler.ActionHandler
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Verifies [ExecutionEngine.onAutomationDeleted] owns the whole engine-side
 * delete lifecycle through the real durable ledger: after a stateful run has
 * armed an exit, deleting the automation must drop both the in-memory and the
 * durable active marker and notify the monitors — so neither this engine nor a
 * fresh engine (process restart) can ever run a stale exit behavior for an
 * automation that no longer exists.
 *
 * Regression this pins: the dashboard delete path used to remove the row
 * without the engine-side cleanup, leaking the durable active marker; after a
 * restart a fresh engine would treat the deleted task as still active and
 * dispatch its configured end behavior.
 */
@RunWith(RobolectricTestRunner::class)
class ExecutionEngineDeleteLifecycleTest {

    private lateinit var context: Context

    private class RecordingHandler : ActionHandler {
        var calls = 0
        override val supportedTypes: Set<ActionType> = setOf(ActionType.SYSTEM_SEND_NOTIFICATION)
        override suspend fun execute(
            action: Action,
            ctx: ActionExecutionContext
        ): SystemControlResult {
            calls++
            return SystemControlResult.ok("ok")
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

    private val notificationAction = Action(
        type = ActionType.SYSTEM_SEND_NOTIFICATION,
        config = mapOf("title" to "end")
    )

    /** A stateful task with a configured end action and a unique id per test. */
    private fun automation(id: String): Automation = Automation(
        id = id,
        name = "Delete lifecycle task",
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = listOf(Trigger(TriggerType.CONNECTIVITY, mapOf("state" to "CONNECTED"))),
        actions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, emptyMap())),
        exitActions = listOf(notificationAction),
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun engine(handler: ActionHandler, history: HistoryRepository): ExecutionEngine =
        ExecutionEngine(
            context = context,
            historyRepository = history,
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(listOf(handler))
        )

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        // Unique ids per test already isolate the shared ledger, but clearing
        // first keeps the class re-runnable in one JVM (DataStore is a JVM-wide
        // singleton per file, frozen at the first sandbox it ever sees).
        listOf("delete-lifecycle-a", "delete-lifecycle-b", "delete-lifecycle-c").forEach {
            ActiveExecutionStore(context).clear(it)
        }
    }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `an active task without delete still runs its end behavior once`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(handler, history)
        val automation = automation("delete-lifecycle-a")

        engine.runAutomation(automation)
        assertEquals(1, handler.calls)

        val exit = engine.runExit(automation)

        // Baseline: the durable marker is honored exactly once.
        assertEquals("end action must run while the task exists", 2, handler.calls)
        assertTrue(exit.success)
        idleMainLooper()
    }

    @Test
    fun `delete clears the lifecycle so neither engine nor a fresh engine runs a stale exit`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(handler, history)
        val automation = automation("delete-lifecycle-b")

        engine.runAutomation(automation)
        assertEquals("main action ran once", 1, handler.calls)

        engine.onAutomationDeleted(automation.id)

        // Same engine: the in-memory marker is gone, so exit is a no-op.
        val sameEngineExit = engine.runExit(automation)
        assertEquals("no end action may run after delete on the same engine", 1, handler.calls)
        assertTrue(sameEngineExit.message.contains("task was not active"))

        // Fresh engine over the same durable store: the marker is gone there
        // too (the restart view of a deleted task must not dispatch its end
        // behavior — the regression this method pins).
        val freshEngineExit = engine(handler, history).runExit(automation)
        assertEquals("no end action may run after delete on a fresh engine", 1, handler.calls)
        assertTrue(freshEngineExit.message.contains("task was not active"))
        idleMainLooper()
    }

    @Test
    fun `delete broadcasts the change so stateful monitors prune immediately`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(handler, history)
        val automation = automation("delete-lifecycle-c")
        engine.runAutomation(automation)

        var changeBroadcasts = 0
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_AUTOMATIONS_CHANGED) changeBroadcasts++
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_AUTOMATIONS_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        try {
            engine.onAutomationDeleted(automation.id)
            idleMainLooper()

            assertEquals(
                "delete must notify monitors so they prune markers",
                1,
                changeBroadcasts
            )
        } finally {
            context.unregisterReceiver(receiver)
        }

        // Deleting again is a safe no-op that still re-notifies. Robolectric
        // only delivers the first broadcast to a receiver registered on the
        // application context, so a fresh receiver verifies the second delete's
        // own notification.
        var secondDeleteBroadcasts = 0
        val secondReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_AUTOMATIONS_CHANGED) secondDeleteBroadcasts++
            }
        }
        ContextCompat.registerReceiver(
            context,
            secondReceiver,
            IntentFilter(ACTION_AUTOMATIONS_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        try {
            engine.onAutomationDeleted(automation.id)
            idleMainLooper()
            assertEquals("a repeated delete must notify monitors again", 1, secondDeleteBroadcasts)
        } finally {
            context.unregisterReceiver(secondReceiver)
        }
    }
}
