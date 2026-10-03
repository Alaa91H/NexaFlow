package com.nexaflow.core.rom

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.core.security.SafeCommandBuilder

/** Package-manager operations extracted from [SystemController]. */
internal class ApplicationPackageController {
    fun forceStop(pkg: String): SystemControlResult =
        shellPackage(pkg, "No package configured", "Force-stop failed",
            { SafeCommandBuilder.build("am", "force-stop", pkg) },
            { "$pkg stopped" })

    fun clearData(pkg: String): SystemControlResult =
        shellPackage(pkg, "No package configured", "Clear data failed",
            { SafeCommandBuilder.build("pm", "clear", pkg) },
            { "$pkg data cleared" })

    fun installApk(path: String): SystemControlResult =
        shellPackage(path, "No APK path", "APK install failed",
            { SafeCommandBuilder.build("pm", "install", "-r", path) },
            { "APK installed" })

    fun uninstall(pkg: String): SystemControlResult =
        shellPackage(pkg, "No package", "Uninstall failed",
            { SafeCommandBuilder.build("pm", "uninstall", pkg) },
            { "$pkg uninstalled" })

    fun disable(pkg: String): SystemControlResult =
        shellPackage(pkg, "No package", "Disable failed",
            { SafeCommandBuilder.build("pm", "disable-user", "--user", "0", pkg) },
            { "$pkg disabled" })

    fun enable(pkg: String): SystemControlResult =
        shellPackage(pkg, "No package", "Enable failed",
            { SafeCommandBuilder.build("pm", "enable", pkg) },
            { "$pkg enabled" })

    private inline fun shellPackage(
        value: String,
        missingMessage: String,
        failurePrefix: String,
        command: () -> String,
        successMessage: () -> String,
    ): SystemControlResult {
        if (value.isBlank()) return SystemControlResult.fail(missingMessage)
        return try {
            val shell = PrivilegedRunner.runShell(command())
            if (shell.success) SystemControlResult.ok(successMessage()) else shell
        } catch (failure: Throwable) {
            SystemControlResult.fail("$failurePrefix: ${failure.message}")
        }
    }
}
