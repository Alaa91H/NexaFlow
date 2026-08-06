package com.nexaflow.core.compat

/**
 * Visual tier used by the settings UI to color the execution-channel badge.
 * Mirrors how much power the selected channel actually grants.
 */
enum class ChannelTier {
    /** Full elevated shell / platform power (Root, Shizuku, ADB, System app). */
    ELEVATED,

    /** Standard user-grantable Android APIs only. */
    STANDARD,

    /** Accessibility-service driven control (UI automation, no shell). */
    ACCESSIBILITY,

    /** No usable channel detected. */
    NONE
}

/**
 * Pure snapshot of the runtime-selected execution channel, ready for the
 * settings UI: which [provider] won, which [tier] it maps to (for the badge
 * color), whether it can run shell commands and how many capabilities were
 * detected on the device. No Android types — JVM-testable over a device matrix.
 */
data class ChannelStatus(
    val provider: ExecutionProviderType?,
    val tier: ChannelTier,
    val shellAccess: Boolean,
    val capabilityCount: Int
) {
    companion object {
        /** The neutral state: no usable channel (or detection failed). */
        fun none(capabilityCount: Int = 0) =
            ChannelStatus(
                provider = null,
                tier = ChannelTier.NONE,
                shellAccess = false,
                capabilityCount = capabilityCount
            )
    }
}

/**
 * Maps the runtime-selected [ExecutionProvider] (as picked by
 * [ExecutionChannelSelector.selectFor]) to the [ChannelStatus] the settings
 * screen displays. Pure logic; the UI only turns the result into localized
 * labels and colors. Deriving the status from the provider directly keeps a
 * single source of truth — the caller passes exactly the channel the engine
 * uses, never a freshly-constructed default selector.
 */
object ChannelStatusMapper {

    fun map(provider: ExecutionProvider?, capabilityCount: Int): ChannelStatus {
        val tier = when (provider?.type) {
            ExecutionProviderType.SYSTEM_APP,
            ExecutionProviderType.ROOT,
            ExecutionProviderType.SHIZUKU,
            ExecutionProviderType.ADB -> ChannelTier.ELEVATED
            ExecutionProviderType.ANDROID -> ChannelTier.STANDARD
            ExecutionProviderType.ACCESSIBILITY -> ChannelTier.ACCESSIBILITY
            null -> ChannelTier.NONE
        }
        return ChannelStatus(
            provider = provider?.type,
            tier = tier,
            shellAccess = provider?.hasShellAccess == true,
            capabilityCount = capabilityCount
        )
    }
}
