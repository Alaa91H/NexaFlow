package com.nexaflow.core.compat

import com.nexaflow.core.rom.PrivilegedRunner
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.SystemControlResult

/** Standard Android APIs — always present, lowest tier. */
object AndroidProvider : ExecutionProvider {
    override val type = ExecutionProviderType.ANDROID
    override val baseScore = 10

    override val supportedCapabilities: Set<RomCapability> = setOf(
        RomCapability.WRITE_SETTINGS,
        RomCapability.SYSTEM_ALERT_WINDOW,
        RomCapability.DND_ACCESS,
        RomCapability.PACKAGE_USAGE_STATS,
        RomCapability.KILL_BACKGROUND_PROCESSES
    )

    override fun isAvailable(profile: DeviceProfile): Boolean = true

    override fun execute(command: String): SystemControlResult =
        SystemControlResult.fail("Android provider has no shell access")
}

/** AccessibilityService-driven control — enabled when the service is running. */
object AccessibilityProvider : ExecutionProvider {
    override val type = ExecutionProviderType.ACCESSIBILITY
    override val baseScore = 30

    // Accessibility grants no shell-level RomCapability on its own; the
    // service is a UI-automation channel, not a privileged-permission grant.
    override val supportedCapabilities: Set<RomCapability> = emptySet()

    override fun isAvailable(profile: DeviceProfile): Boolean = profile.accessibilityEnabled

    override fun execute(command: String): SystemControlResult =
        SystemControlResult.fail("Accessibility provider has no shell access")
}

/** Elevated commands through the Shizuku service. */
object ShizukuProvider : ExecutionProvider {
    override val type = ExecutionProviderType.SHIZUKU
    override val baseScore = 70

    // Mirrors RomCapabilityProvider.isElevated(): ROOT and SHIZUKU both satisfy
    // the ROM-specific hidden-API capabilities on their matching ROM families.
    override val supportedCapabilities: Set<RomCapability> = setOf(
        RomCapability.SHIZUKU,
        RomCapability.WRITE_SECURE_SETTINGS,
        RomCapability.READ_LOGS,
        RomCapability.MODIFY_PHONE_STATE,
        RomCapability.FORCE_STOP_PACKAGES,
        RomCapability.KILL_BACKGROUND_PROCESSES,
        RomCapability.STATUS_BAR_CONTROL,
        RomCapability.LINEAGEOS_SDK,
        RomCapability.LINEAGEOS_HARDWARE,
        RomCapability.MIUI_HIDDEN_API,
        RomCapability.COLOROS_HIDDEN_API,
        RomCapability.ONE_UI_HIDDEN_API,
        RomCapability.OEM_HIDDEN_API
    )

    override fun isAvailable(profile: DeviceProfile): Boolean = profile.shizukuGranted

    override fun execute(command: String): SystemControlResult = PrivilegedRunner.runShizuku(command)
}

/** Wireless-debugging ADB shell. */
object AdbProvider : ExecutionProvider {
    override val type = ExecutionProviderType.ADB
    override val baseScore = 50

    override val supportedCapabilities: Set<RomCapability> = setOf(
        RomCapability.WRITE_SECURE_SETTINGS,
        RomCapability.READ_LOGS,
        RomCapability.MODIFY_PHONE_STATE,
        RomCapability.FORCE_STOP_PACKAGES,
        RomCapability.KILL_BACKGROUND_PROCESSES,
        RomCapability.STATUS_BAR_CONTROL
    )

    override fun isAvailable(profile: DeviceProfile): Boolean = profile.adbConnected

    override fun execute(command: String): SystemControlResult =
        SystemControlResult.fail("ADB execution not yet implemented")
}

/** Direct root shell (su). */
object RootProvider : ExecutionProvider {
    override val type = ExecutionProviderType.ROOT
    override val baseScore = 80

    // Mirrors RomCapabilityProvider.isElevated(): ROOT satisfies the ROM-specific
    // hidden-API capabilities on their matching ROM families.
    override val supportedCapabilities: Set<RomCapability> = setOf(
        RomCapability.ROOT_SHELL,
        RomCapability.WRITE_SECURE_SETTINGS,
        RomCapability.READ_LOGS,
        RomCapability.MODIFY_PHONE_STATE,
        RomCapability.FORCE_STOP_PACKAGES,
        RomCapability.KILL_BACKGROUND_PROCESSES,
        RomCapability.STATUS_BAR_CONTROL,
        RomCapability.LINEAGEOS_SDK,
        RomCapability.LINEAGEOS_HARDWARE,
        RomCapability.MIUI_HIDDEN_API,
        RomCapability.COLOROS_HIDDEN_API,
        RomCapability.ONE_UI_HIDDEN_API,
        RomCapability.OEM_HIDDEN_API
    )

    override fun isAvailable(profile: DeviceProfile): Boolean = profile.rootAvailable

    override fun execute(command: String): SystemControlResult = PrivilegedRunner.runRoot(command)
}

/** System / priv-app / platform-signed app — highest tier. */
object SystemAppProvider : ExecutionProvider {
    override val type = ExecutionProviderType.SYSTEM_APP
    override val baseScore = 100

    override val supportedCapabilities: Set<RomCapability> = setOf(
        RomCapability.WRITE_SECURE_SETTINGS,
        RomCapability.READ_LOGS,
        RomCapability.MODIFY_PHONE_STATE,
        RomCapability.STATUS_BAR_CONTROL,
        RomCapability.FORCE_STOP_PACKAGES,
        RomCapability.KILL_BACKGROUND_PROCESSES,
        RomCapability.LINEAGEOS_SDK,
        RomCapability.LINEAGEOS_HARDWARE,
        RomCapability.MIUI_HIDDEN_API,
        RomCapability.COLOROS_HIDDEN_API,
        RomCapability.ONE_UI_HIDDEN_API,
        RomCapability.OEM_HIDDEN_API
    )

    override fun isAvailable(profile: DeviceProfile): Boolean {
        return profile.integrationLevel == IntegrationLevel.SYSTEM_APP ||
            profile.integrationLevel == IntegrationLevel.PRIVILEGED_SYSTEM_APP ||
            profile.integrationLevel == IntegrationLevel.PLATFORM_SIGNED_SYSTEM_APP
    }

    override fun execute(command: String): SystemControlResult = PrivilegedRunner.runShell(command)
}

/** The built-in provider set, ordered by [ExecutionProviderType]. */
val DEFAULT_PROVIDERS: List<ExecutionProvider> = listOf(
    AndroidProvider,
    AccessibilityProvider,
    AdbProvider,
    ShizukuProvider,
    RootProvider,
    SystemAppProvider
)
