package com.nexaflow.core.rom

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.content.ClipData
import android.content.ClipboardManager
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.net.Uri
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.nexaflow.core.security.SafeCommandBuilder
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.SystemControlResult

@Suppress("TooManyFunctions", "LargeClass") // System-ops façade: many small, single-purpose device operations
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
    // BLUETOOTH_CONNECT is checked at runtime below (required only from API 31+);
    // when missing we fall back to the privileged shell command. Lint's dataflow
    // cannot prove the SDK-conditional guard, so the suppression is scoped here.
    @SuppressLint("MissingPermission")
    fun setBluetooth(enabled: Boolean): SystemControlResult {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return SystemControlResult.fail("Bluetooth is not available")
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        val set = if (hasPermission) {
            runCatching {
                if (enabled) adapter.enable() else adapter.disable()
            }.getOrDefault(false)
        } else {
            false
        }
        return if (set) {
            SystemControlResult.ok(if (enabled) "Bluetooth enabled" else "Bluetooth disabled")
        } else {
            tryPrivileged(
                command = "svc bluetooth ${if (enabled) "enable" else "disable"}",
                successMessage = if (enabled) "Bluetooth enabled" else "Bluetooth disabled"
            )
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
            "STOP" -> KeyEvent.KEYCODE_MEDIA_STOP
            "FAST_FORWARD" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            "REWIND" -> KeyEvent.KEYCODE_MEDIA_REWIND
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

    fun vibrate(durationSeconds: Int): SystemControlResult {
        val ms = durationSeconds.coerceIn(1, 60) * 1000L
        return try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
            SystemControlResult.ok("Vibrated for ${durationSeconds}s")
        } catch (t: Throwable) {
            SystemControlResult.fail("Vibrate failed: ${t.message}")
        }
    }

    /** Briefly wakes the screen (full brightness for ~2 seconds). */
    fun wakeScreen(): SystemControlResult {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wl = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "nexaflow:wake"
            )
            wl.acquire(2000)
            wl.release()
            SystemControlResult.ok("Screen woken")
        } catch (t: Throwable) {
            SystemControlResult.fail("Wake screen failed: ${t.message}")
        }
    }

    /** Copies [text] to the system clipboard. */
    fun setClipboard(text: String): SystemControlResult {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("nexaflow", text))
            SystemControlResult.ok("Clipboard set")
        } catch (t: Throwable) {
            SystemControlResult.fail("Clipboard failed: ${t.message}")
        }
    }

    /** Expands the notification shade (shell path with reflection fallback). */
    fun expandNotifications(): SystemControlResult {
        val shell = PrivilegedRunner.runShell("cmd statusbar expand-notifications")
        if (shell.success) return shell
        return expandStatusBarPanel("expandNotificationsPanel", shell)
    }

    /** Expands the quick-settings panel (shell path with reflection fallback). */
    fun expandQuickSettings(): SystemControlResult {
        val shell = PrivilegedRunner.runShell("cmd statusbar expand-settings")
        if (shell.success) return shell
        return expandStatusBarPanel("expandSettingsPanel", shell)
    }

    private fun expandStatusBarPanel(method: String, fallback: SystemControlResult): SystemControlResult {
        return try {
            val service = context.getSystemService("statusbar")
                ?: return fallback
            RomSystemApiBridge.invokeInstance(service, method)
            SystemControlResult.ok("Status bar expanded ($method)")
        } catch (t: Throwable) {
            fallback
        }
    }

    fun openUrl(url: String): SystemControlResult {
        if (url.isBlank()) return SystemControlResult.fail("No URL configured")
        return try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
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

    /** Screen timeout (seconds). Requires WRITE_SETTINGS or an elevated runtime. */
    fun setScreenTimeout(seconds: Int): SystemControlResult {
        val millis = seconds.coerceIn(10, 1800) * 1000
        return setScreenTimeoutMillis(millis)
    }

    /** Keep the screen on while charging. Requires WRITE_SETTINGS or an elevated runtime. */
    fun setStayAwake(enabled: Boolean): SystemControlResult {
        val value = if (enabled) 1 else 0
        return writeSystemInt(
            name = "stay_on_while_plugged_in",
            value = value,
            successMessage = if (enabled) "Stay-awake enabled" else "Stay-awake disabled"
        )
    }

    /** Toggle automatic (adaptive) brightness. Requires WRITE_SETTINGS or an elevated runtime. */
    fun setAutoBrightness(enabled: Boolean): SystemControlResult {
        val mode = if (enabled) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        return writeSystemInt(
            name = Settings.System.SCREEN_BRIGHTNESS_MODE,
            value = mode,
            successMessage = if (enabled) "Auto-brightness enabled" else "Auto-brightness disabled"
        )
    }

    /** Ringer mode: NORMAL / VIBRATE / SILENT. Needs DND access or elevated runtime. */
    fun setRingerMode(mode: String): SystemControlResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringerMode = when (mode.uppercase()) {
            "VIBRATE" -> AudioManager.RINGER_MODE_VIBRATE
            "SILENT" -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        val label = mode.uppercase().lowercase().replaceFirstChar { it.uppercase() }
        return try {
            if (!capabilityProvider.isAvailable(RomCapability.DND_ACCESS)) {
                return tryPrivileged(
                    command = "cmd audio set_mode $ringerMode",
                    successMessage = "Ringer mode set to $label"
                )
            }
            audioManager.ringerMode = ringerMode
            SystemControlResult.ok("Ringer mode set to $label")
        } catch (t: Throwable) {
            tryPrivileged(
                command = "cmd audio set_mode $ringerMode",
                successMessage = "Ringer mode set to $label"
            ).takeIf { it.success } ?: SystemControlResult.fail("Failed to set ringer mode: ${t.message}")
        }
    }

    /** Toggle mobile data. Requires MODIFY_PHONE_STATE or an elevated runtime. */
    fun setMobileData(enabled: Boolean): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.MODIFY_PHONE_STATE)) {
            return tryPrivileged(
                command = "svc data ${if (enabled) "enable" else "disable"}",
                successMessage = if (enabled) "Mobile data enabled" else "Mobile data disabled"
            )
        }
        return try {
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE)
            RomSystemApiBridge.invokeInstance(telephony, "setDataEnabled", enabled)
            SystemControlResult.ok(if (enabled) "Mobile data enabled" else "Mobile data disabled")
        } catch (t: Throwable) {
            tryPrivileged(
                command = "svc data ${if (enabled) "enable" else "disable"}",
                successMessage = if (enabled) "Mobile data enabled" else "Mobile data disabled"
            )
        }
    }

    /** Toggle Wi-Fi hotspot. Requires an elevated runtime (root/Shizuku/system). */
    fun setHotspot(enabled: Boolean): SystemControlResult {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager == null) {
                return tryPrivileged(
                    command = "cmd connectivity set-softap ${if (enabled) "enable" else "disable"}",
                    successMessage = if (enabled) "Hotspot enabled" else "Hotspot disabled"
                )
            }
            val method = wifiManager.javaClass.getMethod(
                "setWifiApEnabled",
                android.net.wifi.WifiConfiguration::class.java,
                Boolean::class.javaPrimitiveType
            )
            val success = method.invoke(wifiManager, null, enabled) as? Boolean ?: false
            if (success) {
                SystemControlResult.ok(if (enabled) "Hotspot enabled" else "Hotspot disabled")
            } else {
                tryPrivileged(
                    command = "cmd connectivity set-softap ${if (enabled) "enable" else "disable"}",
                    successMessage = if (enabled) "Hotspot enabled" else "Hotspot disabled"
                )
            }
        } catch (t: Throwable) {
            tryPrivileged(
                command = "cmd connectivity set-softap ${if (enabled) "enable" else "disable"}",
                successMessage = if (enabled) "Hotspot enabled" else "Hotspot disabled"
            ).takeIf { it.success } ?: SystemControlResult.fail("Failed to toggle hotspot: ${t.message}")
        }
    }

    /** Toggle NFC. Requires WRITE_SECURE_SETTINGS or an elevated runtime. */
    fun setNfc(enabled: Boolean): SystemControlResult {
        return try {
            val nfcManager = context.getSystemService("nfc")
            // Only trust the reflection path when it actually returned true; a
            // failed reflection must fall through to the shell command instead
            // of reporting a fake success (which left NFC untouched).
            val viaBridge = nfcManager?.let { manager ->
                RomSystemApiBridge.invokeInstance(manager, "setNfcEnabled", enabled) == true
            } ?: false
            if (viaBridge) {
                return SystemControlResult.ok(if (enabled) "NFC enabled" else "NFC disabled")
            }
            tryPrivileged(
                command = "svc nfc ${if (enabled) "enable" else "disable"}",
                successMessage = if (enabled) "NFC enabled" else "NFC disabled"
            )
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to toggle NFC: ${t.message}")
        }
    }

    /** Toggle battery saver. Requires elevated runtime on most ROMs. */
    fun setPowerSaver(enabled: Boolean): SystemControlResult {
        return tryPrivileged(
            command = "settings put global low_power ${if (enabled) 1 else 0}",
            successMessage = if (enabled) "Battery saver enabled" else "Battery saver disabled"
        )
    }

    /** Enable or disable system animations. Requires WRITE_SECURE_SETTINGS or elevated runtime. */
    fun setAnimations(enabled: Boolean): SystemControlResult {
        val scale = if (enabled) 1.0f else 0.0f
        return try {
            val names = listOf(
                Settings.Global.WINDOW_ANIMATION_SCALE,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                Settings.Global.ANIMATOR_DURATION_SCALE
            )
            if (!capabilityProvider.isAvailable(RomCapability.WRITE_SECURE_SETTINGS)) {
                val cmd = names.joinToString(" && ") {
                    "settings put global $it $scale"
                }
                return tryPrivileged(
                    command = cmd,
                    successMessage = if (enabled) "Animations enabled" else "Animations disabled"
                )
            }
            names.forEach {
                Settings.Global.putFloat(context.contentResolver, it, scale)
            }
            SystemControlResult.ok(if (enabled) "Animations enabled" else "Animations disabled")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to change animations: ${t.message}")
        }
    }

    /** Lock the screen now. Needs a device admin or an elevated runtime. */
    fun lockScreenNow(): SystemControlResult {
        return try {
            val policyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
            val dpm = policyManager as android.app.admin.DevicePolicyManager
            val admins = dpm.activeAdmins
            if (admins != null && admins.any { it.packageName == context.packageName }) {
                dpm.lockNow()
                SystemControlResult.ok("Screen locked")
            } else {
                tryPrivileged(
                    command = "input keyevent 26",
                    successMessage = "Screen locked"
                )
            }
        } catch (t: Throwable) {
            tryPrivileged(
                command = "input keyevent 26",
                successMessage = "Screen locked"
            ).takeIf { it.success } ?: SystemControlResult.fail("Failed to lock screen: ${t.message}")
        }
    }

    /** Create an alarm via the system clock app. Works on all devices. */
    fun setAlarm(hour: Int, minute: Int): SystemControlResult {
        return try {
            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
                .putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                .putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            SystemControlResult.ok("Alarm set for %02d:%02d".format(hour, minute))
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to set alarm: ${t.message}")
        }
    }

    /** Force dark mode via ui_mode night override. Requires WRITE_SECURE_SETTINGS or elevated runtime. */
    fun setDarkMode(enabled: Boolean): SystemControlResult {
        val value = if (enabled) 2 else 1
        return writeSecureInt(
            name = "ui_night_mode",
            value = value,
            successMessage = if (enabled) "Dark mode enabled" else "Dark mode disabled"
        )
    }

    /** Open the recent-apps screen. Requires status bar control or elevated runtime. */
    fun openRecents(): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.STATUS_BAR_CONTROL)) {
            return tryPrivileged(
                command = "input keyevent KEYCODE_APP_SWITCH",
                successMessage = "Recents opened"
            )
        }
        return try {
            val service = context.getSystemService("statusbar")
                ?: return SystemControlResult.fail("Status bar service is unavailable")
            RomSystemApiBridge.invokeInstance(service, "toggleRecentApps")
            SystemControlResult.ok("Recents opened")
        } catch (t: Throwable) {
            tryPrivileged(
                command = "input keyevent KEYCODE_APP_SWITCH",
                successMessage = "Recents opened"
            )
        }
    }

    /** Go to the home screen. Requires an elevated runtime or accessibility. */
    fun goHome(): SystemControlResult {
        return tryPrivileged(
            command = "input keyevent KEYCODE_HOME",
            successMessage = "Home screen shown"
        )
    }

    /** Set the ring (incoming call) volume. Requires no special permission. */
    fun setRingVolume(value: Int): SystemControlResult {
        return try {
            val audioManager = context.getSystemService(AudioManager::class.java)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            val clamped = value.coerceIn(0, max)
            audioManager.setStreamVolume(AudioManager.STREAM_RING, clamped, 0)
            SystemControlResult.ok("Ring volume set to $clamped")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to set ring volume: ${t.message}")
        }
    }    /**
     * Forces the preferred cellular network generation (2G/3G/4G/5G).
     * Delegates to [NetworkModeController], which applies the modern
     * per-subscription `setAllowedNetworkTypesForReason` bitmask API with
     * legacy and elevated-shell fallbacks and verifies the result.
     */
    fun setNetworkMode(mode: String): SystemControlResult =
        NetworkModeController(context).setNetworkMode(mode)

    /** The currently configured default ringtone URI, or null when unreadable. */
    fun currentDefaultRingtone(): String? = runCatching {
        android.media.RingtoneManager.getDefaultUri(
            android.media.RingtoneManager.TYPE_RINGTONE
        )?.toString()
    }.getOrNull()

    /**
     * Sets the default ringtone to a content URI (from the ringtone picker).
     * Requires the WRITE_SETTINGS appop; an elevated runtime grants it on the
     * fly and retries once before giving up.
     */
    fun setRingtone(uri: String): SystemControlResult {
        return try {
            android.media.RingtoneManager.setActualDefaultRingtoneUri(
                context,
                android.media.RingtoneManager.TYPE_RINGTONE,
                uri.toUri()
            )
            SystemControlResult.ok("Ringtone set")
        } catch (t: Throwable) {
            val appopGranted = tryPrivileged(
                command = "appops set ${context.packageName} WRITE_SETTINGS allow",
                successMessage = ""
            ).success
            if (!appopGranted) {
                return SystemControlResult.fail("Failed to set ringtone: ${t.message}")
            }
            try {
                android.media.RingtoneManager.setActualDefaultRingtoneUri(
                    context,
                    android.media.RingtoneManager.TYPE_RINGTONE,
                    uri.toUri()
                )
                SystemControlResult.ok("Ringtone set (via elevated appop)")
            } catch (t2: Throwable) {
                SystemControlResult.fail("Failed to set ringtone: ${t2.message}")
            }
        }
    }

    /** Toggle GPS/location. Requires WRITE_SECURE_SETTINGS or an elevated runtime. */
    @Suppress("DEPRECATION")
    fun setLocationEnabled(enabled: Boolean): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.WRITE_SECURE_SETTINGS)) {
            return tryPrivileged(
                command = "settings put secure location_mode ${if (enabled) 3 else 0}",
                successMessage = if (enabled) "Location enabled" else "Location disabled"
            )
        }
        return try {
            val written = Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                if (enabled) Settings.Secure.LOCATION_MODE_HIGH_ACCURACY else Settings.Secure.LOCATION_MODE_OFF
            )
            if (written) {
                SystemControlResult.ok(if (enabled) "Location enabled" else "Location disabled")
            } else {
                tryPrivileged(
                    command = "settings put secure location_mode ${if (enabled) 3 else 0}",
                    successMessage = if (enabled) "Location enabled" else "Location disabled"
                )
            }
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to change location: ${t.message}")
        }
    }

    /** Open the Google Play Store updates page. Works on all devices. */
    fun openPlayStoreUpdates(): SystemControlResult {
        return try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                "https://play.google.com/store/apps".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            SystemControlResult.ok("Opened Play Store updates")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to open Play Store: ${t.message}")
        }
    }

    /** Open the Samsung Galaxy Store (falls back to any installed store). */
    fun openGalaxyStore(): SystemControlResult {
        return try {
            val galaxyIntent = context.packageManager.getLaunchIntentForPackage("com.sec.android.app.samsungapps")
            if (galaxyIntent != null) {
                galaxyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(galaxyIntent)
                SystemControlResult.ok("Opened Galaxy Store")
            } else {
                openPlayStoreUpdates()
            }
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to open Galaxy Store: ${t.message}")
        }
    }

    /** Send an SMS text message. Requires SEND_SMS permission. */
    fun sendSms(number: String, text: String): SystemControlResult {
        if (number.isBlank()) return SystemControlResult.fail("No phone number configured")
        return try {
            val smsManager = context.getSystemService(android.telephony.SmsManager::class.java)
            val parts = smsManager.divideMessage(text.ifBlank { "NexaFlow automation" })
            smsManager.sendMultipartTextMessage(number, null, parts, null, null)
            SystemControlResult.ok("SMS sent to $number")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to send SMS: ${t.message}")
        }
    }

    /** Open a system settings page (Wi-Fi, Bluetooth, location, sound...). */
    fun openSystemSettings(page: String): SystemControlResult {
        val settingsIntent = when (page) {
            "WIFI" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "BLUETOOTH" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "LOCATION" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "SOUND" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "DISPLAY" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "BATTERY" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "NOTIFICATION" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            "DATA_USAGE" -> Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
            "STORAGE" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            "SECURITY" -> Intent(Settings.ACTION_SECURITY_SETTINGS)
            "ACCESSIBILITY" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "APPS" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            "ABOUT" -> Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }
        return try {
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            SystemControlResult.ok("Opened $page settings")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to open settings: ${t.message}")
        }
    }

    /** Open the app details page for a package. Works on all devices. */
    fun openAppSettings(packageName: String): SystemControlResult {
        if (packageName.isBlank()) return SystemControlResult.fail("No app selected")
        return try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:$packageName".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            SystemControlResult.ok("Opened settings for $packageName")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to open app settings: ${t.message}")
        }
    }

    private fun writeSystemInt(name: String, value: Int, successMessage: String): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.WRITE_SETTINGS)) {
            return tryPrivileged(
                command = "settings put system $name $value",
                successMessage = successMessage
            )
        }
        return try {
            val written = Settings.System.putInt(context.contentResolver, name, value)
            if (written) SystemControlResult.ok(successMessage)
            else SystemControlResult.fail("The ROM rejected the change")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to change setting: ${t.message}")
        }
    }

    private fun writeSecureInt(name: String, value: Int, successMessage: String): SystemControlResult {
        if (!capabilityProvider.isAvailable(RomCapability.WRITE_SECURE_SETTINGS)) {
            return tryPrivileged(
                command = "settings put secure $name $value",
                successMessage = successMessage
            )
        }
        return try {
            val written = Settings.Secure.putInt(context.contentResolver, name, value)
            if (written) SystemControlResult.ok(successMessage)
            else SystemControlResult.fail("The ROM rejected the change")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to change setting: ${t.message}")
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

    /**
     * Sends a notification. `sound` controls the alert tone:
     *  - RINGTONE: the default ringtone
     *  - NOTIFICATION: the default notification sound
     *  - BEEP: a single short beep (played even without notification sound)
     *  - SILENT: no sound
     *  - anything else (DEFAULT): the channel default
     *
     * [actions] are attached as interactive action buttons (built upstream with
     * [com.nexaflow.core.execution.NotificationActionButtons]); tapping one
     * routes a PendingIntent broadcast to run a specific task.
     */
    fun sendNotification(
        title: String,
        text: String,
        channelId: String = "nexaflow_actions",
        sound: String = "DEFAULT",
        actions: List<NotificationCompat.Action> = emptyList()
    ): SystemControlResult {
        return try {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                channelId,
                "NexaFlow Actions",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)

            val soundUri = when (sound) {
                "RINGTONE" -> android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                "NOTIFICATION" -> android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                "SILENT" -> null
                else -> null // DEFAULT: use channel sound
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_nexaflow)
                // M3: brand-tinted small icon + action icons.
                // Colorized (API 31+) fills the header with the brand color.
                .setColor(context.getColor(R.color.notification_brand_color))
                .setColorized(true)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                // M3: keep task details off the lock screen.
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
            if (sound == "RINGTONE" || sound == "NOTIFICATION") {
                soundUri?.let { builder.setSound(it) }
            }
            if (sound == "SILENT") {
                builder.setSilent(true)
            }
            actions.forEach { builder.addAction(it) }
            notificationManager.notify(ACTION_NOTIFICATION_ID, builder.build())

            if (sound == "BEEP") {
                playBeep()
            }
            SystemControlResult.ok("Notification sent")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to send notification: ${t.message}")
        }
    }

    /** Plays a single short beep through the notification stream. */
    private fun playBeep() {
        try {
            val toneGenerator = android.media.ToneGenerator(
                android.media.AudioManager.STREAM_NOTIFICATION,
                80
            )
            toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 250)
            // ToneGenerator plays asynchronously; release shortly after the tone.
            android.os.Handler(context.mainLooper).postDelayed({ toneGenerator.release() }, 500L)
        } catch (_: Throwable) {
            // Beep is best-effort.
        }
    }

    private fun tryPrivileged(command: String, successMessage: String): SystemControlResult {
        val result = PrivilegedRunner.runShell(command)
        return if (result.success) SystemControlResult.ok(successMessage) else result
    }

    /**
     * Writes any ROM custom setting (Evolution X / LineageOS Evolver keys)
     * through the [EvolutionXSettingsBridge]. Convenience used by the deep
     * ROM-integration actions.
     */
    fun writeRomSetting(
        namespace: EvolutionXSettingsBridge.Namespace,
        key: String,
        value: String
    ): SystemControlResult =
        EvolutionXSettingsBridge.write(context, namespace, key, value)

    /**
     * Toggles the Quick Settings "smart pulldown" / notification access flags
     * used by LineageOS-family ROMs (e.g. `quick_settings_tiles`). Writes the
     * given Evolver key to 1/0 through the elevated runtime.
     */
    fun setRomToggle(
        key: String,
        enabled: Boolean,
        namespace: EvolutionXSettingsBridge.Namespace = EvolutionXSettingsBridge.Namespace.SECURE
    ): SystemControlResult =
        writeRomSetting(namespace, key, if (enabled) "1" else "0")


    /** Writes any Settings key (SYSTEM/SECURE/GLOBAL) through the shell. */
    fun writeSetting(namespace: String, key: String, value: String): SystemControlResult {
        val ns = when (namespace.uppercase()) {
            "SYSTEM" -> "system"
            "SECURE" -> "secure"
            else -> "global"
        }
        if (key.isBlank()) return SystemControlResult.fail("No settings key configured")
        if (!SafeCommandBuilder.isSafeCommand(value)) {
            return SystemControlResult.fail("Settings value rejected: unsafe characters")
        }
        return try {
            val shell = PrivilegedRunner.runShell(SafeCommandBuilder.build("settings", "put", ns, key, value))
            if (shell.success) SystemControlResult.ok("$ns/$key = $value") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Settings write failed: ${t.message}")
        }
    }

    /** Captures a screenshot to the Pictures/NexaFlow folder. */
    fun screenshot(filename: String): SystemControlResult {
        val safeName = filename.trim().ifBlank { "screenshot_${System.currentTimeMillis()}.png" }
        val safe = safeName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return try {
            val shell = PrivilegedRunner.runShell(
                "mkdir -p /sdcard/Pictures/NexaFlow && " +
                    SafeCommandBuilder.build("screencap", "-p", "/sdcard/Pictures/NexaFlow/$safe")
            )
            if (shell.success) {
                SystemControlResult.ok("Screenshot saved: /sdcard/Pictures/NexaFlow/$safe")
            } else {
                shell
            }
        } catch (t: Throwable) {
            SystemControlResult.fail("Screenshot failed: ${t.message}")
        }
    }

    /** Injects text as if typed (shell `input text`; %s is converted to a space). */
    fun inputText(text: String): SystemControlResult {
        if (text.isBlank()) return SystemControlResult.fail("No text configured")
        if (!SafeCommandBuilder.isSafeCommand(text)) {
            return SystemControlResult.fail("Text rejected: unsafe characters")
        }
        return try {
            // `input text` renders %s as a space, so real spaces must be sent
            // as %s inside the quoted argument.
            val arg = text.replace(" ", "%s")
            val shell = PrivilegedRunner.runShell(SafeCommandBuilder.build("input", "text", arg))
            if (shell.success) SystemControlResult.ok("Text injected") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Text injection failed: ${t.message}")
        }
    }

    /** Injects a key event by preset name or raw KEYCODE number. */
    fun keyEvent(key: String): SystemControlResult {
        val code = key.trim().toIntOrNull() ?: KEYCODES[key.trim().uppercase()] ?: -1
        if (code < 0) return SystemControlResult.fail("Unknown key event: $key")
        return try {
            val shell = PrivilegedRunner.runShell(SafeCommandBuilder.build("input", "keyevent", code.toString()))
            if (shell.success) SystemControlResult.ok("Key event $key sent") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Key event failed: ${t.message}")
        }
    }

    /** Taps at absolute screen coordinates. */
    fun inputTap(x: Int, y: Int): SystemControlResult {
        if (x < 0 || y < 0) return SystemControlResult.fail("Invalid coordinates ($x, $y)")
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("input", "tap", x.toString(), y.toString())
            )
            if (shell.success) SystemControlResult.ok("Tapped ($x, $y)") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Tap failed: ${t.message}")
        }
    }

    /** Swipes between two points with an optional duration in ms. */
    fun inputSwipe(
        x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int
    ): SystemControlResult {
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build(
                    "input", "swipe", x1.toString(), y1.toString(),
                    x2.toString(), y2.toString(), durationMs.toString()
                )
            )
            if (shell.success) SystemControlResult.ok("Swiped ($x1,$y1) -> ($x2,$y2)") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Swipe failed: ${t.message}")
        }
    }

    /** Force-stops an app package (`am force-stop`). */
    fun forceStopApp(pkg: String): SystemControlResult {
        if (pkg.isBlank()) return SystemControlResult.fail("No package configured")
        return try {
            val shell = PrivilegedRunner.runShell(SafeCommandBuilder.build("am", "force-stop", pkg))
            if (shell.success) SystemControlResult.ok("$pkg stopped") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Force-stop failed: ${t.message}")
        }
    }

    /** Clears an app's data (`pm clear`). */
    fun clearAppData(pkg: String): SystemControlResult {
        if (pkg.isBlank()) return SystemControlResult.fail("No package configured")
        return try {
            val shell = PrivilegedRunner.runShell(SafeCommandBuilder.build("pm", "clear", pkg))
            if (shell.success) SystemControlResult.ok("$pkg data cleared") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Clear data failed: ${t.message}")
        }
    }


    /** Sets the location mode: OFF=0, SENSORS_ONLY=1, BATTERY_SAVING=2, HIGH_ACCURACY=3. */
    fun setLocationMode(mode: String): SystemControlResult {
        val code = when (mode.uppercase()) {
            "OFF" -> "0"
            "SENSORS_ONLY" -> "1"
            "BATTERY_SAVING" -> "2"
            "HIGH_ACCURACY" -> "3"
            else -> mode
        }
        if (code !in setOf("0", "1", "2", "3")) return SystemControlResult.fail("Invalid location mode: $mode")
        return try {
            val secure = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("settings", "put", "secure", "location_mode", code)
            )
            if (secure.success) {
                // consent flag required on Android 10+ for high accuracy
                if (code != "0") {
                    PrivilegedRunner.runShell(
                        SafeCommandBuilder.build("settings", "put", "secure", "location_global_consent", "1")
                    )
                }
                SystemControlResult.ok("Location mode set to $mode")
            } else secure
        } catch (t: Throwable) {
            SystemControlResult.fail("Location mode failed: ${t.message}")
        }
    }

    /** Enables/disables the global data saver. */
    fun setDataSaver(enabled: Boolean): SystemControlResult {
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("settings", "put", "global", "data_saver", if (enabled) "1" else "0")
            )
            if (shell.success) SystemControlResult.ok("Data saver ${if (enabled) "on" else "off"}") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Data saver failed: ${t.message}")
        }
    }

    /** Sets the system font scale (e.g. 0.85, 1.0, 1.15, 1.3). */
    fun setFontScale(scale: Float): SystemControlResult {
        if (scale <= 0f || scale > 2f) return SystemControlResult.fail("Invalid font scale: $scale")
        val value = scale.toString()
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("settings", "put", "system", "font_scale", value)
            )
            if (shell.success) SystemControlResult.ok("Font scale $scale") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Font scale failed: ${t.message}")
        }
    }

    /** Sets the display density in dpi (wm density). */
    fun setDisplayDensity(dpi: Int): SystemControlResult {
        if (dpi < 120 || dpi > 1000) return SystemControlResult.fail("Invalid density: $dpi")
        return try {
            val shell = PrivilegedRunner.runShell(SafeCommandBuilder.build("wm", "density", dpi.toString()))
            if (shell.success) SystemControlResult.ok("Density set to $dpi dpi") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Density failed: ${t.message}")
        }
    }

    /** Enables/disables the screensaver (daydream). */
    fun setScreensaver(enabled: Boolean): SystemControlResult {
        val on = if (enabled) "1" else "0"
        return try {
            val secure = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("settings", "put", "secure", "screensaver_enabled", on)
            )
            if (secure.success) SystemControlResult.ok("Screensaver ${if (enabled) "on" else "off"}") else secure
        } catch (t: Throwable) {
            SystemControlResult.fail("Screensaver failed: ${t.message}")
        }
    }

    /** Sets the battery saver trigger level as a percentage (0-100). */
    fun setBatterySaverThreshold(percent: Int): SystemControlResult {
        val p = percent.coerceIn(0, 100)
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("settings", "put", "global", "low_power_trigger_level", p.toString())
            )
            if (shell.success) SystemControlResult.ok("Battery saver at $p%") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Battery threshold failed: ${t.message}")
        }
    }

    /** Enables/disables the always-on display. */
    fun setAlwaysOnDisplay(enabled: Boolean): SystemControlResult {
        val on = if (enabled) "1" else "0"
        return try {
            val secure = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("settings", "put", "secure", "always_on_display_enabled", on)
            )
            if (secure.success) SystemControlResult.ok("Always-on display ${if (enabled) "on" else "off"}") else secure
        } catch (t: Throwable) {
            SystemControlResult.fail("Always-on display failed: ${t.message}")
        }
    }

    /** Shows/hides touch taps. */
    fun setShowTaps(enabled: Boolean): SystemControlResult {
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("settings", "put", "system", "show_touches", if (enabled) "1" else "0")
            )
            if (shell.success) SystemControlResult.ok("Show taps ${if (enabled) "on" else "off"}") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Show taps failed: ${t.message}")
        }
    }

    /** Shows/hides the pointer location overlay. */
    fun setPointerLocation(enabled: Boolean): SystemControlResult {
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("settings", "put", "system", "pointer_location", if (enabled) "1" else "0")
            )
            if (shell.success) SystemControlResult.ok("Pointer location ${if (enabled) "on" else "off"}") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Pointer location failed: ${t.message}")
        }
    }

    /** Enables/disables adaptive battery. */
    fun setAdaptiveBattery(enabled: Boolean): SystemControlResult {
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build(
                    "settings", "put", "global", "adaptive_battery_management_enabled",
                    if (enabled) "1" else "0"
                )
            )
            if (shell.success) SystemControlResult.ok("Adaptive battery ${if (enabled) "on" else "off"}") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Adaptive battery failed: ${t.message}")
        }
    }

    /** Sets the Wi-Fi sleep policy: 0=always on, 1=only when plugged in, 2=never. */
    fun setWifiSleepPolicy(policy: String): SystemControlResult {
        val code = when (policy.uppercase()) {
            "ALWAYS" -> "0"
            "PLUGGED" -> "1"
            "NEVER" -> "2"
            else -> policy
        }
        if (code !in setOf("0", "1", "2")) return SystemControlResult.fail("Invalid Wi-Fi sleep policy: $policy")
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("settings", "put", "global", "wifi_sleep_policy", code)
            )
            if (shell.success) SystemControlResult.ok("Wi-Fi sleep policy $policy") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Wi-Fi sleep policy failed: ${t.message}")
        }
    }

    /** Sets Bluetooth discoverability timeout: 0=never, 150=30s, 300=60s (seconds). */
    fun setBluetoothDiscoverability(timeoutSeconds: Int): SystemControlResult {
        val t = timeoutSeconds.coerceIn(0, 3600)
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("settings", "put", "global", "bluetooth_discoverable_timeout", t.toString())
            )
            if (shell.success) SystemControlResult.ok("Bluetooth discoverable ${t}s") else shell
        } catch (e: Throwable) {
            SystemControlResult.fail("Bluetooth discoverability failed: ${e.message}")
        }
    }

    /** Enables/disables automatic date & time. */
    fun setAutoTime(enabled: Boolean): SystemControlResult {
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("settings", "put", "global", "auto_time", if (enabled) "1" else "0")
            )
            if (shell.success) SystemControlResult.ok("Auto time ${if (enabled) "on" else "off"}") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Auto time failed: ${t.message}")
        }
    }

    /** Enables/disables automatic timezone. */
    fun setAutoTimezone(enabled: Boolean): SystemControlResult {
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("settings", "put", "global", "auto_time_zone", if (enabled) "1" else "0")
            )
            if (shell.success) SystemControlResult.ok("Auto timezone ${if (enabled) "on" else "off"}") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Auto timezone failed: ${t.message}")
        }
    }

    /** Sets haptic feedback intensity (0-255). */
    fun setHapticIntensity(level: Int): SystemControlResult {
        val l = level.coerceIn(0, 255)
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("settings", "put", "system", "haptic_feedback_intensity", l.toString())
            )
            if (shell.success) SystemControlResult.ok("Haptic intensity $l") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Haptic intensity failed: ${t.message}")
        }
    }

    /** Enables/disables the camera shutter sound. */
    fun setCameraShutterSound(enabled: Boolean): SystemControlResult {
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("settings", "put", "system", "camera_sound", if (enabled) "1" else "0")
            )
            if (shell.success) SystemControlResult.ok("Camera sound ${if (enabled) "on" else "off"}") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Camera sound failed: ${t.message}")
        }
    }

    /** Enables/disables always-on Wi-Fi scanning. */
    fun setWifiScanning(enabled: Boolean): SystemControlResult {
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("settings", "put", "global", "wifi_scan_always_enabled", if (enabled) "1" else "0")
            )
            if (shell.success) SystemControlResult.ok("Wi-Fi scanning ${if (enabled) "on" else "off"}") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Wi-Fi scanning failed: ${t.message}")
        }
    }

    /** Dials a phone number via the dialer (no call placed). */
    fun dialNumber(number: String): SystemControlResult {
        if (number.isBlank()) return SystemControlResult.fail("No number configured")
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            SystemControlResult.ok("Dialer opened for $number")
        } catch (t: Throwable) {
            SystemControlResult.fail("Dial failed: ${t.message}")
        }
    }

    /** Opens the camera app. */
    fun openCamera(): SystemControlResult {
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            SystemControlResult.ok("Camera opened")
        } catch (t: Throwable) {
            SystemControlResult.fail("Camera failed: ${t.message}")
        }
    }

    /** Opens the Play Store app itself. */
    fun openPlayStoreApp(): SystemControlResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage("com.android.vending")
                ?: return SystemControlResult.fail("Play Store not installed")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            SystemControlResult.ok("Play Store opened")
        } catch (t: Throwable) {
            SystemControlResult.fail("Play Store failed: ${t.message}")
        }
    }

    /** Reboots the device (requires root). */
    fun rebootDevice(): SystemControlResult =
        tryPrivileged(SafeCommandBuilder.build("reboot"), "Reboot requested")

    /** Shuts the device down (requires root). */
    fun shutdownDevice(): SystemControlResult =
        tryPrivileged(SafeCommandBuilder.build("reboot", "-p"), "Shutdown requested")

    /** Restarts the System UI process (requires root; falls back to a soft refresh). */
    fun restartSystemUi(): SystemControlResult {
        val shell = PrivilegedRunner.runShell(SafeCommandBuilder.build("pkill", "-f", "com.android.systemui"))
        if (shell.success) return SystemControlResult.ok("System UI restarted")
        // Some Roms expose the stopservice path through cmd; otherwise report the failure.
        val fallback = PrivilegedRunner.runShell(
            SafeCommandBuilder.build("am", "broadcast", "-a", "android.intent.action.CLOSE_SYSTEM_DIALOGS")
        )
        return if (fallback.success) {
            SystemControlResult.ok("System UI refresh requested")
        } else {
            SystemControlResult.fail("System UI restart requires root: ${shell.message}")
        }
    }

    companion object {
        /** Notification id used by [sendNotification]; exposed so dismiss buttons can cancel it. */
        const val ACTION_NOTIFICATION_ID = 1001

        /** Preset names -> android.view.KeyEvent KEYCODE values for [keyEvent]. */
        private val KEYCODES = mapOf(
            "POWER" to 26, "BACK" to 4, "HOME" to 3, "MENU" to 82, "CAMERA" to 27,
            "RECENTS" to 187, "SEARCH" to 84, "NOTIFICATIONS" to 83, "CALL" to 5,
            "ENDCALL" to 6, "VOLUME_UP" to 24, "VOLUME_DOWN" to 25, "MUTE" to 91,
            "MEDIA_PLAY_PAUSE" to 85, "MEDIA_NEXT" to 87, "MEDIA_PREVIOUS" to 88,
            "MEDIA_STOP" to 86, "MEDIA_REWIND" to 89, "MEDIA_FAST_FORWARD" to 90,
            "BRIGHTNESS_UP" to 220, "BRIGHTNESS_DOWN" to 221, "DPAD_UP" to 19,
            "DPAD_DOWN" to 20, "DPAD_LEFT" to 21, "DPAD_RIGHT" to 22, "DPAD_CENTER" to 23,
            "ENTER" to 66, "TAB" to 61, "SPACE" to 62, "DEL" to 67, "ESCAPE" to 111,
            "SCREENSHOT" to 120, "SLEEP" to 223, "WAKEUP" to 224, "APP_SWITCH" to 187
        )
    }
}
