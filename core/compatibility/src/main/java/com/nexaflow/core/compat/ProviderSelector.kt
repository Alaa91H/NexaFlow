package com.nexaflow.core.compat

import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.core.rom.model.SystemControlResult

/**
 * Scores available [ExecutionProvider]s for a [DeviceProfile] and picks the
 * best automatically, generalizing the legacy `tryPrivileged` fallback chain.
 *
 * Scoring combines three signals (per the Phase-5 spec):
 *  - **Availability** — providers not usable on the profile are excluded.
 *  - **Capability** — when a specific [RomCapability] is requested, providers
 *    that cannot satisfy it are excluded.
 *  - **ROM profile** — a provider gets a bonus on ROM families where its
 *    channel is the natural fit (e.g. system-app privileges on custom ROMs).
 *
 * [executeWithFallback] walks the ranked providers and returns the first
 * successful result — the graceful-degradation path (Shizuku → root → ...).
 */
class ProviderSelector(
    private val providers: List<ExecutionProvider>
) {

    /** All providers usable on [profile], ranked best-first. */
    fun rankedFor(profile: DeviceProfile): List<ExecutionProvider> {
        return providers
            .filter { it.isAvailable(profile) }
            .sortedWith(compareByDescending<ExecutionProvider> { score(it, profile) }
                .thenBy { it.type.ordinal })
    }

    /** Best provider for [profile], optionally restricted to a [capability]. */
    fun bestFor(profile: DeviceProfile, capability: RomCapability? = null): ExecutionProvider? {
        return rankedFor(profile).firstOrNull { provider ->
            capability == null || capability in provider.supportedCapabilities
        }
    }

    /** Best provider supporting every one of [capabilities]. */
    fun bestForAll(profile: DeviceProfile, capabilities: Set<RomCapability>): ExecutionProvider? {
        if (capabilities.isEmpty()) return bestFor(profile)
        return rankedFor(profile).firstOrNull { provider ->
            capabilities.all { it in provider.supportedCapabilities }
        }
    }

    /**
     * Executes [command] through the ranked providers, returning the first
     * successful result (graceful fallback) as a [ProviderExecutionReport] that
     * names the [ProviderExecutionReport.channel] that ran it and the full
     * fallback chain that was walked. When [capability] is given, only
     * providers that can satisfy it are tried.
     */
    fun executeWithFallback(
        profile: DeviceProfile,
        command: String,
        capability: RomCapability? = null
    ): ProviderExecutionReport {
        val ranked = rankedFor(profile).filter {
            capability == null || capability in it.supportedCapabilities
        }
        val attempted = mutableListOf<ExecutionProviderType>()
        for (provider in ranked) {
            val result = provider.execute(command)
            if (result.success) {
                return ProviderExecutionReport.ok(
                    message = result.message,
                    channel = provider.type,
                    attemptedChannels = attempted + provider.type
                )
            }
            attempted += provider.type
        }
        return ProviderExecutionReport.fail(
            message = if (ranked.isEmpty()) {
                "No execution provider supports this capability"
            } else {
                "No execution provider could run the command"
            },
            attemptedChannels = attempted
        )
    }

    /**
     * Combined score: intrinsic tier + ROM-profile fit + detected-capability
     * match. The capability signal uses the profile's *detected* capabilities
     * (from [DeviceProfileDetector]) intersected with what the provider can
     * satisfy — a provider whose channel is already proven on this device is
     * preferred over an equally-scored rival.
     */
    fun score(provider: ExecutionProvider, profile: DeviceProfile): Int {
        val capabilityBonus = profile.capabilities.intersect(provider.supportedCapabilities).size * 2
        return provider.baseScore + romBonus(provider.type, profile.romFamily) + capabilityBonus
    }

    private fun romBonus(type: ExecutionProviderType, family: RomFamily): Int {
        return when (family) {
            // Custom ROMs with full privileged SDKs reward system-app integration.
            RomFamily.LINEAGE_OS,
            RomFamily.CR_DROID,
            RomFamily.EVOLUTION_X,
            RomFamily.PIXEL_EXPERIENCE,
            RomFamily.PARANOID_ANDROID -> if (type == ExecutionProviderType.SYSTEM_APP) 15 else 0
            // OEM skins expose hidden APIs to system apps.
            RomFamily.HYPER_OS,
            RomFamily.MIUI,
            RomFamily.COLOR_OS,
            RomFamily.OXYGEN_OS,
            RomFamily.ONE_UI -> if (type == ExecutionProviderType.SYSTEM_APP) 10 else 0
            else -> 0
        }
    }

    companion object {
        /** The selector over the built-in provider set. */
        fun default(): ProviderSelector = ProviderSelector(DEFAULT_PROVIDERS)
    }
}
