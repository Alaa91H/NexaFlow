package com.nexaflow.core.rom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
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
            return tryPrivileged(
                command = "cmd notification set_interruption_filter " +
                    if (enabled) "none" else "all",
                successMessage = if (enabled) "Do Not Disturb enabled" else "Do Not Disturb disabled"
            )
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
            tryPrivileged(
                command = "cmd notification set_interruption_filter " +
                    if (enabled) "none" else "all",
                successMessage = if (enabled) "Do Not Disturb enabled" else "Do Not Disturb disabled"
            ).takeIf { it.success } ?: SystemControlResult.fail("Failed to change Do Not Disturb: ${t.message}")
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
            return tryPrivileged(
                command = "settings put system screen_off_timeout $millis",
                successMessage = "Screen timeout set to $millis ms"
            )
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

    fun setBrightness(value: Int): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.WRITE_SETTINGS)) {
            return tryPrivileged(
                command = "settings put system screen_brightness ${value.coerceIn(0, 255)}",
                successMessage = "Brightness set to ${value.coerceIn(0, 255)}"
            )
        }
        return try {
            val clamped = value.coerceIn(0, 255)
            val written = Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                clamped
            )
            if (written) {
                SystemControlResult.ok("Brightness set to $clamped")
            } else {
                SystemControlResult.fail("The ROM rejected the brightness change")
            }
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to set brightness: ${t.message}")
        }
    }

    fun setVolume(stream: Int, value: Int): SystemControlResult {
        return try {
            val audioManager = context.getSystemService(AudioManager::class.java)
            val max = audioManager.getStreamMaxVolume(stream)
            val clamped = value.coerceIn(0, max)
            audioManager.setStreamVolume(stream, clamped, 0)
            SystemControlResult.ok("Volume set to $clamped")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to set volume: ${t.message}")
        }
    }

    fun setScreenRotation(autoRotate: Boolean): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.WRITE_SETTINGS)) {
            return tryPrivileged(
                command = "settings put system accelerometer_rotation ${if (autoRotate) 1 else 0}",
                successMessage = if (autoRotate) "Auto-rotate enabled" else "Auto-rotate disabled"
            )
        }
        return try {
            val written = Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (autoRotate) 1 else 0
            )
            if (written) {
                SystemControlResult.ok(if (autoRotate) "Auto-rotate enabled" else "Auto-rotate disabled")
            } else {
                SystemControlResult.fail("The ROM rejected the rotation change")
            }
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to change rotation: ${t.message}")
        }
    }

    fun launchApp(packageName: String): SystemControlResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return SystemControlResult.fail("No launch intent for $packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            SystemControlResult.ok("Launched $packageName")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to launch $packageName: ${t.message}")
        }
    }

    fun sendNotification(title: String, text: String, channelId: String = "nexaflow_actions"): SystemControlResult {
        return try {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "NexaFlow Actions",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channel)
            }
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(1001, notification)
            SystemControlResult.ok("Notification sent")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to send notification: ${t.message}")
        }
    }

    private fun tryPrivileged(command: String, successMessage: String): SystemControlResult {
        val result = PrivilegedRunner.runShell(command)
        return if (result.success) SystemControlResult.ok(successMessage) else result
    }
}
