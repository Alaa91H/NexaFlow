package com.nexaflow.feature.automations

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.DataSaver
import androidx.compose.material.icons.filled.PortableWifiOff
import androidx.compose.material.icons.filled.SettingsOverscan
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexaflow.domain.models.ActionType

/**
 * Shared localized presentation of an [ActionType]: (titleRes, subtitleRes,
 * icon). Lives in this module so the run-details timeline and the automation
 * details screen render action names identically.
 */
fun actionPresentation(type: ActionType): Triple<Int, Int, ImageVector> = when (type) {
    ActionType.SYSTEM_BRIGHTNESS -> Triple(R.string.action_brightness, R.string.action_brightness_sub, Icons.Filled.BrightnessHigh)
    ActionType.SYSTEM_VOLUME -> Triple(R.string.action_volume, R.string.action_volume_sub, Icons.AutoMirrored.Filled.VolumeUp)
    ActionType.SYSTEM_STREAM_VOLUME -> Triple(R.string.action_stream_volume, R.string.action_stream_volume_sub, Icons.AutoMirrored.Filled.VolumeUp)
    ActionType.SYSTEM_DND -> Triple(R.string.action_dnd, R.string.action_dnd_sub, Icons.Filled.DoNotDisturb)
    ActionType.SYSTEM_SCREEN_ROTATION -> Triple(R.string.action_rotation, R.string.action_rotation_sub, Icons.Filled.ScreenRotation)
    ActionType.SYSTEM_OPEN_APP -> Triple(R.string.action_open_apps, R.string.action_open_apps_sub, Icons.Filled.Apps)
    ActionType.SYSTEM_SEND_NOTIFICATION -> Triple(R.string.action_notification, R.string.action_notification_sub, Icons.Filled.NotificationImportant)
    ActionType.SYSTEM_WIFI -> Triple(R.string.action_wifi, R.string.action_wifi_sub, Icons.Filled.Wifi)
    ActionType.SYSTEM_BLUETOOTH -> Triple(R.string.action_bluetooth, R.string.action_bluetooth_sub, Icons.Filled.Bluetooth)
    ActionType.SYSTEM_FLASHLIGHT -> Triple(R.string.action_flashlight, R.string.action_flashlight_sub, Icons.Filled.FlashOn)
    ActionType.SYSTEM_AIRPLANE_MODE -> Triple(R.string.action_airplane, R.string.action_airplane_sub, Icons.Filled.AirplanemodeActive)
    ActionType.SYSTEM_MEDIA_PLAY_PAUSE -> Triple(R.string.action_media_play, R.string.action_media_play_sub, Icons.Filled.MusicNote)
    ActionType.SYSTEM_MEDIA_NEXT -> Triple(R.string.action_media_next, R.string.action_media_next_sub, Icons.Filled.SkipNext)
    ActionType.SYSTEM_MEDIA_PREVIOUS -> Triple(R.string.action_media_prev, R.string.action_media_prev_sub, Icons.Filled.SkipPrevious)
    ActionType.SYSTEM_OPEN_URL -> Triple(R.string.action_open_url, R.string.action_open_url_sub, Icons.Filled.Link)
    ActionType.SYSTEM_BLOCK_NOTIFICATION -> Triple(R.string.action_block_notification, R.string.action_block_notification_sub, Icons.Filled.NotificationsActive)
    ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS -> Triple(R.string.action_clear_app_notifications, R.string.action_clear_app_notifications_sub, Icons.Filled.Notifications)
    ActionType.SYSTEM_CLEAR_NOTIFICATIONS -> Triple(R.string.action_clear_notifs, R.string.action_clear_notifs_sub, Icons.Filled.Notifications)
    ActionType.SYSTEM_EXPAND_STATUS_BAR -> Triple(R.string.action_expand_bar, R.string.action_expand_bar_sub, Icons.Filled.NotificationImportant)
    ActionType.SYSTEM_COLLAPSE_STATUS_BAR -> Triple(R.string.action_collapse_bar, R.string.action_collapse_bar_sub, Icons.Filled.NotificationImportant)
    ActionType.SYSTEM_SCREEN_TIMEOUT -> Triple(R.string.action_screen_timeout, R.string.action_screen_timeout_sub, Icons.Filled.Timer)
    ActionType.SYSTEM_STAY_AWAKE -> Triple(R.string.action_stay_awake, R.string.action_stay_awake_sub, Icons.Filled.WbSunny)
    ActionType.SYSTEM_AUTO_BRIGHTNESS -> Triple(R.string.action_auto_brightness, R.string.action_auto_brightness_sub, Icons.Filled.BrightnessAuto)
    ActionType.SYSTEM_RINGER_MODE -> Triple(R.string.action_ringer, R.string.action_ringer_sub, Icons.AutoMirrored.Filled.VolumeUp)
    ActionType.SYSTEM_MOBILE_DATA -> Triple(R.string.action_mobile_data, R.string.action_mobile_data_sub, Icons.Filled.SignalCellularAlt)
    ActionType.SYSTEM_NETWORK_MODE -> Triple(R.string.action_network_mode, R.string.action_network_mode_sub, Icons.Filled.SignalCellularAlt)
    ActionType.SYSTEM_HOTSPOT -> Triple(R.string.action_hotspot, R.string.action_hotspot_sub, Icons.Filled.PortableWifiOff)
    ActionType.SYSTEM_SET_RINGTONE -> Triple(R.string.action_set_ringtone, R.string.action_set_ringtone_sub, Icons.Filled.MusicNote)
    ActionType.SYSTEM_NFC -> Triple(R.string.action_nfc, R.string.action_nfc_sub, Icons.Filled.Nfc)
    ActionType.SYSTEM_POWER_SAVER -> Triple(R.string.action_power_saver, R.string.action_power_saver_sub, Icons.Filled.BatteryChargingFull)
    ActionType.SYSTEM_ANIMATIONS -> Triple(R.string.action_animations, R.string.action_animations_sub, Icons.Filled.Animation)
    ActionType.SYSTEM_LOCK_SCREEN -> Triple(R.string.action_lock_screen, R.string.action_lock_screen_sub, Icons.Filled.Lock)
    ActionType.SYSTEM_SET_ALARM -> Triple(R.string.action_set_alarm, R.string.action_set_alarm_sub, Icons.Filled.Schedule)
    ActionType.SYSTEM_DARK_MODE -> Triple(R.string.action_dark_mode, R.string.action_dark_mode_sub, Icons.Filled.DarkMode)
    ActionType.SYSTEM_OPEN_RECENTS -> Triple(R.string.action_open_recents, R.string.action_open_recents_sub, Icons.Filled.Apps)
    ActionType.SYSTEM_GO_HOME -> Triple(R.string.action_go_home, R.string.action_go_home_sub, Icons.Filled.Home)
    ActionType.APPLICATION_OPEN_APP_SETTINGS -> Triple(R.string.action_open_app_settings, R.string.action_open_app_settings_sub, Icons.Filled.Settings)
    ActionType.SYSTEM_RING_VOLUME -> Triple(R.string.action_ring_volume, R.string.action_ring_volume_sub, Icons.AutoMirrored.Filled.VolumeUp)
    ActionType.SYSTEM_LOCATION -> Triple(R.string.action_location, R.string.action_location_sub, Icons.Filled.Place)
    ActionType.SYSTEM_OPEN_PLAY_UPDATES -> Triple(R.string.action_play_updates, R.string.action_play_updates_sub, Icons.Filled.Store)
    ActionType.SYSTEM_OPEN_GALAXY_STORE -> Triple(R.string.action_galaxy_store, R.string.action_galaxy_store_sub, Icons.Filled.Store)
    ActionType.SYSTEM_VIBRATE -> Triple(R.string.action_vibrate, R.string.action_vibrate_sub, Icons.Filled.Vibration)
    ActionType.SYSTEM_WAKE_SCREEN -> Triple(R.string.action_wake_screen, R.string.action_wake_screen_sub, Icons.Filled.WbSunny)
    ActionType.SYSTEM_CLIPBOARD_SET -> Triple(R.string.action_clipboard, R.string.action_clipboard_sub, Icons.Filled.ContentPaste)
    ActionType.SYSTEM_MEDIA_STOP -> Triple(R.string.action_media_stop, R.string.action_media_stop_sub, Icons.Filled.Stop)
    ActionType.SYSTEM_OPEN_NOTIFICATIONS -> Triple(R.string.action_open_notifications, R.string.action_open_notifications_sub, Icons.Filled.Notifications)
    ActionType.SYSTEM_OPEN_QUICK_SETTINGS -> Triple(R.string.action_open_quick_settings, R.string.action_open_quick_settings_sub, Icons.Filled.Settings)
    ActionType.SYSTEM_SEND_SMS -> Triple(R.string.action_send_sms, R.string.action_send_sms_sub, Icons.AutoMirrored.Filled.Message)
    ActionType.SYSTEM_SEND_REMINDER -> Triple(R.string.action_reminder, R.string.action_reminder_sub, Icons.Filled.Schedule)
    ActionType.SYSTEM_OPEN_SETTINGS -> Triple(R.string.action_open_settings, R.string.action_open_settings_sub, Icons.Filled.Settings)
    ActionType.SYSTEM_WAIT -> Triple(R.string.action_wait, R.string.action_wait_sub, Icons.Filled.Schedule)
    ActionType.BATTERY_ALERTS -> Triple(R.string.action_battery_alert, R.string.action_battery_alert_sub, Icons.Filled.BatteryChargingFull)
    ActionType.BATTERY_CHARGING_NOTIFICATIONS -> Triple(R.string.action_charging_alert, R.string.action_charging_alert_sub, Icons.Filled.BatteryChargingFull)
    ActionType.APPLICATION_LAUNCH_APP -> Triple(R.string.action_open_apps, R.string.action_open_apps_sub, Icons.Filled.Add)
    ActionType.APPLICATION_CLOSE_APP -> Triple(R.string.action_close_app, R.string.action_close_app_sub, Icons.Filled.Close)
    ActionType.ADVANCED_SHIZUKU -> Triple(R.string.action_shizuku, R.string.action_shizuku_sub, Icons.Filled.Security)
    ActionType.ADVANCED_ROOT -> Triple(R.string.action_root, R.string.action_root_sub, Icons.Filled.Lock)
    ActionType.SYSTEM_HTTP_REQUEST -> Triple(R.string.action_http_request, R.string.action_http_request_sub, Icons.Filled.Public)
    ActionType.PLUGIN_FIRE -> Triple(R.string.action_plugin, R.string.action_plugin_sub, Icons.Filled.Extension)
    ActionType.SYSTEM_SET_SETTING -> Triple(R.string.action_set_setting, R.string.action_set_setting_sub, Icons.Filled.Tune)
    ActionType.SYSTEM_SCREENSHOT -> Triple(R.string.action_screenshot, R.string.action_screenshot_sub, Icons.Filled.CameraAlt)
    ActionType.SYSTEM_INPUT_TEXT -> Triple(R.string.action_input_text, R.string.action_input_text_sub, Icons.Filled.Chat)
    ActionType.SYSTEM_KEY_EVENT -> Triple(R.string.action_key_event, R.string.action_key_event_sub, Icons.Filled.Build)
    ActionType.SYSTEM_INPUT_TAP -> Triple(R.string.action_input_tap, R.string.action_input_tap_sub, Icons.Filled.GpsFixed)
    ActionType.SYSTEM_INPUT_SWIPE -> Triple(R.string.action_input_swipe, R.string.action_input_swipe_sub, Icons.AutoMirrored.Filled.ArrowForward)
    ActionType.SYSTEM_COLOR_INVERSION -> Triple(R.string.action_color_inversion, R.string.action_color_inversion_sub, Icons.Filled.Contrast)
    ActionType.SYSTEM_GRAYSCALE -> Triple(R.string.action_grayscale, R.string.action_grayscale_sub, Icons.Filled.Gradient)
    ActionType.SYSTEM_EXTRA_DIM -> Triple(R.string.action_extra_dim, R.string.action_extra_dim_sub, Icons.Filled.BrightnessLow)
    ActionType.SYSTEM_NIGHT_LIGHT -> Triple(R.string.action_night_light, R.string.action_night_light_sub, Icons.Filled.NightsStay)
    ActionType.SYSTEM_HAPTIC_FEEDBACK -> Triple(R.string.action_haptic_feedback, R.string.action_haptic_feedback_sub, Icons.Filled.GraphicEq)
    ActionType.SYSTEM_SOUND_EFFECTS -> Triple(R.string.action_sound_effects, R.string.action_sound_effects_sub, Icons.Filled.GraphicEq)
    ActionType.SYSTEM_FORCE_STOP_APP -> Triple(R.string.action_force_stop_app, R.string.action_force_stop_app_sub, Icons.Filled.Stop)
    ActionType.SYSTEM_CLEAR_APP_DATA -> Triple(R.string.action_clear_app_data, R.string.action_clear_app_data_sub, Icons.Filled.DeleteSweep)
    ActionType.SYSTEM_LOCATION_MODE -> Triple(R.string.action_location_mode, R.string.action_location_mode_sub, Icons.Filled.Place)
    ActionType.SYSTEM_DATA_SAVER -> Triple(R.string.action_data_saver, R.string.action_data_saver_sub, Icons.Filled.DataUsage)
    ActionType.SYSTEM_FONT_SCALE -> Triple(R.string.action_font_scale, R.string.action_font_scale_sub, Icons.Filled.TextFields)
    ActionType.SYSTEM_DISPLAY_DENSITY -> Triple(R.string.action_display_density, R.string.action_display_density_sub, Icons.Filled.SettingsOverscan)
    ActionType.SYSTEM_SCREENSAVER -> Triple(R.string.action_screensaver, R.string.action_screensaver_sub, Icons.Filled.BrightnessLow)
    ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD -> Triple(R.string.action_battery_saver_threshold, R.string.action_battery_saver_threshold_sub, Icons.Filled.BatteryChargingFull)
    ActionType.SYSTEM_ALWAYS_ON_DISPLAY -> Triple(R.string.action_always_on_display, R.string.action_always_on_display_sub, Icons.Filled.WbSunny)
    ActionType.SYSTEM_SHOW_TAPS -> Triple(R.string.action_show_taps, R.string.action_show_taps_sub, Icons.Filled.TouchApp)
    ActionType.SYSTEM_POINTER_LOCATION -> Triple(R.string.action_pointer_location, R.string.action_pointer_location_sub, Icons.Filled.GpsFixed)
    ActionType.SYSTEM_ADAPTIVE_BATTERY -> Triple(R.string.action_adaptive_battery, R.string.action_adaptive_battery_sub, Icons.Filled.BatteryChargingFull)
    ActionType.SYSTEM_WIFI_SLEEP_POLICY -> Triple(R.string.action_wifi_sleep_policy, R.string.action_wifi_sleep_policy_sub, Icons.Filled.Wifi)
    ActionType.SYSTEM_BLUETOOTH_DISCOVERABILITY -> Triple(R.string.action_bluetooth_discoverability, R.string.action_bluetooth_discoverability_sub, Icons.Filled.Bluetooth)
    ActionType.SYSTEM_AUTO_TIME -> Triple(R.string.action_auto_time, R.string.action_auto_time_sub, Icons.Filled.Schedule)
    ActionType.SYSTEM_AUTO_TIMEZONE -> Triple(R.string.action_auto_timezone, R.string.action_auto_timezone_sub, Icons.Filled.Public)
    ActionType.SYSTEM_HAPTIC_INTENSITY -> Triple(R.string.action_haptic_intensity, R.string.action_haptic_intensity_sub, Icons.Filled.GraphicEq)
    ActionType.SYSTEM_CAMERA_SHUTTER_SOUND -> Triple(R.string.action_camera_shutter_sound, R.string.action_camera_shutter_sound_sub, Icons.Filled.CameraAlt)
    ActionType.SYSTEM_WIFI_SCANNING -> Triple(R.string.action_wifi_scanning, R.string.action_wifi_scanning_sub, Icons.Filled.Wifi)
    ActionType.SYSTEM_OPEN_WIFI_SETTINGS -> Triple(R.string.action_open_wifi_settings, R.string.action_open_wifi_settings_sub, Icons.Filled.Wifi)
    ActionType.SYSTEM_OPEN_BLUETOOTH_SETTINGS -> Triple(R.string.action_open_bluetooth_settings, R.string.action_open_bluetooth_settings_sub, Icons.Filled.Bluetooth)
    ActionType.SYSTEM_OPEN_LOCATION_SETTINGS -> Triple(R.string.action_open_location_settings, R.string.action_open_location_settings_sub, Icons.Filled.Place)
    ActionType.SYSTEM_OPEN_DATA_USAGE_SETTINGS -> Triple(R.string.action_open_data_usage_settings, R.string.action_open_data_usage_settings_sub, Icons.Filled.DataUsage)
    ActionType.SYSTEM_OPEN_BATTERY_SETTINGS -> Triple(R.string.action_open_battery_settings, R.string.action_open_battery_settings_sub, Icons.Filled.BatteryChargingFull)
    ActionType.SYSTEM_OPEN_DISPLAY_SETTINGS -> Triple(R.string.action_open_display_settings, R.string.action_open_display_settings_sub, Icons.Filled.ScreenRotation)
    ActionType.SYSTEM_OPEN_SOUND_SETTINGS -> Triple(R.string.action_open_sound_settings, R.string.action_open_sound_settings_sub, Icons.AutoMirrored.Filled.VolumeUp)
    ActionType.SYSTEM_OPEN_STORAGE_SETTINGS -> Triple(R.string.action_open_storage_settings, R.string.action_open_storage_settings_sub, Icons.Filled.Storage)
    ActionType.SYSTEM_OPEN_SECURITY_SETTINGS -> Triple(R.string.action_open_security_settings, R.string.action_open_security_settings_sub, Icons.Filled.Security)
    ActionType.SYSTEM_OPEN_ACCESSIBILITY_SETTINGS -> Triple(R.string.action_open_accessibility_settings, R.string.action_open_accessibility_settings_sub, Icons.Filled.Accessibility)
    ActionType.SYSTEM_OPEN_APP_SETTINGS_LIST -> Triple(R.string.action_open_app_settings_list, R.string.action_open_app_settings_list_sub, Icons.Filled.Apps)
    ActionType.SYSTEM_OPEN_ABOUT_PHONE -> Triple(R.string.action_open_about_phone, R.string.action_open_about_phone_sub, Icons.Filled.Info)
    ActionType.SYSTEM_MEDIA_FAST_FORWARD -> Triple(R.string.action_media_fast_forward, R.string.action_media_fast_forward_sub, Icons.Filled.FastForward)
    ActionType.SYSTEM_MEDIA_REWIND -> Triple(R.string.action_media_rewind, R.string.action_media_rewind_sub, Icons.Filled.FastRewind)
    ActionType.SYSTEM_DIAL_NUMBER -> Triple(R.string.action_dial_number, R.string.action_dial_number_sub, Icons.Filled.Phone)
    ActionType.SYSTEM_OPEN_CAMERA -> Triple(R.string.action_open_camera, R.string.action_open_camera_sub, Icons.Filled.CameraAlt)
    ActionType.SYSTEM_OPEN_PLAY_STORE_APP -> Triple(R.string.action_open_play_store_app, R.string.action_open_play_store_app_sub, Icons.Filled.Store)
    ActionType.SYSTEM_REBOOT -> Triple(R.string.action_reboot, R.string.action_reboot_sub, Icons.Filled.Refresh)
    ActionType.SYSTEM_SHUTDOWN -> Triple(R.string.action_shutdown, R.string.action_shutdown_sub, Icons.Filled.PowerSettingsNew)
    ActionType.SYSTEM_RESTART_SYSTEM_UI -> Triple(R.string.action_restart_system_ui, R.string.action_restart_system_ui_sub, Icons.Filled.Refresh)
    ActionType.SYSTEM_TOAST -> Triple(R.string.action_toast, R.string.action_toast_sub, Icons.Filled.Info)
    ActionType.SYSTEM_ALERT -> Triple(R.string.action_alert, R.string.action_alert_sub, Icons.Filled.Warning)
    ActionType.SYSTEM_VIBRATE_PATTERN -> Triple(R.string.action_vibrate_pattern, R.string.action_vibrate_pattern_sub, Icons.Filled.Vibration)
    ActionType.SYSTEM_PASTE -> Triple(R.string.action_paste, R.string.action_paste_sub, Icons.Filled.ContentPaste)
    ActionType.SYSTEM_OPEN_APP_DRAWER -> Triple(R.string.action_open_app_drawer, R.string.action_open_app_drawer_sub, Icons.Filled.Apps)
    ActionType.SYSTEM_TOGGLE_PIP -> Triple(R.string.action_toggle_pip, R.string.action_toggle_pip_sub, Icons.Filled.PictureInPicture)
    ActionType.SYSTEM_WIFI_CONNECT -> Triple(R.string.action_wifi_connect, R.string.action_wifi_connect_sub, Icons.Filled.Wifi)
    ActionType.SYSTEM_WIFI_FORGET -> Triple(R.string.action_wifi_forget, R.string.action_wifi_forget_sub, Icons.Filled.WifiOff)
    ActionType.SYSTEM_DATA_ROAMING -> Triple(R.string.action_data_roaming, R.string.action_data_roaming_sub, Icons.Filled.DataUsage)
    ActionType.SYSTEM_SCREENSAVER_TIMEOUT -> Triple(R.string.action_screensaver_timeout, R.string.action_screensaver_timeout_sub, Icons.Filled.Timelapse)
    ActionType.SYSTEM_POINTER_SPEED -> Triple(R.string.action_pointer_speed, R.string.action_pointer_speed_sub, Icons.Filled.GpsFixed)
    ActionType.SYSTEM_INSTALL_APK -> Triple(R.string.action_install_apk, R.string.action_install_apk_sub, Icons.Filled.Download)
    ActionType.SYSTEM_UNINSTALL_APP -> Triple(R.string.action_uninstall_app, R.string.action_uninstall_app_sub, Icons.Filled.Delete)
    ActionType.SYSTEM_DISABLE_APP -> Triple(R.string.action_disable_app, R.string.action_disable_app_sub, Icons.Filled.Block)
    ActionType.SYSTEM_ENABLE_APP -> Triple(R.string.action_enable_app, R.string.action_enable_app_sub, Icons.Filled.CheckCircle)
    ActionType.SYSTEM_SET_NOTIFICATION_TONE -> Triple(R.string.action_set_notification_tone, R.string.action_set_notification_tone_sub, Icons.Filled.MusicNote)
    ActionType.SYSTEM_CALL_VIBRATION -> Triple(R.string.action_call_vibration, R.string.action_call_vibration_sub, Icons.Filled.Vibration)
    ActionType.SYSTEM_OPEN_NETWORK_SETTINGS -> Triple(R.string.action_open_network_settings, R.string.action_open_network_settings_sub, Icons.Filled.Wifi)
    ActionType.SYSTEM_OPEN_NFC_SETTINGS -> Triple(R.string.action_open_nfc_settings, R.string.action_open_nfc_settings_sub, Icons.Filled.Nfc)
    ActionType.SYSTEM_OPEN_DATA_SAVER_SETTINGS -> Triple(R.string.action_open_data_saver_settings, R.string.action_open_data_saver_settings_sub, Icons.Filled.DataSaver)
    ActionType.SYSTEM_OPEN_DEVELOPER_SETTINGS -> Triple(R.string.action_open_developer_settings, R.string.action_open_developer_settings_sub, Icons.Filled.Build)
    ActionType.SYSTEM_OPEN_MAPS -> Triple(R.string.action_open_maps, R.string.action_open_maps_sub, Icons.Filled.Map)
    ActionType.SYSTEM_SOFT_RESTART -> Triple(R.string.action_soft_restart, R.string.action_soft_restart_sub, Icons.Filled.Refresh)
    ActionType.SYSTEM_STATUS_BAR_TOGGLE -> Triple(R.string.action_status_bar_toggle, R.string.action_status_bar_toggle_sub, Icons.Filled.Visibility)
    ActionType.SYSTEM_OPEN_CONTACTS -> Triple(R.string.action_open_contacts, R.string.action_open_contacts_sub, Icons.Filled.Contacts)
    ActionType.SYSTEM_SEND_EMAIL -> Triple(R.string.action_send_email, R.string.action_send_email_sub, Icons.Filled.Email)
    ActionType.SYSTEM_OPEN_NOTIFICATION_SETTINGS -> Triple(R.string.action_open_notification_settings, R.string.action_open_notification_settings_sub, Icons.Filled.Notifications)
    ActionType.SYSTEM_OPEN_PRIVACY_SETTINGS -> Triple(R.string.action_open_privacy_settings, R.string.action_open_privacy_settings_sub, Icons.Filled.Lock)
    ActionType.SYSTEM_OPEN_CAST_SETTINGS -> Triple(R.string.action_open_cast_settings, R.string.action_open_cast_settings_sub, Icons.Filled.Cast)
    ActionType.SYSTEM_OPEN_INPUT_METHOD_SETTINGS -> Triple(R.string.action_open_input_method_settings, R.string.action_open_input_method_settings_sub, Icons.Filled.Keyboard)
    ActionType.SYSTEM_OPEN_DEFAULT_APPS_SETTINGS -> Triple(R.string.action_open_default_apps_settings, R.string.action_open_default_apps_settings_sub, Icons.Filled.Apps)
    ActionType.SYSTEM_OPEN_VPN_SETTINGS -> Triple(R.string.action_open_vpn_settings, R.string.action_open_vpn_settings_sub, Icons.Filled.Lock)
    ActionType.SYSTEM_OPEN_DATE_SETTINGS -> Triple(R.string.action_open_date_settings, R.string.action_open_date_settings_sub, Icons.Filled.DateRange)
    ActionType.SYSTEM_OPEN_PRINT_SETTINGS -> Triple(R.string.action_open_print_settings, R.string.action_open_print_settings_sub, Icons.Filled.Print)
    ActionType.SYSTEM_OPEN_DEVICE_ADMIN_SETTINGS -> Triple(R.string.action_open_device_admin_settings, R.string.action_open_device_admin_settings_sub, Icons.Filled.Security)
    ActionType.SYSTEM_OPEN_USAGE_ACCESS_SETTINGS -> Triple(R.string.action_open_usage_access_settings, R.string.action_open_usage_access_settings_sub, Icons.Filled.BarChart)
    ActionType.SYSTEM_OPEN_AIRPLANE_MODE_SETTINGS -> Triple(R.string.action_open_airplane_settings, R.string.action_open_airplane_settings_sub, Icons.Filled.AirplanemodeActive)
    ActionType.SYSTEM_BLUETOOTH_SCAN -> Triple(R.string.action_bluetooth_scan, R.string.action_bluetooth_scan_sub, Icons.Filled.Bluetooth)
    ActionType.SYSTEM_WIFI_SCAN_NOW -> Triple(R.string.action_wifi_scan_now, R.string.action_wifi_scan_now_sub, Icons.Filled.Wifi)
    ActionType.SYSTEM_SET_TIMEZONE -> Triple(R.string.action_set_timezone, R.string.action_set_timezone_sub, Icons.Filled.Schedule)
}
