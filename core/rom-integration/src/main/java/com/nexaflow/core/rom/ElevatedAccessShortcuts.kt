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

    /**
     * Shizuku delivers the grant-dialog result through a listener registered
     * with [Shizuku.addRequestPermissionResultListener]. Without it the result
     * is silently dropped and the app never observes the grant, so the
     * permission manager would keep asking forever. Registered lazily once.
     */
    private val shizukuResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        // Shizuku 13.1.5 delivers the grant result as an int: PERMISSION_GRANTED
        // (0) when granted, PERMISSION_DENIED (-1) otherwise.
        if (requestCode == SHIZUKU_REQUEST_CODE &&
            grantResult == PackageManager.PERMISSION_GRANTED
        ) {
            // Grant landed: arm the elevated shell channel immediately so the
            // next elevated command runs through the AIDL UserService.
            shizukuAppContext?.let { ShizukuShellBridge.initialize(it) }
        }
    }

    @Volatile
    private var shizukuAppContext: Context? = null

    @Volatile
    private var shizukuListenerRegistered = false

    private fun ensureShizukuResultListener() {
        if (shizukuListenerRegistered) return
        shizukuListenerRegistered = true
        try {
            Shizuku.addRequestPermissionResultListener(shizukuResultListener)
        } catch (_: Throwable) {
            shizukuListenerRegistered = false
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
     */
    fun requestRootAccess(context: Context, onResult: (Boolean) -> Unit = {}) {
        val appContext = context.applicationContext
        if (!SystemAppStatusDetector.isSuBinaryAvailable()) {
            openRootManager(appContext)
            return
        }
        Thread {
            val granted = PrivilegedRunner.triggerSuPrompt()
            // Drop the cached probe so permission checks pick up the new grant
            // immediately instead of within the TTL window.
            SystemAppStatusDetector.refreshRootAvailability()
            Handler(Looper.getMainLooper()).post { onResult(granted) }
        }.start()
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
     */
    fun openShizuku(context: Context) {
        try {
            if (!Shizuku.pingBinder()) {
                openShizukuManager(context)
                return
            }
            if (Shizuku.isPreV11() ||
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            ) {
                // Already granted: (re)arm the UserService bind so elevated
                // commands use the AIDL channel instead of the legacy path.
                ShizukuShellBridge.initialize(context)
                return
            }
            shizukuAppContext = context.applicationContext
            ensureShizukuResultListener()
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        } catch (_: Throwable) {
            openShizukuManager(context)
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
