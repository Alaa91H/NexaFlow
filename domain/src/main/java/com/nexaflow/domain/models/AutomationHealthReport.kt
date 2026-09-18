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
        val skipped = relevant.count(ExecutionOutcomeClassifier::isSkipped)
        val failed = relevant.count { ExecutionOutcomeClassifier.classify(it) == ExecutionHistoryOutcome.FAILED }
        val completed = relevant.count { it.success && !ExecutionOutcomeClassifier.isSkipped(it) }
        val consecutiveFailures = relevant.takeWhile {
            ExecutionOutcomeClassifier.classify(it) == ExecutionHistoryOutcome.FAILED
        }.size
        val latestFailure = relevant.firstOrNull {
            ExecutionOutcomeClassifier.classify(it) == ExecutionHistoryOutcome.FAILED
        }?.message
        // A skip does not prove that previously blocked recovery work was resolved.
        // Only a later admitted run supersedes the last recovery deferral.
        val recoveryPending = relevant.firstOrNull {
            ExecutionOutcomeClassifier.awaitsRecoveryReview(it.message) ||
                !ExecutionOutcomeClassifier.isSkipped(it)
        }?.let { ExecutionOutcomeClassifier.awaitsRecoveryReview(it.message) } == true
        return AutomationHealthReport(
            automationId = automationId,
            lastExecutionAt = relevant.firstOrNull()?.executedAt,
            completedRuns = completed,
            skippedRuns = skipped,
            failedRuns = failed,
            consecutiveFailures = consecutiveFailures,
            latestFailureMessage = latestFailure,
            status = when {
                relevant.isEmpty() -> AutomationHealthStatus.NO_EXECUTIONS
                recoveryPending -> AutomationHealthStatus.NEEDS_ATTENTION
                consecutiveFailures >= REPEATED_FAILURE_THRESHOLD -> AutomationHealthStatus.NEEDS_ATTENTION
                else -> AutomationHealthStatus.HEALTHY
            },
            recoveryReviewPending = recoveryPending
        )
    }

}
