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
        if (snapshot.neverObserved) {
            // First capability scan hasn't completed yet (startup race): admit and let
            // the real action handler make the live decision, instead of blocking on
            // a snapshot that only looks unsupported because it is still empty.
            return WorkflowCapabilityValidationResult(admissible = true, missingCapabilities = emptySet())
        }
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
