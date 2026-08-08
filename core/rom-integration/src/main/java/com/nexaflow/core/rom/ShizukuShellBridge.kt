package com.nexaflow.core.rom

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.nexaflow.core.rom.model.SystemControlResult
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

/**
 * Executes elevated shell commands through a Shizuku UserService (AIDL) — the
 * supported replacement for the deprecated `Shizuku.newProcess`, which became
 * private in Shizuku API 13.1.5 and is removed in API 14.
 *
 * Channel strategy (first available wins):
 *  1. Bound [IUserShellService] — preferred. Runs `sh -c` in a process forked
 *     from the Shizuku server (root / shell uid).
 *  2. Legacy `Shizuku.newProcess` via reflection — API 13.x still ships the
 *     private method, so devices running an older Shizuku server keep working
 *     during the migration to API 14.
 *  3. A clear failure message — the caller can then fall back to root / su.
 *
 * [initialize] arms the bind (and the sticky rebind hook for Shizuku
 * restarts); it is called from the Application and from the Shizuku grant
 * flow ([ElevatedAccessShortcuts]) so the AIDL channel is live before the
 * first elevated command runs. Until then, [execute] degrades gracefully.
 */
object ShizukuShellBridge {

    /** Must match `android:process=":shell"` in this module's manifest. */
    private const val PROCESS_NAME_SUFFIX = "shell"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var boundShell: IUserShellService? = null

    @Volatile
    private var bindAttempted = false

    @Volatile
    private var listenerRegistered = false

    private val lock = Any()

    // ── Test seams (mirror PrivilegedRunner.suProbe) ──────────────────────
    /** Simulates the bound AIDL service: returns the raw response, null = "not bound". */
    internal var execProbe: ((String) -> String?)? = null

    /** Simulates the legacy newProcess channel: returns the result directly. */
    internal var legacyProbe: ((String) -> SystemControlResult)? = null

    /** True when the AIDL channel is live (or simulated by a probe). */
    val isUserServiceBound: Boolean
        get() = execProbe != null || boundShell != null

    /**
     * Idempotent init. Stores the application context and arms the sticky
     * binder-received hook so the service is (re)bound when Shizuku
     * (re)starts or a grant lands. Safe to call from the Application and from
     * the Shizuku grant flow.
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (listenerRegistered) return
        synchronized(lock) {
            if (listenerRegistered) return
            listenerRegistered = true
            try {
                Shizuku.addBinderReceivedListenerSticky {
                    bindIfGranted()
                }
            } catch (_: Throwable) {
                listenerRegistered = false
            }
        }
        bindIfGranted()
    }

    /** Binds the UserService once, only when Shizuku is running and granted. */
    private fun bindIfGranted() {
        if (!PrivilegedRunner.isShizukuGranted()) return
        if (boundShell != null || bindAttempted) return
        synchronized(lock) {
            if (boundShell != null || bindAttempted) return
            val context = appContext ?: return
            bindAttempted = true
            try {
                Shizuku.bindUserService(
                    Shizuku.UserServiceArgs(
                        ComponentName(context, UserShellService::class.java)
                    )
                        .daemon(true)
                        .processNameSuffix(PROCESS_NAME_SUFFIX),
                    connection
                )
            } catch (_: Throwable) {
                // Server died between the check and the call — allow a retry.
                bindAttempted = false
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            boundShell = binder?.let { IUserShellService.Stub.asInterface(it) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Drop the channel AND allow a rebind: without resetting the flag,
            // a Shizuku restart would leave the AIDL path permanently dead and
            // every command would degrade to the legacy channel forever.
            boundShell = null
            bindAttempted = false
        }
    }

    /**
     * Executes one validated command. Never throws: returns a
     * [SystemControlResult] from the best available channel.
     */
    fun execute(command: String): SystemControlResult {
        execProbe?.let { probe ->
            val raw = probe(command)
            if (raw != null) return parseAidlResponse(raw, command)
            return legacyProbe?.invoke(command)
                ?: SystemControlResult.fail("No elevated runtime available (Shizuku or root)")
        }
        val service = boundShell
        if (service != null) {
            // A non-zero exit from the service is a real command result, not a
            // channel failure — so no legacy fallback here (only when unbound).
            return try {
                parseAidlResponse(service.exec(command), command)
            } catch (t: Throwable) {
                SystemControlResult.fail("Shizuku execution failed: ${t.message}")
            }
        }
        legacyProbe?.let { return it(command) }
        return execViaNewProcess(command)
    }

    /**
     * Parses the "exitCode\noutput" response produced by [UserShellService].
     * Pure function — unit-tested without Android.
     */
    internal fun parseAidlResponse(response: String, command: String): SystemControlResult {
        val newline = response.indexOf('\n')
        val exit = if (newline > 0) response.substring(0, newline).trim().toIntOrNull() else null
        val output = if (newline >= 0) response.substring(newline + 1) else response
        return if (exit == 0) {
            SystemControlResult.ok(output.trim().ifBlank { "Command executed" })
        } else {
            SystemControlResult.fail(
                "Command failed (exit ${exit ?: "unknown"}): " +
                    output.trim().ifBlank { command }
            )
        }
    }

    // ── Legacy channel: Shizuku.newProcess via reflection ─────────────────

    /**
     * Legacy fallback. `Shizuku.newProcess` is private since API 13.1.5 and
     * removed in API 14; reflection keeps devices with an older Shizuku server
     * working until they upgrade.
     */
    private fun execViaNewProcess(command: String): SystemControlResult {
        return try {
            val process = createShizukuProcess(arrayOf("sh", "-c", command), null, null)
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
}
