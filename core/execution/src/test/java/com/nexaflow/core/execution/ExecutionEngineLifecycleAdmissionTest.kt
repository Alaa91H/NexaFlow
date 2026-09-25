package com.nexaflow.core.execution

import android.content.Context
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression coverage for the durable lifecycle admission boundary.
 *
 * A concurrent occurrence that loses the AutomationRuntimeStore claim must not
 * arm the legacy ActiveExecutionStore marker. Otherwise a later exit callback
 * can consume a marker for a run whose main actions never started.
 */
@RunWith(RobolectricTestRunner::class)
class ExecutionEngineLifecycleAdmissionTest {

    private class RecordingHistory : HistoryRepository {
        val records = mutableListOf<ExecutionRecord>()

        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(records)
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> =
            emptyPagingSource()
        override suspend fun getExecutionById(id: String): ExecutionRecord? =
            records.firstOrNull { it.id == id }
        override suspend fun recordExecution(record: ExecutionRecord) {
            records += record
        }
    }

    @Test
    fun rejectedConcurrentLifecycleDoesNotArmLegacyStartedMarker() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val automationId = "lifecycle-admission-race"
        val activeStore = ActiveExecutionStore(context)
        val runtimeStore = AutomationRuntimeStore(context)
        activeStore.clear(automationId)
        runtimeStore.clear(automationId)

        assertTrue(
            runtimeStore.activate(
                AutomationRuntimeState(
                    automationId = automationId,
                    occurrenceId = "existing-occurrence",
                    source = "battery",
                    sourceKey = "$automationId|connected",
                    lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                    activatedAt = 100L,
                )
            )
        )

        val history = RecordingHistory()
        val engine = ExecutionEngine(
            context = context,
            historyRepository = history,
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(emptyList()),
            activeExecutionStore = activeStore,
            automationRuntimeStore = runtimeStore,
        )
        val automation = Automation(
            id = automationId,
            name = "Concurrent lifecycle",
            description = "",
            icon = "bolt",
            iconColor = 0L,
            backgroundColor = 0L,
            category = "test",
            priority = 1,
            enabled = true,
            triggers = emptyList(),
            actions = emptyList(),
            createdAt = 0L,
            updatedAt = 0L,
        )

        val record = engine.runAutomation(
            automation = automation,
            lifecycleContext = AutomationLifecycleContext(
                occurrenceId = "losing-occurrence",
                source = "connectivity",
                sourceKey = "$automationId|wifi",
            ),
        )

        assertTrue(record.message.contains("prior automation lifecycle"))
        assertFalse(
            "Rejected occurrence must not leave an exit marker behind",
            activeStore.consumeStarted(automationId),
        )

        runtimeStore.clear(automationId)
        activeStore.clear(automationId)
    }
}
