package com.nexaflow.core.rom

import android.content.pm.PackageManager
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.core.security.SafeCommandBuilder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

object PrivilegedRunner {

    fun isShizukuGranted(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun isRootAvailable(): Boolean = SystemAppStatusDetector.isRootAvailable()

    fun runShell(command: String): SystemControlResult {
        val safe = SafeCommandBuilder.validateUserCommand(command)
            ?: return SystemControlResult.fail("Command rejected: unsafe characters or too long")
        return when {
            isShizukuGranted() -> runShizuku(safe)
            isRootAvailable() -> runRoot(safe)
            else -> SystemControlResult.fail("No elevated runtime available (Shizuku or root)")
        }
    }

    @Suppress("DEPRECATION")
    fun runShizuku(command: String): SystemControlResult {
        if (!isShizukuGranted()) {
            return SystemControlResult.fail("Shizuku is not granted. Open the Shizuku app and grant NexaFlow")
        }
        val safe = SafeCommandBuilder.validateUserCommand(command)
            ?: return SystemControlResult.fail("Command rejected: unsafe characters or too long")
        return try {
            val process = createShizukuProcess(arrayOf("sh", "-c", safe), null, null)
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            process.destroy()
            if (exit == 0) {
                SystemControlResult.ok(output.trim().ifBlank { "Command executed" })
            } else {
                SystemControlResult.fail("Command failed (exit $exit): ${output.trim()}")
            }
        } catch (t: Throwable) {
            SystemControlResult.fail("Shizuku execution failed: ${t.message}")
        }
    }

    /**
     * Since Shizuku API 13.1.5, [Shizuku.newProcess] was made private (it is deprecated and
     * planned for removal in API 14). It still exists in the current release, so invoke it via
     * reflection. If a future API version removes it entirely, fail with a clear message.
     */
    private fun createShizukuProcess(
        cmd: Array<String>,
        env: Array<String>?,
        dir: String?
    ): ShizukuRemoteProcess {
        try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            return method.invoke(null, cmd, env, dir) as ShizukuRemoteProcess
        } catch (t: Throwable) {
            throw IllegalStateException(
                "Shizuku.newProcess is unavailable in this Shizuku API version", t
            )
        }
    }

    fun runRoot(command: String): SystemControlResult {
        if (!isRootAvailable()) {
            return SystemControlResult.fail("Root is not available (no su binary)")
        }
        val safe = SafeCommandBuilder.validateUserCommand(command)
            ?: return SystemControlResult.fail("Command rejected: unsafe characters or too long")
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", safe))
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            process.destroy()
            if (exit == 0) {
                SystemControlResult.ok(output.trim().ifBlank { "Command executed" })
            } else {
                SystemControlResult.fail("Root command failed (exit $exit): ${output.trim()}")
            }
        } catch (t: Throwable) {
            SystemControlResult.fail("Root execution failed: ${t.message}")
        }
    }
}
