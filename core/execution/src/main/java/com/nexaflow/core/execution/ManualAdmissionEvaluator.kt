package com.nexaflow.core.execution

import android.content.Context
import com.nexaflow.core.execution.constraints.AutomationConstraintGate
import com.nexaflow.core.execution.constraints.ConstraintStateReader
import com.nexaflow.core.execution.capability.CapabilityExecutionService
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.ConstraintSnapshot
import com.nexaflow.domain.models.requiresTimeRangeForEndBehavior

/**
 * Side-effect-free evaluator for manual task admission.
 *
 * Keeping this logic outside [ExecutionEngine] makes the manual UI policy
 * independently testable and prevents the execution coordinator from growing
 * with every new explanation surface.
 */
internal class ManualAdmissionEvaluator(
    private val context: Context,
    private val capabilityExecutionService: CapabilityExecutionService?,
    private val constraintStateProvider: (() -> ConstraintSnapshot?)?
) {
    suspend fun describe(automation: Automation): ExecutionEngine.ManualBlockReason {
        if (automation.requiresTimeRangeForEndBehavior) {
            return reason(ExecutionEngine.ManualBlockKind.INVALID_TIME_RANGE)
        }

        val triggerResult = TriggerStateEvaluator.evaluateAsync(
            context = context,
            triggers = automation.triggers,
            matchMode = automation.triggerMatch
        )
        val failedTriggers = if (triggerResult == ConditionResult.Satisfied) {
            emptyList()
        } else {
            automation.triggers.filter { trigger ->
                TriggerStateEvaluator.evaluateAsync(context, listOf(trigger)) !=
                    ConditionResult.Satisfied
            }.map(TriggerStateEvaluator::triggerLabel)
        }

        var failedConstraints: List<String> = emptyList()
        var constraintSatisfied = true
        if (automation.constraints.isNotEmpty()) {
            val state = constraintStateProvider?.invoke()
                ?: runCatching { ConstraintStateReader.capture(context) }.getOrNull()
            val result = AutomationConstraintGate(capabilityExecutionService)
                .evaluate(automation, state)
            constraintSatisfied = result == ConditionResult.Satisfied
            if (!constraintSatisfied) {
                failedConstraints = automation.constraints.map { it.type.name }
            }
        }

        return when {
            failedTriggers.isEmpty() && constraintSatisfied ->
                reason(ExecutionEngine.ManualBlockKind.NONE)
            failedTriggers.isNotEmpty() && triggerResult == ConditionResult.Unknown ->
                reason(
                    ExecutionEngine.ManualBlockKind.TRIGGERS_UNKNOWN,
                    failedTriggers = failedTriggers
                )
            failedTriggers.isNotEmpty() ->
                reason(
                    ExecutionEngine.ManualBlockKind.TRIGGERS_NOT_MET,
                    failedTriggers = failedTriggers
                )
            else ->
                reason(
                    ExecutionEngine.ManualBlockKind.CONSTRAINTS_NOT_MET,
                    failedConstraints = failedConstraints
                )
        }
    }

    private fun reason(
        kind: ExecutionEngine.ManualBlockKind,
        failedTriggers: List<String> = emptyList(),
        failedConstraints: List<String> = emptyList()
    ) = ExecutionEngine.ManualBlockReason(
        kind = kind,
        failedTriggerLabels = failedTriggers,
        failedConstraintLabels = failedConstraints
    )
}
