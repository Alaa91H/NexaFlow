package com.nexaflow.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.domain.models.ActionExecutionResult
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
    /** Distinct action-config failure messages, newest first. */
    val failures: List<String>,
    /**
     * Distinct end-behavior failure messages (`*_END` results), newest first.
     * A task can carry action-config problems, end-behavior problems, or both.
     */
    val endBehaviorFailures: List<String> = emptyList()
) {
    val hasProblems: Boolean get() = failures.isNotEmpty() || endBehaviorFailures.isNotEmpty()
}

/**
 * Derives configuration-problem findings from the durable execution log.
 *
 * Two buckets per task:
 * - action-config failures: a failed action result whose message names a
 *   configuration problem (conservative markers only, so ordinary runtime
 *   errors like a missing permission are not mislabelled as config bugs).
 * - end-behavior failures: any failed `*_END` result — the value the user
 *   configured for "when the task ends" did not apply at runtime.
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
        val byTask = LinkedHashMap<String, Pair<MutableList<String>, MutableList<String>>>()
        val names = HashMap<String, String>()
        records.sortedByDescending { it.executedAt }.forEach { record ->
            val configMessages = mutableListOf<String>()
            val endMessages = mutableListOf<String>()
            record.actionResults.forEach { result ->
                if (result.success) return@forEach
                if (isEndBehaviorResult(result)) {
                    endMessages.add("${result.actionType}: ${result.message}")
                } else if (CONFIG_FAILURE_HINTS.any(result.message.lowercase()::contains)) {
                    configMessages.add("${result.actionType}: ${result.message}")
                }
            }
            if (configMessages.isEmpty() && endMessages.isEmpty()) return@forEach
            names[record.automationId] = record.automationName
            val buckets = byTask.getOrPut(record.automationId) {
                mutableListOf<String>() to mutableListOf()
            }
            buckets.first.addAll(configMessages)
            buckets.second.addAll(endMessages)
        }
        return byTask.mapNotNull { (id, buckets) ->
            val (configFailures, endFailures) = buckets
            val finding = ConfigFailureFinding(
                automationId = id,
                automationName = names[id] ?: id,
                failures = configFailures.distinct(),
                endBehaviorFailures = endFailures.distinct()
            )
            finding.takeIf { it.hasProblems }
        }
    }

    companion object {
        /** Conservative markers of an invalid-configuration fallback. */
        val CONFIG_FAILURE_HINTS = listOf(
            "invalid", "unknown value", "unrecognized", "invalid value",
            "no enum", "unparsable", "out of range", "not a valid"
        )

        /**
         * The engine tags per-action end-behavior outcomes with an `_END`
         * suffix on the action type (e.g. `SYSTEM_BRIGHTNESS_END`), and
         * whole-state restores run as `STATE_RESTORE`.
         */
        fun isEndBehaviorResult(result: ActionExecutionResult): Boolean =
            result.actionType.endsWith(END_SUFFIX) || result.actionType == STATE_RESTORE_TYPE

        private const val END_SUFFIX = "_END"
        private const val STATE_RESTORE_TYPE = "STATE_RESTORE"
    }
}
