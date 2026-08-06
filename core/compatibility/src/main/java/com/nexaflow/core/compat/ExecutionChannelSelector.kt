package com.nexaflow.core.compat

import android.content.Context
import com.nexaflow.core.rom.model.RomCapability

/**
 * Production bridge between [DeviceProfileDetector] and [ProviderSelector]:
 * every task run detects the live device profile and picks the best execution
 * channel automatically, with graceful fallback that reports the channel used.
 *
 * The Context-bound entry points ([select]/[execute]) are thin wrappers over
 * the pure, JVM-testable paths ([selectFor]/[executeFor]) that operate on a
 * fixed [DeviceProfile].
 */
class ExecutionChannelSelector(
    private val selector: ProviderSelector = ProviderSelector.default(),
    private val detector: (Context) -> DeviceProfile = DeviceProfileDetector::detect
) {

    /** Best channel for the live device, optionally restricted to [capability]. */
    fun select(context: Context, capability: RomCapability? = null): ExecutionProvider? =
        selectFor(detector(context), capability)

    /**
     * Executes [command] on the live device through the best channel with
     * graceful fallback, reporting which channel ran it.
     */
    fun execute(
        context: Context,
        command: String,
        capability: RomCapability? = null
    ): ProviderExecutionReport = executeFor(detector(context), command, capability)

    // ---- Pure paths (JVM-testable) ----

    fun selectFor(profile: DeviceProfile, capability: RomCapability? = null): ExecutionProvider? =
        selector.bestFor(profile, capability)

    fun executeFor(
        profile: DeviceProfile,
        command: String,
        capability: RomCapability? = null
    ): ProviderExecutionReport = selector.executeWithFallback(profile, command, capability)

    /** Current live profile (for diagnostics and settings UI). */
    fun detect(context: Context): DeviceProfile = detector(context)
}
