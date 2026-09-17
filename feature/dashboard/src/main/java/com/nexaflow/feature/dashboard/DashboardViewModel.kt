package com.nexaflow.feature.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.ExecutionResultPresentation
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    historyRepository: HistoryRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    /** automationId -> most recent durable execution — O(automationCount) via SQL, not O(historySize). */
    private val lastRunFlow = historyRepository.getLatestExecutions()
        .map { list -> list.associateBy { it.automationId } }
        .distinctUntilChanged()

    private val automationsFlow = combine(
        automationRepository.getAutomations(),
        lastRunFlow
    ) { automations, lastRuns ->
        automations.map { automation ->
            val lastRun = lastRuns[automation.id]
            AutomationRow(
                automation = automation,
                lastRunAt = lastRun?.executedAt,
                lastRunSucceeded = lastRun?.success
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val automations: StateFlow<List<AutomationRow>> = automationsFlow

    private val _runningIds = MutableStateFlow<Set<String>>(emptySet())
    val runningIds: StateFlow<Set<String>> = _runningIds

    private val _executionMessage = MutableStateFlow<String?>(null)
    val executionMessage: StateFlow<String?> = _executionMessage

    /** Toggles a single routine on/off — strict: enable runs immediately if triggers match, disable runs exit. */
    fun toggleAutomation(automation: Automation, enabled: Boolean) {
        viewModelScope.launch {
            automationRepository.updateAutomationStatus(automation.id, enabled)
            if (!enabled) {
                // Strict: when disabling, immediately attempt to run "when task ends"
                try {
                    executionEngine.runExit(automation, forceConfiguredEnd = true)
                } catch (_: Exception) {}
            } else {
                // Strict: when enabling, if triggers already match, run immediately
                try {
                    executionEngine.runWithConditionGate(automation)
                } catch (_: Exception) {}
            }
            // Show toast if enabled for this task
            if (automation.showToastOnToggle) {
                val message = if (enabled) {
                    appContext.getString(R.string.task_enabled_toast, automation.name)
                } else {
                    appContext.getString(R.string.task_disabled_toast, automation.name)
                }
                _executionMessage.value = message
            }
            // Notify the monitors so an enabled task whose condition already
            // holds runs immediately (redundant with direct run, but ensures
            // stateful monitors are armed), and for disable, ensure lifecycle reconciled
            executionEngine.notifyAutomationsChanged()
        }
    }

    fun setShowToastOnToggle(automation: Automation, showToast: Boolean) {
        viewModelScope.launch {
            val updated = automation.copy(showToastOnToggle = showToast)
            automationRepository.saveAutomation(updated)
        }
    }

    /** Deletes one routine after the dashboard confirmation dialog is accepted. */
    fun deleteAutomation(automation: Automation) {
        viewModelScope.launch {
            try {
                automationRepository.deleteAutomation(automation)
                // The row is gone: no monitor can ever resolve this id again, so
                // the engine ledger is unreachable. Cleanup is therefore
                // best-effort — a storage failure must not turn a successful
                // delete into a failure report.
                try {
                    executionEngine.onAutomationDeleted(automation.id)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // Best-effort; the durable marker is inert once the row is gone.
                }
                _executionMessage.value = appContext.getString(R.string.task_deleted, automation.name)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Room rolls the delete back on failure, so the task still exists
                // and its engine state must stay intact. Surface the failure
                // instead of crashing the app.
                _executionMessage.value = appContext.getString(R.string.task_delete_failed, automation.name)
            }
        }
    }

    /** Runs a routine immediately from the dashboard action menu — obeys the trigger/condition gate. */
    fun runNow(automation: Automation) {
        if (automation.id in _runningIds.value) return
        viewModelScope.launch {
            _runningIds.value = _runningIds.value + automation.id
            // Manual "Run now" must obey the task's triggers and constraints:
            // satisfied → run the main chain; unsatisfied → run the configured
            // end behavior ("when the task ends"), or record an explicit
            // conditions-not-satisfied outcome when none is configured. This is
            // the single manual-admission policy, shared with the details
            // screen, the enable toggle, and the builder save path.
            val record = executionEngine.runWithConditionGate(automation)
            _executionMessage.value = formatExecutionMessage(record)
            _runningIds.value = _runningIds.value - automation.id
        }
    }

    /**
     * Typed explanation of why a manual run would be rejected right now.
     * The UI shows it on the Run-now mismatch dialog; null means admissible.
     */
    suspend fun describeManualBlock(automation: Automation): ExecutionEngine.ManualBlockReason? {
        val reason = executionEngine.describeManualBlock(automation)
        return if (reason.kind == ExecutionEngine.ManualBlockKind.NONE) null else reason
    }

    /** Saved tasks still carrying the legacy combined CONNECTIVITY trigger. */
    val legacyConnectivityTasks: StateFlow<List<Automation>> = automationRepository.getAutomations()
        .map { list ->
            list.filter { automation ->
                automation.triggers.any { it.type == com.nexaflow.domain.models.TriggerType.CONNECTIVITY }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * One-tap migration: replaces each legacy combined CONNECTIVITY trigger
     * with dedicated WIFI_CONNECTED / MOBILE_DATA_CONNECTED triggers that
     * preserve the original network selection and state. Non-migratable
     * selections (HOTSPOT/ETHERNET/VPN) are left untouched.
     */
    fun migrateLegacyConnectivityTriggers() {
        viewModelScope.launch {
            legacyConnectivityTasks.value.forEach { automation ->
                val newTriggers = mutableListOf<com.nexaflow.domain.models.Trigger>()
                var changed = false
                automation.triggers.forEach { trigger ->
                    if (trigger.type != com.nexaflow.domain.models.TriggerType.CONNECTIVITY) {
                        newTriggers += trigger
                        return@forEach
                    }
                    val network = (trigger.config["network"] ?: "WIFI").uppercase()
                    val state = trigger.config["state"] ?: "CONNECTED"
                    when (network) {
                        "WIFI" -> newTriggers += com.nexaflow.domain.models.Trigger(
                            com.nexaflow.domain.models.TriggerType.WIFI_CONNECTED,
                            mapOf("state" to state)
                        )
                        "MOBILE" -> newTriggers += com.nexaflow.domain.models.Trigger(
                            com.nexaflow.domain.models.TriggerType.MOBILE_DATA_CONNECTED,
                            mapOf("state" to state)
                        )
                        else -> {
                            // HOTSPOT has its own dedicated trigger; ETHERNET/VPN
                            // have no split equivalent yet and must keep working.
                            newTriggers += trigger
                            return@forEach
                        }
                    }
                    changed = true
                }
                if (changed) {
                    automationRepository.saveAutomation(
                        automation.copy(triggers = newTriggers, updatedAt = System.currentTimeMillis())
                    )
                }
            }
        }
    }

    /** Explicit user override after the force-run confirmation dialog. */
    fun forceRun(automation: Automation) {
        if (automation.id in _runningIds.value) return
        viewModelScope.launch {
            _runningIds.value = _runningIds.value + automation.id
            val record = executionEngine.forceRun(automation)
            _executionMessage.value = formatExecutionMessage(record)
            _runningIds.value = _runningIds.value - automation.id
        }
    }

    private fun formatExecutionMessage(record: ExecutionRecord): String =
        ExecutionResultPresentation.summary(appContext, record)

    fun consumeExecutionMessage() {
        _executionMessage.value = null
    }
}

/** A routine paired with its latest durable outcome (null = never ran). */
data class AutomationRow(
    val automation: Automation,
    val lastRunAt: Long?,
    /** Null when no run exists; false means the action chain or configuration failed. */
    val lastRunSucceeded: Boolean? = null
)
