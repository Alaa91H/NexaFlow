package com.nexaflow.core.rom

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.util.concurrent.TimeUnit

/**
 * Shizuku UserService that executes `sh -c <command>` with elevated
 * privileges. Shizuku forks this component's process from its own server
 * process (root on rooted devices, shell over wireless debugging), so the
 * commands run as uid 0 / shell.
 *
 * A plain [Service] exposing the [IUserShellService] AIDL stub from [onBind] —
 * the pattern the Shizuku docs and sample apps have used since the API 13.1.5
 * deprecation of `Shizuku.newProcess`.
 *
 * The process name (set in this module's AndroidManifest.xml as `:shell`) must
 * match the [ShizukuShellBridge]'s `processNameSuffix`; otherwise the service
 * would run in the app's own unprivileged process instead of the
 * Shizuku-spawned one.
 */
class UserShellService : Service() {

    private val binder = object : IUserShellService.Stub() {
        /** Compatibility-only path for legacy SystemController callers. */
        override fun exec(command: String): String = try {
            runCommand(command)
        } catch (t: Throwable) {
            "$INTERNAL_ERROR_EXIT\n${t.message ?: "internal error"}"
        }

        override fun executeOperation(
            operationId: String,
            first: String,
            second: String,
            third: String
        ): String = try {
            val operation = PrivilegedOperation.fromWire(operationId, first, second, third)
                ?: return "$INTERNAL_ERROR_EXIT\nUnsupported or invalid privileged operation"
            runTypedOperation(operation)
        } catch (t: Throwable) {
            "$INTERNAL_ERROR_EXIT\n${t.message ?: "internal error"}"
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    /**
     * Uses a direct ITelephony read/write when its reflected signature exists,
     * then retains the fixed TelephonyShell argv as a compatibility fallback.
     * This method accepts only a closed [PrivilegedOperation] shape.
     */
    private fun runTypedOperation(operation: PrivilegedOperation): String = when (operation) {
        is PrivilegedOperation.ReadAllowedNetworkTypes -> {
            PrivilegedTelephonyBridge.readUserAllowedNetworkTypes(operation.subscriptionId)
                ?.let { "0\n${java.lang.Long.toString(it, 2)}" }
                ?: runArgv(operation.argv())
        }
        is PrivilegedOperation.ReadDefaultNetworkProfile -> {
            PrivilegedTelephonyBridge.readSupportedRadioAccessFamily(operation.slotIndex)
                ?.let { "0\n${java.lang.Long.toString(it, 2)}" }
                ?: runArgv(operation.argv())
        }
        is PrivilegedOperation.SetAllowedNetworkTypes -> {
            if (PrivilegedTelephonyBridge.setUserAllowedNetworkTypes(
                    operation.subscriptionId,
                    operation.allowedNetworkTypes
                )
            ) {
                "0\nset-allowed-network-types-for-users dispatched via ITelephony"
            } else {
                runArgv(operation.argv())
            }
        }
        else -> runArgv(operation.argv())
    }

    /**
     * Runs one command with a hard timeout so a hung process can never block
     * the caller (or the binder thread) forever. stdout and stderr are merged,
     * matching the legacy `ShizukuRemoteProcess` behaviour.
     */
    private fun runCommand(command: String): String =
        runArgv(listOf("sh", "-c", command))

    /** Runs typed argv directly; no operation-controlled shell parsing occurs. */
    private fun runArgv(argv: List<String>): String {
        val process = ProcessBuilder(argv)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = Thread {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        output.append(line).append('\n')
                        line = reader.readLine()
                    }
                }
            } catch (_: Throwable) {
                // Stream closed on forced destroy — ignore.
            }
        }
        reader.start()
        val exited = process.waitFor(EXEC_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!exited) {
            process.destroyForcibly()
            reader.join(2000)
            return "$TIMEOUT_EXIT_CODE\n${output.toString().trim()}"
        }
        reader.join(2000)
        val exit = process.exitValue()
        process.destroy()
        return "$exit\n${output.toString().trim()}"
    }

    private companion object {
        /** Commands never run unbounded; the bridge parser reads the code. */
        const val EXEC_TIMEOUT_MS = 15_000L
        /** Matches the `timeout`(1) convention for killed commands. */
        const val TIMEOUT_EXIT_CODE = 124
        /** Marker for exceptions inside the service process. */
        const val INTERNAL_ERROR_EXIT = 126
    }
}
