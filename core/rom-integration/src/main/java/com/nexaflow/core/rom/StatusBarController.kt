package com.nexaflow.core.rom

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.core.security.SafeCommandBuilder

/** Status-bar/navigation operations extracted from the legacy system façade. */
internal class StatusBarController(
    context: Context,
    private val capabilityProvider: RomCapabilityProvider,
) {
    private val appContext = context

    @SuppressLint("WrongConstant")
    fun expandStatusBar(): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.STATUS_BAR_CONTROL)) {
            return SystemControlResult.fail(
                "Status bar control is not available at the current integration level"
            )
        }
        return try {
            val service = appContext.getSystemService("statusbar")
                ?: return SystemControlResult.fail("Status bar service is unavailable")
            RomSystemApiBridge.invokeInstance(service, "expandNotificationsPanel")
            SystemControlResult.ok("Status bar expanded")
        } catch (failure: Throwable) {
            SystemControlResult.fail("Failed to expand status bar: ${failure.message}")
        }
    }

    @SuppressLint("WrongConstant")
    fun collapseStatusBar(): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.STATUS_BAR_CONTROL)) {
            return SystemControlResult.fail(
                "Status bar control is not available at the current integration level"
            )
        }
        return try {
            val service = appContext.getSystemService("statusbar")
                ?: return SystemControlResult.fail("Status bar service is unavailable")
            RomSystemApiBridge.invokeInstance(service, "collapsePanels")
            SystemControlResult.ok("Status bar collapsed")
        } catch (failure: Throwable) {
            SystemControlResult.fail("Failed to collapse status bar: ${failure.message}")
        }
    }

    fun expandNotifications(): SystemControlResult {
        val shell = PrivilegedRunner.runShell("cmd statusbar expand-notifications")
        if (shell.success) return shell
        return expandPanel("expandNotificationsPanel", shell)
    }

    fun expandQuickSettings(): SystemControlResult {
        val shell = PrivilegedRunner.runShell("cmd statusbar expand-settings")
        if (shell.success) return shell
        return expandPanel("expandSettingsPanel", shell)
    }

    fun openRecents(): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.STATUS_BAR_CONTROL)) {
            return privileged("input keyevent KEYCODE_APP_SWITCH", "Recents opened")
        }
        return try {
            val service = appContext.getSystemService("statusbar")
                ?: return SystemControlResult.fail("Status bar service is unavailable")
            RomSystemApiBridge.invokeInstance(service, "toggleRecentApps")
            SystemControlResult.ok("Recents opened")
        } catch (_: Throwable) {
            privileged("input keyevent KEYCODE_APP_SWITCH", "Recents opened")
        }
    }

    fun goHome(): SystemControlResult =
        privileged("input keyevent KEYCODE_HOME", "Home screen shown")

    fun openAppDrawer(): SystemControlResult {
        val shell = PrivilegedRunner.runShell(
            SafeCommandBuilder.build("input", "keyevent", "187")
        )
        return if (shell.success) SystemControlResult.ok("App drawer opened") else shell
    }

    fun toggleStatusBar(show: Boolean): SystemControlResult {
        return try {
            val activity = appContext as? android.app.Activity
                ?: return SystemControlResult.fail("Not an activity context")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val controller = activity.window.insetsController
                if (show) controller?.show(android.view.WindowInsets.Type.statusBars())
                else controller?.hide(android.view.WindowInsets.Type.statusBars())
            } else {
                @Suppress("DEPRECATION")
                run {
                    activity.window.decorView.systemUiVisibility =
                        if (show) android.view.View.SYSTEM_UI_FLAG_VISIBLE
                        else android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                }
            }
            SystemControlResult.ok("Status bar ${if (show) "shown" else "hidden"}")
        } catch (failure: Throwable) {
            SystemControlResult.fail("Status bar failed: ${failure.message}")
        }
    }

    private fun expandPanel(method: String, fallback: SystemControlResult): SystemControlResult {
        return try {
            val service = appContext.getSystemService("statusbar") ?: return fallback
            RomSystemApiBridge.invokeInstance(service, method)
            SystemControlResult.ok("Status bar expanded ($method)")
        } catch (_: Throwable) {
            fallback
        }
    }

    private fun privileged(command: String, successMessage: String): SystemControlResult {
        val result = PrivilegedRunner.runShell(command)
        return if (result.success) SystemControlResult.ok(successMessage) else result
    }
}
