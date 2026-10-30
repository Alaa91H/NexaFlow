package com.nexaflow.feature.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.agentruntime.AgentChoiceSource
import com.nexaflow.core.agentruntime.EnabledAgent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Supplies the saved, enabled agents to the AGENT_RUN action editor.
 *
 * Only enabled agents are offered: the runtime refuses a disabled agent, so the
 * picker must not be able to save a task that can never run.
 */
@HiltViewModel
class AgentRunChoiceViewModel @Inject constructor(
    source: AgentChoiceSource,
) : ViewModel() {

    val agents: StateFlow<List<EnabledAgent>> = source.observeEnabled()
        .map { agents -> agents.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
