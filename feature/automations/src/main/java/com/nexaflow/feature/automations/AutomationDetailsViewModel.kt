package com.nexaflow.feature.automations

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.ExecutionResultPresentation
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.AutomationHealthReport
import com.nexaflow.domain.models.AutomationHealthStatus
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutomationDetailsViewModel @Inject constructor(
    private val repository: AutomationRepository,
    private val healthRepository: HealthRepository,
    private val executionEngine: ExecutionEngine,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val automationId: String = savedStateHandle["automationId"] ?: ""

    val automation: StateFlow<Automation?> = repository.getAutomations()
        .map { list -> list.find { it.id == automationId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Durable recovery state is kept separate from history-derived health. */
    private val _recoveryPending = MutableStateFlow(false)

    /**
     * Read-only health for the routine. History provides execution counts and
     * failure streaks; the durable execution ledger alone decides whether
     * recovery review is currently pending.
     */
    val healthReport: StateFlow<AutomationHealthReport> = combine(
        healthRepository.getHealthReports(),
        _recoveryPending
    ) { reports, recoveryPending ->
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

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    /** Guards the delete entry point against double-invocation (double pop). */
    private val _deleting = MutableStateFlow(false)

    private val _executionMessage = MutableStateFlow<String?>(null)
    val executionMessage: StateFlow<String?> = _executionMessage

    init {
        refreshRecoveryState()
    }

    private fun refreshRecoveryState() {
        viewModelScope.launch {
            _recoveryPending.value = runCatching {
                executionEngine.recoveryBacklogCount(automationId) > 0
            }.getOrDefault(false)
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
            if (!enabled && wasEnabled) {
                try {
                    automation.value?.let { executionEngine.runExit(it, forceConfiguredEnd = true) }
                } catch (_: Exception) {}
            } else if (enabled && !wasEnabled) {
                // Strict: enable → run immediately if triggers match
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
                repository.getAutomationById(automationId)?.let { repository.deleteAutomation(it) }
                // The row is gone: no monitor can ever resolve this id again, so
                // the engine ledger is unreachable. Cleanup is therefore
                // best-effort — a storage failure must not strand the user on a
                // screen for a task that no longer exists.
                try {
                    executionEngine.onAutomationDeleted(automationId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // Best-effort; the durable marker is inert once the row is gone.
                }
                onDeleted()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Room rolls the delete back on failure, so the task still exists
                // and its engine state must stay intact. Surface the failure
                // instead of crashing the app.
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
            // Strict manual admission: triggers and constraints must match
            // before the main chain runs. A mismatch runs the configured end
            // behavior, or is reported explicitly when none is configured —
            // a manual tap never bypasses the task's own conditions.
            val record = executionEngine.runWithConditionGate(current)
            _executionMessage.value = formatExecutionMessage(record)
            _running.value = false
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
        }
    }

    /** Explicitly acknowledges unresolved legacy recovery records for this routine. */
    fun clearRecoveryBacklog() {
        viewModelScope.launch {
            val cleared = executionEngine.clearRecoveryBacklog(automationId)
            _recoveryPending.value = executionEngine.recoveryBacklogCount(automationId) > 0
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
    suspend fun describeManualBlock(): ExecutionEngine.ManualBlockReason? {
        val current = automation.value ?: return null
        val reason = executionEngine.describeManualBlock(current)
        return if (reason.kind == ExecutionEngine.ManualBlockKind.NONE) null else reason
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
