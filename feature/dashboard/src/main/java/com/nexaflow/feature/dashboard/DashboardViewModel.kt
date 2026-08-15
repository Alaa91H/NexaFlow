package com.nexaflow.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    historyRepository: HistoryRepository
) : ViewModel() {

    /** automationId -> timestamp of the most recent execution, if any. */
    private val lastRunFlow = historyRepository.getExecutionHistory()
        .map { history ->
            history.groupBy { it.automationId }
                .mapValues { (_, records) -> records.maxOf { it.executedAt } }
        }

    private val automationsFlow = combine(
        automationRepository.getAutomations(),
        lastRunFlow
    ) { automations, lastRuns ->
        automations.map { automation ->
            AutomationRow(automation, lastRuns[automation.id])
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
        }
    }

    /** Runs a routine immediately from the home screen (Samsung-style play button). */
    fun runNow(automation: Automation) {
        if (automation.id in _runningIds.value) return
        viewModelScope.launch {
            _runningIds.value = _runningIds.value + automation.id
            val record = executionEngine.runWithConditionGate(automation)
            _executionMessage.value =
                if (record.success) "Ran: ${record.message}" else "Failed: ${record.message}"
            _runningIds.value = _runningIds.value - automation.id
        }
    }

    fun consumeExecutionMessage() {
        _executionMessage.value = null
    }
}

/** A routine paired with the timestamp of its last execution (null = never ran). */
data class AutomationRow(
    val automation: Automation,
    val lastRunAt: Long?
)
