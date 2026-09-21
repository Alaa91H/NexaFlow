package com.nexaflow.core.rom

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import rikka.shizuku.Shizuku

/**
 * Shared helpers for granting elevated access: requests superuser access by
 * triggering the root manager's grant dialog automatically, or asks for
 * Shizuku access in-app. Used by both the permission manager (settings) and
 * the task editor (builder) so the grant flows stay identical everywhere.
 */
object ElevatedAccessShortcuts {

    private const val SHIZUKU_REQUEST_CODE = 0x4E58

    private val shizukuRequestLock = Any()
    private var shizukuPermissionRequestInFlight = false
    private val pendingShizukuCallbacks = mutableListOf<(Boolean) -> Unit>()

    @Volatile
    private var shizukuAppContext: Context? = null

    @Volatile
    private var shizukuResultListenerRegistered = false

    @Volatile
    private var shizukuBinderDeadListenerRegistered = false

    /**
     * Shizuku delivers the grant-dialog result through a listener registered
     * with [Shizuku.addRequestPermissionResultListener]. Without it the result
     * is silently dropped and the app never observes the grant, so the
     * permission manager would keep asking forever. Registered lazily once.
     */
    private val shizukuResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            val appContext = synchronized(shizukuRequestLock) { shizukuAppContext }
            // Shizuku 13.1.5 delivers the grant result as an int:
            // PERMISSION_GRANTED (0) when granted, PERMISSION_DENIED (-1)
            // otherwise. Arm the UserService before notifying callers so a
            // follow-up "grant all" pass can immediately use the shell.
            if (granted) {
                appContext?.let { context ->
                    runCatching { ShizukuShellBridge.initialize(context) }
                }
            }
            completeShizukuRequest(granted)
        }
    }

    private val rootRequestLock = Any()
    private val pendingRootCallbacks = mutableListOf<(Boolean) -> Unit>()
    private var rootRequestInFlight = false

    @Synchronized
    private fun ensureShizukuLifecycleListeners(): Boolean {
        if (!shizukuResultListenerRegistered) {
            try {
                Shizuku.addRequestPermissionResultListener(shizukuResultListener)
                shizukuResultListenerRegistered = true
            } catch (_: Throwable) {
                return false
            }
        }
        if (!shizukuBinderDeadListenerRegistered) {
            try {
                // A Shizuku restart can kill the binder while its permission
                // dialog is in flight. The permission-result callback may then
                // never arrive; clear the request gate immediately so a later
                // user action can reconnect instead of being suppressed forever.
                Shizuku.addBinderDeadListener {
                    completeShizukuRequest(false)
                }
                shizukuBinderDeadListenerRegistered = true
            } catch (_: Throwable) {
                return false
            }
        }
        return true
    }

    private fun beginShizukuPermissionRequest(
        context: Context,
        onResult: (Boolean) -> Unit
    ): Boolean = synchronized(shizukuRequestLock) {
        pendingShizukuCallbacks += onResult
        if (shizukuPermissionRequestInFlight) {
            false
        } else {
            shizukuPermissionRequestInFlight = true
            shizukuAppContext = context.applicationContext
            true
        }
    }

    private fun clearShizukuPermissionRequest() {
        synchronized(shizukuRequestLock) {
            shizukuPermissionRequestInFlight = false
            shizukuAppContext = null
            pendingShizukuCallbacks.clear()
        }
    }

    private fun completeShizukuRequest(granted: Boolean) {
        val callbacks = synchronized(shizukuRequestLock) {
            shizukuPermissionRequestInFlight = false
            shizukuAppContext = null
            pendingShizukuCallbacks.toList().also { pendingShizukuCallbacks.clear() }
        }
        Handler(Looper.getMainLooper()).post {
            callbacks.forEach { callback ->
                runCatching { callback(granted) }
            }
        }
    }

    private fun completeRootRequest(granted: Boolean, mainHandler: Handler) {
        val callbacks = synchronized(rootRequestLock) {
            rootRequestInFlight = false
            pendingRootCallbacks.toList().also { pendingRootCallbacks.clear() }
        }
        mainHandler.post {
            callbacks.forEach { callback ->
                runCatching { callback(granted) }
            }
        }
    }

    /**
     * Requests superuser access the way Tasker/Termux do: when a root manager
     * (Magisk / KernelSU / APatch) is installed, executing `su -c id` makes it
     * pop its allow/deny grant dialog immediately — the user taps Allow and the
     * app is granted, with no detour through app info or settings pages.
     *
     * Falls back to opening the root manager app when no `su` binary exists
     * yet (device not rooted). Runs off the main thread so the dialog prompt
     * never blocks the UI; [onResult] reports whether root was granted.
     * Probe failures fail closed instead of crashing the caller or leaving the
     * permission flow stuck without a result. Concurrent requests are coalesced
     * into one root-manager prompt so rapid taps or multiple feature requests
     * cannot spam overlapping superuser dialogs.
     */
    fun requestRootAccess(context: Context, onResult: (Boolean) -> Unit = {}) {
        val appContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())
        val shouldStartRequest = synchronized(rootRequestLock) {
            pendingRootCallbacks += onResult
            if (rootRequestInFlight) {
                false
            } else {
                rootRequestInFlight = true
                true
            }
        }
        if (!shouldStartRequest) return

        val suBinaryAvailable = runCatching {
            SystemAppStatusDetector.isSuBinaryAvailable()
        }.getOrDefault(false)
        if (!suBinaryAvailable) {
            openRootManager(appContext)
            // Opening a manager is only a navigation fallback, not a successful
            // grant. Always terminate the request so callers can clear loading
            // state and offer their normal Android/manual fallback immediately.
            completeRootRequest(false, mainHandler)
            return
        }
        val requestThread = Thread {
            val granted = runCatching {
                PrivilegedRunner.triggerSuPrompt()
            }.getOrDefault(false)
            // Drop the cached probe so permission checks pick up the new grant
            // immediately instead of within the TTL window. Refresh failures
            // must not suppress delivery of the actual root-manager result.
            runCatching { SystemAppStatusDetector.refreshRootAvailability() }
            completeRootRequest(granted, mainHandler)
        }
        runCatching { requestThread.start() }
            .onFailure { completeRootRequest(false, mainHandler) }
    }

    /**
     * Requests the battery-optimization exemption through the system dialog
     * (one tap, no detour) when the app is still restricted — background
     * monitoring would otherwise be killed by Doze. No-op when already exempt.
     */
    fun requestBatteryOptimizationExemption(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) return
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
            // Some OEMs block the direct request; fall back to the exemption list.
            try {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) {
                // Best-effort: the permission manager still offers this.
            }
        }
    }

    /** Opens the installed root manager, falling back to the Shizuku manager. */
    fun openRootManager(context: Context) {
        val candidates = listOf(
            "com.topjohnwu.magisk",
            "com.topjohnwu.magisk.delta",
            "io.github.huskydg.magisk",
            "me.weishu.kernelsu",
            "me.bmax.apatch",
            "com.dergoogler.mmrl"
        )
        val launch = candidates.firstNotNullOfOrNull { pkg ->
            runCatching { context.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull()
        }
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(launch) }
        } else {
            openShizukuManager(context)
        }
    }

    /**
     * Requests Shizuku access through the in-app grant dialog when the server is
     * running; falls back to opening the Shizuku manager app when it is not.
     * Concurrent requests share the single in-flight system prompt. Listener or
     * binder failures clear that state so the user can retry safely, including a
     * Shizuku service restart while the grant dialog is visible.
     */
    fun openShizuku(context: Context, onResult: (Boolean) -> Unit = {}) {
        val appContext = context.applicationContext
        try {
            if (!Shizuku.pingBinder()) {
                clearShizukuPermissionRequest()
                openShizukuManager(appContext)
                Handler(Looper.getMainLooper()).post { onResult(false) }
                return
            }
            if (Shizuku.isPreV11() ||
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            ) {
                clearShizukuPermissionRequest()
                // Already granted: (re)arm the UserService bind so elevated
                // commands use the AIDL channel instead of the legacy path.
                runCatching { ShizukuShellBridge.initialize(appContext) }
                Handler(Looper.getMainLooper()).post { onResult(true) }
                return
            }
            if (!beginShizukuPermissionRequest(appContext, onResult)) return
            if (!ensureShizukuLifecycleListeners()) {
                completeShizukuRequest(false)
                openShizukuManager(appContext)
                return
            }
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        } catch (_: Throwable) {
            completeShizukuRequest(false)
            openShizukuManager(appContext)
        }
    }

    private fun openShizukuManager(context: Context) {
        val launch = runCatching {
            context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        }.getOrNull()
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(launch) }
        }
    }
}
