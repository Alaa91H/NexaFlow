package com.nexaflow.core.execution

import android.content.Context
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationRunListenerTest {

    private lateinit var context: Context

    private class RecordingHistory : HistoryRepository {
        val records = mutableListOf<ExecutionRecord>()
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(records)
        override fun getExecutionPaging() = emptyPagingSource<Int, ExecutionRecord>()
        override suspend fun getExecutionById(id: String): ExecutionRecord? =
            records.firstOrNull { it.id == id }
        override suspend fun recordExecution(record: ExecutionRecord) {
            records += record
        }
    }

    private class RecordingHandler : ActionHandler {
        var calls = 0
        override val supportedTypes = setOf(ActionType.SYSTEM_SEND_NOTIFICATION)
        override suspend fun execute(
            action: Action,
            ctx: ActionExecutionContext
        ): SystemControlResult {
            calls += 1
            return SystemControlResult.ok("ok")
        }
    }

    private class RecordingListener(
        private val throwOnRecord: Boolean = false
    ) : AutomationRunListener {
        val triggered = mutableListOf<Pair<String, String>>()
        val records = mutableListOf<ExecutionRecord>()

        override suspend fun onTriggered(automationId: String, runId: String) {
            triggered += automationId to runId
        }

        override suspend fun onRecord(record: ExecutionRecord) {
            if (throwOnRecord) throw IllegalStateException("telemetry failure")
            records += record
        }
    }

    private fun automation(match: TriggerMatchMode) = Automation(
        id = "run-listener",
        name = "Listener task",
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = listOf(
            // Charger trigger (firing monitor) + TIME range 22:00–07:00:
            // the charger is not connected in the test environment and the
            // default clock sits outside the range, so ALL mode provably skips.
            Trigger(TriggerType.CHARGER, mapOf("event" to "CONNECTED")),
            Trigger(TriggerType.TIME, mapOf("timeMode" to "RANGE", "rangeStart" to "22:00", "rangeEnd" to "07:00"))
        ),
        triggerMatch = match,
        actions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "hi"))),
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun engine(
        history: RecordingHistory,
        listener: AutomationRunListener
    ): ExecutionEngine = ExecutionEngine(
        context = context,
        historyRepository = history,
        notificationPreferences = NotificationPreferences(context),
        actionRegistry = ActionRegistry.from(listOf(RecordingHandler())),
        runListener = listener
    )

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        ActiveExecutionStore(context).clear("run-listener")
    }

    @Test
    fun admittedRunFansOutTriggeredAndCompleted() = runBlocking {
        val history = RecordingHistory()
        val listener = RecordingListener()
        val record = engine(history, listener).runAutomation(
            automation(TriggerMatchMode.ANY),
            bypassTriggerMatch = true
        )

        assertTrue(record.success)
        assertEquals("run-listener", listener.triggered.single().first)
        assertTrue(listener.triggered.single().second.isNotBlank())
        assertEquals(listOf(record.id), listener.records.map { it.id })
    }

    @Test
    fun skippedRunFansOutRecordWithoutTriggered() = runBlocking {
        val history = RecordingHistory()
        val listener = RecordingListener()
        // ALL mode with provably unsatisfied triggers: the run is skipped
        // before admission, so no triggered event fires, but the durable
        // skip record still fans out.
        val record = engine(history, listener).runAutomation(automation(TriggerMatchMode.ALL))

        assertTrue(listener.triggered.isEmpty())
        assertEquals(listOf(record.id), listener.records.map { it.id })
    }

    @Test
    fun listenerFailureNeverBreaksExecution() = runBlocking {
        val history = RecordingHistory()
        val listener = RecordingListener(throwOnRecord = true)
        val record = engine(history, listener).runAutomation(
            automation(TriggerMatchMode.ANY),
            bypassTriggerMatch = true
        )

        assertTrue(record.success)
        assertEquals(1, history.records.size)
    }
}
