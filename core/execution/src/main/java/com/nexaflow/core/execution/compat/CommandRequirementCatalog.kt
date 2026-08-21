package com.nexaflow.core.execution.compat

import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequirement
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType

/**
 * Capability-engine admission metadata layered on top of [CommandCatalog].
 *
 * `CommandCatalog` remains the source of Android/ROM compatibility. This catalog
 * adds only requirements backed by a registered, typed capability backend.
 * Legacy commands continue through their existing handlers, where the concrete
 * runner verifies Root, Shizuku or Android privilege at execution time. They
 * must not be rejected here merely because they have no capability-engine map.
 */
object CommandRequirementCatalog {
    fun requirementFor(type: ActionType): CapabilityRequirement = when (type) {
        // These two ActionTypes already pass through CapabilityActionMapper and
        // therefore have a semantics-preserving registered backend.
        ActionType.SYSTEM_OPEN_URL -> CapabilityRequirement.Capability(CapabilityId.INTENT_LAUNCH)
        ActionType.SYSTEM_OPEN_SETTINGS -> CapabilityRequirement.Capability(CapabilityId.SETTINGS_LAUNCH)

        // No documented typed backend can automate app-store updates. Hiding
        // avoids promoting the legacy launcher label into a completion claim.
        ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS -> unsupportedRequirement()

        else -> requirementForSpec(CommandCatalog.specFor(type))
    }

    fun requirementFor(type: TriggerType): CapabilityRequirement =
        requirementForSpec(CommandCatalog.specFor(type))

    private fun requirementForSpec(spec: CommandSpec?): CapabilityRequirement = when {
        // No catalog entry means no execution path has been documented.
        spec == null -> unsupportedRequirement()
        // This is an explicit product-level exclusion, not a missing privilege.
        spec.strategy == ExecutionStrategy.UNSUPPORTED -> unsupportedRequirement()
        // DIRECT, BRIDGE, SHELL and ELEVATED commands retain their existing
        // handler paths. The handler/runner verifies the concrete privilege at
        // dispatch time, so a static capability snapshot cannot reject them.
        else -> CapabilityRequirement.None
    }

    /**
     * False by construction, while retaining the existing sealed requirement
     * contract and avoiding a fictitious CapabilityId for unmapped commands.
     */
    private fun unsupportedRequirement(): CapabilityRequirement =
        CapabilityRequirement.Not(CapabilityRequirement.None)

}
