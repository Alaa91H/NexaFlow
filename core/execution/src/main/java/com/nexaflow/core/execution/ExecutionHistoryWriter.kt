package com.nexaflow.core.execution

import com.nexaflow.core.common.rethrowIfCancellation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository

/** Durable history + non-authoritative listener fan-out. */
internal class ExecutionHistoryWriter(
    private val historyRepository: HistoryRepository,
    private val runListener: AutomationRunListener,
) {
    suspend fun record(record: ExecutionRecord) {
        try {
            historyRepository.recordExecution(record)
        } finally {
            try {
                runListener.onRecord(record)
            } catch (failure: Exception) {
                failure.rethrowIfCancellation()
            }
        }
    }

    suspend fun triggered(automationId: String, runId: String) {
        try {
            runListener.onTriggered(automationId, runId)
        } catch (failure: Exception) {
            failure.rethrowIfCancellation()
        }
    }
}
