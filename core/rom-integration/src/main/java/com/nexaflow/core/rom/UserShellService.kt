package com.nexaflow.core.rom

import android.annotation.TargetApi
import android.content.Context
import android.content.ContextWrapper
import android.net.TetheringManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import androidx.annotation.Keep
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier
import rikka.shizuku.SystemServiceHelper

/**
 * Shizuku UserService that executes typed privileged operations (and a
 * compatibility `sh -c` path) with elevated privileges. Shizuku forks a
 * process from its own server (root on rooted devices, shell over wireless
 * debugging), instantiates this class reflectively inside that process and
 * uses the instance itself as the binder — so this class MUST extend
 * [IUserShellService.Stub] directly.
 *
 * Extending [android.app.Service] and returning the stub from `onBind` does
 * NOT work for the UserService lifecycle: Shizuku's ServiceStarter casts the
 * constructed instance to `android.os.IBinder` and a `Service` is not one
 * (`ClassCastException: ... cannot be cast to android.os.IBinder` in the
 * starter logs), the bind silently fails and every elevated operation
 * reports "Shizuku UserService is unavailable". The component is therefore
 * also deliberately absent from the manifest — the Shizuku server starts it
 * by class name; a regular platform-service declaration is meaningless here.
 */
class UserShellService : IUserShellService.Stub {

    private var serviceContext: Context? = null

    /** Kept for Shizuku versions before v13. */
    constructor() : super()

    /**
     * Shizuku v13+ supplies a package Context. We retain it only for framework
     * manager construction; the actual Binder calls still execute as the
     * UserService UID (shell or root), never as the normal app process.
     */
    @Keep
    constructor(context: Context) : super() {
        serviceContext = context
    }

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

    /**
     * Uses a direct ITelephony read/write when its reflected signature exists,
     * then retains the fixed TelephonyShell argv as a compatibility fallback.
     * This method accepts only a closed [PrivilegedOperation] shape.
     */
    private fun runTypedOperation(operation: PrivilegedOperation): String = when (operation) {
        is PrivilegedOperation.SetHotspot -> runHotspotTethering(operation.enabled)
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
     * Android 16/API 36 made TetheringManager's start/stop request contract
     * public. Use that contract from the elevated UserService instead of the
     * Wi-Fi shell's start-softap command: the latter creates a raw Soft AP,
     * requires an explicit SSID/security/passphrase, and is not equivalent to
     * Internet tethering.
     *
     * TetheringService validates that callerPkg belongs to the Binder calling
     * UID before it checks TETHER_PRIVILEGED. A Shizuku UserService executes as
     * shell (uid 2000) or root (uid 0), while its package Context still belongs
     * to NexaFlow. The small ContextWrapper therefore supplies the canonical
     * package identity for that elevated UID.
     */
    @TargetApi(API_PUBLIC_TETHERING_CONTROL)
    private fun runHotspotTethering(enabled: Boolean): String {
        if (Build.VERSION.SDK_INT < API_PUBLIC_TETHERING_CONTROL) {
            return "$INTERNAL_ERROR_EXIT\nAutomatic Internet hotspot control requires Android 16 or newer"
        }
        val baseContext = serviceContext
            ?: return "$INTERNAL_ERROR_EXIT\nShizuku UserService context is unavailable"

        return try {
            val manager = createElevatedTetheringManager(baseContext)

            if (!enabled) {
                // The public request-scoped stop API only stops a matching
                // request. A routine must also be able to stop tethering that
                // the user (or Settings) started, so use the reviewed system
                // API by type from this privileged UserService. Post-condition
                // verification in the app still confirms the actual OFF state.
                val stopByType = manager.javaClass.getMethod(
                    "stopTethering",
                    Int::class.javaPrimitiveType
                )
                stopByType.invoke(manager, TetheringManager.TETHERING_WIFI)
                return "0\nWi-Fi Internet tethering stop dispatched"
            }

            val request = buildWifiTetheringRequest()
            val result = AtomicInteger(UNSET_TETHERING_RESULT)
            val latch = CountDownLatch(1)
            manager.startTethering(
                request,
                DIRECT_EXECUTOR,
                object : TetheringManager.StartTetheringCallback {
                    override fun onTetheringStarted() {
                        result.set(TetheringManager.TETHER_ERROR_NO_ERROR)
                        latch.countDown()
                    }

                    override fun onTetheringFailed(error: Int) {
                        result.set(error)
                        latch.countDown()
                    }
                }
            )

            if (!latch.await(TETHERING_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                "$TIMEOUT_EXIT_CODE\nTetheringManager start request timed out"
            } else {
                val code = result.get()
                if (code == TetheringManager.TETHER_ERROR_NO_ERROR) {
                    "0\nWi-Fi Internet tethering started"
                } else {
                    "1\nTetheringManager start failed with error $code"
                }
            }
        } catch (t: Throwable) {
            "$INTERNAL_ERROR_EXIT\nTetheringManager ${if (enabled) "start" else "stop"} failed: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    /**
     * TetheringManager's connector-supplier constructor is a module/system API,
     * so normal SDK stubs do not expose it even though it exists on API 36+.
     * UserService has no non-SDK reflection restriction; construct it against
     * the real tethering binder while keeping the operation fully typed.
     */
    @TargetApi(API_PUBLIC_TETHERING_CONTROL)
    private fun buildWifiTetheringRequest(): TetheringManager.TetheringRequest {
        val builder = TetheringManager.TetheringRequest.Builder(TetheringManager.TETHERING_WIFI)
        // Background automation must never unexpectedly launch carrier UI.
        // This system API exists on API 36+ but is not in public SDK stubs;
        // UserService may call it reflectively without hidden-API restrictions.
        runCatching {
            builder.javaClass
                .getMethod("setShouldShowEntitlementUi", Boolean::class.javaPrimitiveType)
                .invoke(builder, false)
        }
        return builder.build()
    }

    @TargetApi(API_PUBLIC_TETHERING_CONTROL)
    private fun createElevatedTetheringManager(baseContext: Context): TetheringManager {
        val callerPackage = when (Process.myUid()) {
            ROOT_UID -> ROOT_PACKAGE
            SHELL_UID -> SHELL_PACKAGE
            else -> baseContext.opPackageName
        }
        val identityContext = object : ContextWrapper(baseContext) {
            override fun getOpPackageName(): String = callerPackage
        }
        val constructor = TetheringManager::class.java.getDeclaredConstructor(
            Context::class.java,
            Supplier::class.java
        )
        constructor.isAccessible = true
        val supplier = Supplier<IBinder> {
            SystemServiceHelper.getSystemService(TETHERING_SERVICE)
        }
        return constructor.newInstance(identityContext, supplier)
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
        val DIRECT_EXECUTOR = Executor { command -> command.run() }
        const val API_PUBLIC_TETHERING_CONTROL = 36
        const val TETHERING_TIMEOUT_MS = 15_000L
        const val UNSET_TETHERING_RESULT = Int.MIN_VALUE
        const val ROOT_UID = 0
        const val SHELL_UID = 2_000
        const val ROOT_PACKAGE = "root"
        const val SHELL_PACKAGE = "com.android.shell"
        const val TETHERING_SERVICE = "tethering"

        /** Commands never run unbounded; the bridge parser reads the code. */
        const val EXEC_TIMEOUT_MS = 15_000L
        /** Matches the `timeout`(1) convention for killed commands. */
        const val TIMEOUT_EXIT_CODE = 124
        /** Marker for exceptions inside the service process. */
        const val INTERNAL_ERROR_EXIT = 126
    }
}
