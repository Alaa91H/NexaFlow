package com.nexaflow.core.execution

import android.content.Context
import android.content.res.Configuration
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
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintSnapshot
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.MaintenanceKind
import com.nexaflow.domain.models.MaintenanceProfile
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.HistoryRepository
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the constraint gate in [ExecutionEngine]: when a task declares
 * constraints, the run is skipped (recorded as blocked, no action executed)
 * unless EVERY constraint passes. The device state is pinned via the engine's
 * test seam so the tests are fully deterministic.
 */
@RunWith(RobolectricTestRunner::class)
class ExecutionEngineConstraintsTest {

    private lateinit var context: Context

    private class RecordingHandler : ActionHandler {
        private val invocationCount = AtomicInteger()
        val calls: Int get() = invocationCount.get()
        val actionTypes = mutableListOf<ActionType>()
        override val supportedTypes: Set<ActionType> = setOf(
            ActionType.SYSTEM_SEND_NOTIFICATION,
            ActionType.SYSTEM_CLEAR_NOTIFICATIONS
        )
        override suspend fun execute(
            action: Action,
            ctx: ActionExecutionContext
        ): SystemControlResult {
            invocationCount.incrementAndGet()
            actionTypes += action.type
            return SystemControlResult.ok("ok")
        }
    }

    private class ThrowingHandler : ActionHandler {
        override val supportedTypes: Set<ActionType> = setOf(ActionType.SYSTEM_SEND_NOTIFICATION)
        override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult =
            throw IllegalStateException("handler exploded")
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

    private fun automation(
        constraints: List<Constraint>,
        triggers: List<Trigger> = emptyList(),
        exitActions: List<Action> = emptyList()
    ): Automation = Automation(
        id = "auto-constraint",
        name = "Gated task",
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = triggers,
        actions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "hi"))),
        constraints = constraints,
        exitActions = exitActions,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun engine(
        handler: ActionHandler,
        history: RecordingHistory,
        state: ConstraintSnapshot
    ): ExecutionEngine = ExecutionEngine(
        context = context,
        historyRepository = history,
        notificationPreferences = NotificationPreferences(context),
        actionRegistry = ActionRegistry.from(listOf(handler)),
        constraintStateProvider = { state }
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking { ActiveExecutionStore(context).clear("auto-constraint") }
    }

    @Test
    fun `failing constraint blocks the run without executing actions`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(
            handler,
            history,
            ConstraintSnapshot(wifiConnected = false, batteryLevel = 90)
        )

        val record = engine.runAutomation(
            automation(
                listOf(
                    Constraint(ConstraintType.WIFI),
                    Constraint(ConstraintType.BATTERY, mapOf("direction" to "ABOVE", "level" to "30"))
                )
            )
        )

        assertEquals(0, handler.calls)
        assertTrue(record.message.contains("Skipped"))
        assertTrue("blocked run must be recorded", history.messages.any { it.contains("Skipped") })
    }

    @Test
    fun `blocked run does not execute configured exit behavior`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(
            handler,
            history,
            ConstraintSnapshot(wifiConnected = false)
        )
        val automation = automation(
            constraints = listOf(Constraint(ConstraintType.WIFI)),
            exitActions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "ended")))
        )

        engine.runAutomation(automation)
        val exitRecord = engine.runExit(automation)

        assertEquals("blocked main actions must not arm an exit", 0, handler.calls)
        assertTrue(exitRecord.message.contains("not active"))
        assertTrue("blocked execution remains visible in history", history.messages.any { it.contains("Skipped") })
    }

    @Test
    fun `all constraints satisfied executes the run`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(
            handler,
            history,
            ConstraintSnapshot(wifiConnected = true, batteryLevel = 60)
        )

        val record = engine.runAutomation(
            automation(
                listOf(
                    Constraint(ConstraintType.WIFI),
                    Constraint(ConstraintType.BATTERY, mapOf("direction" to "ABOVE", "level" to "30"))
                )
            )
        )

        assertEquals(1, handler.calls)
        assertTrue(record.message.contains("ok"))
    }

    @Test
    fun `completed maintenance occurrence executes side effect only once per day`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(handler, history, ConstraintSnapshot())
        val maintenance = automation(emptyList()).copy(
            maintenanceProfile = MaintenanceProfile(kind = MaintenanceKind.DAILY)
        )

        engine.runAutomation(maintenance)
        val duplicate = engine.runAutomation(maintenance)

        assertEquals(1, handler.calls)
        assertTrue(duplicate.message.startsWith("Skipped"))
    }

    @Test
    fun `manual run requires every verifiable trigger and executes only end behavior when an event is absent`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(handler, history, ConstraintSnapshot())
        val darkModeState = if (
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        ) "ON" else "OFF"
        val automation = automation(
            constraints = emptyList(),
            triggers = listOf(
                Trigger(TriggerType.DARK_MODE, mapOf("state" to darkModeState)),
                Trigger(TriggerType.NFC_TAG_SCANNED, emptyMap())
            ),
            exitActions = listOf(Action(ActionType.SYSTEM_CLEAR_NOTIFICATIONS, emptyMap()))
        )

        val record = engine.runWithConditionGate(automation)

        assertEquals(
            "an event trigger without a live event must never authorize the main action",
            listOf(ActionType.SYSTEM_CLEAR_NOTIFICATIONS),
            handler.actionTypes
        )
        assertTrue(record.message.startsWith(ExecutionEngine.MANUAL_CONDITION_NOT_MET_PREFIX))
        assertTrue(history.messages.any { it.startsWith(ExecutionEngine.MANUAL_CONDITION_NOT_MET_PREFIX) })
    }

    @Test
    fun `manual run with no triggers still executes the main action`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(handler, history, ConstraintSnapshot())

        val record = engine.runWithConditionGate(automation(constraints = emptyList()))

        assertEquals(listOf(ActionType.SYSTEM_SEND_NOTIFICATION), handler.actionTypes)
        assertTrue(!record.message.startsWith(ExecutionEngine.MANUAL_CONDITION_NOT_MET_PREFIX))
    }

    @Test
    fun `concurrent exits execute configured end action exactly once`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val automation = automation(
            constraints = emptyList(),
            exitActions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "ended"))),
        )
        val engine = engine(handler, history, ConstraintSnapshot())
        val pressureScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            engine.runAutomation(automation)
            val exits = List(128) { pressureScope.async { engine.runExit(automation) } }.awaitAll()

            assertEquals("one main action plus one end action must execute", 2, handler.calls)
            assertEquals(
                "only one concurrent callback may consume the active lifecycle",
                1,
                exits.count { it.actionResults.size == 1 },
            )
            assertEquals(
                "all remaining callbacks must be recorded as inactive exits",
                127,
                exits.count { it.message.contains("not active") },
            )
        } finally {
            pressureScope.cancel()
        }
    }

    @Test
    fun `handler exception is recorded and leaves lifecycle armed for exit`() = runBlocking {
        val history = RecordingHistory()
        val automation = automation(emptyList())
        val engine = engine(ThrowingHandler(), history, ConstraintSnapshot())

        val failedRun = engine.runAutomation(automation)
        val exitRecord = engine.runExit(automation)

        assertTrue("handler failure must be represented in the execution record", !failedRun.success)
        assertTrue(failedRun.message.contains("handler exploded"))
        assertTrue("a started task still owns one exit lifecycle", exitRecord.message.contains("No exit behavior"))
        assertTrue("failed run must remain visible in history", history.messages.any { it.contains("handler exploded") })
    }

    @Test
    fun `no constraints means no gate`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(handler, history, ConstraintSnapshot(wifiConnected = false))

        engine.runAutomation(automation(emptyList()))

        assertEquals(1, handler.calls)
    }

    @Test
    fun `unreadable state fails closed`() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val engine = engine(handler, history, ConstraintSnapshot(batteryLevel = -1))

        val record = engine.runAutomation(
            automation(listOf(Constraint(ConstraintType.BATTERY, mapOf("direction" to "BELOW", "level" to "20"))))
        )

        assertEquals(0, handler.calls)
        assertTrue(record.message.contains("Skipped"))
    }
}
