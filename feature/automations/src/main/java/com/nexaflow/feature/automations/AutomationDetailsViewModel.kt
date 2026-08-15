package com.nexaflow.feature.automations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val executionEngine: ExecutionEngine,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val automationId: String = savedStateHandle["automationId"] ?: ""

    val automation: StateFlow<Automation?> = repository.getAutomations()
        .map { list -> list.find { it.id == automationId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
            _executionMessage.value =
                if (record.success) "Ran: ${record.message}" else "Failed: ${record.message}"
            _running.value = false
        }
    }

    fun consumeExecutionMessage() {
        _executionMessage.value = null
    }
}
