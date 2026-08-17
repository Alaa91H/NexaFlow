package com.nexaflow.core.execution

import android.content.Context
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

    private fun automation(
        constraints: List<Constraint>,
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
        triggers = emptyList(),
        actions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "hi"))),
        constraints = constraints,
        exitActions = exitActions,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun engine(
        handler: RecordingHandler,
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
