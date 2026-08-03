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

    fun saveAutomation(
        name: String,
        icon: String,
        trigger: Trigger?,
        conditions: List<Condition>,
        actions: List<Action>
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val automation = Automation(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "Untitled Automation" },
                description = buildDescription(trigger, actions),
                icon = icon,
                iconColor = 0xFF1B62B7,
                backgroundColor = 0xFFE3EEFA,
                category = "custom",
                priority = 1,
                enabled = true,
                triggers = listOfNotNull(trigger),
                conditions = conditions,
                actions = actions,
                createdAt = now,
                updatedAt = now
            )
            repository.saveAutomation(automation)
        }
    }

    private fun buildDescription(trigger: Trigger?, actions: List<Action>): String {
        val triggerText = trigger?.let {
            "When ${it.type.name.replace('_', ' ').lowercase()}"
        } ?: "When configured"
        return "$triggerText, then ${actions.size} action(s)"
    }
}
