package com.nexaflow.core.execution

import android.content.Context
import android.os.SystemClock
import com.nexaflow.core.logging.TraceReasons
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.ExecutionRecord
import java.util.UUID

/** Rejects unavailable or false opted-in expressions before execution checkpoints/actions. */
internal suspend fun ExecutionEngine.rejectTriggerExpression(
    context: Context,
    automation: Automation,
    occurrence: TriggerOccurrence?,
    startedAt: Long,
    runId: String,
    channel: String?,
    recordMessagePrefix: String
): ExecutionRecord? {
    val evaluation = triggerExpressionRuntimeEvaluator.evaluate(
        context = context,
        automation = automation,
        occurrence = occurrence,
        elapsedRealtimeMs = SystemClock.elapsedRealtime()
    )
    if (evaluation.result == ConditionResult.Satisfied) return null
    val reason = when {
        evaluation.invalid -> "INVALID_EXPRESSION"
        evaluation.result == ConditionResult.Unknown -> "UNKNOWN"
        evaluation.result == ConditionResult.Unavailable -> "UNAVAILABLE"
        evaluation.result is ConditionResult.Error -> "ERROR"
        else -> "UNSATISFIED"
    }
    val message = (recordMessagePrefix + "Skipped: trigger expression $reason").take(500)
    val record = ExecutionRecord(
        id = UUID.randomUUID().toString(),
        automationId = automation.id,
        automationName = automation.name,
        success = true,
        message = message,
        executedAt = startedAt,
        channel = channel
    )
    // These collaborators are suspendable; this gate already runs from suspend runAutomation.
    if (triggerExpressionSkipReportThrottle.shouldReport(automation.id, "TRIGGER_EXPRESSION:$reason", startedAt)) {
        triggerExpressionHistoryWriter.record(record)
    }
    triggerExpressionDiagnostics.recordTimeline(automation, "TRIGGER_EXPRESSION_BLOCKED", record, startedAt, runId)
    triggerExpressionTraceRecorder.recordGateBlocked(
        runId = runId,
        automationId = automation.id,
        reasonCode = TraceReasons.TRIGGER_ALL_GATE_BLOCKED,
        detail = "EXPRESSION_$reason",
        atEpochMs = startedAt,
    )
    return record
}
