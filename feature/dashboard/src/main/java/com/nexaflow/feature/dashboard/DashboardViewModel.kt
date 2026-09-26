package com.nexaflow.feature.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.ManualBlockReason
import com.nexaflow.core.execution.ManualBlockKind
import com.nexaflow.data.backup.BackupManager
import com.nexaflow.core.execution.ExecutionResultPresentation
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.AutomationHealthReport
import com.nexaflow.domain.models.AutomationHealthStatus
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HealthRepository
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
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    historyRepository: HistoryRepository,
    healthRepository: HealthRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    /** Recently minted deep-link token, surfaced once for the user to copy/share. */
    private val _deepLinkToken = MutableStateFlow<String?>(null)
    val deepLinkToken: StateFlow<String?> = _deepLinkToken

    /** automationId -> most recent durable execution — O(automationCount) via SQL, not O(historySize). */
    private val lastRunFlow = historyRepository.getLatestExecutions()
        .map { list -> list.associateBy { it.automationId } }
        .distinctUntilChanged()

    private val automationsFlow = combine(
        automationRepository.getAutomations(),
        lastRunFlow,
        healthRepository.getHealthReports()
    ) { automations, lastRuns, healthReports ->
        val healthByAutomation = healthReports.associateBy { it.automationId }
        automations.map { automation ->
            val lastRun = lastRuns[automation.id]
            AutomationRow(
                automation = automation,
                lastRunAt = lastRun?.executedAt,
                lastRunSucceeded = lastRun?.success,
                latestExecution = lastRun,
                healthReport = healthByAutomation[automation.id]
                    ?: emptyHealthReport(automation.id)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val automations: StateFlow<List<AutomationRow>> = automationsFlow

    private val _runningIds = MutableStateFlow<Set<String>>(emptySet())
    val runningIds: StateFlow<Set<String>> = _runningIds

    private val _executionMessage = MutableStateFlow<String?>(null)
    val executionMessage: StateFlow<String?> = _executionMessage

    /** Serializes one task to the single-task (.nexaflow) share format; null when unavailable. */
    suspend fun exportTaskJson(automationId: String): String? {
        val automation = automationRepository.getAutomationById(automationId) ?: return null
        return runCatching { BackupManager(automationRepository).exportSingle(automation) }.getOrNull()
    }

    /** Toggles a single routine on/off — strict: enable runs immediately if triggers match, disable runs exit. */
    fun toggleAutomation(automation: Automation, enabled: Boolean) {
        viewModelScope.launch {
            automationRepository.updateAutomationStatus(automation.id, enabled)
            if (!enabled) {
                // Strict: when disabling, immediately attempt to run "when task ends"
                try {
                    executionEngine.runDisableCleanup(automation)
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

    /**
     * P0.2 deep-link opt-in: mints a fresh 128-bit base64url capability token
     * for [automation] (or rotates the existing one). Callers expose the full
     * `nexaflow://run-task/{id}?token=...` link to the user — sharing it
     * grants run access, which the UI must state explicitly.
     */
    fun grantDeepLinkAccess(automation: Automation) {
        viewModelScope.launch {
            val token = newDeepLinkToken()
            automationRepository.saveAutomation(automation.copy(deepLinkToken = token))
            _deepLinkToken.value = token
        }
    }

    /** P0.2: revokes external deep-link execution for [automation]. */
    fun revokeDeepLinkAccess(automation: Automation) {
        viewModelScope.launch {
            automationRepository.saveAutomation(automation.copy(deepLinkToken = null))
            _deepLinkToken.value = null
        }
    }

    /** Deletes one routine after the dashboard confirmation dialog is accepted. */
    fun deleteAutomation(automation: Automation) {
        viewModelScope.launch {
            var disabledForDelete = false
            try {
                // Preserve the definition while cleanup runs. Stateful monitors
                // need it in order to claim the durable occurrence and execute
                // the exact end/revert behavior before the row disappears.
                if (automation.enabled) {
                    automationRepository.updateAutomationStatus(automation.id, false)
                    disabledForDelete = true
                }
                if (!executionEngine.prepareForDeletion(automation)) {
                    _executionMessage.value = appContext.getString(
                        R.string.task_delete_failed,
                        automation.name
                    )
                    return@launch
                }

                try {
                    automationRepository.deleteAutomation(automation)
                } catch (failure: Exception) {
                    if (disabledForDelete) {
                        runCatching {
                            automationRepository.updateAutomationStatus(automation.id, true)
                            executionEngine.notifyAutomationsChanged()
                        }
                    }
                    throw failure
                }

                try {
                    executionEngine.onAutomationDeleted(automation.id)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // Best-effort after the authoritative row deletion.
                }
                _executionMessage.value = appContext.getString(R.string.task_deleted, automation.name)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _executionMessage.value = appContext.getString(R.string.task_delete_failed, automation.name)
            }
        }
    }

    /** Runs a routine immediately from the dashboard action menu — obeys the trigger/condition gate. */
    fun runNow(automation: Automation) {
        if (automation.id in _runningIds.value) return
        viewModelScope.launch {
            _runningIds.value = _runningIds.value + automation.id
            // Manual "Run now" obeys triggers and constraints. A mismatch is
            // side-effect free; the separate dialog action owns explicit end
            // behavior execution.
            val record = executionEngine.runWithConditionGate(automation)
            _executionMessage.value = formatExecutionMessage(record)
            _runningIds.value = _runningIds.value - automation.id
        }
    }

    /** Explicit user choice to run only the configured end behavior. */
    fun runEndBehavior(automation: Automation) {
        if (automation.id in _runningIds.value) return
        viewModelScope.launch {
            _runningIds.value = _runningIds.value + automation.id
            val record = executionEngine.runManualEndBehavior(automation)
            _executionMessage.value = formatExecutionMessage(record)
            _runningIds.value = _runningIds.value - automation.id
        }
    }

    /**
     * Typed explanation of why a manual run would be rejected right now.
     * The UI shows it on the Run-now mismatch dialog; null means admissible.
     */
    suspend fun describeManualBlock(automation: Automation): ManualBlockReason? {
        val reason = executionEngine.describeManualBlock(automation)
        return if (reason.kind == ManualBlockKind.NONE) null else reason
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

    private companion object {
        /** P0.2 token entropy: 128 bits, base64url — ~22 chars, unguessable. */
        const val TOKEN_BYTES = 16

        fun newDeepLinkToken(): String {
            val bytes = ByteArray(TOKEN_BYTES)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}

/** A routine paired with its latest durable outcome (null = never ran). */
data class AutomationRow(
    val automation: Automation,
    val lastRunAt: Long?,
    /** Null when no run exists; false means the action chain or configuration failed. */
    val lastRunSucceeded: Boolean? = null,
    /** Complete latest durable run so expanded cards can present per-action outcomes. */
    val latestExecution: ExecutionRecord? = null,
    /** Read-only execution health derived from durable history. */
    val healthReport: AutomationHealthReport = emptyHealthReport(automation.id)
)

internal fun emptyHealthReport(automationId: String) = AutomationHealthReport(
    automationId = automationId,
    lastExecutionAt = null,
    completedRuns = 0,
    skippedRuns = 0,
    failedRuns = 0,
    consecutiveFailures = 0,
    latestFailureMessage = null,
    status = AutomationHealthStatus.NO_EXECUTIONS
)
