package com.nexaflow.core.rom

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.SystemControlResult

class SystemController(
    private val context: Context,
    private val capabilityProvider: RomCapabilityProvider
) {
    // STATUS_BAR_SERVICE is a hidden constant not in the public SDK; the raw
    // service name is used intentionally for privileged ROM integration.
    @SuppressLint("WrongConstant")
    fun expandStatusBar(): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.STATUS_BAR_CONTROL)) {
            return SystemControlResult.fail("Status bar control is not available at the current integration level")
        }
        return try {
            val service = context.getSystemService("statusbar")
                ?: return SystemControlResult.fail("Status bar service is unavailable")
            RomSystemApiBridge.invokeInstance(service, "expandNotificationsPanel")
            SystemControlResult.ok("Status bar expanded")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to expand status bar: ${t.message}")
        }
    }

    @SuppressLint("WrongConstant")
    fun collapseStatusBar(): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.STATUS_BAR_CONTROL)) {
            return SystemControlResult.fail("Status bar control is not available at the current integration level")
        }
        return try {
            val service = context.getSystemService("statusbar")
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

    @Suppress("DEPRECATION")
    fun setWifi(enabled: Boolean): SystemControlResult {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return SystemControlResult.fail("Wi-Fi service unavailable")
            val set = try {
                wifiManager.setWifiEnabled(enabled)
            } catch (_: Throwable) {
                false
            }
            if (set) {
                SystemControlResult.ok(if (enabled) "Wi-Fi enabled" else "Wi-Fi disabled")
            } else {
                tryPrivileged(
                    command = "svc wifi ${if (enabled) "enable" else "disable"}",
                    successMessage = if (enabled) "Wi-Fi enabled" else "Wi-Fi disabled"
                )
            }
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to change Wi-Fi: ${t.message}")
        }
    }

    @Suppress("DEPRECATION")
    fun setBluetooth(enabled: Boolean): SystemControlResult {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return SystemControlResult.fail("Bluetooth is not available")
            val set = try {
                if (enabled) adapter.enable() else adapter.disable()
            } catch (_: Throwable) {
                false
            }
            if (set) {
                SystemControlResult.ok(if (enabled) "Bluetooth enabled" else "Bluetooth disabled")
            } else {
                tryPrivileged(
                    command = "svc bluetooth ${if (enabled) "enable" else "disable"}",
                    successMessage = if (enabled) "Bluetooth enabled" else "Bluetooth disabled"
                )
            }
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to change Bluetooth: ${t.message}")
        }
    }

    fun setFlashlight(enabled: Boolean): SystemControlResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                runCatching {
                    cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }.getOrDefault(false)
            } ?: return SystemControlResult.fail("No camera with a flashlight was found")
            cameraManager.setTorchMode(cameraId, enabled)
            SystemControlResult.ok(if (enabled) "Flashlight turned on" else "Flashlight turned off")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to control the flashlight: ${t.message}")
        }
    }

    fun setAirplaneMode(enabled: Boolean): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.WRITE_SECURE_SETTINGS)) {
            return tryPrivileged(
                command = "settings put global airplane_mode_on ${if (enabled) 1 else 0} && " +
                    "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $enabled",
                successMessage = if (enabled) "Airplane mode enabled" else "Airplane mode disabled"
            )
        }
        return try {
            val written = Settings.Global.putInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                if (enabled) 1 else 0
            )
            if (written) {
                context.sendBroadcast(
                    Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", enabled)
                )
                SystemControlResult.ok(if (enabled) "Airplane mode enabled" else "Airplane mode disabled")
            } else {
                SystemControlResult.fail("The ROM rejected the airplane mode change")
            }
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to change airplane mode: ${t.message}")
        }
    }

    fun mediaControl(command: String): SystemControlResult {
        val keyCode = when (command) {
            "NEXT" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "PREVIOUS" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            SystemControlResult.ok("Media command sent ($command)")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to send media command: ${t.message}")
        }
    }

    fun openUrl(url: String): SystemControlResult {
        if (url.isBlank()) return SystemControlResult.fail("No URL configured")
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            SystemControlResult.ok("Opened URL")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to open URL: ${t.message}")
        }
    }

    fun clearNotifications(): SystemControlResult {
        return try {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.cancelAll()
            tryPrivileged(
                command = "cmd notification cancel_all",
                successMessage = "Notifications cleared"
            )
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to clear notifications: ${t.message}")
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
            val channel = NotificationChannel(
                channelId,
                "NexaFlow Actions",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
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
