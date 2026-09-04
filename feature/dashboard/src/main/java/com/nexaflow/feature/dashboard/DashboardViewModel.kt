package com.nexaflow.feature.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.ExecutionResultPresentation
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    historyRepository: HistoryRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    /** automationId -> most recent durable execution, including its outcome. */
    private val lastRunFlow = historyRepository.getExecutionHistory()
        .map { history ->
            history.groupBy { it.automationId }
                .mapValues { (_, records) -> records.maxByOrNull { it.executedAt } }
        }

    private val automationsFlow = combine(
        automationRepository.getAutomations(),
        lastRunFlow
    ) { automations, lastRuns ->
        automations.map { automation ->
            val lastRun = lastRuns[automation.id]
            AutomationRow(
                automation = automation,
                lastRunAt = lastRun?.executedAt,
                lastRunSucceeded = lastRun?.success
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val automations: StateFlow<List<AutomationRow>> = automationsFlow

    private val _runningIds = MutableStateFlow<Set<String>>(emptySet())
    val runningIds: StateFlow<Set<String>> = _runningIds

    private val _executionMessage = MutableStateFlow<String?>(null)
    val executionMessage: StateFlow<String?> = _executionMessage

    /** Toggles a single routine on/off straight from the home screen. */
    fun toggleAutomation(automation: Automation, enabled: Boolean) {
        viewModelScope.launch {
            automationRepository.updateAutomationStatus(automation.id, enabled)
            // Notify the monitors so an enabled task whose condition already
            // holds runs immediately, and a disabled active task runs its end
            // behavior right away instead of waiting for the next event.
            executionEngine.notifyAutomationsChanged()
        }
    }

    /** Deletes one routine after the dashboard confirmation dialog is accepted. */
    fun deleteAutomation(automation: Automation) {
        viewModelScope.launch {
            try {
                automationRepository.deleteAutomation(automation)
                // The row is gone: no monitor can ever resolve this id again, so
                // the engine ledger is unreachable. Cleanup is therefore
                // best-effort — a storage failure must not turn a successful
                // delete into a failure report.
                try {
                    executionEngine.onAutomationDeleted(automation.id)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // Best-effort; the durable marker is inert once the row is gone.
                }
                _executionMessage.value = appContext.getString(R.string.task_deleted, automation.name)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Room rolls the delete back on failure, so the task still exists
                // and its engine state must stay intact. Surface the failure
                // instead of crashing the app.
                _executionMessage.value = appContext.getString(R.string.task_delete_failed, automation.name)
            }
        }
    }

    /** Runs a routine immediately from the dashboard action menu. */
    fun runNow(automation: Automation) {
        if (automation.id in _runningIds.value) return
        viewModelScope.launch {
            _runningIds.value = _runningIds.value + automation.id
            val record = executionEngine.runWithConditionGate(automation)
            _executionMessage.value = formatExecutionMessage(record)
            _runningIds.value = _runningIds.value - automation.id
        }
    }

    private fun formatExecutionMessage(record: ExecutionRecord): String =
        ExecutionResultPresentation.summary(appContext, record)

    fun consumeExecutionMessage() {
        _executionMessage.value = null
    }
}

/** A routine paired with its latest durable outcome (null = never ran). */
data class AutomationRow(
    val automation: Automation,
    val lastRunAt: Long?,
    /** Null when no run exists; false means the action chain or configuration failed. */
    val lastRunSucceeded: Boolean? = null
)
