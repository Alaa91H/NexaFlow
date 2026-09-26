package com.nexaflow.core.rom

import android.content.pm.PackageManager
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.core.security.SafeCommandBuilder
import rikka.shizuku.Shizuku
import java.io.IOException
import java.util.concurrent.TimeUnit

object PrivilegedRunner {

    /**
     * True when the Shizuku server is running AND the app was granted access.
     * [Shizuku.checkSelfPermission] alone can report granted while the server
     * is dead (binder gone), so we require a live binder too. Pre-v11 Shizuku
     * has no per-app permission system: a running server is already "granted".
     */
    fun isShizukuGranted(): Boolean {
        shizukuGrantProbe?.let { return runCatching(it).getOrDefault(false) }
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

    fun isRootAvailable(): Boolean {
        rootProbeOverride?.let { return runCatching(it).getOrDefault(false) }
        return SystemAppStatusDetector.isRootAvailable()
    }

    /**
     * Runs an untrusted-command shell request through the best currently
     * granted elevated channel. Shizuku is preferred when it is granted and
     * its UserService is connected; Root — which is an absolute grant — is
     * tried when the Shizuku transport is definitely unusable before a side
     * effect is known to have been dispatched. An uncertain transport result
     * is terminal for this attempt: retrying the same command through Root
     * could duplicate a side effect that already landed.
     */
    fun runShell(command: String): SystemControlResult {
        val safe = SafeCommandBuilder.validateUserCommand(command)
            ?: return SystemControlResult.fail("Command rejected: unsafe characters or too long")
        shellRouteProbe?.let { probe -> return resolveShellRoute(probe(safe)) }
        var lastFailure: SystemControlResult? = null
        if (isShizukuGranted()) {
            val viaShizuku = runShizuku(safe)
            if (viaShizuku.success || viaShizuku.outcomeUncertain) return viaShizuku
            lastFailure = viaShizuku
        }
        if (isRootAvailable()) {
            val viaRoot = runRoot(safe)
            if (viaRoot.success) return viaRoot
            lastFailure = viaRoot
        }
        return lastFailure ?: SystemControlResult.fail(NO_ELEVATED_RUNTIME)
    }

    /**
     * Compatibility-only command bridge through the bound Shizuku UserService.
     * It never reflects into `Shizuku.newProcess`; new workflow capabilities
     * must use [runShizukuOperation] with a closed [PrivilegedOperation].
     */
    fun runShizuku(command: String): SystemControlResult {
        if (!isShizukuGranted()) {
            return SystemControlResult.fail("Shizuku is not granted. Open the Shizuku app and grant NexaFlow")
        }
        val safe = SafeCommandBuilder.validateUserCommand(command)
            ?: return SystemControlResult.fail("Command rejected: unsafe characters or too long")
        @Suppress("DEPRECATION")
        return ShizukuShellBridge.execute(safe)
    }

    /**
     * Executes one reviewed typed operation through the best currently granted
     * elevated channel. The operation itself has a closed argv shape, so this
     * fallback never turns workflow input into a shell expression.
     *
     * Root is an absolute privilege: when Shizuku definitely fails before a
     * side effect can be confirmed as dispatched (server restarted, bind
     * dropped), the same operation may fall back to the granted root shell.
     * If Shizuku reports an uncertain outcome, execution stops immediately so
     * a possible side effect is reconciled instead of being issued twice.
     */
    fun runElevatedOperation(operation: PrivilegedOperation): SystemControlResult {
        operationRouteProbe?.let { probe -> return resolveOperationRoute(probe(operation)) }
        var lastFailure: SystemControlResult? = null
        if (isShizukuGranted()) {
            val viaShizuku = runShizukuOperation(operation)
            if (viaShizuku.success || viaShizuku.outcomeUncertain) return viaShizuku
            lastFailure = viaShizuku
        }
        if (isRootAvailable()) {
            val viaRoot = runRootOperation(operation)
            if (viaRoot.success) return viaRoot
            lastFailure = viaRoot
        }
        return lastFailure ?: SystemControlResult.fail(NO_ELEVATED_RUNTIME)
    }

    /** New typed path: Shizuku only, no Root fallback and no generic command input. */
    fun runShizukuOperation(operation: PrivilegedOperation): SystemControlResult {
        if (!isShizukuGranted()) {
            return SystemControlResult.fail("Shizuku is not granted. Open the Shizuku app and grant NexaFlow")
        }
        return ShizukuShellBridge.executeOperation(operation)
    }

    /** New typed path: Root only, no Shizuku/ADB fallback and no generic command input. */
    fun runRootOperation(operation: PrivilegedOperation): SystemControlResult {
        if (!isRootAvailable()) {
            return SystemControlResult.fail("Root is not available. Grant NexaFlow in your root manager")
        }
        if (operation is PrivilegedOperation.SetHotspot) {
            return SystemControlResult.fail(
                "Root shell Soft AP commands are not used for Internet tethering; connect Shizuku UserService or use Android Settings"
            )
        }
        return try {
            val command = operation.rootCommand()
            val attempts = listOf(
                arrayOf("su", "-c", command),
                arrayOf("su", "0", "-c", command),
                arrayOf("/system/bin/su", "-c", command)
            )
            var lastFailure: String? = null
            for (attempt in attempts) {
                val result = runSu(attempt)
                if (result.success) return result
                lastFailure = result.message
            }
            invalidateRootAfterTransportFailure()
            SystemControlResult.fail(lastFailure ?: "Root operation failed")
        } catch (t: Throwable) {
            SystemControlResult.fail("Root operation failed: ${t.message}")
        }
    }

    /**
     * Actually invokes `su` with a long timeout so the root manager
     * (Magisk/KernelSU/APatch) can show its allow/deny grant dialog and wait
     * for the user. Returns true once the shell answers as uid=0 (granted).
     * This is the same mechanism Tasker/libsu use to request root in one tap.
     *
     * Tries the same invocation forms as [runRoot]: bare `su` (PATH-resolved,
     * Magisk/KernelSU/APatch), `su 0` (some APatch builds) and the classic
     * `/system/bin/su` (legacy SuperSU/OEM ROMs where su is not on PATH). It
     * only falls through when the binary itself is missing (command not found)
     * — a denial or timeout from one form stops the loop so the user is not
     * spammed with repeated grant dialogs.
     */
    fun triggerSuPrompt(): Boolean {
        if (!SystemAppStatusDetector.isSuBinaryAvailable()) return false
        val attempts = listOf(
            arrayOf("su", "-c", "id"),
            arrayOf("su", "0", "-c", "id"),
            arrayOf("/system/bin/su", "-c", "id")
        )
        for (attempt in attempts) {
            val outcome = runSuGrantProbe(attempt)
            // null = binary not found at this location → try the next one.
            if (outcome == null) continue
            return outcome
        }
        return false
    }

    /**
     * Test seam: replaces the real process spawn so tests can simulate each
     * root manager's behavior (granted / denied / command-not-found) without
     * spawning actual `su` processes on the host.
     */
    internal var suProbe: ((Array<String>) -> Boolean?)? = null

    /**
     * Runs one su grant probe. Returns true (granted), false (denied or
     * timed-out), or null when the binary was not found (command not found).
     */
    private fun runSuGrantProbe(cmd: Array<String>): Boolean? {
        suProbe?.let { return it(cmd) }
        return try {
            val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val output = StringBuilder()
            val reader = Thread {
                output.append(process.inputStream.bufferedReader().readText())
            }
            reader.start()
            // Long timeout: the grant dialog stays up until the user answers.
            val exited = process.waitFor(SU_GRANT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!exited) {
                process.destroyForcibly()
                reader.join(1000)
                return false
            }
            reader.join(1000)
            val exit = process.exitValue()
            process.destroy()
            exit == 0 && output.contains("uid=0")
        } catch (_: IOException) {
            null // binary not present at this location
        } catch (_: Throwable) {
            false
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
            invalidateRootAfterTransportFailure()
            SystemControlResult.fail(lastFailure ?: "Root command failed")
        } catch (t: Throwable) {
            SystemControlResult.fail("Root execution failed: ${t.message}")
        }
    }

    private fun invalidateRootAfterTransportFailure() {
        // A root manager can revoke access while the process is alive. Do not
        // let the previous positive uid=0 cache keep routing later operations
        // into a dead transport; the unified privilege store will re-probe.
        SystemAppStatusDetector.refreshRootAvailability()
        PrivilegeStateEvents.notifyChanged()
    }

    /** Runs one su invocation with a hard timeout and merged output. */
    private fun runSu(cmd: Array<String>): SystemControlResult {
        suRunnerProbe?.let { return it(cmd) }
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
    private const val SU_GRANT_TIMEOUT_MS = 30_000L

    /** Stable failure text used by engines that detect a missing elevated runtime. */
    internal const val NO_ELEVATED_RUNTIME = "No elevated runtime available (Shizuku or root)"

    /**
     * Test seam: forces a deterministic route ("shizuku" or "root") through
     * [runShell] without a Shizuku server or a real su binary. Returns the
     * chosen route plus the command to execute on that route.
     */
    internal var shellRouteProbe: ((String) -> Pair<String, String>)? = null

    /** Test seam for [runElevatedOperation]: route plus the operation to dispatch. */
    internal var operationRouteProbe: ((PrivilegedOperation) -> Pair<String, PrivilegedOperation>)? = null

    /**
     * Test seam: overrides the Shizuku grant answer (normally a live binder
     * + permission check) so multi-route behavior is testable on the JVM.
     */
    internal var shizukuGrantProbe: (() -> Boolean)? = null

    /** Test seam: overrides the root-availability answer. */
    internal var rootProbeOverride: (() -> Boolean)? = null

    /** Test seam: replaces the real `su` invocation for the root command route. */
    internal var suRunnerProbe: ((Array<String>) -> SystemControlResult)? = null

    private fun resolveShellRoute(pair: Pair<String, String>): SystemControlResult = when (pair.first) {
        "shizuku" -> runShizuku(pair.second)
        "root" -> runRoot(pair.second)
        else -> SystemControlResult.fail(NO_ELEVATED_RUNTIME)
    }

    private fun resolveOperationRoute(pair: Pair<String, PrivilegedOperation>): SystemControlResult =
        when (pair.first) {
            "shizuku" -> runShizukuOperation(pair.second)
            "root" -> runRootOperation(pair.second)
            else -> SystemControlResult.fail(NO_ELEVATED_RUNTIME)
        }
}
