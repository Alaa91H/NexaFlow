package com.nexaflow.feature.builder

import android.content.Context
import com.nexaflow.core.execution.compat.CommandCompatibilityEngine
import com.nexaflow.core.execution.compat.CommandCatalog
import com.nexaflow.core.execution.compat.CommandRequirementCatalog
import com.nexaflow.core.execution.compat.DeviceProfile
import com.nexaflow.domain.capability.CapabilityRequirementResolver
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType

/**
 * Hidden compatibility gate for the builder UI.
 *
 * Resolves the live [DeviceProfile] once and exposes the command lists that
 * can actually execute on this device, exactly as the compatibility engine
 * dictates. Commands with no viable path (and unified duplicates) are dropped
 * so they appear as if they never existed. Selected commands are never removed
 * from an in-progress task — only the picker lists are filtered.
 */
object CompatibilityGate {

    private val engine = CommandCompatibilityEngine()

    /** Live device profile, cached per capture call (cheap; ROM detection is memoized). */
    fun profile(context: Context): DeviceProfile = DeviceProfile.capture(context)

    /** Action options that can run on this device (duplicates + unsupported hidden). */
    fun supportedActionOptions(context: Context): List<ActionOption> {
        val p = profile(context)
        return actionOptions.filter { engine.isSupported(it.actionType, p) }
    }

    /**
     * Snapshot-aware action options. A command must pass both the existing
     * Android/ROM compatibility gate and its concrete capability requirement;
     * unavailable options are omitted rather than rendered disabled.
     */
    fun supportedActionOptions(
        context: Context,
        snapshot: CapabilitySnapshot
    ): List<ActionOption> {
        val p = profile(context)
        return actionOptions.filter { option ->
            engine.isSupported(option.actionType, p) &&
                CapabilityRequirementResolver.resolve(
                    CommandRequirementCatalog.requirementFor(option.actionType),
                    snapshot
                ).available
        }
    }

    /** Trigger options that can run on this device. */
    fun supportedTriggerOptions(context: Context): List<TriggerType> {
        val p = profile(context)
        return triggerTypeOptions.filter { engine.isSupported(it, p) }
    }

    /** Snapshot-aware trigger options; unsupported entries are not rendered. */
    fun supportedTriggerOptions(
        context: Context,
        snapshot: CapabilitySnapshot
    ): List<TriggerType> {
        val p = profile(context)
        return triggerTypeOptions.filter { type ->
            engine.isSupported(type, p) &&
                CapabilityRequirementResolver.resolve(
                    CommandRequirementCatalog.requirementFor(type),
                    snapshot
                ).available
        }
    }

    /** True when [type] is a unified duplicate hidden from the pickers. */
    fun isHiddenDuplicate(type: ActionType): Boolean = CommandCatalog.isUnifiedAlias(type)

    /** Resolves a persisted duplicate to its canonical command. */
    fun canonical(type: ActionType): ActionType = engine.canonical(type)
}
