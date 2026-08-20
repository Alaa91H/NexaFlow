package com.nexaflow.core.execution.compat

import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequirementResolver
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.models.Automation

/** Pure workflow admission check shared by run and import/preflight callers. */
object WorkflowCapabilityValidator {
    fun validate(
        automation: Automation,
        snapshot: CapabilitySnapshot
    ): WorkflowCapabilityValidationResult {
        val resolutions = buildList {
            automation.triggers.forEach { trigger ->
                add(CapabilityRequirementResolver.resolve(CommandRequirementCatalog.requirementFor(trigger.type), snapshot))
            }
            (automation.actions + automation.exitActions).forEach { action ->
                add(CapabilityRequirementResolver.resolve(CommandRequirementCatalog.requirementFor(action.type), snapshot))
            }
        }
        return WorkflowCapabilityValidationResult(
            admissible = resolutions.all { it.available },
            missingCapabilities = resolutions.flatMapTo(linkedSetOf()) { it.missingCapabilities }
        )
    }
}

data class WorkflowCapabilityValidationResult(
    val admissible: Boolean,
    val missingCapabilities: Set<CapabilityId>
)
