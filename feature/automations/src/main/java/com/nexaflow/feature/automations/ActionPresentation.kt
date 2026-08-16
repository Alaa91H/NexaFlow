package com.nexaflow.feature.automations

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexaflow.domain.models.ActionType

/**
 * Shared localized presentation of an [ActionType]: (titleRes, subtitleRes,
 * icon). Lives in this module so the run-details timeline and the automation
 * details screen render action names identically.
 */
fun actionPresentation(type: ActionType): Triple<Int, Int, ImageVector> = when (type) {
    ActionType.SYSTEM_BRIGHTNESS -> Triple(R.string.action_brightness, R.string.action_brightness_sub, Icons.Filled.FlashOn)
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
    ActionType.SYSTEM_MEDIA_NEXT -> Triple(R.string.action_media_next, R.string.action_media_next_sub, Icons.Filled.MusicNote)
    ActionType.SYSTEM_MEDIA_PREVIOUS -> Triple(R.string.action_media_prev, R.string.action_media_prev_sub, Icons.Filled.MusicNote)
    ActionType.SYSTEM_OPEN_URL -> Triple(R.string.action_open_url, R.string.action_open_url_sub, Icons.Filled.Link)
    ActionType.SYSTEM_BLOCK_NOTIFICATION -> Triple(R.string.action_block_notification, R.string.action_block_notification_sub, Icons.Filled.NotificationsActive)
    ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS -> Triple(R.string.action_clear_app_notifications, R.string.action_clear_app_notifications_sub, Icons.Filled.Notifications)
    ActionType.SYSTEM_CLEAR_NOTIFICATIONS -> Triple(R.string.action_clear_notifs, R.string.action_clear_notifs_sub, Icons.Filled.Notifications)
    ActionType.SYSTEM_EXPAND_STATUS_BAR -> Triple(R.string.action_expand_bar, R.string.action_expand_bar_sub, Icons.Filled.NotificationImportant)
    ActionType.SYSTEM_COLLAPSE_STATUS_BAR -> Triple(R.string.action_collapse_bar, R.string.action_collapse_bar_sub, Icons.Filled.NotificationImportant)
    ActionType.SYSTEM_SCREEN_TIMEOUT -> Triple(R.string.action_screen_timeout, R.string.action_screen_timeout_sub, Icons.Filled.ScreenRotation)
    ActionType.SYSTEM_STAY_AWAKE -> Triple(R.string.action_stay_awake, R.string.action_stay_awake_sub, Icons.Filled.FlashOn)
    ActionType.SYSTEM_AUTO_BRIGHTNESS -> Triple(R.string.action_auto_brightness, R.string.action_auto_brightness_sub, Icons.Filled.FlashOn)
    ActionType.SYSTEM_RINGER_MODE -> Triple(R.string.action_ringer, R.string.action_ringer_sub, Icons.AutoMirrored.Filled.VolumeUp)
    ActionType.SYSTEM_MOBILE_DATA -> Triple(R.string.action_mobile_data, R.string.action_mobile_data_sub, Icons.Filled.Wifi)
    ActionType.SYSTEM_NETWORK_MODE -> Triple(R.string.action_network_mode, R.string.action_network_mode_sub, Icons.Filled.SignalCellularAlt)
    ActionType.SYSTEM_HOTSPOT -> Triple(R.string.action_hotspot, R.string.action_hotspot_sub, Icons.Filled.Wifi)
    ActionType.SYSTEM_SET_RINGTONE -> Triple(R.string.action_set_ringtone, R.string.action_set_ringtone_sub, Icons.Filled.MusicNote)
    ActionType.SYSTEM_NFC -> Triple(R.string.action_nfc, R.string.action_nfc_sub, Icons.Filled.Wifi)
    ActionType.SYSTEM_POWER_SAVER -> Triple(R.string.action_power_saver, R.string.action_power_saver_sub, Icons.Filled.BatteryChargingFull)
    ActionType.SYSTEM_ANIMATIONS -> Triple(R.string.action_animations, R.string.action_animations_sub, Icons.Filled.ScreenRotation)
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
}
