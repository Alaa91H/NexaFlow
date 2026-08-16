package com.nexaflow.core.execution.handler

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.delay

private fun Boolean.bool(): String = if (this) "1" else "0"

/** Flashlight, URL, status bar, lock screen, alarm, recents/home, stores, SMS, wait. */
class SystemActionsHandler : ActionHandler {
    override val supportedTypes: Set<ActionType> = setOf(
        ActionType.SYSTEM_FLASHLIGHT,
        ActionType.SYSTEM_OPEN_URL,
        ActionType.SYSTEM_EXPAND_STATUS_BAR,
        ActionType.SYSTEM_COLLAPSE_STATUS_BAR,
        ActionType.SYSTEM_LOCK_SCREEN,
        ActionType.SYSTEM_SET_ALARM,
        ActionType.SYSTEM_OPEN_RECENTS,
        ActionType.SYSTEM_GO_HOME,
        ActionType.SYSTEM_OPEN_PLAY_UPDATES,
        ActionType.SYSTEM_OPEN_GALAXY_STORE,
        ActionType.SYSTEM_OPEN_SETTINGS,
        ActionType.SYSTEM_SEND_SMS,
        ActionType.SYSTEM_WAIT,
        ActionType.SYSTEM_POWER_SAVER,
        ActionType.SYSTEM_VIBRATE,
        ActionType.SYSTEM_WAKE_SCREEN,
        ActionType.SYSTEM_CLIPBOARD_SET,
        ActionType.SYSTEM_OPEN_NOTIFICATIONS,
        ActionType.SYSTEM_OPEN_QUICK_SETTINGS,
        ActionType.SYSTEM_SET_SETTING,
        ActionType.SYSTEM_SCREENSHOT,
        ActionType.SYSTEM_INPUT_TEXT,
        ActionType.SYSTEM_KEY_EVENT,
        ActionType.SYSTEM_INPUT_TAP,
        ActionType.SYSTEM_INPUT_SWIPE,
        ActionType.SYSTEM_COLOR_INVERSION,
        ActionType.SYSTEM_GRAYSCALE,
        ActionType.SYSTEM_EXTRA_DIM,
        ActionType.SYSTEM_NIGHT_LIGHT,
        ActionType.SYSTEM_HAPTIC_FEEDBACK,
        ActionType.SYSTEM_SOUND_EFFECTS,
        ActionType.SYSTEM_FORCE_STOP_APP,
        ActionType.SYSTEM_CLEAR_APP_DATA,
        ActionType.SYSTEM_LOCATION_MODE,
        ActionType.SYSTEM_DATA_SAVER,
        ActionType.SYSTEM_FONT_SCALE,
        ActionType.SYSTEM_DISPLAY_DENSITY,
        ActionType.SYSTEM_SCREENSAVER,
        ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD,
        ActionType.SYSTEM_ALWAYS_ON_DISPLAY,
        ActionType.SYSTEM_SHOW_TAPS,
        ActionType.SYSTEM_POINTER_LOCATION,
        ActionType.SYSTEM_ADAPTIVE_BATTERY,
        ActionType.SYSTEM_WIFI_SLEEP_POLICY,
        ActionType.SYSTEM_BLUETOOTH_DISCOVERABILITY,
        ActionType.SYSTEM_AUTO_TIME,
        ActionType.SYSTEM_AUTO_TIMEZONE,
        ActionType.SYSTEM_HAPTIC_INTENSITY,
        ActionType.SYSTEM_CAMERA_SHUTTER_SOUND,
        ActionType.SYSTEM_WIFI_SCANNING,
        ActionType.SYSTEM_OPEN_WIFI_SETTINGS,
        ActionType.SYSTEM_OPEN_BLUETOOTH_SETTINGS,
        ActionType.SYSTEM_OPEN_LOCATION_SETTINGS,
        ActionType.SYSTEM_OPEN_DATA_USAGE_SETTINGS,
        ActionType.SYSTEM_OPEN_BATTERY_SETTINGS,
        ActionType.SYSTEM_OPEN_DISPLAY_SETTINGS,
        ActionType.SYSTEM_OPEN_SOUND_SETTINGS,
        ActionType.SYSTEM_OPEN_STORAGE_SETTINGS,
        ActionType.SYSTEM_OPEN_SECURITY_SETTINGS,
        ActionType.SYSTEM_OPEN_ACCESSIBILITY_SETTINGS,
        ActionType.SYSTEM_OPEN_APP_SETTINGS_LIST,
        ActionType.SYSTEM_OPEN_ABOUT_PHONE,
        ActionType.SYSTEM_MEDIA_FAST_FORWARD,
        ActionType.SYSTEM_MEDIA_REWIND,
        ActionType.SYSTEM_DIAL_NUMBER,
        ActionType.SYSTEM_OPEN_CAMERA,
        ActionType.SYSTEM_OPEN_PLAY_STORE_APP,
        ActionType.SYSTEM_REBOOT,
        ActionType.SYSTEM_SHUTDOWN,
        ActionType.SYSTEM_RESTART_SYSTEM_UI,
        ActionType.SYSTEM_TOAST,
        ActionType.SYSTEM_ALERT,
        ActionType.SYSTEM_VIBRATE_PATTERN,
        ActionType.SYSTEM_PASTE,
        ActionType.SYSTEM_OPEN_APP_DRAWER,
        ActionType.SYSTEM_TOGGLE_PIP,
        ActionType.SYSTEM_WIFI_CONNECT,
        ActionType.SYSTEM_WIFI_FORGET,
        ActionType.SYSTEM_DATA_ROAMING,
        ActionType.SYSTEM_SCREENSAVER_TIMEOUT,
        ActionType.SYSTEM_POINTER_SPEED,
        ActionType.SYSTEM_INSTALL_APK,
        ActionType.SYSTEM_UNINSTALL_APP,
        ActionType.SYSTEM_DISABLE_APP,
        ActionType.SYSTEM_ENABLE_APP,
        ActionType.SYSTEM_SET_NOTIFICATION_TONE,
        ActionType.SYSTEM_CALL_VIBRATION,
        ActionType.SYSTEM_OPEN_NETWORK_SETTINGS,
        ActionType.SYSTEM_OPEN_NFC_SETTINGS,
        ActionType.SYSTEM_OPEN_DATA_SAVER_SETTINGS,
        ActionType.SYSTEM_OPEN_DEVELOPER_SETTINGS,
        ActionType.SYSTEM_OPEN_MAPS,
        ActionType.SYSTEM_SOFT_RESTART,
        ActionType.SYSTEM_STATUS_BAR_TOGGLE,
        ActionType.SYSTEM_OPEN_CONTACTS,
        ActionType.SYSTEM_SEND_EMAIL,
        ActionType.SYSTEM_OPEN_NOTIFICATION_SETTINGS,
        ActionType.SYSTEM_OPEN_PRIVACY_SETTINGS,
        ActionType.SYSTEM_OPEN_CAST_SETTINGS,
        ActionType.SYSTEM_OPEN_INPUT_METHOD_SETTINGS,
        ActionType.SYSTEM_OPEN_DEFAULT_APPS_SETTINGS,
        ActionType.SYSTEM_OPEN_VPN_SETTINGS,
        ActionType.SYSTEM_OPEN_DATE_SETTINGS,
        ActionType.SYSTEM_OPEN_PRINT_SETTINGS,
        ActionType.SYSTEM_OPEN_DEVICE_ADMIN_SETTINGS,
        ActionType.SYSTEM_OPEN_USAGE_ACCESS_SETTINGS,
        ActionType.SYSTEM_OPEN_AIRPLANE_MODE_SETTINGS,
        ActionType.SYSTEM_BLUETOOTH_SCAN,
        ActionType.SYSTEM_WIFI_SCAN_NOW,
        ActionType.SYSTEM_SET_TIMEZONE
    )

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        return when (action.type) {
            ActionType.SYSTEM_FLASHLIGHT ->
                ctx.controller.setFlashlight(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_OPEN_URL ->
                ctx.controller.openUrl(action.config["url"] ?: "")
            ActionType.SYSTEM_EXPAND_STATUS_BAR ->
                ctx.controller.expandStatusBar()
            ActionType.SYSTEM_COLLAPSE_STATUS_BAR ->
                ctx.controller.collapseStatusBar()
            ActionType.SYSTEM_LOCK_SCREEN ->
                ctx.controller.lockScreenNow()
            ActionType.SYSTEM_SET_ALARM ->
                ctx.controller.setAlarm(
                    action.config["hour"]?.toIntOrNull() ?: 7,
                    action.config["minute"]?.toIntOrNull() ?: 0
                )
            ActionType.SYSTEM_OPEN_RECENTS ->
                ctx.controller.openRecents()
            ActionType.SYSTEM_GO_HOME ->
                ctx.controller.goHome()
            ActionType.SYSTEM_OPEN_PLAY_UPDATES ->
                ctx.controller.openPlayStoreUpdates()
            ActionType.SYSTEM_OPEN_GALAXY_STORE ->
                ctx.controller.openGalaxyStore()
            ActionType.SYSTEM_OPEN_SETTINGS ->
                ctx.controller.openSystemSettings(action.config["page"] ?: "")
            ActionType.SYSTEM_SEND_SMS ->
                ctx.controller.sendSms(action.config["number"] ?: "", action.config["text"] ?: "")
            ActionType.SYSTEM_WAIT -> {
                val seconds = action.config["seconds"]?.toIntOrNull()?.coerceIn(1, 3600) ?: 5
                delay(seconds * 1000L)
                SystemControlResult.ok("Waited ${seconds}s")
            }
            ActionType.SYSTEM_POWER_SAVER ->
                ctx.controller.setPowerSaver(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_VIBRATE ->
                ctx.controller.vibrate(action.config["seconds"]?.toIntOrNull() ?: 1)
            ActionType.SYSTEM_WAKE_SCREEN ->
                ctx.controller.wakeScreen()
            ActionType.SYSTEM_CLIPBOARD_SET ->
                ctx.controller.setClipboard(action.config["text"] ?: "")
            ActionType.SYSTEM_OPEN_NOTIFICATIONS ->
                ctx.controller.expandNotifications()
            ActionType.SYSTEM_OPEN_QUICK_SETTINGS ->
                ctx.controller.expandQuickSettings()
            ActionType.SYSTEM_SET_SETTING ->
                ctx.controller.writeSetting(
                    action.config["namespace"] ?: "GLOBAL",
                    action.config["key"] ?: "",
                    action.config["value"] ?: ""
                )
            ActionType.SYSTEM_SCREENSHOT ->
                ctx.controller.screenshot(action.config["filename"] ?: "")
            ActionType.SYSTEM_INPUT_TEXT ->
                ctx.controller.inputText(action.config["text"] ?: "")
            ActionType.SYSTEM_KEY_EVENT ->
                ctx.controller.keyEvent(action.config["key"] ?: "")
            ActionType.SYSTEM_INPUT_TAP ->
                ctx.controller.inputTap(
                    action.config["x"]?.toIntOrNull() ?: 0,
                    action.config["y"]?.toIntOrNull() ?: 0
                )
            ActionType.SYSTEM_INPUT_SWIPE ->
                ctx.controller.inputSwipe(
                    action.config["x1"]?.toIntOrNull() ?: 0,
                    action.config["y1"]?.toIntOrNull() ?: 0,
                    action.config["x2"]?.toIntOrNull() ?: 0,
                    action.config["y2"]?.toIntOrNull() ?: 0,
                    action.config["durationMs"]?.toIntOrNull() ?: 300
                )
            ActionType.SYSTEM_COLOR_INVERSION ->
                ctx.controller.writeSetting(
                    "SECURE", "accessibility_display_inversion_enabled",
                    (action.config["enabled"]?.toBoolean() ?: true).bool()
                )
            ActionType.SYSTEM_GRAYSCALE -> {
                val on = action.config["enabled"]?.toBoolean() ?: true
                // Grayscale = daltonizer monochromacy (mode 12) + enable flag.
                val primary = ctx.controller.writeSetting(
                    "SECURE", "accessibility_display_daltonizer_enabled", on.bool()
                )
                if (on) {
                    ctx.controller.writeSetting("SECURE", "accessibility_display_daltonizer", "12")
                }
                primary
            }
            ActionType.SYSTEM_EXTRA_DIM ->
                ctx.controller.writeSetting(
                    "SECURE", "reduce_bright_colors_activated",
                    (action.config["enabled"]?.toBoolean() ?: true).bool()
                )
            ActionType.SYSTEM_NIGHT_LIGHT ->
                ctx.controller.writeSetting(
                    "SECURE", "night_display_activated",
                    (action.config["enabled"]?.toBoolean() ?: true).bool()
                )
            ActionType.SYSTEM_HAPTIC_FEEDBACK ->
                ctx.controller.writeSetting(
                    "SYSTEM", "haptic_feedback_enabled",
                    (action.config["enabled"]?.toBoolean() ?: true).bool()
                )
            ActionType.SYSTEM_SOUND_EFFECTS ->
                ctx.controller.writeSetting(
                    "SYSTEM", "sound_effects_enabled",
                    (action.config["enabled"]?.toBoolean() ?: true).bool()
                )
            ActionType.SYSTEM_FORCE_STOP_APP ->
                ctx.controller.forceStopApp(action.config["package"] ?: "")
            ActionType.SYSTEM_CLEAR_APP_DATA ->
                ctx.controller.clearAppData(action.config["package"] ?: "")
            ActionType.SYSTEM_LOCATION_MODE ->
                ctx.controller.setLocationMode(action.config["mode"] ?: "HIGH_ACCURACY")
            ActionType.SYSTEM_DATA_SAVER ->
                ctx.controller.setDataSaver(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_FONT_SCALE ->
                ctx.controller.setFontScale(action.config["scale"]?.toFloatOrNull() ?: 1.0f)
            ActionType.SYSTEM_DISPLAY_DENSITY ->
                ctx.controller.setDisplayDensity(action.config["dpi"]?.toIntOrNull() ?: 440)
            ActionType.SYSTEM_SCREENSAVER ->
                ctx.controller.setScreensaver(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD ->
                ctx.controller.setBatterySaverThreshold(action.config["percent"]?.toIntOrNull() ?: 20)
            ActionType.SYSTEM_ALWAYS_ON_DISPLAY ->
                ctx.controller.setAlwaysOnDisplay(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_SHOW_TAPS ->
                ctx.controller.setShowTaps(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_POINTER_LOCATION ->
                ctx.controller.setPointerLocation(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_ADAPTIVE_BATTERY ->
                ctx.controller.setAdaptiveBattery(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_WIFI_SLEEP_POLICY ->
                ctx.controller.setWifiSleepPolicy(action.config["policy"] ?: "ALWAYS")
            ActionType.SYSTEM_BLUETOOTH_DISCOVERABILITY ->
                ctx.controller.setBluetoothDiscoverability(action.config["timeoutSeconds"]?.toIntOrNull() ?: 300)
            ActionType.SYSTEM_AUTO_TIME ->
                ctx.controller.setAutoTime(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_AUTO_TIMEZONE ->
                ctx.controller.setAutoTimezone(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_HAPTIC_INTENSITY ->
                ctx.controller.setHapticIntensity(action.config["level"]?.toIntOrNull() ?: 255)
            ActionType.SYSTEM_CAMERA_SHUTTER_SOUND ->
                ctx.controller.setCameraShutterSound(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_WIFI_SCANNING ->
                ctx.controller.setWifiScanning(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_OPEN_WIFI_SETTINGS ->
                ctx.controller.openSystemSettings("WIFI")
            ActionType.SYSTEM_OPEN_BLUETOOTH_SETTINGS ->
                ctx.controller.openSystemSettings("BLUETOOTH")
            ActionType.SYSTEM_OPEN_LOCATION_SETTINGS ->
                ctx.controller.openSystemSettings("LOCATION")
            ActionType.SYSTEM_OPEN_DATA_USAGE_SETTINGS ->
                ctx.controller.openSystemSettings("DATA_USAGE")
            ActionType.SYSTEM_OPEN_BATTERY_SETTINGS ->
                ctx.controller.openSystemSettings("BATTERY")
            ActionType.SYSTEM_OPEN_DISPLAY_SETTINGS ->
                ctx.controller.openSystemSettings("DISPLAY")
            ActionType.SYSTEM_OPEN_SOUND_SETTINGS ->
                ctx.controller.openSystemSettings("SOUND")
            ActionType.SYSTEM_OPEN_STORAGE_SETTINGS ->
                ctx.controller.openSystemSettings("STORAGE")
            ActionType.SYSTEM_OPEN_SECURITY_SETTINGS ->
                ctx.controller.openSystemSettings("SECURITY")
            ActionType.SYSTEM_OPEN_ACCESSIBILITY_SETTINGS ->
                ctx.controller.openSystemSettings("ACCESSIBILITY")
            ActionType.SYSTEM_OPEN_APP_SETTINGS_LIST ->
                ctx.controller.openSystemSettings("APPS")
            ActionType.SYSTEM_OPEN_ABOUT_PHONE ->
                ctx.controller.openSystemSettings("ABOUT")
            ActionType.SYSTEM_MEDIA_FAST_FORWARD ->
                ctx.controller.mediaControl("FAST_FORWARD")
            ActionType.SYSTEM_MEDIA_REWIND ->
                ctx.controller.mediaControl("REWIND")
            ActionType.SYSTEM_DIAL_NUMBER ->
                ctx.controller.dialNumber(action.config["number"] ?: "")
            ActionType.SYSTEM_OPEN_CAMERA ->
                ctx.controller.openCamera()
            ActionType.SYSTEM_OPEN_PLAY_STORE_APP ->
                ctx.controller.openPlayStoreApp()
            ActionType.SYSTEM_REBOOT ->
                ctx.controller.rebootDevice()
            ActionType.SYSTEM_SHUTDOWN ->
                ctx.controller.shutdownDevice()
            ActionType.SYSTEM_RESTART_SYSTEM_UI ->
                ctx.controller.restartSystemUi()
            ActionType.SYSTEM_TOAST ->
                ctx.controller.showToast(action.config["text"] ?: "")
            ActionType.SYSTEM_ALERT ->
                ctx.controller.showAlert(action.config["title"] ?: "", action.config["text"] ?: "")
            ActionType.SYSTEM_VIBRATE_PATTERN ->
                ctx.controller.vibratePattern(action.config["pattern"] ?: "...")
            ActionType.SYSTEM_PASTE ->
                ctx.controller.pasteClipboard()
            ActionType.SYSTEM_OPEN_APP_DRAWER ->
                ctx.controller.openAppDrawer()
            ActionType.SYSTEM_TOGGLE_PIP ->
                ctx.controller.togglePip()
            ActionType.SYSTEM_WIFI_CONNECT ->
                ctx.controller.wifiConnect(action.config["ssid"] ?: "", action.config["password"] ?: "")
            ActionType.SYSTEM_WIFI_FORGET ->
                ctx.controller.wifiForget(action.config["ssid"] ?: "")
            ActionType.SYSTEM_DATA_ROAMING ->
                ctx.controller.setDataRoaming(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_SCREENSAVER_TIMEOUT ->
                ctx.controller.setScreensaverTimeout(action.config["minutes"]?.toIntOrNull() ?: 10)
            ActionType.SYSTEM_POINTER_SPEED ->
                ctx.controller.setPointerSpeed(action.config["speed"]?.toIntOrNull() ?: 0)
            ActionType.SYSTEM_INSTALL_APK ->
                ctx.controller.installApk(action.config["path"] ?: "")
            ActionType.SYSTEM_UNINSTALL_APP ->
                ctx.controller.uninstallApp(action.config["package"] ?: "")
            ActionType.SYSTEM_DISABLE_APP ->
                ctx.controller.disableApp(action.config["package"] ?: "")
            ActionType.SYSTEM_ENABLE_APP ->
                ctx.controller.enableApp(action.config["package"] ?: "")
            ActionType.SYSTEM_SET_NOTIFICATION_TONE ->
                ctx.controller.setNotificationTone(action.config["tone"] ?: "")
            ActionType.SYSTEM_CALL_VIBRATION ->
                ctx.controller.setCallVibration(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_OPEN_NETWORK_SETTINGS ->
                ctx.controller.openSystemSettings("NETWORK")
            ActionType.SYSTEM_OPEN_NFC_SETTINGS ->
                ctx.controller.openSystemSettings("NFC")
            ActionType.SYSTEM_OPEN_DATA_SAVER_SETTINGS ->
                ctx.controller.openSystemSettings("DATA_SAVER")
            ActionType.SYSTEM_OPEN_DEVELOPER_SETTINGS ->
                ctx.controller.openSystemSettings("DEVELOPER")
            ActionType.SYSTEM_OPEN_MAPS ->
                ctx.controller.openMaps(action.config["lat"] ?: "", action.config["lng"] ?: "")
            ActionType.SYSTEM_SOFT_RESTART ->
                ctx.controller.softRestart()
            ActionType.SYSTEM_STATUS_BAR_TOGGLE ->
                ctx.controller.toggleStatusBar(action.config["show"]?.toBoolean() ?: true)
            ActionType.SYSTEM_OPEN_CONTACTS ->
                ctx.controller.openContacts()
            ActionType.SYSTEM_SEND_EMAIL ->
                ctx.controller.sendEmail(
                    action.config["to"] ?: "",
                    action.config["subject"] ?: "",
                    action.config["body"] ?: ""
                )
            ActionType.SYSTEM_OPEN_NOTIFICATION_SETTINGS ->
                ctx.controller.openSystemSettings("NOTIFICATION_LIST")
            ActionType.SYSTEM_OPEN_PRIVACY_SETTINGS ->
                ctx.controller.openSystemSettings("PRIVACY")
            ActionType.SYSTEM_OPEN_CAST_SETTINGS ->
                ctx.controller.openSystemSettings("CAST")
            ActionType.SYSTEM_OPEN_INPUT_METHOD_SETTINGS ->
                ctx.controller.openSystemSettings("INPUT_METHOD")
            ActionType.SYSTEM_OPEN_DEFAULT_APPS_SETTINGS ->
                ctx.controller.openSystemSettings("DEFAULT_APPS")
            ActionType.SYSTEM_OPEN_VPN_SETTINGS ->
                ctx.controller.openSystemSettings("VPN")
            ActionType.SYSTEM_OPEN_DATE_SETTINGS ->
                ctx.controller.openSystemSettings("DATE")
            ActionType.SYSTEM_OPEN_PRINT_SETTINGS ->
                ctx.controller.openSystemSettings("PRINT")
            ActionType.SYSTEM_OPEN_DEVICE_ADMIN_SETTINGS ->
                ctx.controller.openSystemSettings("DEVICE_ADMIN")
            ActionType.SYSTEM_OPEN_USAGE_ACCESS_SETTINGS ->
                ctx.controller.openSystemSettings("USAGE_ACCESS")
            ActionType.SYSTEM_OPEN_AIRPLANE_MODE_SETTINGS ->
                ctx.controller.openSystemSettings("AIRPLANE_MODE")
            ActionType.SYSTEM_BLUETOOTH_SCAN ->
                ctx.controller.bluetoothScan()
            ActionType.SYSTEM_WIFI_SCAN_NOW ->
                ctx.controller.wifiScanNow()
            ActionType.SYSTEM_SET_TIMEZONE ->
                ctx.controller.setTimezone(action.config["zone"] ?: "")
            else -> SystemControlResult.fail("Unsupported action ${action.type}")
        }
    }
}
