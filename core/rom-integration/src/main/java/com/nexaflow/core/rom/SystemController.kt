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
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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
            "DATA" -> Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
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
                .setContentTitle(title)
                .setContentText(text)
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

    companion object {
        /** Notification id used by [sendNotification]; exposed so dismiss buttons can cancel it. */
        const val ACTION_NOTIFICATION_ID = 1001
    }
}
