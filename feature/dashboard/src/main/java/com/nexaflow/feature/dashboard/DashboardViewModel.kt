package com.nexaflow.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.usecases.GetAutomationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    getAutomations: GetAutomationsUseCase
) : ViewModel() {

    private val automationsFlow = getAutomations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val automations: StateFlow<List<Automation>> = automationsFlow

    val activeCount: StateFlow<Int> = automationsFlow
        .map { list -> list.count { it.enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val scheduledCount: StateFlow<Int> = automationsFlow
        .map { list -> list.count { a -> a.triggers.any { it.type == TriggerType.TIME } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCount: StateFlow<Int> = automationsFlow
        .map { list -> list.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
