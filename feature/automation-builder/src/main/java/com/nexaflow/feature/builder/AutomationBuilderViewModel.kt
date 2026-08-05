package com.nexaflow.feature.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AutomationBuilderViewModel @Inject constructor(
    private val repository: AutomationRepository
) : ViewModel() {

    /** Draft id reused across quick-saves so they update instead of duplicating. */
    private var draftId: String? = null

    /** The automation being edited, if the builder was opened in edit mode. */
    private var existing: Automation? = null

    private val _loaded = MutableStateFlow<Automation?>(null)
    val loaded: StateFlow<Automation?> = _loaded

    /** Loads an existing automation so the builder can pre-fill and update it. */
    fun loadAutomation(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch {
            val automation = repository.getAutomationById(id)
            existing = automation
            draftId = automation?.id
            _loaded.value = automation
        }
    }

    fun saveAutomation(
        name: String,
        icon: String,
        triggers: List<Trigger>,
        actions: List<Action>,
        exitActions: List<Action> = emptyList(),
        revertOnExit: Boolean = false
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val prev = existing
            val id = prev?.id ?: draftId ?: UUID.randomUUID().toString().also { draftId = it }
            val automation = Automation(
                id = id,
                name = name.ifBlank { "Untitled Task" },
                description = buildDescription(triggers, actions),
                icon = icon,
                iconColor = prev?.iconColor ?: 0xFF1B62B7,
                backgroundColor = prev?.backgroundColor ?: 0xFFE3EEFA,
                category = prev?.category ?: "custom",
                priority = prev?.priority ?: 1,
                enabled = prev?.enabled ?: true,
                triggers = triggers,
                actions = actions,
                exitActions = exitActions,
                revertOnExit = revertOnExit,
                createdAt = prev?.createdAt ?: now,
                updatedAt = now
            )
            existing = automation
            repository.saveAutomation(automation)
        }
    }

    private fun buildDescription(triggers: List<Trigger>, actions: List<Action>): String {
        val triggerText = if (triggers.isEmpty()) {
            "When configured"
        } else {
            val types = triggers.joinToString(", ") { it.type.name.replace('_', ' ').lowercase() }
            "When $types"
        }
        return "$triggerText, then ${actions.size} action(s)"
    }
}
