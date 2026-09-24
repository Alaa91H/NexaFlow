package com.nexaflow.core.execution.compat

import android.os.Build
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.capability.ExecutionRequirementResolver
import com.nexaflow.domain.capability.ExecutionRequirementState
import com.nexaflow.domain.capability.PrivilegeRequirementRef
import com.nexaflow.domain.capability.PrivilegeSnapshot
import com.nexaflow.domain.models.Automation

/**
 * Whole-workflow admission check shared by runtime and preflight callers.
 *
 * UNKNOWN is admitted rather than converted into a denial: startup snapshots
 * can legitimately be unobserved. BLOCKED is emitted only when the unified
 * requirement graph has completed observations proving that no declared path
 * can satisfy a requirement.
 */
object WorkflowCapabilityValidator {
    fun validate(
        automation: Automation,
        capabilitySnapshot: CapabilitySnapshot,
        privilegeSnapshot: PrivilegeSnapshot = PrivilegeSnapshot(),
        sdk: Int = Build.VERSION.SDK_INT.takeIf { it > 0 } ?: 37
    ): WorkflowCapabilityValidationResult {
        val plan = WorkflowRequirementCatalog.plan(automation, sdk)
        val resolution = ExecutionRequirementResolver.resolve(
            plan.requirement,
            capabilitySnapshot,
            privilegeSnapshot
        )
        val entryResolutions = plan.resolveEntries(
            capabilitySnapshot = capabilitySnapshot,
            privilegeSnapshot = privilegeSnapshot
        )
        return WorkflowCapabilityValidationResult(
            admissible = resolution.state != ExecutionRequirementState.BLOCKED,
            state = resolution.state,
            missingCapabilities = resolution.missingCapabilities,
            missingPrivileges = resolution.missingPrivileges,
            unknownCapabilities = resolution.unknownCapabilities,
            unknownPrivileges = resolution.unknownPrivileges,
            blockedOwners = entryResolutions
                .filter { it.resolution.state == ExecutionRequirementState.BLOCKED }
                .mapTo(linkedSetOf()) { it.owner },
            unknownOwners = entryResolutions
                .filter { it.resolution.state == ExecutionRequirementState.UNKNOWN }
                .mapTo(linkedSetOf()) { it.owner }
        )
    }
}

data class WorkflowCapabilityValidationResult(
    val admissible: Boolean,
    val missingCapabilities: Set<CapabilityId>,
    val missingPrivileges: Set<PrivilegeRequirementRef> = emptySet(),
    val state: ExecutionRequirementState =
        if (admissible) ExecutionRequirementState.READY else ExecutionRequirementState.BLOCKED,
    val unknownCapabilities: Set<CapabilityId> = emptySet(),
    val unknownPrivileges: Set<PrivilegeRequirementRef> = emptySet(),
    val blockedOwners: Set<String> = emptySet(),
    val unknownOwners: Set<String> = emptySet()
)
