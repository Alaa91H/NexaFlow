package com.nexaflow.feature.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.engine.BatteryMonitor
import com.nexaflow.core.execution.capability.CapabilityStateStore
import com.nexaflow.core.execution.compat.WorkflowCapabilityValidator
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.MaintenanceProfile
import com.nexaflow.domain.models.GlobalVariable
import com.nexaflow.domain.models.PluginInfo
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.PluginRepository
import com.nexaflow.domain.repositories.VariableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AutomationBuilderViewModel @Inject constructor(
    private val repository: AutomationRepository,
    private val variableRepository: VariableRepository,
    private val pluginRepository: PluginRepository,
    private val batteryMonitor: BatteryMonitor,
    private val capabilityStateStore: CapabilityStateStore
) : ViewModel() {

    /** One capability-engine snapshot for all builder visibility decisions. */
    val capabilitySnapshot: StateFlow<CapabilitySnapshot> = capabilityStateStore.snapshot

    /** User-defined global variables, so the editor can offer %VAR insertion. */
    val variables: StateFlow<List<GlobalVariable>> = variableRepository.getVariables()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * All saved tasks, so the notification action can attach interactive
     * buttons that run another task straight from the notification.
     */
    val automations: StateFlow<List<Automation>> = repository.getAutomations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Installed external plugins (Locale protocol), for the plugin action. */
    private val _plugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val plugins: StateFlow<List<PluginInfo>> = _plugins

    /** Re-discovers installed plugins from the package manager. */
    fun refreshPlugins() {
        viewModelScope.launch {
            _plugins.value = runCatching { pluginRepository.discoverPlugins() }
                .getOrDefault(_plugins.value)
        }
    }

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
        iconColor: Long = 0xFF0B57D0,
        triggers: List<Trigger>,
        actions: List<Action>,
        constraints: List<Constraint> = emptyList(),
        exitActions: List<Action> = emptyList(),
        revertOnExit: Boolean = false,
        // Always immediate: the cooldown UI was removed and every trigger now
        // fires at once; the engine gate stays pinned to zero.
        cooldownSeconds: Int = 0,
        maintenanceProfile: MaintenanceProfile? = null
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
                iconColor = iconColor,
                backgroundColor = prev?.backgroundColor ?: 0xFFE3EEFA,
                category = prev?.category ?: "custom",
                priority = prev?.priority ?: 1,
                enabled = false,
                triggers = triggers,
                actions = actions,
                constraints = constraints,
                exitActions = exitActions,
                revertOnExit = revertOnExit,
                cooldownSeconds = cooldownSeconds,
                maintenanceProfile = maintenanceProfile ?: prev?.maintenanceProfile,
                createdAt = prev?.createdAt ?: now,
                updatedAt = now
            )
            val admitted = WorkflowCapabilityValidator.validate(
                automation,
                capabilityStateStore.snapshot.value
            ).admissible
            val storedAutomation = automation.copy(enabled = (prev?.enabled ?: true) && admitted)
            existing = storedAutomation
            repository.saveAutomation(storedAutomation)
            // Battery triggers only evaluate on ACTION_BATTERY_CHANGED broadcasts;
            // a task saved while the level is already steady below the threshold
            // would wait for the battery to move again. Re-evaluate now so a
            // freshly saved low-battery task runs immediately when applicable.
            batteryMonitor.refresh()
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
