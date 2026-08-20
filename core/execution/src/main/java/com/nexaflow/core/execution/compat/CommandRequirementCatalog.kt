package com.nexaflow.core.execution.compat

import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequirement
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType

/**
 * Capability-engine admission metadata layered on top of [CommandCatalog].
 *
 * `CommandCatalog` remains the source of Android/ROM compatibility. This catalog
 * adds only requirements backed by a registered, typed capability backend. A
 * legacy command marked SHELL, ELEVATED or BRIDGE is deliberately non-admissible
 * here until it is mapped into such a backend; Root/Shizuku presence alone is
 * never promoted into a generic command entitlement.
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
        spec == null -> unsupportedRequirement()
        spec.strategy in UNMAPPED_PRIVILEGED_STRATEGIES -> unsupportedRequirement()
        else -> CapabilityRequirement.None
    }

    /**
     * False by construction, while retaining the existing sealed requirement
     * contract and avoiding a fictitious CapabilityId for unmapped commands.
     */
    private fun unsupportedRequirement(): CapabilityRequirement =
        CapabilityRequirement.Not(CapabilityRequirement.None)

    private val UNMAPPED_PRIVILEGED_STRATEGIES = setOf(
        ExecutionStrategy.SHELL,
        ExecutionStrategy.ELEVATED,
        ExecutionStrategy.BRIDGE,
        ExecutionStrategy.UNSUPPORTED
    )
}
