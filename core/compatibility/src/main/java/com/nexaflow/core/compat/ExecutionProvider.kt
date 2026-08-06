package com.nexaflow.core.compat

import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.SystemControlResult

/** The execution channels a provider represents. */
enum class ExecutionProviderType(
    val displayName: String,
    val description: String
) {
    ANDROID(
        "Android API",
        "Standard Android APIs with runtime-grantable permissions only."
    ),
    ACCESSIBILITY(
        "Accessibility",
        "AccessibilityService-driven control (UI automation, gesture input)."
    ),
    SHIZUKU(
        "Shizuku",
        "Elevated commands through the Shizuku service (ADB or root-backed)."
    ),
    ADB(
        "ADB",
        "Wireless debugging shell over the ADB pair port."
    ),
    ROOT(
        "Root",
        "Direct su shell (Magisk / KernelSU / APatch)."
    ),
    SYSTEM_APP(
        "System app",
        "Platform privileges (system / priv-app / platform-signed) with hidden APIs."
    )
}

/**
 * A channel for executing privileged operations. Providers are scored by
 * [ProviderSelector] using availability, capability support and ROM profile;
 * execution itself delegates to the existing runner where applicable.
 */
interface ExecutionProvider {

    val type: ExecutionProviderType

    /** Intrinsic capability tier used by the selector's scoring. */
    val baseScore: Int

    /** Capabilities this provider can satisfy directly. */
    val supportedCapabilities: Set<RomCapability>

    /** True when this provider is usable on [profile]. */
    fun isAvailable(profile: DeviceProfile): Boolean

    /**
     * True when this channel can run raw shell commands. Pure-API channels
     * (Android, Accessibility) return false; shell channels (Shizuku, ADB,
     * Root, System app) return true. Used by the engine to decide whether a
     * shell action can be routed through the runtime-selected provider.
     */
    val hasShellAccess: Boolean
        get() = type == ExecutionProviderType.SHIZUKU ||
            type == ExecutionProviderType.ADB ||
            type == ExecutionProviderType.ROOT ||
            type == ExecutionProviderType.SYSTEM_APP

    /**
     * Executes a shell command through this channel. Providers without shell
     * access (Android, Accessibility) return a clear failure. Root/Shizuku
     * delegate to the privileged runner.
     */
    fun execute(command: String): SystemControlResult
}
