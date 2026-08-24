package com.nexaflow.core.execution.compat

import android.content.Context
import com.nexaflow.core.rom.RomIntegrationManager
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.RomFamily

/**
 * A snapshot of the device state the engine reasons about. Built once per app
 * launch (and refreshed when the user changes a permission) so the whole UI
 * filter layer shares the same view.
 */
data class DeviceProfile(
    val sdk: Int,
    val romFamily: RomFamily,
    val integrationLevel: IntegrationLevel,
    val capabilities: Set<RomCapability>,
    val grantedPermissions: Set<String>,
    /** True when the app holds elevated shell access (root or Shizuku). */
    val hasElevatedShell: Boolean
) {
    val isSystemApp: Boolean
        get() = integrationLevel == IntegrationLevel.SYSTEM_APP ||
            integrationLevel == IntegrationLevel.PRIVILEGED_SYSTEM_APP ||
            integrationLevel == IntegrationLevel.PLATFORM_SIGNED_SYSTEM_APP

    companion object {
        /**
         * Builds the current profile. Never throws: every failure degrades to
         * the least-privileged profile so no command is ever wrongly enabled.
         */
        fun capture(context: Context): DeviceProfile {
            return try {
                val info = RomIntegrationManager.buildInfo(context)
                val level = RomIntegrationManager.integrationLevel(context)
                val caps = RomIntegrationManager.availableCapabilities(context)
                DeviceProfile(
                    sdk = info.androidSdk.takeIf { it > 0 } ?: android.os.Build.VERSION.SDK_INT,
                    romFamily = info.family,
                    integrationLevel = level,
                    capabilities = caps.toSet(),
                    grantedPermissions = emptySet(), // filled below via provider when possible
                    hasElevatedShell = level == IntegrationLevel.ROOT || level == IntegrationLevel.SHIZUKU
                ).withPermissions(context)
            } catch (_: Throwable) {
                DeviceProfile(
                    sdk = android.os.Build.VERSION.SDK_INT,
                    romFamily = RomFamily.OTHER,
                    integrationLevel = IntegrationLevel.NORMAL,
                    capabilities = emptySet(),
                    grantedPermissions = emptySet(),
                    hasElevatedShell = false
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

    /** Resolves the effective strategy for a command on this device. */
    fun resolve(spec: CommandSpec, profile: DeviceProfile): ExecutionStrategy {
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
                } else {
                    ExecutionStrategy.UNSUPPORTED
                }

            ExecutionStrategy.DIRECT,
            ExecutionStrategy.BRIDGE -> {
                if (spec.capabilities.isEmpty() || capabilitiesOk(spec, profile)) {
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

    /** Convenience: is this command usable at all on this device? */
    fun isSupported(type: Any, profile: DeviceProfile): Boolean {
        // Unified duplicates are hidden as if they did not exist.
        if (type is com.nexaflow.domain.models.ActionType && catalog.isUnifiedAlias(type)) {
            return false
        }
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
}
