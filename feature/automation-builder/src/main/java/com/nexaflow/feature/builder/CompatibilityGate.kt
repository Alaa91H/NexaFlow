package com.nexaflow.feature.builder

import android.content.Context
import com.nexaflow.core.execution.compat.CommandCompatibilityEngine
import com.nexaflow.core.execution.compat.CommandCatalog
import com.nexaflow.core.execution.compat.CommandRequirementCatalog
import com.nexaflow.core.execution.compat.DeviceProfile
import com.nexaflow.domain.catalog.AutomationNodeCatalog\nimport com.nexaflow.domain.catalog.AutomationNodeVisibility\nimport com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequirement
import com.nexaflow.domain.capability.CapabilityRequirementResolver
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType

/**
 * User-facing executability state for one builder option.
 *
 * The states deliberately distinguish a permission the user can grant from a
 * backend that is merely unavailable and from a capability this device cannot
 * support at all. This keeps the UI honest without coupling it to Root,
 * Shizuku, or Android permission implementation details.
 */
internal enum class BuilderOptionAvailability {
    READY,
    PERMISSION_REQUIRED,
    UNAVAILABLE,
    UNSUPPORTED
}

internal data class BuilderActionOptionState(
    val option: ActionOption,
    val availability: BuilderOptionAvailability,
    val missingCapabilities: Set<CapabilityId>
)

internal data class BuilderTriggerOptionState(
    val type: TriggerType,
    val availability: BuilderOptionAvailability,
    val missingCapabilities: Set<CapabilityId>
)

/**
 * Classifies a declarative requirement without knowing which concrete backend
 * will eventually satisfy it.
 *
 * For `AnyOf`, the best viable route wins (ready > grantable > unavailable >
 * unsupported). For `AllOf`, the weakest required route wins because every
 * child must be executable. `PARTIAL` is deliberately treated as unavailable:
 * capability admission remains fail-closed until a backend reports AVAILABLE.
 */
internal fun classifyBuilderRequirement(
    requirement: CapabilityRequirement,
    snapshot: CapabilitySnapshot
): BuilderOptionAvailability = when (requirement) {
    CapabilityRequirement.None -> BuilderOptionAvailability.READY
    is CapabilityRequirement.Capability -> when (snapshot.availabilityOf(requirement.id)) {
        CapabilityAvailability.AVAILABLE -> BuilderOptionAvailability.READY
        CapabilityAvailability.PERMISSION_REQUIRED -> BuilderOptionAvailability.PERMISSION_REQUIRED
        CapabilityAvailability.PARTIAL,
        CapabilityAvailability.UNAVAILABLE -> BuilderOptionAvailability.UNAVAILABLE
        CapabilityAvailability.UNSUPPORTED -> BuilderOptionAvailability.UNSUPPORTED
    }
    is CapabilityRequirement.AllOf -> requirement.requirements
        .map { classifyBuilderRequirement(it, snapshot) }
        .maxBy(::builderOptionRank)
    is CapabilityRequirement.AnyOf -> requirement.requirements
        .map { classifyBuilderRequirement(it, snapshot) }
        .minBy(::builderOptionRank)
    is CapabilityRequirement.Not -> {
        if (CapabilityRequirementResolver.resolve(requirement, snapshot).available) {
            BuilderOptionAvailability.READY
        } else {
            BuilderOptionAvailability.UNAVAILABLE
        }
    }
}

private fun builderOptionRank(state: BuilderOptionAvailability): Int = when (state) {
    BuilderOptionAvailability.READY -> 0
    BuilderOptionAvailability.PERMISSION_REQUIRED -> 1
    BuilderOptionAvailability.UNAVAILABLE -> 2
    BuilderOptionAvailability.UNSUPPORTED -> 3
}

/**
 * Hidden compatibility gate for the builder UI.
 *
 * Resolves the live [DeviceProfile] once and exposes device-compatible options.
 * The capability-aware state APIs below are the single source of truth for
 * whether each compatible option is ready, needs a permission, is temporarily
 * unavailable, or is unsupported. Selected commands are never removed from an
 * in-progress task — only picker discovery is filtered.
 */
object CompatibilityGate {

    private val engine = CommandCompatibilityEngine()

    /** Live device profile, cached per capture call (cheap; ROM detection is memoized). */
    fun profile(context: Context): DeviceProfile = DeviceProfile.capture(context)

    /**
     * Discoverable action options for this device.
     *
     * Device-unsupported commands (ROM denials, elevated-only without a
     * shell, missing signature permissions, absent hardware) are hidden.
     * Commands whose only obstacle is a user-grantable permission (write
     * settings, DND access) stay discoverable: the UI renders them as
     * locked rows with the grant flow instead of pretending the option
     * does not exist (GitHub issue #5 — "Many options are missing on my
     * phone": the tablet had the grant, the phone did not).
     */
    fun supportedActionOptions(context: Context): List<ActionOption> =
        discoverableActionOptions(context).map { it.first }

    /** Capability-aware state for every discoverable action option. */
    internal fun actionOptionStates(
        context: Context,
        snapshot: CapabilitySnapshot
    ): List<BuilderActionOptionState> = discoverableActionOptions(context).map { (option, requirement) ->
        val resolution = CapabilityRequirementResolver.resolve(requirement, snapshot)
        BuilderActionOptionState(
            option = option,
            availability = classifyBuilderRequirement(requirement, snapshot),
            missingCapabilities = resolution.missingCapabilities
        )
    }

    private fun discoverableActionOptions(
        context: Context
    ): List<Pair<ActionOption, CapabilityRequirement>> {
        val p = profile(context)
        return actionOptions.mapNotNull { option ->
            val definition = AutomationNodeCatalog.definitionFor(option.actionType)
            if (definition.visibility != AutomationNodeVisibility.DISCOVERABLE) return@mapNotNull null
            if (!engine.isSupported(option.actionType, p)) return@mapNotNull null
            option to CommandRequirementCatalog.requirementFor(option.actionType)
        }
    }

    /**
     * Discoverable trigger options for this device. Same contract as
     * [supportedActionOptions]: grantable-permission items stay visible as
     * locked rows; truly unsupported items are hidden.
     */
    fun supportedTriggerOptions(context: Context): List<TriggerType> =
        discoverableTriggerOptions(context).map { it.first }

    /** Capability-aware state for every discoverable trigger option. */
    internal fun triggerOptionStates(
        context: Context,
        snapshot: CapabilitySnapshot
    ): List<BuilderTriggerOptionState> = discoverableTriggerOptions(context).map { (type, requirement) ->
        val resolution = CapabilityRequirementResolver.resolve(requirement, snapshot)
        BuilderTriggerOptionState(
            type = type,
            availability = classifyBuilderRequirement(requirement, snapshot),
            missingCapabilities = resolution.missingCapabilities
        )
    }

    private fun discoverableTriggerOptions(
        context: Context
    ): List<Pair<TriggerType, CapabilityRequirement>> {
        val p = profile(context)
        return triggerTypeOptions.mapNotNull { type ->
            val definition = AutomationNodeCatalog.definitionFor(type)
            if (definition.visibility != AutomationNodeVisibility.DISCOVERABLE) return@mapNotNull null
            if (!engine.isSupported(type, p)) return@mapNotNull null
            type to CommandRequirementCatalog.requirementFor(type)
        }
    }

    /** True when [type] is a unified duplicate hidden from the pickers. */
    fun isHiddenDuplicate(type: ActionType): Boolean = CommandCatalog.isUnifiedAlias(type)

    /** Resolves a persisted duplicate to its canonical command. */
    fun canonical(type: ActionType): ActionType = engine.canonical(type)
}
