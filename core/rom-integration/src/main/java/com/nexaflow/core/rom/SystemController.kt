package com.nexaflow.core.rom

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.SystemControlResult

class SystemController(
    private val context: Context,
    private val capabilityProvider: RomCapabilityProvider
) {
    fun expandStatusBar(): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.STATUS_BAR_CONTROL)) {
            return SystemControlResult.fail("Status bar control is not available at the current integration level")
        }
        return try {
            val service = context.getSystemService(Context.STATUS_BAR_SERVICE)
                ?: return SystemControlResult.fail("Status bar service is unavailable")
            RomSystemApiBridge.invokeInstance(service, "expandNotificationsPanel")
            SystemControlResult.ok("Status bar expanded")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to expand status bar: ${t.message}")
        }
    }

    fun collapseStatusBar(): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.STATUS_BAR_CONTROL)) {
            return SystemControlResult.fail("Status bar control is not available at the current integration level")
        }
        return try {
            val service = context.getSystemService(Context.STATUS_BAR_SERVICE)
                ?: return SystemControlResult.fail("Status bar service is unavailable")
            RomSystemApiBridge.invokeInstance(service, "collapsePanels")
            SystemControlResult.ok("Status bar collapsed")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to collapse status bar: ${t.message}")
        }
    }

    fun setDoNotDisturb(enabled: Boolean): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.DND_ACCESS)) {
            return SystemControlResult.fail("Do Not Disturb access is not available")
        }
        return try {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            if (enabled) {
                val policy = NotificationManager.Policy(
                    0,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY
                )
                notificationManager.setNotificationPolicy(policy)
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            } else {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            SystemControlResult.ok(if (enabled) "Do Not Disturb enabled" else "Do Not Disturb disabled")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to change Do Not Disturb: ${t.message}")
        }
    }

    fun writeSecureSetting(name: String, value: String): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.WRITE_SECURE_SETTINGS)) {
            return SystemControlResult.fail("Write secure settings is not available at the current integration level")
        }
        return try {
            Settings.Secure.putString(context.contentResolver, name, value)
            SystemControlResult.ok("Set $name = $value")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to write secure setting: ${t.message}")
        }
    }

    fun setScreenTimeoutMillis(millis: Int): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.WRITE_SETTINGS)) {
            return SystemControlResult.fail("Write settings is not available")
        }
        return try {
            val written = Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                millis
            )
            if (written) {
                SystemControlResult.ok("Screen timeout set to $millis ms")
            } else {
                SystemControlResult.fail("The ROM rejected the screen timeout change")
            }
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to set screen timeout: ${t.message}")
        }
    }

    fun forceStopPackage(packageName: String): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.FORCE_STOP_PACKAGES)) {
            return SystemControlResult.fail("Force-stop is not available at the current integration level")
        }
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
                ?: return SystemControlResult.fail("Activity service is unavailable")
            RomSystemApiBridge.invokeInstance(activityManager, "forceStopPackage", packageName)
            SystemControlResult.ok("Force-stopped $packageName")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to force-stop package: ${t.message}")
        }
    }
}
