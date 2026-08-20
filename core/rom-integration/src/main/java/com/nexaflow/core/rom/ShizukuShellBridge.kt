package com.nexaflow.core.rom

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.nexaflow.core.rom.model.SystemControlResult
import rikka.shizuku.Shizuku
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Elevated Shizuku transport backed exclusively by the public UserService AIDL
 * lifecycle. It deliberately does not reflect into `Shizuku.newProcess`; when
 * the binder is unavailable the caller receives a clear unavailable result and
 * can make an explicit policy decision rather than falling back silently.
 */
object ShizukuShellBridge {
    private const val PROCESS_NAME_SUFFIX = "shell"

    @Volatile private var appContext: Context? = null
    @Volatile private var boundShell: IUserShellService? = null
    @Volatile private var bindAttempted = false
    @Volatile private var listenerRegistered = false
    private val lock = Any()
    private val stateListeners = CopyOnWriteArraySet<() -> Unit>()

    /** Test seam for responses from the typed UserService endpoint. */
    internal var operationProbe: ((PrivilegedOperation) -> String?)? = null
    /** Compatibility test seam for the retained legacy bridge method. */
    internal var legacyExecProbe: ((String) -> String?)? = null

    val isUserServiceBound: Boolean
        get() = operationProbe != null || boundShell != null

    /**
     * Registers an observer for a real Shizuku lifecycle transition. Observers
     * must re-query their typed capability backend rather than treating this
     * callback as authorization to execute.
     */
    fun addStateListener(listener: () -> Unit) {
        stateListeners += listener
        listener.invoke()
    }

    private fun notifyStateChanged() {
        stateListeners.forEach { listener -> runCatching { listener.invoke() } }
    }

    /** Idempotently stores context and arms sticky rebind after a Shizuku restart. */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (listenerRegistered) {
            bindIfGranted()
            notifyStateChanged()
            return
        }
        synchronized(lock) {
            if (listenerRegistered) return
            listenerRegistered = true
            try {
                Shizuku.addBinderReceivedListenerSticky {
                    bindIfGranted()
                    notifyStateChanged()
                }
                Shizuku.addBinderDeadListener {
                    boundShell = null
                    bindAttempted = false
                    notifyStateChanged()
                }
            } catch (_: Throwable) {
                listenerRegistered = false
            }
        }
        bindIfGranted()
        notifyStateChanged()
    }

    private fun bindIfGranted() {
        if (!PrivilegedRunner.isShizukuGranted() || boundShell != null || bindAttempted) return
        synchronized(lock) {
            if (!PrivilegedRunner.isShizukuGranted() || boundShell != null || bindAttempted) return
            val context = appContext ?: return
            bindAttempted = true
            try {
                Shizuku.bindUserService(
                    Shizuku.UserServiceArgs(ComponentName(context, UserShellService::class.java))
                        .daemon(true)
                        .processNameSuffix(PROCESS_NAME_SUFFIX),
                    connection
                )
            } catch (_: Throwable) {
                bindAttempted = false
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            boundShell = binder?.let { IUserShellService.Stub.asInterface(it) }
            notifyStateChanged()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundShell = null
            bindAttempted = false
            notifyStateChanged()
        }
    }

    /**
     * Compatibility-only command path for legacy callers. It uses UserService
     * when bound but never uses reflection or a Root fallback. New capability
     * backends must call [executeOperation].
     */
    @Deprecated("Use executeOperation for all new privileged capabilities")
    fun execute(command: String): SystemControlResult {
        if (!PrivilegedRunner.isShizukuGranted()) {
            return SystemControlResult.fail("Shizuku is not granted. Open Shizuku and grant NexaFlow")
        }
        val raw = legacyExecProbe?.invoke(command) ?: try {
            boundShell?.exec(command)
        } catch (t: Throwable) {
            return SystemControlResult.fail("Shizuku UserService failed: ${t.message}")
        }
        return raw?.let { parseAidlResponse(it, command) }
            ?: SystemControlResult.fail("Shizuku UserService is unavailable; reconnect Shizuku and retry")
    }

    /** Executes one closed [PrivilegedOperation] through the typed AIDL endpoint. */
    fun executeOperation(operation: PrivilegedOperation): SystemControlResult {
        if (!PrivilegedRunner.isShizukuGranted()) {
            return SystemControlResult.fail("Shizuku is not granted. Open Shizuku and grant NexaFlow")
        }
        val args = operation.wireArguments().let { it + List(3 - it.size) { "" } }
        val raw = operationProbe?.invoke(operation) ?: try {
            boundShell?.executeOperation(operation.wireId.wireValue, args[0], args[1], args[2])
        } catch (t: Throwable) {
            return SystemControlResult.fail("Shizuku UserService failed: ${t.message}")
        }
        return raw?.let { parseAidlResponse(it, operation.wireId.wireValue) }
            ?: SystemControlResult.fail("Shizuku UserService is unavailable; reconnect Shizuku and retry")
    }

    /** Parses the `exitCode\noutput` response produced by [UserShellService]. */
    internal fun parseAidlResponse(response: String, operation: String): SystemControlResult {
        val newline = response.indexOf('\n')
        val exit = if (newline > 0) response.substring(0, newline).trim().toIntOrNull() else null
        val output = if (newline >= 0) response.substring(newline + 1) else response
        return if (exit == 0) {
            SystemControlResult.ok(output.trim().ifBlank { "Operation executed" })
        } else {
            SystemControlResult.fail(
                "Operation failed (exit ${exit ?: "unknown"}): " + output.trim().ifBlank { operation }
            )
        }
    }
}
