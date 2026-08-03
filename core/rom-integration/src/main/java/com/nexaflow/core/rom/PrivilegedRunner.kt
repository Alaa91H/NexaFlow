package com.nexaflow.core.rom

import android.content.pm.PackageManager
import com.nexaflow.core.rom.model.SystemControlResult
import rikka.shizuku.Shizuku

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
        return when {
            isShizukuGranted() -> runShizuku(command)
            isRootAvailable() -> runRoot(command)
            else -> SystemControlResult.fail("No elevated runtime available (Shizuku or root)")
        }
    }

    fun runShizuku(command: String): SystemControlResult {
        if (!isShizukuGranted()) {
            return SystemControlResult.fail("Shizuku is not granted. Open the Shizuku app and grant NexaFlow")
        }
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
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

    fun runRoot(command: String): SystemControlResult {
        if (!isRootAvailable()) {
            return SystemControlResult.fail("Root is not available (no su binary)")
        }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
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
