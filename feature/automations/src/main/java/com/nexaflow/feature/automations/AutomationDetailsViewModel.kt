package com.nexaflow.feature.automations

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.execution.AutomationExecutionProgress
import com.nexaflow.core.engine.ExitCoordinator
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.ManualBlockReason
import com.nexaflow.core.execution.ManualBlockKind
import com.nexaflow.core.execution.ManualAdmissionDiagnostics
import com.nexaflow.core.execution.ExecutionResultPresentation
import com.nexaflow.core.execution.RecoveryReviewItem
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutomationDetailsViewModel @Inject constructor(
    private val repository: AutomationRepository,
    private val healthRepository: HealthRepository,
    private val historyRepository: HistoryRepository,
    private val executionEngine: ExecutionEngine,
    private val exitCoordinator: ExitCoordinator,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val automationId: String = savedStateHandle["automationId"] ?: ""

    val automation: StateFlow<Automation?> = repository.getAutomations()
        .map { list -> list.find { it.id == automationId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Durable recovery evidence is kept separate from history-derived health. */
    private val _recoveryItems = MutableStateFlow<List<RecoveryReviewItem>>(emptyList())
    val recoveryItems: StateFlow<List<RecoveryReviewItem>> = _recoveryItems

    /**
     * Read-only health for the routine. History provides execution counts and
     * failure streaks; the durable execution ledger alone decides whether
     * recovery review is currently pending.
     */
    val healthReport: StateFlow<AutomationHealthReport> = combine(
        healthRepository.getHealthReports(),
        _recoveryItems
    ) { reports, recoveryItems ->
        val recoveryPending = recoveryItems.isNotEmpty()
        val report = reports.find { it.automationId == automationId }
            ?: emptyHealthReport(automationId)
        report.copy(
            recoveryReviewPending = recoveryPending,
            status = if (recoveryPending) {
                AutomationHealthStatus.NEEDS_ATTENTION
            } else {
                report.status
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyHealthReport(automationId))

    /** Latest durable run, including per-action route and verification metadata. */
    val latestExecution: StateFlow<ExecutionRecord?> = historyRepository.getLatestExecutions()
        .map { records -> records.find { it.automationId == automationId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** In-process progress while the main action chain is executing. */
    val liveProgress: StateFlow<AutomationExecutionProgress?> =
        executionEngine.observeExecutionProgress(automationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _diagnostics = MutableStateFlow<ManualAdmissionDiagnostics?>(null)
    val diagnostics: StateFlow<ManualAdmissionDiagnostics?> = _diagnostics

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    /** Guards the delete entry point against double-invocation (double pop). */
    private val _deleting = MutableStateFlow(false)

    private val _executionMessage = MutableStateFlow<String?>(null)
    val executionMessage: StateFlow<String?> = _executionMessage

    init {
        refreshRecoveryState()
        viewModelScope.launch {
            automation.filterNotNull().collectLatest { current ->
                _diagnostics.value = runCatching {
                    executionEngine.diagnoseManualAdmission(current)
                }.getOrNull()
            }
        }
    }

    private fun refreshRecoveryState() {
        viewModelScope.launch {
            _recoveryItems.value = runCatching {
                executionEngine.recoveryReviewItems(automationId)
            }.getOrDefault(emptyList())
        }
    }

    /** Re-evaluates live trigger/constraint state without executing any action. */
    fun refreshDiagnostics() {
        val current = automation.value ?: return
        viewModelScope.launch {
            _diagnostics.value = runCatching {
                executionEngine.diagnoseManualAdmission(current)
            }.getOrNull()
        }
    }

    fun setDeepLinkAccess(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getAutomationById(automationId) ?: return@launch
            repository.saveAutomation(current.copy(
                deepLinkToken = if (enabled) com.nexaflow.domain.security.ExternalAccessPolicy.newToken() else null,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    fun repairWebhookTokens() {
        viewModelScope.launch {
            val current = repository.getAutomationById(automationId) ?: return@launch
            repository.saveAutomation(current.copy(triggers = current.triggers.map {
                if (it.type == com.nexaflow.domain.models.TriggerType.WEBHOOK && it.config["token"].isNullOrBlank())
                    it.copy(config = it.config + ("token" to com.nexaflow.domain.security.ExternalAccessPolicy.newToken()))
                else it
            }))
            executionEngine.notifyAutomationsChanged()
        }
    }

    fun toggleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val wasEnabled = automation.value?.enabled == true
            repository.updateAutomationStatus(automationId, enabled)
            if (enabled && !wasEnabled) {
                // Enable may run immediately when current conditions already
                // match. Disable cleanup is claimed by the monitoring layer's
                // durable lifecycle coordinator after the status commit.
                try {
                    automation.value?.let { executionEngine.runWithConditionGate(it) }
                } catch (_: Exception) {}
            }
            // Notify the monitors so an enabled task whose condition already
            // holds runs immediately, and for disable, ensure lifecycle reconciled
            executionEngine.notifyAutomationsChanged()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        // A second tap while the first delete is in flight would otherwise pop
        // the details screen twice (the second pop removes the screen below it).
        if (_deleting.value) return
        _deleting.value = true
        viewModelScope.launch {
            try {
                val current = repository.getAutomationById(automationId)
                if (current != null) {
                    // Deletion is a two-phase safety operation: persist disabled
                    // intent first so a later repository-delete failure cannot
                    // leave an enabled task whose lifecycle was already closed.
                    if (current.enabled) {
                        repository.updateAutomationStatus(current.id, false)
                        executionEngine.notifyAutomationsChanged()
                    }
                    val disabled = current.copy(enabled = false)
                    if (!exitCoordinator.prepareForDeletion(disabled)) {
                        _executionMessage.value = appContext.getString(R.string.task_delete_failed)
                        return@launch
                    }
                    repository.deleteAutomation(disabled)
                }
                try {
                    executionEngine.onAutomationDeleted(automationId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // No unresolved runtime work remained when deletion was
                    // admitted; residual process-local cleanup is best-effort.
                }
                onDeleted()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _executionMessage.value = appContext.getString(R.string.task_delete_failed)
            } finally {
                _deleting.value = false
            }
        }
    }

    fun runNow() {
        val current = automation.value ?: return
        if (_running.value) return
        viewModelScope.launch {
            _running.value = true
            // Strict manual admission: a mismatch is side-effect free. End
            // behavior and Force Run remain separate explicit user choices.
            val record = executionEngine.runWithConditionGate(current)
            _executionMessage.value = formatExecutionMessage(record)
            _running.value = false
            refreshDiagnostics()
        }
    }

    /** Explicit user choice to run only the configured end behavior. */
    fun runEndBehavior() {
        val current = automation.value ?: return
        if (_running.value) return
        viewModelScope.launch {
            _running.value = true
            val record = executionEngine.runManualEndBehavior(current)
            _executionMessage.value = formatExecutionMessage(record)
            _running.value = false
            refreshDiagnostics()
        }
    }

    /** Explicitly acknowledges unresolved legacy recovery records for this routine. */
    fun clearRecoveryBacklog() {
        viewModelScope.launch {
            val cleared = executionEngine.clearRecoveryBacklog(automationId)
            _recoveryItems.value = executionEngine.recoveryReviewItems(automationId)
            _executionMessage.value = appContext.getString(
                R.string.recovery_backlog_cleared,
                cleared
            )
        }
    }

    /**
     * Typed mismatch explanation for the Run-now dialog; null when admissible.
     * Same policy source as the dashboard and deep-link paths.
     */
    suspend fun describeManualBlock(): ManualBlockReason? {
        val current = automation.value ?: return null
        val reason = executionEngine.describeManualBlock(current)
        return if (reason.kind == ManualBlockKind.NONE) null else reason
    }

    /** Explicit user override after the force-run confirmation dialog. */
    fun forceRun() {
        val current = automation.value ?: return
        if (_running.value) return
        viewModelScope.launch {
            _running.value = true
            val record = executionEngine.forceRun(current)
            _executionMessage.value = formatExecutionMessage(record)
            _running.value = false
            refreshDiagnostics()
        }
    }

    private fun formatExecutionMessage(record: ExecutionRecord): String =
        ExecutionResultPresentation.summary(appContext, record)

    fun consumeExecutionMessage() {
        _executionMessage.value = null
    }
}

private fun emptyHealthReport(automationId: String) = AutomationHealthReport(
    automationId = automationId,
    lastExecutionAt = null,
    completedRuns = 0,
    skippedRuns = 0,
    failedRuns = 0,
    consecutiveFailures = 0,
    latestFailureMessage = null,
    status = AutomationHealthStatus.NO_EXECUTIONS
)
