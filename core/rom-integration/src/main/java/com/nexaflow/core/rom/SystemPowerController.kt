package com.nexaflow.core.rom

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.core.security.SafeCommandBuilder

/**
 * Elevated power operations: reboot into a declared target, shutdown, and a
 * System UI restart.
 *
 * Extracted from [SystemController] so the target mapping, the command shapes
 * and the honest fallback of the System UI restart live next to each other and
 * can be tested through the shell-route seam without growing the façade.
 */
class SystemPowerController {

    /**
     * Reboots into [mode] (`NORMAL`, `RECOVERY` or `BOOTLOADER`; requires root).
     * An unsupported mode fails without issuing a command, so a request for
     * recovery can never be recorded as a plain reboot.
     */
    fun reboot(mode: String = RebootMode.NORMAL): SystemControlResult {
        val arguments = RebootMode.argumentsFor(mode)
            ?: return SystemControlResult.fail("Unsupported reboot mode: $mode")
        return tryPrivileged(
            SafeCommandBuilder.build("reboot", *arguments.toTypedArray()),
            RebootMode.successMessage(mode)
        )
    }

    /** Shuts the device down (requires root). */
    fun shutdown(): SystemControlResult =
        tryPrivileged(SafeCommandBuilder.build("reboot", "-p"), "Shutdown requested")

    /** Restarts the System UI process (requires root; falls back to a soft refresh). */
    fun restartSystemUi(): SystemControlResult {
        val shell = PrivilegedRunner.runShell(
            SafeCommandBuilder.build("pkill", "-f", "com.android.systemui")
        )
        if (shell.success) return SystemControlResult.ok("System UI restarted")
        // Some Roms expose the stopservice path through cmd; otherwise report the failure.
        val fallback = PrivilegedRunner.runShell(
            SafeCommandBuilder.build("am", "broadcast", "-a", "android.intent.action.CLOSE_SYSTEM_DIALOGS")
        )
        return if (fallback.success) {
            SystemControlResult.ok("System UI refresh requested")
        } else {
            SystemControlResult.fail("System UI restart requires root: ${shell.message}")
        }
    }

    private fun tryPrivileged(command: String, successMessage: String): SystemControlResult {
        val result = PrivilegedRunner.runShell(command)
        return if (result.success) SystemControlResult.ok(successMessage) else result
    }
}
