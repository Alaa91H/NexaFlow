package com.nexaflow.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One task's grouped configuration failures from the execution log. */
data class ConfigFailureFinding(
    val automationId: String,
    val automationName: String,
    /** Distinct failure messages, newest first. */
    val failures: List<String>
)

/**
 * Derives configuration-problem findings from the durable execution log.
 * A failed action result counts when its message names a configuration
 * problem; matching is deliberately conservative so ordinary runtime errors
 * (missing permission, service down) are not mislabelled as config bugs.
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    historyRepository: HistoryRepository
) : ViewModel() {

    private val _findings = MutableStateFlow<List<ConfigFailureFinding>>(emptyList())
    val findings: StateFlow<List<ConfigFailureFinding>> = _findings.asStateFlow()

    init {
        viewModelScope.launch {
            historyRepository.getExecutionHistory().collect { records ->
                _findings.value = deriveFindings(records)
            }
        }
    }

    private fun deriveFindings(records: List<ExecutionRecord>): List<ConfigFailureFinding> {
        val byTask = LinkedHashMap<String, MutableList<String>>()
        val names = HashMap<String, String>()
        records.sortedByDescending { it.executedAt }.forEach { record ->
            val messages = record.actionResults
                .filter { !it.success && CONFIG_FAILURE_HINTS.any(it.message::contains) }
                .map { "${it.actionType}: ${it.message}" }
            if (messages.isEmpty()) return@forEach
            names[record.automationId] = record.automationName
            byTask.getOrPut(record.automationId) { mutableListOf() }.addAll(messages)
        }
        return byTask.map { (id, failures) ->
            ConfigFailureFinding(
                automationId = id,
                automationName = names[id] ?: id,
                failures = failures.distinct()
            )
        }
    }

    private companion object {
        /** Conservative markers of an invalid-configuration fallback. */
        val CONFIG_FAILURE_HINTS = listOf(
            "invalid", "unknown value", "unrecognized", "invalid value",
            "no enum", "unparsable", "out of range", "not a valid"
        )
    }
}
