package com.nexaflow.core.rom

import android.content.pm.PackageManager
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.core.security.SafeCommandBuilder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.util.concurrent.TimeUnit

object PrivilegedRunner {

    /**
     * True when the Shizuku server is running AND the app was granted access.
     * [Shizuku.checkSelfPermission] alone can report granted while the server
     * is dead (binder gone), so we require a live binder too. Pre-v11 Shizuku
     * has no per-app permission system: a running server is already "granted".
     */
    fun isShizukuGranted(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            Shizuku.isPreV11() ||
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * True when Shizuku is installed and its server is running (regardless of
     * whether this app has been granted yet) — used to offer the in-app grant
     * dialog instead of sending the user to the Shizuku app.
     */
    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
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
            return SystemControlResult.fail(
                "Root is not available. Open your root manager (Magisk/KernelSU) and grant NexaFlow superuser access."
            )
        }
        val safe = SafeCommandBuilder.validateUserCommand(command)
            ?: return SystemControlResult.fail("Command rejected: unsafe characters or too long")
        return try {
            // Magisk and KernelSU both accept `su -c`; some APatch builds need
            // the explicit `su 0` form. Try in order and use the first that runs.
            val attempts = listOf(
                arrayOf("su", "-c", safe),
                arrayOf("su", "0", "-c", safe),
                arrayOf("/system/bin/su", "-c", safe)
            )
            var lastFailure: String? = null
            for (attempt in attempts) {
                val result = runSu(attempt)
                if (result.success) return result
                lastFailure = result.message
            }
            SystemControlResult.fail(lastFailure ?: "Root command failed")
        } catch (t: Throwable) {
            SystemControlResult.fail("Root execution failed: ${t.message}")
        }
    }

    /** Runs one su invocation with a hard timeout and merged output. */
    private fun runSu(cmd: Array<String>): SystemControlResult {
        return try {
            val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val output = StringBuilder()
            val reader = Thread {
                output.append(process.inputStream.bufferedReader().readText())
            }
            reader.start()
            val exited = process.waitFor(ROOT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!exited) {
                process.destroyForcibly()
                reader.join(1000)
                return SystemControlResult.fail("Root command timed out after ${ROOT_TIMEOUT_MS}ms")
            }
            reader.join(1000)
            val exit = process.exitValue()
            process.destroy()
            if (exit == 0) {
                SystemControlResult.ok(output.toString().trim().ifBlank { "Command executed" })
            } else {
                SystemControlResult.fail("Root command failed (exit $exit): ${output.toString().trim()}")
            }
        } catch (t: Throwable) {
            SystemControlResult.fail("Root command error: ${t.message}")
        }
    }

    private const val ROOT_TIMEOUT_MS = 10_000L
}
