package com.nexaflow.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Blocked-call log entries: records produced by call-screening tasks whose
 * policy verdict was BLOCK. The rule name is the task that blocked the call.
 */
data class BlockedCallEntry(
    val id: String,
    val automationId: String,
    val automationName: String,
    val message: String,
    val executedAt: Long
)

@HiltViewModel
class BlockedCallsViewModel @Inject constructor(
    historyRepository: HistoryRepository
) : ViewModel() {

    /** Null shows every rule; a task name filters to that rule's calls. */
    private val _selectedRule = MutableStateFlow<String?>(null)
    val selectedRule: StateFlow<String?> = _selectedRule.asStateFlow()

    private val blockedRecords = historyRepository.getExecutionHistory()
        .map { records -> records.filter { it.message.startsWith(BLOCKED_MARKER) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Distinct blocking rules (task names), for the filter chips. */
    val rules: StateFlow<List<String>> = blockedRecords
        .map { records -> records.map { it.automationName }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entries: StateFlow<List<BlockedCallEntry>> = combine(blockedRecords, _selectedRule) { records, rule ->
        records.asSequence()
            .filter { rule == null || it.automationName == rule }
            .sortedByDescending { it.executedAt }
            .map {
                BlockedCallEntry(
                    id = it.id,
                    automationId = it.automationId,
                    automationName = it.automationName,
                    message = it.message.removePrefix(BLOCKED_MARKER),
                    executedAt = it.executedAt
                )
            }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectRule(rule: String?) {
        _selectedRule.value = rule
    }

    private companion object {
        /** Engine marker written by NexaCallScreeningService on BLOCK verdicts. */
        const val BLOCKED_MARKER = "Call blocked: "
    }
}
