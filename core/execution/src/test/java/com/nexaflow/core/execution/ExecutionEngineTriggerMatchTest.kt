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
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.HistoryRepository
import java.util.concurrent.atomic.AtomicInteger
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
 * Contract tests for the ALL trigger-match gate: with `triggerMatch = ALL`
 * the firing monitor only starts the evaluation — every configured trigger
 * must be verifiably true right now or the run is an intentional skip
 * (recorded, no actions executed). ANY keeps the historical semantics.
 */
@RunWith(RobolectricTestRunner::class)
class ExecutionEngineTriggerMatchTest {

    private lateinit var context: Context

    private class RecordingHandler : ActionHandler {
        private val invocationCount = AtomicInteger()
        val calls: Int get() = invocationCount.get()
        override val supportedTypes: Set<ActionType> = setOf(ActionType.SYSTEM_SEND_NOTIFICATION)
        override suspend fun execute(
            action: Action,
            ctx: ActionExecutionContext
        ): SystemControlResult {
            invocationCount.incrementAndGet()
            return SystemControlResult.ok("ok")
        }
    }

    private class RecordingHistory : HistoryRepository {
        val messages = mutableListOf<String>()
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(emptyList())
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> = emptyPagingSource()
        override suspend fun getExecutionById(id: String): ExecutionRecord? = null
        override suspend fun recordExecution(record: ExecutionRecord) {
            messages += record.message
        }
    }

    private fun automation(triggerMatch: TriggerMatchMode): Automation = Automation(
        id = "auto-match",
        name = "Charging at night",
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = listOf(
            // Charger trigger (the firing monitor) + a TIME range 22:00–07:00.
            Trigger(TriggerType.CHARGER, mapOf("event" to "CONNECTED")),
            Trigger(TriggerType.TIME, mapOf("timeMode" to "RANGE", "rangeStart" to "22:00", "rangeEnd" to "07:00"))
        ),
        triggerMatch = triggerMatch,
        actions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "hi"))),
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
        runBlocking { ActiveExecutionStore(context).clear("auto-match") }
    }

    @Test
    fun allModeSkipsWhenTheSecondTriggerIsNotSatisfied() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        // Robolectric default time is outside 22:00–07:00 for the range trigger,
        // and the charger is not connected in the test environment, so at least
        // one trigger is verifiably unsatisfied.
        val record = engine(handler, history).runAutomation(automation(TriggerMatchMode.ALL))
        assertEquals(0, handler.calls)
        assertTrue(record.message.contains("Skipped"))
        assertTrue(history.messages.any { it.contains("not all trigger conditions") })
    }

    @Test
    fun anyModeIgnoresUnsatisfiedSiblingTriggers() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val record = engine(handler, history).runAutomation(automation(TriggerMatchMode.ANY))
        // Historical OR semantics: the monitor's own firing is enough.
        assertEquals(1, handler.calls)
        assertTrue(!record.message.contains("not all trigger conditions"))
    }

    @Test
    fun manualAnyHonorsSelectedMatchMode() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val current = if (
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        ) "ON" else "OFF"
        val opposite = if (current == "ON") "OFF" else "ON"
        val task = automation(TriggerMatchMode.ANY).copy(
            id = "manual-any",
            triggers = listOf(
                Trigger(TriggerType.DARK_MODE, mapOf("state" to current)),
                Trigger(TriggerType.DARK_MODE, mapOf("state" to opposite))
            )
        )
        ActiveExecutionStore(context).clear(task.id)

        val record = engine(handler, history).runWithConditionGate(task)

        assertEquals(1, handler.calls)
        assertTrue(!record.message.startsWith(ExecutionEngine.MANUAL_CONDITION_NOT_MET_PREFIX))
    }

    @Test
    fun manualAllRejectsTheSameMixedState() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val current = if (
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        ) "ON" else "OFF"
        val opposite = if (current == "ON") "OFF" else "ON"
        val task = automation(TriggerMatchMode.ALL).copy(
            id = "manual-all",
            triggers = listOf(
                Trigger(TriggerType.DARK_MODE, mapOf("state" to current)),
                Trigger(TriggerType.DARK_MODE, mapOf("state" to opposite))
            )
        )
        ActiveExecutionStore(context).clear(task.id)

        val record = engine(handler, history).runWithConditionGate(task)

        assertEquals(0, handler.calls)
        assertTrue(record.message.startsWith("Skipped:"))
    }

    @Test
    fun liveDiagnosticsExposeEveryTriggerResultInSavedOrder() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val current = if (
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        ) "ON" else "OFF"
        val opposite = if (current == "ON") "OFF" else "ON"
        val task = automation(TriggerMatchMode.ALL).copy(
            id = "diagnostic-all",
            triggers = listOf(
                Trigger(TriggerType.DARK_MODE, mapOf("state" to current)),
                Trigger(TriggerType.DARK_MODE, mapOf("state" to opposite))
            )
        )
        ActiveExecutionStore(context).clear(task.id)

        val diagnostics = engine(handler, history).diagnoseManualAdmission(task)

        assertEquals(
            listOf(
                com.nexaflow.domain.models.ConditionResult.Satisfied,
                com.nexaflow.domain.models.ConditionResult.Unsatisfied
            ),
            diagnostics.triggerResults
        )
        assertEquals(
            com.nexaflow.domain.models.ConditionResult.Unsatisfied,
            diagnostics.triggerGateResult
        )
        assertTrue(!diagnostics.admissible)
    }

    @Test
    fun forceRunBypassesAllTriggerGate() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val current = if (
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        ) "ON" else "OFF"
        val opposite = if (current == "ON") "OFF" else "ON"
        val task = automation(TriggerMatchMode.ALL).copy(
            id = "force-all",
            triggers = listOf(
                Trigger(TriggerType.DARK_MODE, mapOf("state" to current)),
                Trigger(TriggerType.DARK_MODE, mapOf("state" to opposite))
            )
        )
        ActiveExecutionStore(context).clear(task.id)

        engine(handler, history).forceRun(task)

        assertEquals(1, handler.calls)
        assertTrue(history.messages.any { it.startsWith(ExecutionEngine.MANUAL_FORCE_PREFIX) })
    }

    @Test
    fun allModeGatesOnLiveEvaluationOfEveryCondition() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        // ALL mode evaluates every condition live, including a single one:
        // the firing monitor only starts the evaluation, it is not proof that
        // its condition still holds (a past event is not current truth).
        val record = engine(handler, history).runAutomation(
            automation(TriggerMatchMode.ALL).copy(
                triggers = listOf(Trigger(TriggerType.AIRPLANE_MODE, mapOf("state" to "ON")))
            )
        )
        assertEquals(0, handler.calls)
        assertTrue(record.message.contains("Skipped"))
        assertTrue(history.messages.any { it.contains("not all trigger conditions") })
    }

    @Test
    fun allModeSingleSatisfiedConditionRuns() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val record = engine(handler, history).runAutomation(
            automation(TriggerMatchMode.ALL).copy(
                triggers = listOf(Trigger(TriggerType.AIRPLANE_MODE, mapOf("state" to "OFF")))
            )
        )
        // Airplane mode is OFF in the test environment, so the live evaluation
        // passes and the action must run — identical to ANY for one condition.
        assertEquals(1, handler.calls)
        assertTrue(!record.message.contains("not all trigger conditions"))
    }

    @Test
    fun allModeRunsWhenStickyChargerAndTimeRangeAreBothActive() = runBlocking {
        val handler = RecordingHandler()
        val history = RecordingHistory()

        @Suppress("DEPRECATION")
        context.sendStickyBroadcast(
            android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED)
                .putExtra(android.os.BatteryManager.EXTRA_LEVEL, 50)
                .putExtra(
                    android.os.BatteryManager.EXTRA_STATUS,
                    android.os.BatteryManager.BATTERY_STATUS_CHARGING
                )
                .putExtra(
                    android.os.BatteryManager.EXTRA_PLUGGED,
                    android.os.BatteryManager.BATTERY_PLUGGED_USB
                )
        )

        val now = java.time.LocalTime.now()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val task = automation(TriggerMatchMode.ALL).copy(
            id = "charger-active-time-all",
            triggers = listOf(
                Trigger(TriggerType.CHARGER, mapOf("event" to "CONNECTED")),
                Trigger(
                    TriggerType.TIME,
                    mapOf(
                        "timeMode" to "RANGE",
                        "rangeStart" to now.minusHours(1).format(formatter),
                        "rangeEnd" to now.plusHours(1).format(formatter)
                    )
                )
            )
        )
        ActiveExecutionStore(context).clear(task.id)

        val record = engine(handler, history).runAutomation(task)

        assertEquals(1, handler.calls)
        assertTrue(!record.message.contains("not all trigger conditions"))
    }

}
