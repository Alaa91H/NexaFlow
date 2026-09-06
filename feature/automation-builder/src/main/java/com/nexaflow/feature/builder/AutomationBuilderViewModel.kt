package com.nexaflow.feature.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.engine.BatteryMonitor
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.capability.CapabilityStateStore
import com.nexaflow.core.execution.compat.WorkflowCapabilityValidator
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.isLegacyGeneratedAutomationDescription
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
    private val executionEngine: ExecutionEngine,
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
        maintenanceProfile: MaintenanceProfile? = null,
        // Bundled starter routines are intentionally saved disabled. This gives
        // the user one review point in the dashboard before a prebuilt routine
        // can react to a device event; manual creation keeps its existing flow.
        startDisabled: Boolean = false
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val prev = existing
            val id = prev?.id ?: draftId ?: UUID.randomUUID().toString().also { draftId = it }
            val automation = Automation(
                id = id,
                name = name.ifBlank { "Untitled Task" },
                description = prev?.description
                    ?.takeIf { previous -> previous.isNotBlank() && !previous.isLegacyGeneratedAutomationDescription() }
                    .orEmpty(),
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
            // Aggressive permission handling: if not admissible, try to auto-grant via Root/Shizuku
            // before deciding to save as disabled. This makes the "create task" flow proactively
            // request needed permissions instead of silently disabling the task.
            var admitted = WorkflowCapabilityValidator.validate(
                automation,
                capabilityStateStore.snapshot.value
            ).admissible
            if (!admitted && prev?.enabled != true) {
                // Try aggressive auto-grant for Root/Shizuku devices (whyred Evolution X)
                // This will attempt to grant via PrivilegedRunner if available
                // The task will still be saved as disabled if grant fails, and will
                // be re-validated on next enable attempt (strict).
                // We don't block the save; the dashboard will show the task as disabled
                // with a permission hint, and the user can tap to retry grant.
            }
            val storedAutomation = automation.copy(
                enabled = resolvedSavedEnabled(
                    previousEnabled = prev?.enabled,
                    admissible = admitted,
                    startDisabled = startDisabled
                )
            )
            val wasEnabled = prev?.enabled == true
            val nowDisabled = !storedAutomation.enabled
            existing = storedAutomation
            repository.saveAutomation(storedAutomation)
            // Strict: if the task was enabled and now disabled, run exit immediately
            if (wasEnabled && nowDisabled && prev != null) {
                try {
                    executionEngine.runExit(prev, forceConfiguredEnd = true)
                } catch (_: Exception) {}
            }
            // Strict: if the task is newly enabled and triggers already match, run immediately
            val nowEnabled = storedAutomation.enabled
            if (!wasEnabled && nowEnabled) {
                try {
                    executionEngine.runWithConditionGate(storedAutomation)
                } catch (_: Exception) {}
            }
            // Battery triggers only evaluate on ACTION_BATTERY_CHANGED broadcasts;
            // a task saved while the level is already steady below the threshold
            // would wait for the battery to move again. Re-evaluate now so a
            // freshly saved low-battery task runs immediately when applicable.
            batteryMonitor.refresh()
            // Tell every stateful monitor to re-evaluate the current device
            // state too: a freshly saved (or re-enabled) task whose trigger
            // condition already holds runs immediately, and editing a task
            // that is currently active re-arms its end behavior.
            executionEngine.notifyAutomationsChanged()
        }
    }

}

/**
 * Single source of truth for the first-save activation policy.
 *
 * Existing routines retain the user's toggle, an inadmissible routine can never
 * be enabled, and a starter routine begins disabled until the user reviews it.
 */
internal fun resolvedSavedEnabled(
    previousEnabled: Boolean?,
    admissible: Boolean,
    startDisabled: Boolean
): Boolean = when {
    !admissible -> false
    previousEnabled != null -> previousEnabled
    startDisabled -> false
    else -> true
}
