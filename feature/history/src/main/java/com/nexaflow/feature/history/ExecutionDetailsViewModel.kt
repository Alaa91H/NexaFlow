package com.nexaflow.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.logging.ExecutionTimelineEntry
import com.nexaflow.core.logging.LogStore
import com.nexaflow.core.logging.RunExplainer
import com.nexaflow.domain.models.ExecutionOutcomeClassifier
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.usecases.GetExecutionByIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Screen state: [loading] distinguishes a pending load from a genuinely missing record. */
data class ExecutionDetailsUiState(
    val loading: Boolean = false,
    val record: ExecutionRecord? = null,
    val explanation: RunExplainer.Explanation? = null
)

@HiltViewModel
class ExecutionDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getExecutionById: GetExecutionByIdUseCase,
    private val logStore: LogStore
) : ViewModel() {

    private val recordId: String = checkNotNull(savedStateHandle["recordId"])

    private val _uiState = MutableStateFlow(ExecutionDetailsUiState(loading = true))
    val uiState: StateFlow<ExecutionDetailsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val record = getExecutionById(recordId)
            if (record == null) {
                _uiState.value = ExecutionDetailsUiState(loading = false)
                return@launch
            }

            // Trace rows are emitted immediately after the durable history row.
            // Observe the existing bounded LogStore so the explanation appears
            // as soon as its structured event is recorded, without parsing the
            // human-readable history message.
            logStore.timeline().collect { timeline ->
                _uiState.value = ExecutionDetailsUiState(
                    loading = false,
                    record = record,
                    explanation = explanationForRecord(record, timeline)
                )
            }
        }
    }
}

/**
 * Correlates a durable history row with the typed trace run that started at
 * the same instant. This deliberately requires an exact timestamp match:
 * guessing across nearby runs could show the wrong diagnosis when a routine
 * fires repeatedly.
 */
internal fun explanationForRecord(
    record: ExecutionRecord,
    timeline: List<ExecutionTimelineEntry>
): RunExplainer.Explanation? {
    if (record.success && !ExecutionOutcomeClassifier.isSkipped(record)) return null

    val anchorAt = timeline
        .firstOrNull { it.id == record.id && it.automationId == record.automationId }
        ?.startedAt
        ?: record.executedAt

    val runId = timeline
        .asSequence()
        .filter { entry ->
            entry.automationId == record.automationId &&
                entry.startedAt == anchorAt &&
                entry.traceRunId != null
        }
        .mapNotNull { it.traceRunId }
        .firstOrNull()
        ?: return null

    return RunExplainer.explainTimeline(timeline, runId)
}
