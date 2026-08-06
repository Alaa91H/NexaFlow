package com.nexaflow.feature.widgets

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Exposes the task list so each quick-settings tile can be bound to a task. */
@HiltViewModel
class WidgetsViewModel @Inject constructor(
    private val repository: AutomationRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val automations: StateFlow<List<Automation>> = repository.getAutomations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Returns the task pinned to [slot], or null for automatic binding. */
    fun bindingFor(slot: Int): String? = TileBindingStore.bindingFor(context, slot)

    /** Pins [automationId] to [slot]; pass null to restore automatic binding. */
    fun setBinding(slot: Int, automationId: String?) {
        TileBindingStore.setBinding(context, slot, automationId)
    }
}
