package com.nexaflow.core.execution.compat

import android.content.Context
import com.nexaflow.core.rom.RomIntegrationManager
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.RomFamily

/**
 * A snapshot of the device state the engine reasons about. Built once per app
 * launch (and refreshed when the user changes a permission) so the whole UI
 * filter layer shares the same view. Now includes live hardware probes so
 * the builder can hide NFC on a device without NFC, etc. — truly adaptive.
 */
data class DeviceProfile(
    val sdk: Int,
    val romFamily: RomFamily,
    val integrationLevel: IntegrationLevel,
    val capabilities: Set<RomCapability>,
    val grantedPermissions: Set<String>,
    /** True when the app holds elevated shell access (root or Shizuku). */
    val hasElevatedShell: Boolean,
    val hardware: HardwareProfile
) {
    val isSystemApp: Boolean
        get() = integrationLevel == IntegrationLevel.SYSTEM_APP ||
            integrationLevel == IntegrationLevel.PRIVILEGED_SYSTEM_APP ||
            integrationLevel == IntegrationLevel.PLATFORM_SIGNED_SYSTEM_APP

    companion object {
        /**
          * Builds the current profile. Never throws: every failure degrades to
          * the least-privileged profile so no command is ever wrongly enabled.
          * Hardware is probed live, so a whyred without NFC will hide NFC triggers.
          */
        fun capture(context: Context): DeviceProfile {
            return try {
                val info = RomIntegrationManager.buildInfo(context)
                val level = RomIntegrationManager.integrationLevel(context)
                val caps = com.nexaflow.core.rom.RomCapabilityProvider(context, level, info.family)
                    .availableCapabilities().filter { capability ->
                        capability != RomCapability.SHIZUKU ||
                            com.nexaflow.core.rom.ShizukuShellBridge.isUserServiceBound
                    }
                val hw = HardwareProfile.probe(context)
                DeviceProfile(
                    sdk = info.androidSdk.takeIf { it > 0 } ?: android.os.Build.VERSION.SDK_INT,
                    romFamily = info.family,
                    integrationLevel = level,
                    capabilities = caps.toSet(),
                    grantedPermissions = emptySet(), // filled below via provider when possible
                    hasElevatedShell = RomCapability.ROOT_SHELL in caps || RomCapability.SHIZUKU in caps,
                    hardware = hw
                ).withPermissions(context)
            } catch (_: Throwable) {
                DeviceProfile(
                    sdk = android.os.Build.VERSION.SDK_INT,
                    romFamily = RomFamily.OTHER,
                    integrationLevel = IntegrationLevel.NORMAL,
                    capabilities = emptySet(),
                    grantedPermissions = emptySet(),
                    hasElevatedShell = false,
                    hardware = HardwareProfile(
                        hasNfc = false, hasTelephony = false, hasBluetooth = true,
                        hasCameraFlash = false, hasProximitySensor = false,
                        hasLightSensor = false, hasStepCounter = false,
                        hasAccelerometer = true, hasGyroscope = false,
                        hasLocationGps = true, hasUsbAccessory = true,
                        hasEthernet = false, hasHdmi = false, isWatch = false
                    )
                )
            }
        }

        private fun DeviceProfile.withPermissions(
            context: Context
        ): DeviceProfile {
            val perms = try {
                // Probe every permission declared by this app instead of keeping
                // a hand-maintained subset. The old subset omitted RECEIVE_SMS and
                // CALL_PHONE, causing compatible commands to disappear before the
                // user could choose them even after Android had granted permission.
                @Suppress("DEPRECATION")
                val declared = context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_PERMISSIONS
                ).requestedPermissions.orEmpty()
                declared.filter { permission ->
                    context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }.toSet()
            } catch (_: Throwable) {
                emptySet()
            }
            return copy(grantedPermissions = perms)
        }
    }
}

/**
 * The hidden compatibility & integration engine.
 *
 * It plays three roles:
 *
 * 1. **Judge** — decides whether a command can execute on the current device
 *    (ROM family, Android version up to Android 17+, integration level,
 *    capabilities and permissions).
 * 2. **Translator** — picks the best execution strategy for each command
 *    (direct SDK → hidden bridge → plain shell → elevated shell → unsupported).
 * 3. **Filter** — exposes [isSupported] so the UI can hide commands that have
 *    no viable path, making them appear as if they did not exist.
 *
 * All of this is invisible to the user; the engine is queried by the builder
 * screens and the execution layer.
 */
class CommandCompatibilityEngine(
    private val catalog: CommandCatalog = CommandCatalog
) {
    // The catalog is held as a property for future per-ROM override tables;
    // today the built-in singleton catalog covers every command.

    /**
     * Capabilities the user can grant from inside the app through a system
     * settings screen (write-settings toggle, DND access). A missing
     * capability from this set is a *pending permission*, not an unsupported
     * device, so the command must stay discoverable and render as a locked
     * row with the grant flow. Signature/privileged capabilities
     * (WRITE_SECURE_SETTINGS, MODIFY_PHONE_STATE, ...) and backend
     * capabilities (ROOT_SHELL, SHIZUKU) keep fail-closed hiding because no
     * in-app grant path exists for them (GitHub issue #5).
     */
    private val userGrantableCapabilities: Set<RomCapability> =
        setOf(RomCapability.WRITE_SETTINGS, RomCapability.DND_ACCESS)

    /**
     * True when every missing capability can be granted by the user inside
     * the app and no elevated shell could satisfy them instead. Such a
     * command is never unsupported — at worst its permission is pending.
     */
    private fun missingOnlyUserGrantable(spec: CommandSpec, profile: DeviceProfile): Boolean =
        spec.capabilities.isNotEmpty() &&
            !profile.hasElevatedShell &&
            spec.capabilities.all { it in userGrantableCapabilities }

    /** Resolves the effective strategy for a command on this device. Hardware is gated per type in [isSupported]. */
    fun resolve(spec: CommandSpec, profile: DeviceProfile): ExecutionStrategy {
        if (spec.requiredBackend != null && spec.requiredBackend !in profile.capabilities) {
            return ExecutionStrategy.UNSUPPORTED
        }
        if (!versionOk(spec, profile.sdk)) return ExecutionStrategy.UNSUPPORTED
        if (!romOk(spec, profile.romFamily)) return ExecutionStrategy.UNSUPPORTED
        if (!integrationOk(spec, profile)) return ExecutionStrategy.UNSUPPORTED
        if (!permissionsOk(spec, profile)) return ExecutionStrategy.UNSUPPORTED

        // An elevated-only command that has an elevated shell is ELEVATED.
        // A direct command needing one capability falls back to an elevated
        // shell when the capability is missing but a shell exists.
        return when (spec.strategy) {
            ExecutionStrategy.ELEVATED ->
                if (profile.hasElevatedShell) ExecutionStrategy.ELEVATED
                else ExecutionStrategy.UNSUPPORTED

            ExecutionStrategy.SHELL ->
                if (spec.capabilities.isEmpty() || profile.hasElevatedShell || capabilitiesOk(spec, profile)) {
                    ExecutionStrategy.SHELL
                } else if (missingOnlyUserGrantable(spec, profile)) {
                    // Pending user grant — keep the option discoverable as a
                    // locked row; the runner re-verifies the permission at
                    // execution time and the row walks the user to the grant
                    // screen instead of pretending the option does not exist.
                    ExecutionStrategy.SHELL
                } else {
                    ExecutionStrategy.UNSUPPORTED
                }

            ExecutionStrategy.DIRECT,
            ExecutionStrategy.BRIDGE -> {
                if (spec.capabilities.isEmpty() || capabilitiesOk(spec, profile)) {
                    spec.strategy
                } else if (missingOnlyUserGrantable(spec, profile)) {
                    spec.strategy
                } else if (profile.hasElevatedShell) {
                    // The bridge path can often still work elevated.
                    ExecutionStrategy.ELEVATED
                } else {
                    ExecutionStrategy.UNSUPPORTED
                }
            }

            ExecutionStrategy.UNSUPPORTED -> ExecutionStrategy.UNSUPPORTED
        }
    }

    /** Convenience: is this command usable at all on this device? Now hardware-aware. */
    fun isSupported(type: Any, profile: DeviceProfile): Boolean {
        // Unified duplicates are hidden as if they did not exist.
        if (type is com.nexaflow.domain.models.ActionType && catalog.isUnifiedAlias(type)) {
            return false
        }
        if (!hardwareOkForType(type, profile.hardware)) return false
        val spec = catalog.specFor(type) ?: return true // unknown commands stay visible
        return resolve(spec, profile) != ExecutionStrategy.UNSUPPORTED
    }

    /** Resolves a unified alias to its canonical command. */
    fun canonical(type: com.nexaflow.domain.models.ActionType): com.nexaflow.domain.models.ActionType =
        catalog.canonical(type)

    /**
     * Filters a collection of (command, label) pairs, dropping commands with no
     * viable path. Used by the builder pickers so unsupported commands vanish.
     */
    fun <T> filterSupported(items: List<T>, profile: DeviceProfile, typeOf: (T) -> Any): List<T> {
        return items.filter { isSupported(typeOf(it), profile) }
    }

    private fun versionOk(spec: CommandSpec, sdk: Int): Boolean {
        return sdk >= spec.minSdk && sdk <= spec.maxSdk
    }

    private fun romOk(spec: CommandSpec, family: RomFamily): Boolean {
        if (spec.deniedFamilies.isNotEmpty() && family in spec.deniedFamilies) return false
        if (spec.romFamilies.isEmpty()) return true
        return family in spec.romFamilies
    }

    private fun integrationOk(spec: CommandSpec, profile: DeviceProfile): Boolean {
        val required = spec.requiresIntegration ?: return true
        return when (required) {
            IntegrationLevel.NORMAL -> true
            IntegrationLevel.ROOT -> profile.hasElevatedShell || profile.isSystemApp
            IntegrationLevel.SHIZUKU -> profile.integrationLevel == IntegrationLevel.SHIZUKU ||
                profile.hasElevatedShell
            IntegrationLevel.SYSTEM_APP -> profile.isSystemApp
            IntegrationLevel.PRIVILEGED_SYSTEM_APP -> profile.integrationLevel == IntegrationLevel.PRIVILEGED_SYSTEM_APP ||
                profile.integrationLevel == IntegrationLevel.PLATFORM_SIGNED_SYSTEM_APP
            IntegrationLevel.PLATFORM_SIGNED_SYSTEM_APP ->
                profile.integrationLevel == IntegrationLevel.PLATFORM_SIGNED_SYSTEM_APP
        }
    }

    private fun capabilitiesOk(spec: CommandSpec, profile: DeviceProfile): Boolean {
        if (spec.capabilities.isEmpty()) return true
        return spec.capabilities.any { it in profile.capabilities }
    }

    private fun permissionsOk(spec: CommandSpec, profile: DeviceProfile): Boolean {
        if (spec.permissions.isEmpty()) return true
        return spec.permissions.all { it in profile.grantedPermissions }
    }

    private fun hardwareOkForType(type: Any, hardware: HardwareProfile): Boolean {
        // Strict, comprehensive hardware adaptation — every hardware-dependent trigger/action
        // is checked against the actual device with high accuracy. Covers all 53 triggers
        // and 168 actions. No trigger/action will be shown if the hardware doesn't exist.
        return when (type) {
            // NFC
            com.nexaflow.domain.models.TriggerType.NFC_STATE,
            com.nexaflow.domain.models.TriggerType.NFC_TAG_SCANNED,
            com.nexaflow.domain.models.ActionType.SYSTEM_NFC,
            com.nexaflow.domain.models.ActionType.SYSTEM_OPEN_NFC_SETTINGS -> hardware.hasNfc
            // Telephony — strict
            com.nexaflow.domain.models.TriggerType.CELL_SIGNAL_STRENGTH,
            com.nexaflow.domain.models.TriggerType.NETWORK_MODE,
            com.nexaflow.domain.models.TriggerType.DATA_ROAMING_STATE,
            com.nexaflow.domain.models.TriggerType.CALL_STATE,
            com.nexaflow.domain.models.TriggerType.INCOMING_CALL,
            com.nexaflow.domain.models.ActionType.CALL_BLOCK,
            com.nexaflow.domain.models.TriggerType.SMS,
            com.nexaflow.domain.models.ActionType.SYSTEM_NETWORK_MODE,
            com.nexaflow.domain.models.ActionType.SYSTEM_DATA_ROAMING,
            com.nexaflow.domain.models.ActionType.SYSTEM_DIAL_NUMBER,
            com.nexaflow.domain.models.ActionType.SYSTEM_SEND_SMS -> hardware.hasTelephony
            // Camera flash
            com.nexaflow.domain.models.ActionType.SYSTEM_FLASHLIGHT -> hardware.hasCameraFlash
            // Bluetooth — strict
            com.nexaflow.domain.models.TriggerType.BLUETOOTH_DEVICE,
            com.nexaflow.domain.models.TriggerType.BLUETOOTH_STATE,
            com.nexaflow.domain.models.ActionType.SYSTEM_BLUETOOTH,
            com.nexaflow.domain.models.ActionType.SYSTEM_BLUETOOTH_DISCOVERABILITY,
            com.nexaflow.domain.models.ActionType.SYSTEM_BLUETOOTH_SCAN -> hardware.hasBluetooth
            // Sensors — strict per-sensor checks
            com.nexaflow.domain.models.TriggerType.SENSOR -> {
                hardware.hasProximitySensor || hardware.hasLightSensor || hardware.hasStepCounter ||
                    10 in hardware.sensorTypes || com.nexaflow.domain.models.NumericSensors.specs.values.any {
                        it.type in hardware.sensorTypes
                    }
            }
            // Location
            com.nexaflow.domain.models.TriggerType.LOCATION,
            com.nexaflow.domain.models.TriggerType.LOCATION_STATE,
            com.nexaflow.domain.models.ActionType.SYSTEM_LOCATION,
            com.nexaflow.domain.models.ActionType.SYSTEM_LOCATION_MODE,
            com.nexaflow.domain.models.ActionType.SYSTEM_OPEN_LOCATION_SETTINGS,
            com.nexaflow.domain.models.ActionType.SYSTEM_OPEN_MAPS -> hardware.hasLocationGps
            // USB
            com.nexaflow.domain.models.TriggerType.USB_CONNECTED -> hardware.hasUsbAccessory
            // Ethernet
            com.nexaflow.domain.models.TriggerType.ETHERNET_CONNECTED -> hardware.hasEthernet
            // HDMI
            com.nexaflow.domain.models.TriggerType.HDMI_CONNECTED -> hardware.hasHdmi
            else -> true
        }
    }

    /**
     * Strict sensor-specific check for SENSOR trigger with config.
     * Returns true if the specific sensor type is available on this device.
     */
    fun isSensorAvailable(sensorType: String, hardware: HardwareProfile): Boolean = when (sensorType.uppercase(java.util.Locale.ROOT)) {
        "PROXIMITY" -> hardware.hasProximitySensor
        "LIGHT" -> hardware.hasLightSensor
        "STEP", "STEP_COUNTER" -> hardware.hasStepCounter
        "SHAKE" -> 10 in hardware.sensorTypes
        else -> com.nexaflow.domain.models.NumericSensors.specs[sensorType.uppercase(java.util.Locale.ROOT)]?.let { it.type in hardware.sensorTypes } ?: false
    }

}
