package com.nexaflow.core.execution

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.requiresTimeRangeForEndBehavior
import java.util.UUID

/** Shared orchestration for manual runs that must pass trigger and constraint gates. */
internal object ManualExecutionGate {
    suspend fun run(engine: ExecutionEngine, automation: Automation): ExecutionRecord {
        val startedAt = engine.epochMillis.now()
        if (automation.requiresTimeRangeForEndBehavior) {
            return engine.diagnostics.rejectIncompleteTimeRange(
                automation,
                startedAt,
                WorkflowRunContext.create(automation.id, startedAt).runId,
            )
        }
        if (engine.manualAdmissionEvaluator.describe(automation).kind == ManualBlockKind.NONE) {
            return engine.runAutomation(automation, bypassTriggerMatch = true)
        }
        val blocked = ExecutionRecord(
            id = UUID.randomUUID().toString(),
            automationId = automation.id,
            automationName = automation.name,
            success = true,
            message = "Skipped: manual conditions not satisfied",
            executedAt = startedAt,
        )
        engine.historyWriter.record(blocked)
        engine.diagnostics.recordTimeline(automation, "MANUAL_CONDITION_BLOCKED", blocked, startedAt)
        return blocked
    }
}
