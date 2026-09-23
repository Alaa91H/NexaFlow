package com.nexaflow.core.execution

import android.content.Context
import com.nexaflow.core.execution.capability.CapabilityExecutionService
import com.nexaflow.core.execution.constraints.AutomationConstraintGate
import com.nexaflow.core.execution.constraints.ConstraintStateReader
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.ConstraintSnapshot
import com.nexaflow.domain.models.requiresTimeRangeForEndBehavior

data class ManualBlockReason(
    val kind: ManualBlockKind,
    val failedTriggerLabels: List<String>,
    val failedConstraintLabels: List<String>
)

enum class ManualBlockKind {
    NONE,
    TRIGGERS_NOT_MET,
    TRIGGERS_UNKNOWN,
    CONSTRAINTS_NOT_MET,
    INVALID_TIME_RANGE
}

/**
 * Side-effect-free live condition snapshot used by the task details screen.
 * Lists preserve the automation's saved order so UI rows can pair by index.
 */
data class ManualAdmissionDiagnostics(
    val triggerResults: List<ConditionResult>,
    val constraintResults: List<ConditionResult>,
    val triggerGateResult: ConditionResult,
    val constraintGateResult: ConditionResult,
    val evaluatedAt: Long
) {
    val admissible: Boolean
        get() = triggerGateResult == ConditionResult.Satisfied &&
            constraintGateResult == ConditionResult.Satisfied
}

/**
 * Side-effect-free evaluator for manual task admission and diagnostics.
 */
internal class ManualAdmissionEvaluator(
    private val context: Context,
    private val capabilityExecutionService: CapabilityExecutionService?,
    private val constraintStateProvider: (() -> ConstraintSnapshot?)?,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun diagnostics(automation: Automation): ManualAdmissionDiagnostics {
        val triggerResults = automation.triggers.map { trigger ->
            runCatching {
                TriggerStateEvaluator.evaluateTriggerState(context, trigger)
            }.getOrElse { ConditionResult.Error(it.message ?: "Trigger state unreadable") }
        }
        val triggerGateResult = TriggerStateEvaluator.aggregate(
            triggerResults,
            automation.triggerMatch
        )

        val state = if (automation.constraints.isEmpty()) {
            null
        } else {
            constraintStateProvider?.invoke()
                ?: runCatching { ConstraintStateReader.capture(context) }.getOrNull()
        }
        val constraintResults = AutomationConstraintGate(capabilityExecutionService)
            .evaluateEach(automation, state)
        val constraintGateResult = aggregateConstraints(constraintResults)

        return ManualAdmissionDiagnostics(
            triggerResults = triggerResults,
            constraintResults = constraintResults,
            triggerGateResult = triggerGateResult,
            constraintGateResult = constraintGateResult,
            evaluatedAt = nowMs()
        )
    }

    suspend fun describe(automation: Automation): ManualBlockReason {
        if (automation.requiresTimeRangeForEndBehavior) {
            return reason(ManualBlockKind.INVALID_TIME_RANGE)
        }

        val snapshot = diagnostics(automation)
        val failedTriggers = automation.triggers.zip(snapshot.triggerResults)
            .filter { (_, result) -> result != ConditionResult.Satisfied }
            .map { (trigger, _) -> TriggerStateEvaluator.triggerLabel(trigger) }
        val failedConstraints = automation.constraints.zip(snapshot.constraintResults)
            .filter { (_, result) -> result != ConditionResult.Satisfied }
            .map { (constraint, _) -> constraint.type.name }

        return when {
            snapshot.admissible -> reason(ManualBlockKind.NONE)
            failedTriggers.isNotEmpty() &&
                snapshot.triggerGateResult == ConditionResult.Unknown ->
                reason(ManualBlockKind.TRIGGERS_UNKNOWN, failedTriggers = failedTriggers)
            failedTriggers.isNotEmpty() ->
                reason(ManualBlockKind.TRIGGERS_NOT_MET, failedTriggers = failedTriggers)
            else ->
                reason(
                    ManualBlockKind.CONSTRAINTS_NOT_MET,
                    failedConstraints = failedConstraints
                )
        }
    }

    private fun aggregateConstraints(results: List<ConditionResult>): ConditionResult = when {
        results.isEmpty() -> ConditionResult.Satisfied
        results.any { it == ConditionResult.Unsatisfied } -> ConditionResult.Unsatisfied
        else -> results.firstOrNull { it != ConditionResult.Satisfied }
            ?: ConditionResult.Satisfied
    }

    private fun reason(
        kind: ManualBlockKind,
        failedTriggers: List<String> = emptyList(),
        failedConstraints: List<String> = emptyList()
    ) = ManualBlockReason(
        kind = kind,
        failedTriggerLabels = failedTriggers,
        failedConstraintLabels = failedConstraints
    )
}
