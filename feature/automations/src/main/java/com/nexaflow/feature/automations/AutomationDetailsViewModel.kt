package com.nexaflow.feature.automations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutomationDetailsViewModel @Inject constructor(
    private val repository: AutomationRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val automationId: String = savedStateHandle["automationId"] ?: ""

    val automation: StateFlow<Automation?> = repository.getAutomations()
        .map { list -> list.find { it.id == automationId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun toggleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAutomationStatus(automationId, enabled)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.getAutomationById(automationId)?.let { repository.deleteAutomation(it) }
            onDeleted()
        }
    }
}
