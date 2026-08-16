package com.nexaflow.core.execution.compat

import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.RomFamily

/**
 * The execution strategy the compatibility engine selects for a command.
 *
 * [DIRECT]      — public Android SDK API, works on every ROM without elevation.
 * [BRIDGE]      — hidden/system API reached through [com.nexaflow.core.rom.RomSystemApiBridge]
 *                 or an OEM bridge (Evolution X / LineageOS settings).
 * [SHELL]       — plain `cmd ...` / `settings put ...` shell command (no root needed).
 * [ELEVATED]    — shell command that requires root or Shizuku.
 * [UNSUPPORTED] — no viable path on this device; the UI must hide the command.
 */
enum class ExecutionStrategy {
    DIRECT,
    BRIDGE,
    SHELL,
    ELEVATED,
    UNSUPPORTED
}

/**
 * Describes everything the engine needs to decide whether a command can run on
 * the current device and which path to take.
 *
 * @property minSdk  minimum required `Build.VERSION.SDK_INT` (e.g. 26 = Android 8.0).
 * @property maxSdk  maximum supported SDK; commands removed in newer Android
 *                   releases (e.g. `cmd wifi` restrictions) set this to exclude
 *                   them there. Default [Int.MAX_VALUE] (Android 17+ and beyond).
 * @property capabilities  at least one of these must be available; empty = none required.
 * @property requiresIntegration  minimum integration level (NORMAL/ROOT/SHIZUKU/SYSTEM...).
 * @property romFamilies  allowed ROM families; empty = all families.
 * @property deniedFamilies  ROM families where the command is known to fail (e.g. MIUI blocking it).
 * @property strategy  preferred execution path.
 * @property permissions  Android runtime/install-time permissions to verify (empty = none).
 */
data class CommandSpec(
    val minSdk: Int = 1,
    val maxSdk: Int = Int.MAX_VALUE,
    val capabilities: Set<RomCapability> = emptySet(),
    val requiresIntegration: IntegrationLevel? = null,
    val romFamilies: Set<RomFamily> = emptySet(),
    val deniedFamilies: Set<RomFamily> = emptySet(),
    val strategy: ExecutionStrategy = ExecutionStrategy.DIRECT,
    val permissions: Set<String> = emptySet()
) {
    companion object {
        /** A command that always works on every ROM/version (e.g. toast, clipboard). */
        val UNIVERSAL = CommandSpec()

        /** A command that needs root or Shizuku and nothing else. */
        fun elevated() = CommandSpec(
            requiresIntegration = IntegrationLevel.ROOT,
            strategy = ExecutionStrategy.ELEVATED
        )

        /** A command that works via a plain shell command on any ROM (e.g. `cmd notification`). */
        fun shell() = CommandSpec(strategy = ExecutionStrategy.SHELL)
    }
}
