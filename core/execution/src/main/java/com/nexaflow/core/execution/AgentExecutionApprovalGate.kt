package com.nexaflow.core.execution

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.requiresTimeRangeForEndBehavior
import java.util.UUID

/** Keeps agent-specific approval admission out of the lifecycle engine. */
internal object AgentExecutionApprovalGate {
    suspend fun isApproved(engine: ExecutionEngine, automation: Automation): Boolean =
        engine.agentApprovalValidator?.isApproved(automation) == true

    suspend fun run(engine: ExecutionEngine, automation: Automation): ExecutionRecord {
        if (!isApproved(engine, automation)) {
            val startedAt = engine.epochMillis.now()
            val record = ExecutionRecord(
                id = UUID.randomUUID().toString(),
                automationId = automation.id,
                automationName = automation.name,
                success = false,
                message = "Blocked: agent approval is missing or no longer matches this task",
                executedAt = startedAt,
            )
            engine.historyWriter.record(record)
            engine.diagnostics.recordTimeline(automation, "AGENT_APPROVAL_REJECTED", record, startedAt)
            return record
        }
        if (automation.requiresTimeRangeForEndBehavior) {
            val startedAt = engine.epochMillis.now()
            return engine.diagnostics.rejectIncompleteTimeRange(
                automation,
                startedAt,
                WorkflowRunContext.create(automation.id, startedAt).runId,
            )
        }
        return if (engine.manualAdmissionEvaluator.describe(automation).kind != ManualBlockKind.NONE) {
            engine.runWithConditionGate(automation)
        } else {
            engine.runAutomation(automation, bypassTriggerMatch = true, agentOrigin = true)
        }
    }
}
