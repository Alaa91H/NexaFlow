package com.nexaflow.feature.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Condition
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AutomationBuilderViewModel @Inject constructor(
    private val repository: AutomationRepository
) : ViewModel() {

    /** Draft id reused across quick-saves so they update instead of duplicating. */
    private var draftId: String? = null

    fun saveAutomation(
        name: String,
        icon: String,
        triggers: List<Trigger>,
        conditions: List<Condition>,
        actions: List<Action>
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = draftId ?: UUID.randomUUID().toString().also { draftId = it }
            val automation = Automation(
                id = id,
                name = name.ifBlank { "Untitled Automation" },
                description = buildDescription(triggers, actions),
                icon = icon,
                iconColor = 0xFF1B62B7,
                backgroundColor = 0xFFE3EEFA,
                category = "custom",
                priority = 1,
                enabled = true,
                triggers = triggers,
                conditions = conditions,
                actions = actions,
                createdAt = now,
                updatedAt = now
            )
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
