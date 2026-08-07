package com.nexaflow.core.rom

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import rikka.shizuku.Shizuku

/**
 * Shared helpers for granting elevated access: opens the installed root manager
 * (Magisk / KernelSU / APatch / MMRL) or requests Shizuku access in-app. Used by
 * both the permission manager (settings) and the task editor (builder) so the
 * grant flows stay identical everywhere.
 */
object ElevatedAccessShortcuts {

    private const val SHIZUKU_REQUEST_CODE = 0x4E58

    /** Opens the installed root manager, falling back to this app's details page. */
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
                .onFailure { openAppDetails(context) }
        } else {
            openAppDetails(context)
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
        } else {
            openAppDetails(context)
        }
    }

    private fun openAppDetails(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
