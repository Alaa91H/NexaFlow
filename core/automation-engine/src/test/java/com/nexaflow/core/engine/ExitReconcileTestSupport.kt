package com.nexaflow.core.engine

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Shared scaffolding for the exit-reconcile-after-restart tests. Each monitor
 * test mirrors the [DeviceEventMonitorExitReconcileTest] contract: a task that
 * was triggered before a process/service restart must still fire its exit
 * behavior when the condition ends, and if the condition already ended while
 * the process was down, the missed exit fires on the first reconcile after
 * start (broadcasts only fire on *changes*, so it would otherwise wait until
 * the state flips again, possibly never).
 */

/** Marker message recorded by [ExecutionEngine.runExit] when the task has no
 *  exit actions and no revert-on-exit — the reliable way to tell an exit run
 *  apart from a normal trigger run in [RecordingHistory]. */
internal const val EXIT_NOOP_MARKER = "No exit behavior configured"

/** Always-empty paging source for [RecordingHistory] (Paging 3.4+). */
internal fun emptyPagingSource(): PagingSource<Int, ExecutionRecord> =
    object : PagingSource<Int, ExecutionRecord>() {
        override fun getRefreshKey(state: PagingState<Int, ExecutionRecord>): Int? = null
        override suspend fun load(params: PagingSource.LoadParams<Int>): PagingSource.LoadResult<Int, ExecutionRecord> =
            PagingSource.LoadResult.Page(emptyList(), null, null)
    }

/** Records every execution message; exit runs leave the [EXIT_NOOP_MARKER]. */
internal class RecordingHistory : HistoryRepository {
    val exits = mutableListOf<String>()
    override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(emptyList())
    override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> = emptyPagingSource()
    override suspend fun getExecutionById(id: String): ExecutionRecord? = null
    override suspend fun recordExecution(record: ExecutionRecord) {
        exits += record.message
    }
}

internal class FakeRepository(
    automations: List<Automation>
) : AutomationRepository {
    private val state = MutableStateFlow(automations)
    override fun getAutomations(): Flow<List<Automation>> = state
    override suspend fun getAutomationById(id: String): Automation? = state.value.firstOrNull { it.id == id }
    override suspend fun saveAutomation(automation: Automation) = state.emit(state.value.map { if (it.id == automation.id) automation else it })
    override suspend fun deleteAutomation(automation: Automation) = state.emit(state.value.filterNot { it.id == automation.id })
    override suspend fun updateAutomationStatus(id: String, enabled: Boolean) =
        state.emit(state.value.map { if (it.id == id) it.copy(enabled = enabled) else it })
}

internal fun testAutomation(id: String, triggers: List<Trigger>): Automation = Automation(
    id = id,
    name = id,
    description = "",
    icon = "bolt",
    iconColor = 0xFF0000,
    backgroundColor = 0xFFEEEE,
    category = "general",
    priority = 1,
    enabled = true,
    triggers = triggers,
    actions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "hi"))),
    exitActions = emptyList(),
    createdAt = 0L,
    updatedAt = 0L,
)

internal fun testEngine(context: Context, history: HistoryRepository): ExecutionEngine = ExecutionEngine(
    context = context,
    historyRepository = history,
    notificationPreferences = NotificationPreferences(context),
    actionRegistry = ActionRegistry.from(emptyList())
)

/**
 * DataStore reads/writes run on real IO threads, so the monitor's re-arm +
 * reconcile completes asynchronously. Poll with a real-time deadline instead
 * of relying on a virtual scheduler.
 */
internal suspend fun waitUntil(timeoutMs: Long = 5_000L, condition: suspend () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        kotlinx.coroutines.delay(20)
    }
    throw AssertionError("condition not met within ${timeoutMs}ms")
}
