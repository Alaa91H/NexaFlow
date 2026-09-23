package com.nexaflow.core.execution

import android.content.Context
import android.content.Intent
import com.nexaflow.core.common.EpochMillis
import com.nexaflow.core.logging.ExecutionTimelineEntry
import com.nexaflow.core.logging.ExecutionTraceEvent
import com.nexaflow.core.logging.LogStore
import com.nexaflow.core.logging.TracePhase
import com.nexaflow.core.logging.TraceRecorder
import com.nexaflow.core.logging.TraceReasons
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import java.util.UUID

internal class ExecutionDiagnostics(
    private val context: Context,
    private val historyRepository: HistoryRepository,
    private val logStore: LogStore,
    private val epochMillis: EpochMillis,
    private val traceRecorder: TraceRecorder,
) {
    suspend fun recordTimeline(
        automation: Automation,
        kind: String,
        record: ExecutionRecord,
        startedAt: Long,
        runId: String? = null,
    ) {
        try {
            logStore.recordExecution(
                ExecutionTimelineEntry(
                    id = record.id,
                    automationId = automation.id,
                    automationName = automation.name,
                    kind = kind,
                    success = record.success,
                    message = record.message,
                    startedAt = startedAt,
                    durationMs = epochMillis.now() - startedAt,
                    channel = record.channel,
                    runId = runId,
                )
            )
        } catch (_: Throwable) {
            // Diagnostics must never break execution.
        }
    }

    suspend fun rejectIncompleteTimeRange(
        automation: Automation,
        startedAt: Long,
        runId: String,
    ): ExecutionRecord {
        val record = ExecutionRecord(
            id = UUID.randomUUID().toString(),
            automationId = automation.id,
            automationName = automation.name,
            success = false,
            message = "Configuration blocked: end behavior requires a time range with an explicit end time",
            executedAt = startedAt,
        )
        historyRepository.recordExecution(record)
        recordTimeline(automation, "CONFIGURATION_BLOCKED", record, startedAt, runId)
        traceRecorder.recordBlockedRun(
            runId,
            automation.id,
            TraceReasons.CONFIGURATION_BLOCKED,
            "end behavior requires a time range with an explicit end time",
            epochMillis.now(),
        )
        context.sendBroadcast(
            Intent(ACTION_AUTOMATIONS_CHANGED).setPackage(context.packageName)
        )
        return record
    }
}

internal suspend fun TraceRecorder.recordBlockedRun(
    runId: String,
    automationId: String,
    reasonCode: String,
    detail: String,
    atEpochMs: Long,
) {
    recordGateBlocked(runId, automationId, reasonCode, detail, atEpochMs)
}

internal suspend fun TraceRecorder.recordRunOutcome(
    runId: String,
    automationId: String,
    results: List<ActionExecutionResult>,
    backend: String?,
    startedAt: Long,
    completedAt: Long,
) {
    val failedAction = results.firstOrNull { !it.success }
    record(
        ExecutionTraceEvent(
            id = UUID.randomUUID().toString(),
            runId = runId,
            automationId = automationId,
            sequence = 0,
            phase = TracePhase.OUTCOME,
            reasonCode = if (failedAction == null) {
                TraceReasons.RUN_COMPLETED
            } else {
                TraceReasons.ACTION_FAILED
            },
            detail = failedAction?.let { result ->
                (result.actionType + ": " + result.message).take(500)
            },
            backend = backend,
            atEpochMs = completedAt,
            durationMs = completedAt - startedAt,
        )
    )
}
