package com.nexaflow.core.rom

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
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
                return // already granted
            }
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
