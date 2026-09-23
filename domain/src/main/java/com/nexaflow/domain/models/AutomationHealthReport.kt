package com.nexaflow.domain.models

/**
 * Read-only health summary derived from persisted execution history. A skipped
 * run is never counted as a completed maintenance success, and the model never
 * invents a time-saved estimate.
 */
data class AutomationHealthReport(
    val automationId: String,
    val lastExecutionAt: Long?,
    val completedRuns: Int,
    val skippedRuns: Int,
    val failedRuns: Int,
    val consecutiveFailures: Int,
    val latestFailureMessage: String?,
    /** Full local record retained so UI can localize the failure safely. */
    val latestFailureRecord: ExecutionRecord? = null,
    val status: AutomationHealthStatus,
    val recoveryReviewPending: Boolean = false
)

enum class AutomationHealthStatus {
    NO_EXECUTIONS,
    HEALTHY,
    NEEDS_ATTENTION
}

object AutomationHealthAnalyzer {
    const val REPEATED_FAILURE_THRESHOLD = 3

    fun analyze(automationId: String, records: List<ExecutionRecord>): AutomationHealthReport {
        val relevant = records
            .asSequence()
            .filter { it.automationId == automationId }
            .sortedByDescending { it.executedAt }
            .toList()
        // Health counts skip episodes, not raw monitor callbacks. Hundreds of
        // identical consecutive gate evaluations represent one blocked state,
        // so collapse them until a different outcome/reason breaks the episode.
        val skipped = countSkipEpisodes(relevant)
        val failed = relevant.count { ExecutionOutcomeClassifier.classify(it) == ExecutionHistoryOutcome.FAILED }
        val completed = relevant.count { it.success && !ExecutionOutcomeClassifier.isSkipped(it) }
        val consecutiveFailures = relevant.takeWhile {
            ExecutionOutcomeClassifier.classify(it) == ExecutionHistoryOutcome.FAILED
        }.size
        val latestFailureRecord = relevant.firstOrNull {
            ExecutionOutcomeClassifier.classify(it) == ExecutionHistoryOutcome.FAILED
        }
        val latestFailure = latestFailureRecord?.message
        // Recovery state is durable engine state, not history-derived state.
        // The UI overlays the live checkpoint-ledger answer on this history
        // projection so stale legacy messages can never keep a task red forever.
        return AutomationHealthReport(
            automationId = automationId,
            lastExecutionAt = relevant.firstOrNull()?.executedAt,
            completedRuns = completed,
            skippedRuns = skipped,
            failedRuns = failed,
            consecutiveFailures = consecutiveFailures,
            latestFailureMessage = latestFailure,
            latestFailureRecord = latestFailureRecord,
            status = when {
                relevant.isEmpty() -> AutomationHealthStatus.NO_EXECUTIONS
                consecutiveFailures >= REPEATED_FAILURE_THRESHOLD -> AutomationHealthStatus.NEEDS_ATTENTION
                else -> AutomationHealthStatus.HEALTHY
            },
            recoveryReviewPending = false
        )
    }

    private fun countSkipEpisodes(records: List<ExecutionRecord>): Int {
        var episodes = 0
        var previousSkipReason: String? = null
        records.forEach { record ->
            if (ExecutionOutcomeClassifier.isSkipped(record)) {
                if (record.message != previousSkipReason) episodes++
                previousSkipReason = record.message
            } else {
                previousSkipReason = null
            }
        }
        return episodes
    }

}
