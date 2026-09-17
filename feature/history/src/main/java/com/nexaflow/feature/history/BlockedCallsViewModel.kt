package com.nexaflow.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Blocked-call log entries: records produced by call-screening tasks whose
 * policy verdict was BLOCK. The rule names are the tasks that blocked the
 * call (persisted in the record; older rows fall back to the task name).
 */
data class BlockedCallEntry(
    val id: String,
    val automationId: String,
    val automationName: String,
    /** Masked caller number, e.g. "******7890". */
    val maskedNumber: String,
    /** Caller category token from the engine: ANY/UNKNOWN/PRIVATE/CONTACT. */
    val callerCategory: String?,
    /** Rule (task) names that matched the call; empty for legacy rows. */
    val ruleNames: List<String>,
    val executedAt: Long
)

@HiltViewModel
class BlockedCallsViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val automationRepository: AutomationRepository
) : ViewModel() {

    /** Null shows every rule; a task name filters to that rule's calls. */
    private val _selectedRule = MutableStateFlow<String?>(null)
    val selectedRule: StateFlow<String?> = _selectedRule.asStateFlow()

    private val blockedRecords = historyRepository.getExecutionHistory()
        .map { records -> records.filter { it.message.startsWith(BLOCKED_MARKER) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Distinct blocking rules (task names), for the filter chips. */
    val rules: StateFlow<List<String>> = blockedRecords
        .map { records -> records.flatMap { it.ruleNames().ifEmpty { listOf(it.automationName) } }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entries: StateFlow<List<BlockedCallEntry>> =
        combine(blockedRecords, _selectedRule) { records, rule ->
            records.asSequence()
                .filter { rule == null || it.automationName.split(';').contains(rule) || it.automationName == rule }
                .sortedByDescending { it.executedAt }
                .map { record ->
                    val parts = record.message.removePrefix(BLOCKED_MARKER).split('|')
                    BlockedCallEntry(
                        id = record.id,
                        automationId = record.automationId,
                        automationName = record.automationName,
                        maskedNumber = parts.getOrNull(0).orEmpty().ifBlank {
                            record.message.removePrefix(BLOCKED_MARKER)
                        },
                        callerCategory = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
                        ruleNames = record.ruleNames(),
                        executedAt = record.executedAt
                    )
                }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectRule(rule: String?) {
        _selectedRule.value = rule
    }

    /**
     * Resolves a rule (task) name to the task's id so the details sheet can
     * jump into the task editor. Null when no saved task has that name.
     */
    suspend fun ruleTaskId(ruleName: String): String? =
        automationRepository.getAutomations().firstOrNull()?.firstOrNull { it.name == ruleName }?.id

    /**
     * Latest engine-run action summaries for the rule (task), so the details
     * sheet shows exactly what the blocking task executed — e.g. end-call,
     * notify — rendered as "ACTION_TYPE: message" per line. Empty when the
     * task has never run through the engine.
     */
    suspend fun latestActionSummaries(ruleName: String): List<String> {
        val taskId = ruleTaskId(ruleName) ?: return emptyList()
        val records = historyRepository.getExecutionHistory().firstOrNull().orEmpty()
        val latest = records
            .filter { it.automationId == taskId && !it.message.startsWith(BLOCKED_MARKER) }
            .maxByOrNull { it.executedAt }
            ?: return emptyList()
        if (latest.actionResults.isEmpty()) {
            return listOf(latest.message)
        }
        return latest.actionResults.map { result ->
            "${result.actionType}: ${result.message}" +
                (if (result.success) "" else " ✕")
        }
    }

    private fun ExecutionRecord.ruleNames(): List<String> =
        message.removePrefix(BLOCKED_MARKER).split('|')
            .getOrNull(2)
            ?.split(';')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
            .ifEmpty { automationName.split(';').map { it.trim() }.filter { it.isNotEmpty() } }

    private companion object {
        /** Engine marker written by NexaCallScreeningService on BLOCK verdicts. */
        const val BLOCKED_MARKER = "Call blocked: "
    }
}
