package com.nexaflow.feature.automations

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.ExecutionResultPresentation
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.AutomationHealthReport
import com.nexaflow.domain.models.AutomationHealthStatus
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutomationDetailsViewModel @Inject constructor(
    private val repository: AutomationRepository,
    private val healthRepository: HealthRepository,
    private val executionEngine: ExecutionEngine,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val automationId: String = savedStateHandle["automationId"] ?: ""

    val automation: StateFlow<Automation?> = repository.getAutomations()
        .map { list -> list.find { it.id == automationId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Read-only, locally-derived execution health for the routine being viewed. */
    val healthReport: StateFlow<AutomationHealthReport> = healthRepository.getHealthReports()
        .map { reports -> reports.find { it.automationId == automationId } ?: emptyHealthReport(automationId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyHealthReport(automationId))

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _executionMessage = MutableStateFlow<String?>(null)
    val executionMessage: StateFlow<String?> = _executionMessage

    fun toggleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAutomationStatus(automationId, enabled)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.getAutomationById(automationId)?.let { repository.deleteAutomation(it) }
            // Drop any captured device state so a deleted task never restores it.
            executionEngine.clearSnapshot(automationId)
            onDeleted()
        }
    }

    fun runNow() {
        val current = automation.value ?: return
        if (_running.value) return
        viewModelScope.launch {
            _running.value = true
            val record = executionEngine.runWithConditionGate(current)
            _executionMessage.value = formatExecutionMessage(record)
            _running.value = false
        }
    }

    private fun formatExecutionMessage(record: ExecutionRecord): String =
        ExecutionResultPresentation.summary(appContext, record)

    fun consumeExecutionMessage() {
        _executionMessage.value = null
    }
}

private fun emptyHealthReport(automationId: String) = AutomationHealthReport(
    automationId = automationId,
    lastExecutionAt = null,
    completedRuns = 0,
    skippedRuns = 0,
    failedRuns = 0,
    consecutiveFailures = 0,
    latestFailureMessage = null,
    status = AutomationHealthStatus.NO_EXECUTIONS
)
