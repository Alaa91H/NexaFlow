package com.nexaflow.core.execution.constraints

import com.nexaflow.core.execution.capability.CapabilityExecutionService
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.VerificationMode
import com.nexaflow.domain.constraints.ConstraintEvaluator
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.ConstraintSnapshot
import com.nexaflow.domain.models.ConstraintType

/**
 * Evaluates an automation's complete constraint set through one gate. Legacy
 * device constraints stay in the pure domain evaluator; external plug-in
 * constraints enter only through the existing capability execution service.
 */
class AutomationConstraintGate(
    private val capabilityExecutionService: CapabilityExecutionService?
) {
    suspend fun evaluate(
        automation: Automation,
        state: ConstraintSnapshot?
    ): ConditionResult {
        val localConstraints = automation.constraints.filter { it.type != ConstraintType.PLUGIN }
        if (localConstraints.isNotEmpty()) {
            val snapshot = state ?: return ConditionResult.Unavailable
            if (!ConstraintEvaluator.allSatisfied(localConstraints, snapshot)) {
                return ConditionResult.Unsatisfied
            }
        }

        val pluginConstraints = automation.constraints.filter { it.type == ConstraintType.PLUGIN }
        for (constraint in pluginConstraints) {
            val instance = constraint.config[KEY_INSTANCE]
                ?: return ConditionResult.Error("Plugin condition instance reference is missing")
            val service = capabilityExecutionService ?: return ConditionResult.Unavailable
            val result = service.execute(
                CapabilityRequest(
                    capability = CapabilityId.PLUGIN_CONDITION_READ,
                    parameters = mapOf(KEY_INSTANCE to instance),
                    verification = VerificationMode.NONE,
                    workflowId = automation.id,
                    actionId = ACTION_ID_CONSTRAINT
                )
            )
            val typed = result.conditionResult ?: when {
                result.errorCode != null -> ConditionResult.Error(result.message)
                else -> ConditionResult.Error("Plugin condition backend returned no typed state")
            }
            if (typed != ConditionResult.Satisfied) return typed
        }
        return ConditionResult.Satisfied
    }

    companion object {
        const val ACTION_ID_CONSTRAINT = "PLUGIN_CONDITION"
        private const val KEY_INSTANCE = "pluginInstance"
    }
}
