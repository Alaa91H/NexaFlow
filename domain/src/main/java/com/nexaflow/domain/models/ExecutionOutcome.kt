package com.nexaflow.domain.models

/**
 * Explicit outcomes that can be selected when inspecting locally persisted
 * execution history. A null selection means that history stays unfiltered.
 */
enum class ExecutionHistoryOutcome(val routeValue: String) {
    FAILED("failed"),
    SKIPPED("skipped");

    companion object {
        fun fromRoute(value: String?): ExecutionHistoryOutcome? =
            entries.firstOrNull { it.routeValue.equals(value?.trim(), ignoreCase = true) }
    }
}

/**
 * Classifies legacy execution records without a schema migration. A skipped
 * execution is deliberately successful: no action side effect should be
 * reported as an error merely because an occurrence or condition was not ready.
 */
object ExecutionOutcomeClassifier {
    const val SKIPPED_MESSAGE_PREFIX = "Skipped:"

    fun isSkipped(record: ExecutionRecord): Boolean =
        record.success && record.message.startsWith(SKIPPED_MESSAGE_PREFIX)

    fun classify(record: ExecutionRecord): ExecutionHistoryOutcome? = when {
        !record.success -> ExecutionHistoryOutcome.FAILED
        isSkipped(record) -> ExecutionHistoryOutcome.SKIPPED
        else -> null
    }
}
