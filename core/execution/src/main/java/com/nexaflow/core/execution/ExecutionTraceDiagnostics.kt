package com.nexaflow.core.execution

import com.nexaflow.core.logging.ExecutionTraceEvent
import com.nexaflow.core.logging.TracePhase
import com.nexaflow.core.logging.TraceRecorder
import com.nexaflow.core.logging.TraceReasons
import com.nexaflow.domain.models.ActionExecutionResult
import java.util.UUID

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
            reasonCode = if (failedAction == null) TraceReasons.RUN_COMPLETED else TraceReasons.ACTION_FAILED,
            detail = failedAction?.let { result ->
                (result.actionType + ": " + result.message).take(500)
            },
            backend = backend,
            atEpochMs = completedAt,
            durationMs = completedAt - startedAt,
        )
    )
}
