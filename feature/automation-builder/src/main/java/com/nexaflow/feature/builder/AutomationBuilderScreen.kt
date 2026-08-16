package com.nexaflow.feature.builder

import androidx.compose.ui.text.font.FontWeight

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.nexaflow.core.engine.LocationAccess
import com.nexaflow.core.execution.NotificationActionButton
import com.nexaflow.core.pluginsdk.LocaleContract
import com.nexaflow.core.pluginsdk.PluginConfigParser
import com.nexaflow.core.rom.ElevatedAccessShortcuts
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowAnimatedVisibility
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowFloatingActionButton
import com.nexaflow.core.ui.NexaFlowIcons
import com.nexaflow.core.ui.nexaFlowEffectsSpec
import com.nexaflow.core.ui.nexaFlowSpatialSpec
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.iconVector
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.PluginInfo
import com.nexaflow.domain.models.EndBehaviorCatalog
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.launch
import java.util.Locale

enum class ActionCategory(val headerRes: Int, val color: Color) {
    DISPLAY(R.string.category_display, Color(0xFF0B57D0)),
    SOUND(R.string.category_sound, Color(0xFF6750A4)),
    CONNECTIVITY(R.string.category_connectivity, Color(0xFF006A6C)),
    MEDIA(R.string.category_media, Color(0xFFC2185B)),
    NOTIFICATIONS(R.string.category_notifications, Color(0xFF8F4C00)),
    APPS(R.string.category_apps, Color(0xFF006D3C)),
    SYSTEM(R.string.category_system, Color(0xFF455A64)),
    BATTERY(R.string.category_battery, Color(0xFF387908)),
    PLUGINS(R.string.category_plugins, Color(0xFF625B71))
}

data class ActionOption(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    val actionType: ActionType,
    val category: ActionCategory
) {
    val color: Color get() = category.color
}

internal val actionOptions = listOf(
    // DISPLAY
    ActionOption(R.string.action_brightness, R.string.action_brightness_sub, Icons.Filled.BrightnessHigh, ActionType.SYSTEM_BRIGHTNESS, ActionCategory.DISPLAY),
    ActionOption(R.string.action_auto_brightness, R.string.action_auto_brightness_sub, Icons.Filled.BrightnessAuto, ActionType.SYSTEM_AUTO_BRIGHTNESS, ActionCategory.DISPLAY),
    ActionOption(R.string.action_rotation, R.string.action_rotation_sub, Icons.Filled.ScreenRotation, ActionType.SYSTEM_SCREEN_ROTATION, ActionCategory.DISPLAY),
    ActionOption(R.string.action_screen_timeout, R.string.action_screen_timeout_sub, Icons.Filled.Timelapse, ActionType.SYSTEM_SCREEN_TIMEOUT, ActionCategory.DISPLAY),
    ActionOption(R.string.action_stay_awake, R.string.action_stay_awake_sub, Icons.Filled.WbSunny, ActionType.SYSTEM_STAY_AWAKE, ActionCategory.DISPLAY),
    ActionOption(R.string.action_dark_mode, R.string.action_dark_mode_sub, Icons.Filled.DarkMode, ActionType.SYSTEM_DARK_MODE, ActionCategory.DISPLAY),
    // SOUND
    ActionOption(R.string.action_volume, R.string.action_volume_sub, Icons.AutoMirrored.Filled.VolumeUp, ActionType.SYSTEM_VOLUME, ActionCategory.SOUND),
    ActionOption(R.string.action_stream_volume, R.string.action_stream_volume_sub, Icons.Filled.GraphicEq, ActionType.SYSTEM_STREAM_VOLUME, ActionCategory.SOUND),
    ActionOption(R.string.action_vibrate, R.string.action_vibrate_sub, Icons.Filled.Vibration, ActionType.SYSTEM_VIBRATE, ActionCategory.SOUND),
    ActionOption(R.string.action_ring_volume, R.string.action_ring_volume_sub, Icons.Filled.PhoneAndroid, ActionType.SYSTEM_RING_VOLUME, ActionCategory.SOUND),
    ActionOption(R.string.action_set_ringtone, R.string.action_set_ringtone_sub, Icons.Filled.MusicNote, ActionType.SYSTEM_SET_RINGTONE, ActionCategory.SOUND),
    ActionOption(R.string.action_ringer, R.string.action_ringer_sub, Icons.Filled.NotificationsActive, ActionType.SYSTEM_RINGER_MODE, ActionCategory.SOUND),
    ActionOption(R.string.action_dnd, R.string.action_dnd_sub, Icons.Filled.DoNotDisturb, ActionType.SYSTEM_DND, ActionCategory.SOUND),
    // CONNECTIVITY
    ActionOption(R.string.action_wifi, R.string.action_wifi_sub, Icons.Filled.Wifi, ActionType.SYSTEM_WIFI, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_hotspot, R.string.action_hotspot_sub, Icons.Filled.WifiTethering, ActionType.SYSTEM_HOTSPOT, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_bluetooth, R.string.action_bluetooth_sub, Icons.Filled.Bluetooth, ActionType.SYSTEM_BLUETOOTH, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_mobile_data, R.string.action_mobile_data_sub, Icons.Filled.DataUsage, ActionType.SYSTEM_MOBILE_DATA, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_network_mode, R.string.action_network_mode_sub, Icons.Filled.SignalCellularAlt, ActionType.SYSTEM_NETWORK_MODE, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_nfc, R.string.action_nfc_sub, Icons.Filled.Nfc, ActionType.SYSTEM_NFC, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_airplane, R.string.action_airplane_sub, Icons.Filled.AirplanemodeActive, ActionType.SYSTEM_AIRPLANE_MODE, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_location, R.string.action_location_sub, Icons.Filled.LocationOn, ActionType.SYSTEM_LOCATION, ActionCategory.CONNECTIVITY),
    // MEDIA
    ActionOption(R.string.action_media_play, R.string.action_media_play_sub, Icons.Filled.PlayArrow, ActionType.SYSTEM_MEDIA_PLAY_PAUSE, ActionCategory.MEDIA),
    ActionOption(R.string.action_media_next, R.string.action_media_next_sub, Icons.Filled.SkipNext, ActionType.SYSTEM_MEDIA_NEXT, ActionCategory.MEDIA),
    ActionOption(R.string.action_media_prev, R.string.action_media_prev_sub, Icons.Filled.SkipPrevious, ActionType.SYSTEM_MEDIA_PREVIOUS, ActionCategory.MEDIA),
    ActionOption(R.string.action_media_stop, R.string.action_media_stop_sub, Icons.Filled.Stop, ActionType.SYSTEM_MEDIA_STOP, ActionCategory.MEDIA),
    // NOTIFICATIONS
    ActionOption(R.string.action_notification, R.string.action_notification_sub, Icons.Filled.Notifications, ActionType.SYSTEM_SEND_NOTIFICATION, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_send_sms, R.string.action_send_sms_sub, Icons.AutoMirrored.Filled.Message, ActionType.SYSTEM_SEND_SMS, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_reminder, R.string.action_reminder_sub, Icons.Filled.NotificationsActive, ActionType.SYSTEM_SEND_REMINDER, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_block_notification, R.string.action_block_notification_sub, Icons.Filled.NotificationsOff, ActionType.SYSTEM_BLOCK_NOTIFICATION, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_clear_app_notifications, R.string.action_clear_app_notifications_sub, Icons.Filled.DeleteSweep, ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_clear_notifs, R.string.action_clear_notifs_sub, Icons.Filled.ClearAll, ActionType.SYSTEM_CLEAR_NOTIFICATIONS, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_open_notifications, R.string.action_open_notifications_sub, Icons.Filled.Notifications, ActionType.SYSTEM_OPEN_NOTIFICATIONS, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_expand_bar, R.string.action_expand_bar_sub, Icons.Filled.ExpandLess, ActionType.SYSTEM_EXPAND_STATUS_BAR, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_collapse_bar, R.string.action_collapse_bar_sub, Icons.Filled.ExpandMore, ActionType.SYSTEM_COLLAPSE_STATUS_BAR, ActionCategory.NOTIFICATIONS),
    // APPS
    ActionOption(R.string.action_open_apps, R.string.action_open_apps_sub, Icons.Filled.Apps, ActionType.SYSTEM_OPEN_APP, ActionCategory.APPS),
    ActionOption(R.string.action_open_recents, R.string.action_open_recents_sub, Icons.Filled.ViewCarousel, ActionType.SYSTEM_OPEN_RECENTS, ActionCategory.APPS),
    ActionOption(R.string.action_close_app, R.string.action_close_app_sub, Icons.Filled.Close, ActionType.APPLICATION_CLOSE_APP, ActionCategory.APPS),
    ActionOption(R.string.action_open_app_settings, R.string.action_open_app_settings_sub, Icons.Filled.Settings, ActionType.APPLICATION_OPEN_APP_SETTINGS, ActionCategory.APPS),
    ActionOption(R.string.action_play_updates, R.string.action_play_updates_sub, Icons.Filled.Storefront, ActionType.SYSTEM_OPEN_PLAY_UPDATES, ActionCategory.APPS),
    ActionOption(R.string.action_launch_app, R.string.action_launch_app_sub, Icons.Filled.Apps, ActionType.APPLICATION_LAUNCH_APP, ActionCategory.APPS),
    ActionOption(R.string.action_galaxy_store, R.string.action_galaxy_store_sub, Icons.Filled.Storefront, ActionType.SYSTEM_OPEN_GALAXY_STORE, ActionCategory.APPS),
    // SYSTEM
    ActionOption(R.string.action_flashlight, R.string.action_flashlight_sub, Icons.Filled.FlashlightOn, ActionType.SYSTEM_FLASHLIGHT, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_url, R.string.action_open_url_sub, Icons.Filled.Link, ActionType.SYSTEM_OPEN_URL, ActionCategory.SYSTEM),
    ActionOption(R.string.action_http_request, R.string.action_http_request_sub, Icons.Filled.Public, ActionType.SYSTEM_HTTP_REQUEST, ActionCategory.SYSTEM),
    ActionOption(R.string.action_power_saver, R.string.action_power_saver_sub, Icons.Filled.EnergySavingsLeaf, ActionType.SYSTEM_POWER_SAVER, ActionCategory.SYSTEM),
    ActionOption(R.string.action_animations, R.string.action_animations_sub, Icons.Filled.Palette, ActionType.SYSTEM_ANIMATIONS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_lock_screen, R.string.action_lock_screen_sub, Icons.Filled.Lock, ActionType.SYSTEM_LOCK_SCREEN, ActionCategory.SYSTEM),
    ActionOption(R.string.action_set_alarm, R.string.action_set_alarm_sub, Icons.Filled.Schedule, ActionType.SYSTEM_SET_ALARM, ActionCategory.SYSTEM),
    ActionOption(R.string.action_wait, R.string.action_wait_sub, Icons.Filled.HourglassEmpty, ActionType.SYSTEM_WAIT, ActionCategory.SYSTEM),
    ActionOption(R.string.action_go_home, R.string.action_go_home_sub, Icons.Filled.Home, ActionType.SYSTEM_GO_HOME, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_settings, R.string.action_open_settings_sub, Icons.Filled.Settings, ActionType.SYSTEM_OPEN_SETTINGS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_quick_settings, R.string.action_open_quick_settings_sub, Icons.Filled.Settings, ActionType.SYSTEM_OPEN_QUICK_SETTINGS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_wake_screen, R.string.action_wake_screen_sub, Icons.Filled.WbSunny, ActionType.SYSTEM_WAKE_SCREEN, ActionCategory.DISPLAY),
    ActionOption(R.string.action_clipboard, R.string.action_clipboard_sub, Icons.Filled.ContentPaste, ActionType.SYSTEM_CLIPBOARD_SET, ActionCategory.SYSTEM),
    ActionOption(R.string.action_set_setting, R.string.action_set_setting_sub, Icons.Filled.Tune, ActionType.SYSTEM_SET_SETTING, ActionCategory.SYSTEM),
    ActionOption(R.string.action_screenshot, R.string.action_screenshot_sub, Icons.Filled.CameraAlt, ActionType.SYSTEM_SCREENSHOT, ActionCategory.SYSTEM),
    ActionOption(R.string.action_input_text, R.string.action_input_text_sub, Icons.Filled.Chat, ActionType.SYSTEM_INPUT_TEXT, ActionCategory.SYSTEM),
    ActionOption(R.string.action_key_event, R.string.action_key_event_sub, Icons.Filled.Build, ActionType.SYSTEM_KEY_EVENT, ActionCategory.SYSTEM),
    ActionOption(R.string.action_input_tap, R.string.action_input_tap_sub, Icons.Filled.GpsFixed, ActionType.SYSTEM_INPUT_TAP, ActionCategory.SYSTEM),
    ActionOption(R.string.action_input_swipe, R.string.action_input_swipe_sub, Icons.AutoMirrored.Filled.ArrowForward, ActionType.SYSTEM_INPUT_SWIPE, ActionCategory.SYSTEM),
    ActionOption(R.string.action_color_inversion, R.string.action_color_inversion_sub, Icons.Filled.Contrast, ActionType.SYSTEM_COLOR_INVERSION, ActionCategory.DISPLAY),
    ActionOption(R.string.action_grayscale, R.string.action_grayscale_sub, Icons.Filled.Gradient, ActionType.SYSTEM_GRAYSCALE, ActionCategory.DISPLAY),
    ActionOption(R.string.action_extra_dim, R.string.action_extra_dim_sub, Icons.Filled.BrightnessLow, ActionType.SYSTEM_EXTRA_DIM, ActionCategory.DISPLAY),
    ActionOption(R.string.action_night_light, R.string.action_night_light_sub, Icons.Filled.NightsStay, ActionType.SYSTEM_NIGHT_LIGHT, ActionCategory.DISPLAY),
    ActionOption(R.string.action_haptic_feedback, R.string.action_haptic_feedback_sub, Icons.Filled.Vibration, ActionType.SYSTEM_HAPTIC_FEEDBACK, ActionCategory.SOUND),
    ActionOption(R.string.action_sound_effects, R.string.action_sound_effects_sub, Icons.Filled.GraphicEq, ActionType.SYSTEM_SOUND_EFFECTS, ActionCategory.SOUND),
    ActionOption(R.string.action_force_stop_app, R.string.action_force_stop_app_sub, Icons.Filled.Stop, ActionType.SYSTEM_FORCE_STOP_APP, ActionCategory.APPS),
    ActionOption(R.string.action_clear_app_data, R.string.action_clear_app_data_sub, Icons.Filled.DeleteSweep, ActionType.SYSTEM_CLEAR_APP_DATA, ActionCategory.APPS),
    ActionOption(R.string.action_media_fast_forward, R.string.action_media_fast_forward_sub, Icons.Filled.FastForward, ActionType.SYSTEM_MEDIA_FAST_FORWARD, ActionCategory.MEDIA),
    ActionOption(R.string.action_media_rewind, R.string.action_media_rewind_sub, Icons.Filled.FastRewind, ActionType.SYSTEM_MEDIA_REWIND, ActionCategory.MEDIA),
    ActionOption(R.string.action_dial_number, R.string.action_dial_number_sub, Icons.Filled.Phone, ActionType.SYSTEM_DIAL_NUMBER, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_camera, R.string.action_open_camera_sub, Icons.Filled.CameraAlt, ActionType.SYSTEM_OPEN_CAMERA, ActionCategory.APPS),
    ActionOption(R.string.action_open_play_store_app, R.string.action_open_play_store_app_sub, Icons.Filled.Storefront, ActionType.SYSTEM_OPEN_PLAY_STORE_APP, ActionCategory.APPS),
    ActionOption(R.string.action_location_mode, R.string.action_location_mode_sub, Icons.Filled.LocationOn, ActionType.SYSTEM_LOCATION_MODE, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_data_saver, R.string.action_data_saver_sub, Icons.Filled.DataUsage, ActionType.SYSTEM_DATA_SAVER, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_wifi_sleep_policy, R.string.action_wifi_sleep_policy_sub, Icons.Filled.Wifi, ActionType.SYSTEM_WIFI_SLEEP_POLICY, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_bluetooth_discoverability, R.string.action_bluetooth_discoverability_sub, Icons.Filled.Bluetooth, ActionType.SYSTEM_BLUETOOTH_DISCOVERABILITY, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_auto_time, R.string.action_auto_time_sub, Icons.Filled.Schedule, ActionType.SYSTEM_AUTO_TIME, ActionCategory.SYSTEM),
    ActionOption(R.string.action_auto_timezone, R.string.action_auto_timezone_sub, Icons.Filled.Public, ActionType.SYSTEM_AUTO_TIMEZONE, ActionCategory.SYSTEM),
    ActionOption(R.string.action_wifi_scanning, R.string.action_wifi_scanning_sub, Icons.Filled.Wifi, ActionType.SYSTEM_WIFI_SCANNING, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_font_scale, R.string.action_font_scale_sub, Icons.Filled.TextFields, ActionType.SYSTEM_FONT_SCALE, ActionCategory.DISPLAY),
    ActionOption(R.string.action_display_density, R.string.action_display_density_sub, Icons.Filled.ScreenRotation, ActionType.SYSTEM_DISPLAY_DENSITY, ActionCategory.DISPLAY),
    ActionOption(R.string.action_screensaver, R.string.action_screensaver_sub, Icons.Filled.BrightnessLow, ActionType.SYSTEM_SCREENSAVER, ActionCategory.DISPLAY),
    ActionOption(R.string.action_always_on_display, R.string.action_always_on_display_sub, Icons.Filled.WbSunny, ActionType.SYSTEM_ALWAYS_ON_DISPLAY, ActionCategory.DISPLAY),
    ActionOption(R.string.action_show_taps, R.string.action_show_taps_sub, Icons.Filled.TouchApp, ActionType.SYSTEM_SHOW_TAPS, ActionCategory.DISPLAY),
    ActionOption(R.string.action_pointer_location, R.string.action_pointer_location_sub, Icons.Filled.GpsFixed, ActionType.SYSTEM_POINTER_LOCATION, ActionCategory.DISPLAY),
    ActionOption(R.string.action_battery_saver_threshold, R.string.action_battery_saver_threshold_sub, Icons.Filled.BatteryChargingFull, ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD, ActionCategory.BATTERY),
    ActionOption(R.string.action_adaptive_battery, R.string.action_adaptive_battery_sub, Icons.Filled.BatteryChargingFull, ActionType.SYSTEM_ADAPTIVE_BATTERY, ActionCategory.BATTERY),
    ActionOption(R.string.action_haptic_intensity, R.string.action_haptic_intensity_sub, Icons.Filled.GraphicEq, ActionType.SYSTEM_HAPTIC_INTENSITY, ActionCategory.SOUND),
    ActionOption(R.string.action_camera_shutter_sound, R.string.action_camera_shutter_sound_sub, Icons.Filled.CameraAlt, ActionType.SYSTEM_CAMERA_SHUTTER_SOUND, ActionCategory.SOUND),
    ActionOption(R.string.action_open_wifi_settings, R.string.action_open_wifi_settings_sub, Icons.Filled.Wifi, ActionType.SYSTEM_OPEN_WIFI_SETTINGS, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_open_bluetooth_settings, R.string.action_open_bluetooth_settings_sub, Icons.Filled.Bluetooth, ActionType.SYSTEM_OPEN_BLUETOOTH_SETTINGS, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_open_location_settings, R.string.action_open_location_settings_sub, Icons.Filled.LocationOn, ActionType.SYSTEM_OPEN_LOCATION_SETTINGS, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_open_data_usage_settings, R.string.action_open_data_usage_settings_sub, Icons.Filled.DataUsage, ActionType.SYSTEM_OPEN_DATA_USAGE_SETTINGS, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_open_battery_settings, R.string.action_open_battery_settings_sub, Icons.Filled.BatteryChargingFull, ActionType.SYSTEM_OPEN_BATTERY_SETTINGS, ActionCategory.BATTERY),
    ActionOption(R.string.action_open_display_settings, R.string.action_open_display_settings_sub, Icons.Filled.ScreenRotation, ActionType.SYSTEM_OPEN_DISPLAY_SETTINGS, ActionCategory.DISPLAY),
    ActionOption(R.string.action_open_sound_settings, R.string.action_open_sound_settings_sub, Icons.AutoMirrored.Filled.VolumeUp, ActionType.SYSTEM_OPEN_SOUND_SETTINGS, ActionCategory.SOUND),
    ActionOption(R.string.action_open_storage_settings, R.string.action_open_storage_settings_sub, Icons.Filled.Storage, ActionType.SYSTEM_OPEN_STORAGE_SETTINGS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_security_settings, R.string.action_open_security_settings_sub, Icons.Filled.Security, ActionType.SYSTEM_OPEN_SECURITY_SETTINGS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_accessibility_settings, R.string.action_open_accessibility_settings_sub, Icons.Filled.Accessibility, ActionType.SYSTEM_OPEN_ACCESSIBILITY_SETTINGS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_app_settings_list, R.string.action_open_app_settings_list_sub, Icons.Filled.Apps, ActionType.SYSTEM_OPEN_APP_SETTINGS_LIST, ActionCategory.APPS),
    ActionOption(R.string.action_open_about_phone, R.string.action_open_about_phone_sub, Icons.Filled.Info, ActionType.SYSTEM_OPEN_ABOUT_PHONE, ActionCategory.SYSTEM),
    ActionOption(R.string.action_reboot, R.string.action_reboot_sub, Icons.Filled.Refresh, ActionType.SYSTEM_REBOOT, ActionCategory.SYSTEM),
    ActionOption(R.string.action_shutdown, R.string.action_shutdown_sub, Icons.Filled.PowerSettingsNew, ActionType.SYSTEM_SHUTDOWN, ActionCategory.SYSTEM),
    ActionOption(R.string.action_restart_system_ui, R.string.action_restart_system_ui_sub, Icons.Filled.Refresh, ActionType.SYSTEM_RESTART_SYSTEM_UI, ActionCategory.SYSTEM),
    // BATTERY
    ActionOption(R.string.action_battery_alert, R.string.action_battery_alert_sub, Icons.Filled.BatteryAlert, ActionType.BATTERY_ALERTS, ActionCategory.BATTERY),
    ActionOption(R.string.action_charging_alert, R.string.action_charging_alert_sub, Icons.Filled.BatteryChargingFull, ActionType.BATTERY_CHARGING_NOTIFICATIONS, ActionCategory.BATTERY),
    // PLUGINS
    ActionOption(R.string.action_plugin, R.string.action_plugin_sub, Icons.Filled.Extension, ActionType.PLUGIN_FIRE, ActionCategory.PLUGINS)
)

internal val actionCategories: List<ActionCategory> = ActionCategory.entries.toList()

/** Representative icon per action category for the accordion chips. */
internal fun ActionCategory.icon(): ImageVector = when (this) {
    ActionCategory.DISPLAY -> Icons.Filled.BrightnessHigh
    ActionCategory.SOUND -> Icons.AutoMirrored.Filled.VolumeUp
    ActionCategory.CONNECTIVITY -> Icons.Filled.Wifi
    ActionCategory.MEDIA -> Icons.Filled.PlayArrow
    ActionCategory.NOTIFICATIONS -> Icons.Filled.Notifications
    ActionCategory.APPS -> Icons.Filled.Apps
    ActionCategory.SYSTEM -> Icons.Filled.Settings
    ActionCategory.BATTERY -> Icons.Filled.BatteryAlert
    ActionCategory.PLUGINS -> Icons.Filled.Extension
}

/**
 * One-line summary of the chosen action values for the collapsed header,
 * mirroring triggerSummary/constraintSummary so every builder row reads
 * "Execution N · <name · chosen values>". Falls back to the action name
 * alone when nothing is configured yet (immediate one-shot actions).
 */
@Composable
private fun actionSummary(option: ActionOption, config: Map<String, String>): String {
    val name = stringResource(option.titleRes)
    val value: String? = when (option.actionType) {
        ActionType.SYSTEM_BRIGHTNESS ->
            stringResource(R.string.brightness_label, config["value"]?.toIntOrNull() ?: 128)
        ActionType.SYSTEM_VOLUME ->
            stringResource(R.string.volume_label, config["value"]?.toIntOrNull() ?: 50)
        ActionType.SYSTEM_RING_VOLUME ->
            stringResource(R.string.ring_volume_label, config["value"]?.toIntOrNull() ?: 50)
        ActionType.SYSTEM_STREAM_VOLUME -> {
            val stream = when (config["stream"] ?: "MUSIC") {
                "RING" -> stringResource(R.string.stream_ring)
                "NOTIFICATION" -> stringResource(R.string.stream_notification)
                "ALARM" -> stringResource(R.string.stream_alarm)
                "VOICE_CALL" -> stringResource(R.string.stream_voice_call)
                "SYSTEM" -> stringResource(R.string.stream_system)
                "DTMF" -> stringResource(R.string.stream_dtmf)
                "ACCESSIBILITY" -> stringResource(R.string.stream_accessibility)
                else -> stringResource(R.string.stream_music)
            }
            "$stream · ${config["value"] ?: "50"}"
        }
        ActionType.SYSTEM_NETWORK_MODE -> when (config["mode"] ?: "AUTO") {
            "2G" -> stringResource(R.string.network_mode_2g)
            "3G" -> stringResource(R.string.network_mode_3g)
            "4G" -> stringResource(R.string.network_mode_4g)
            "5G" -> stringResource(R.string.network_mode_5g)
            else -> stringResource(R.string.network_mode_auto)
        }
        ActionType.SYSTEM_LOCATION,
        ActionType.SYSTEM_DND,
        ActionType.SYSTEM_WIFI,
        ActionType.SYSTEM_BLUETOOTH,
        ActionType.SYSTEM_FLASHLIGHT,
        ActionType.SYSTEM_AIRPLANE_MODE,
        ActionType.SYSTEM_STAY_AWAKE,
        ActionType.SYSTEM_AUTO_BRIGHTNESS,
        ActionType.SYSTEM_MOBILE_DATA,
        ActionType.SYSTEM_HOTSPOT,
        ActionType.SYSTEM_NFC,
        ActionType.SYSTEM_POWER_SAVER,
        ActionType.SYSTEM_ANIMATIONS,
        ActionType.SYSTEM_DARK_MODE ->
            if (config["enabled"]?.toBoolean() ?: true) stringResource(R.string.state_on)
            else stringResource(R.string.state_off)
        ActionType.SYSTEM_SEND_SMS -> {
            val number = config["number"].orEmpty().trim()
            val text = config["text"].orEmpty().trim()
            listOf(number, text).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
        }
        ActionType.SYSTEM_SEND_REMINDER -> {
            val title = config["title"].orEmpty().trim()
            val time = "${config["hour"] ?: "9"}:${(config["minute"] ?: "0").padStart(2, '0')}"
            listOf(title, time).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
        }
        ActionType.SYSTEM_OPEN_SETTINGS -> when (config["page"] ?: "WIFI") {
            "BLUETOOTH" -> stringResource(R.string.settings_bluetooth)
            "LOCATION" -> stringResource(R.string.settings_location)
            "SOUND" -> stringResource(R.string.settings_sound)
            "DISPLAY" -> stringResource(R.string.settings_display)
            "BATTERY" -> stringResource(R.string.settings_battery)
            "NOTIFICATION" -> stringResource(R.string.settings_notification)
            else -> stringResource(R.string.settings_wifi)
        }
        ActionType.SYSTEM_SCREEN_TIMEOUT ->
            stringResource(R.string.timeout_label, config["seconds"]?.toIntOrNull() ?: 60)
        ActionType.SYSTEM_RINGER_MODE -> when (config["mode"] ?: "NORMAL") {
            "VIBRATE" -> stringResource(R.string.ringer_vibrate)
            "SILENT" -> stringResource(R.string.ringer_silent)
            else -> stringResource(R.string.ringer_normal)
        }
        ActionType.SYSTEM_SET_ALARM -> {
            val hour = config["hour"] ?: "7"
            val minute = (config["minute"] ?: "0").padStart(2, '0')
            "$hour:$minute"
        }
        ActionType.APPLICATION_OPEN_APP_SETTINGS,
        ActionType.SYSTEM_BLOCK_NOTIFICATION,
        ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS,
        ActionType.APPLICATION_CLOSE_APP -> config["package"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_OPEN_APP ->
            (config["packages"] ?: config["package"] ?: "").trim().ifEmpty { null }
        ActionType.SYSTEM_SEND_NOTIFICATION -> {
            val title = config["title"].orEmpty().trim()
            val text = config["text"].orEmpty().trim()
            val content = listOf(title, text).firstOrNull { it.isNotEmpty() }
            val buttons = NotificationActionButton.fromConfig(config["action_buttons"])
            when {
                content != null && buttons.isNotEmpty() ->
                    "$content · ${stringResource(R.string.action_buttons_count, buttons.size)}"
                content != null -> content
                else -> null
            }
        }
        ActionType.SYSTEM_WAIT ->
            stringResource(R.string.wait_counter_label, config["seconds"]?.toIntOrNull() ?: 5)
        ActionType.SYSTEM_SCREEN_ROTATION ->
            if (config["autoRotate"]?.toBoolean() ?: true) stringResource(R.string.auto_rotate)
            else stringResource(R.string.state_off)
        ActionType.SYSTEM_OPEN_URL -> config["url"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_HTTP_REQUEST -> {
            val method = config["method"] ?: "GET"
            val url = config["url"].orEmpty().trim()
            if (url.isEmpty()) null else "$method · $url"
        }
        ActionType.BATTERY_ALERTS,
        ActionType.BATTERY_CHARGING_NOTIFICATIONS ->
            stringResource(R.string.alert_below, config["below"]?.toIntOrNull() ?: 20)
        ActionType.ADVANCED_ROOT,
        ActionType.ADVANCED_SHIZUKU -> config["command"].orEmpty().trim().ifEmpty { null }
        ActionType.PLUGIN_FIRE -> config["blurb"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_VIBRATE ->
            "${config["seconds"] ?: "1"}s"
        ActionType.SYSTEM_CLIPBOARD_SET -> config["text"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_SET_SETTING -> {
            val key = config["key"].orEmpty().trim()
            if (key.isEmpty()) null else "$key = ${config["value"] ?: ""}"
        }
        ActionType.SYSTEM_SCREENSHOT -> config["filename"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_INPUT_TEXT -> config["text"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_KEY_EVENT -> config["key"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_INPUT_TAP ->
            "${config["x"] ?: "0"}, ${config["y"] ?: "0"}"
        ActionType.SYSTEM_INPUT_SWIPE ->
            "(${config["x1"] ?: "0"},${config["y1"] ?: "0"}) → (${config["x2"] ?: "0"},${config["y2"] ?: "0"})"
        ActionType.SYSTEM_COLOR_INVERSION,
        ActionType.SYSTEM_GRAYSCALE,
        ActionType.SYSTEM_EXTRA_DIM,
        ActionType.SYSTEM_NIGHT_LIGHT,
        ActionType.SYSTEM_HAPTIC_FEEDBACK,
        ActionType.SYSTEM_SOUND_EFFECTS ->
            if (config["enabled"]?.toBoolean() ?: true) stringResource(R.string.state_on)
            else stringResource(R.string.state_off)
        ActionType.SYSTEM_FORCE_STOP_APP,
        ActionType.SYSTEM_CLEAR_APP_DATA -> config["package"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_DATA_SAVER,
        ActionType.SYSTEM_SCREENSAVER,
        ActionType.SYSTEM_ALWAYS_ON_DISPLAY,
        ActionType.SYSTEM_SHOW_TAPS,
        ActionType.SYSTEM_POINTER_LOCATION,
        ActionType.SYSTEM_ADAPTIVE_BATTERY,
        ActionType.SYSTEM_AUTO_TIME,
        ActionType.SYSTEM_AUTO_TIMEZONE,
        ActionType.SYSTEM_CAMERA_SHUTTER_SOUND,
        ActionType.SYSTEM_WIFI_SCANNING ->
            if (config["enabled"]?.toBoolean() ?: true) stringResource(R.string.state_on)
            else stringResource(R.string.state_off)
        ActionType.SYSTEM_LOCATION_MODE -> when (config["mode"] ?: "HIGH") {
            "OFF" -> stringResource(R.string.location_mode_off)
            "SENSORS" -> stringResource(R.string.location_mode_sensors)
            "BATTERY" -> stringResource(R.string.location_mode_battery)
            else -> stringResource(R.string.location_mode_high)
        }
        ActionType.SYSTEM_FONT_SCALE -> config["scale"] ?: "1.0"
        ActionType.SYSTEM_DISPLAY_DENSITY -> "${config["dpi"] ?: "440"} dpi"
        ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD -> "${config["percent"] ?: "20"}%"
        ActionType.SYSTEM_WIFI_SLEEP_POLICY -> when (config["policy"] ?: "ALWAYS") {
            "PLUGGED" -> stringResource(R.string.wifi_sleep_plugged)
            "NEVER" -> stringResource(R.string.wifi_sleep_never)
            else -> stringResource(R.string.wifi_sleep_always)
        }
        ActionType.SYSTEM_BLUETOOTH_DISCOVERABILITY -> {
            val t = config["timeoutSeconds"]?.toIntOrNull() ?: 300
            if (t == 0) stringResource(R.string.state_off) else "${t}s"
        }
        ActionType.SYSTEM_HAPTIC_INTENSITY -> config["level"] ?: "255"
        ActionType.SYSTEM_OPEN_WIFI_SETTINGS -> stringResource(R.string.settings_wifi)
        ActionType.SYSTEM_OPEN_BLUETOOTH_SETTINGS -> stringResource(R.string.settings_bluetooth)
        ActionType.SYSTEM_OPEN_LOCATION_SETTINGS -> stringResource(R.string.settings_location)
        ActionType.SYSTEM_OPEN_DATA_USAGE_SETTINGS -> stringResource(R.string.settings_data_usage)
        ActionType.SYSTEM_OPEN_BATTERY_SETTINGS -> stringResource(R.string.settings_battery)
        ActionType.SYSTEM_OPEN_DISPLAY_SETTINGS -> stringResource(R.string.settings_display)
        ActionType.SYSTEM_OPEN_SOUND_SETTINGS -> stringResource(R.string.settings_sound)
        ActionType.SYSTEM_OPEN_STORAGE_SETTINGS -> stringResource(R.string.settings_storage)
        ActionType.SYSTEM_OPEN_SECURITY_SETTINGS -> stringResource(R.string.settings_security)
        ActionType.SYSTEM_OPEN_ACCESSIBILITY_SETTINGS -> stringResource(R.string.settings_accessibility)
        ActionType.SYSTEM_OPEN_APP_SETTINGS_LIST -> stringResource(R.string.settings_apps)
        ActionType.SYSTEM_OPEN_ABOUT_PHONE -> stringResource(R.string.settings_about)
        ActionType.SYSTEM_DIAL_NUMBER -> config["number"].orEmpty().trim().ifEmpty { null }
        else -> null
    }
    return if (value.isNullOrBlank()) name else "$name · $value"
}

/** One selected action, in order, with reorder buttons, config editor and permission hint. */
@Composable
private fun SelectedActionCard(
    option: ActionOption,
    index: Int,
    total: Int,
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onPickApp: () -> Unit,
    onRequestPermission: (Array<String>) -> Unit = {},
    refreshKey: Int = 0,
    context: Context,
    // Default keeps the pre-explain behavior (open settings directly) so a call
    // site that forgets to wire the explain screen never gets a dead button.
    onExplainSpecial: (SpecialPermission) -> Unit = { PermissionShortcuts.openSpecial(context, it) },
    availableVariables: List<String> = emptyList(),
    // Saved tasks the notification action can attach as interactive buttons.
    automations: List<Automation> = emptyList(),
    // Re-launches the plugin's EDIT_SETTING activity (plugin actions only).
    onPluginConfigure: () -> Unit = {},
    // Freshly added actions open right away; loaded ones start collapsed.
    initiallyExpanded: Boolean = false,
    // Real drag-and-drop: the reorder handle (arrow column) drives these
    // callbacks; the arrow buttons stay as a secondary tap-to-move option.
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    NexaFlowCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // X + reorder handle pinned to the LEFT and the row number
                    // pinned to the RIGHT regardless of the locale direction.
                    // Expand-only: once opened, the card never collapses again,
                    // so the details only ever grow downward.
                    .clickable(enabled = !expanded) { expanded = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Execution-task row, strictly left-to-right: remove (X) at the
                // far start, then the reorder handle (up arrow stacked above the
                // down arrow), then the task name.
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.remove_action),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                TaskRowHandle(
                    index = index,
                    total = total,
                    isDragging = isDragging,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onDragStart = onDragStart,
                    onDragDelta = onDragDelta,
                    onDragEnd = onDragEnd
                )
                // Single horizontal line: name · chosen values. The row
                // number lives in a badge pinned to the right end.
                Text(
                    text = actionSummary(option, config),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TaskNumberBadge(number = index + 1)
                if (!expanded) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.expand_options),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
            }
            if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // The action subtitle doubles as its one-line instruction.
            Text(
                text = stringResource(option.subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            ActionConfigEditor(
                option = option,
                config = config,
                onConfigChange = onConfigChange,
                onPickApp = onPickApp,
                availableVariables = availableVariables,
                onPluginConfigure = onPluginConfigure,
                automations = automations
            )
            PermissionHintForAction(
                actionType = option.actionType,
                context = context,
                refreshKey = refreshKey,
                onRequestPermission = onRequestPermission,
                onExplainSpecial = onExplainSpecial
            )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationBuilderScreen(
    navController: NavController,
    automationId: String? = null,
    savedStateHandle: SavedStateHandle? = null
) {
    val viewModel: AutomationBuilderViewModel = hiltViewModel()
    val context = LocalContext.current
    val variables by viewModel.variables.collectAsStateWithLifecycle()
    // Saved tasks available for notification action buttons (run from a notification).
    val automations by viewModel.automations.collectAsStateWithLifecycle()
    // %VARIABLE chips offered in text fields: user globals first, then the most
    // useful device-context built-ins (full set still works when typed by hand).
    val availableVariables = remember(variables) {
        variables.map { it.name } + listOf(
            "DATE", "TIME", "DATETIME", "BATTERY", "CHARGING", "WIFI", "BLUETOOTH",
            "RINGER", "SCREEN", "AIRPLANE", "NETWORK", "BRIGHTNESS", "BRAND", "MODEL", "SDK"
        )
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val stringNextNeedsTrigger = stringResource(R.string.next_needs_trigger)
    val stringNextNeedsAction = stringResource(R.string.next_needs_action)
    val stringSavedSuccessfully = stringResource(R.string.saved_successfully)
    val stringLocationFixFailed = stringResource(R.string.location_fix_failed)
    // P2-11: the editable draft survives rotation AND process death via
    // rememberSaveable (custom savers serialize the immutable drafts to Bundle).
    var name by rememberSaveable { mutableStateOf("") }
    // Sequential wizard: 0 = triggers (with constraints inside), 1 = actions (with end behavior inside).
    var step by rememberSaveable { mutableStateOf(0) }
    val triggers = rememberSaveable(saver = TriggerDraftListSaver) { mutableStateListOf<TriggerDraft>() }
    val constraints = rememberSaveable(saver = ConstraintDraftListSaver) { mutableStateListOf<ConstraintDraft>() }
    var showConstraintPicker by remember { mutableStateOf(false) }
    // A freshly picked constraint opens its editor; loaded ones stay collapsed.
    var lastAddedConstraint by remember { mutableStateOf(-1) }
    var selectedIconIndex by rememberSaveable { mutableStateOf(0) }
    // Accent color chosen in the icon picker (ARGB Long, persisted in the
    // automation's iconColor column). Defaults to Google blue.
    var selectedIconColor by rememberSaveable { mutableStateOf(0xFF0B57D0L) }
    var appPickerTarget by remember { mutableStateOf<String?>(null) }
    var bluetoothPickerTarget by remember { mutableStateOf<Int?>(null) }
    var calendarPickerTarget by remember { mutableStateOf<Int?>(null) }
    // Single-open accordions: only ONE category chip is expanded at a time.
    var expandedTriggerCategory by rememberSaveable { mutableStateOf<Int?>(null) }
    var expandedActionCategory by rememberSaveable { mutableStateOf<Int?>(null) }
    // A freshly picked trigger opens its editor; loaded ones stay collapsed.
    var lastAddedTrigger by remember { mutableStateOf(-1) }
    // Same for a freshly picked execution action.
    var lastAddedAction by remember { mutableStateOf(-1) }
    val actionConfigs = rememberSaveable(saver = ActionConfigMapSaver) { mutableStateMapOf<ActionType, Map<String, String>>() }
    val selectedActions = rememberSaveable(saver = ActionOptionListSaver) { mutableStateListOf<ActionOption>() }
    // Real drag-and-drop reorder state — one per reorderable list so
    // dragging in one section never disturbs the others (↕️ handle).
    val actionDrag = remember { TaskDragState<ActionOption>() }
    val triggerDrag = remember { TaskDragState<TriggerDraft>() }
    val constraintDrag = remember { TaskDragState<ConstraintDraft>() }
    // Per-action end behavior (leave / restore / set value) applied when the task ends.
    val actionEndBehaviors = rememberSaveable(saver = ActionEndBehaviorMapSaver) { mutableStateMapOf<ActionType, EndBehavior?>() }
    val exitActionConfigs = rememberSaveable(saver = ActionConfigMapSaver) { mutableStateMapOf<ActionType, Map<String, String>>() }
    val selectedExitActions = rememberSaveable(saver = ActionOptionListSaver) { mutableStateListOf<ActionOption>() }

    // ── External plugins (Locale protocol) ─────────────────────────
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    var pluginPickerTarget by remember { mutableStateOf(false) }
    var pluginLauncherPackage by remember { mutableStateOf<String?>(null) }
    var pluginLauncherReceiver by remember { mutableStateOf<String?>(null) }
    val pluginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pkg = pluginLauncherPackage
        val receiver = pluginLauncherReceiver
        pluginLauncherPackage = null
        pluginLauncherReceiver = null
        if (result.resultCode == Activity.RESULT_OK && result.data != null && pkg != null && receiver != null) {
            val bundle: Bundle? = result.data?.getBundleExtra(LocaleContract.EXTRA_BUNDLE)
            val blurb = result.data?.getStringExtra(LocaleContract.EXTRA_STRING_BLURB)
                ?: result.data?.getStringExtra(LocaleContract.EXTRA_BLURB).orEmpty()
            // Prefer the JSON convention; fall back to legacy flat extras.
            val configMap = if (bundle != null) {
                PluginConfigParser.fromBundle(bundle).ifEmpty {
                    PluginConfigParser.flattenBundle(bundle)
                }
            } else {
                emptyMap()
            }
            actionConfigs[ActionType.PLUGIN_FIRE] = mapOf(
                "package" to pkg,
                "receiver" to receiver,
                "blurb" to blurb,
                "bundleJson" to PluginConfigParser.toJson(configMap)
            )
        } else {
            // Canceled/failed: drop the plugin action unless it was configured earlier.
            if ((actionConfigs[ActionType.PLUGIN_FIRE] ?: emptyMap()).isEmpty()) {
                selectedActions.removeAll { it.actionType == ActionType.PLUGIN_FIRE }
            }
        }
    }
    val stringPluginNoEdit = stringResource(R.string.plugin_no_edit)
    fun configurePlugin(packageName: String?, receiver: String?, config: Map<String, String>) {
        val pkg = packageName ?: return
        val rec = receiver ?: return
        pluginLauncherPackage = pkg
        pluginLauncherReceiver = rec
        val intent = Intent(LocaleContract.ACTION_EDIT_SETTING).apply {
            `package` = pkg
            putExtra(LocaleContract.EXTRA_STRING_BREADCRUMB, "NexaFlow")
        }
        // Reconfiguring: hand the saved bundle back so the plugin can pre-fill.
        val savedJson = config["bundleJson"]
        if (!savedJson.isNullOrBlank()) {
            runCatching {
                intent.putExtra(
                    LocaleContract.EXTRA_BUNDLE,
                    PluginConfigParser.toBundle(PluginConfigParser.parseJson(savedJson))
                )
            }
        }
        try {
            pluginLauncher.launch(intent)
        } catch (_: Throwable) {
            pluginLauncherPackage = null
            pluginLauncherReceiver = null
            scope.launch { snackbarHostState.showSnackbar(stringPluginNoEdit) }
            // The plugin has no edit screen: never leave a stuck, unconfigured
            // action behind (the user can still add other plugins).
            if ((actionConfigs[ActionType.PLUGIN_FIRE] ?: emptyMap()).isEmpty()) {
                selectedActions.removeAll { it.actionType == ActionType.PLUGIN_FIRE }
            }
        }
    }

    // Edit mode: load the existing automation once and pre-fill the drafts.
    LaunchedEffect(automationId) {
        automationId?.let { viewModel.loadAutomation(it) }
    }
    val loadedAutomation by viewModel.loaded.collectAsStateWithLifecycle()
    LaunchedEffect(loadedAutomation) {
        val loaded = loadedAutomation ?: return@LaunchedEffect
        if (loaded.id != automationId) return@LaunchedEffect
        name = loaded.name
        selectedIconIndex = NexaFlowIcons.all.indexOfFirst { it.first == loaded.icon }.coerceAtLeast(0)
        selectedIconColor = loaded.iconColor
        triggers.clear()
        loaded.triggers.forEach { triggers.add(TriggerDraft(it.type, it.config)) }
        constraints.clear()
        loaded.constraints.forEach { constraints.add(ConstraintDraft(it.type, it.config)) }
        selectedActions.clear()
        actionConfigs.clear()
        actionEndBehaviors.clear()
        loaded.actions.forEach { action ->
            actionOptions.find { it.actionType == action.type }?.let { option ->
                selectedActions.add(option)
                actionConfigs[option.actionType] = action.config
                actionEndBehaviors[option.actionType] = action.endBehavior
            }
        }
        // Backward compatibility: the old global revert-on-exit toggle is now
        // expressed per action. Expand it into per-action REVERT behaviors so
        // existing tasks keep restoring exactly what they restored before.
        if (loaded.revertOnExit) {
            loaded.actions.forEach { action ->
                if (action.endBehavior == null && EndBehaviorCatalog.supportsRevert(action.type)) {
                    actionEndBehaviors[action.type] = EndBehavior(EndMode.REVERT)
                }
            }
        }
        selectedExitActions.clear()
        exitActionConfigs.clear()
        loaded.exitActions.forEach { action ->
            actionOptions.find { it.actionType == action.type }?.let { option ->
                selectedExitActions.add(option)
                exitActionConfigs[option.actionType] = action.config
            }
        }
    }
    /**
     * The builder's own savedStateHandle, stable for this destination: the
     * icon picker writes its result here, and the in-app map picker does the
     * same (plus the trigger index it was opened for). Reading it from the
     * navController instead would re-point at the top entry while a picker is
     * open and drop the result. The caller passes the entry's handle; fall
     * back to reading it once from the controller for standalone composition.
     */
    val stableSavedStateHandle = remember {
        savedStateHandle ?: navController.currentBackStackEntry?.savedStateHandle
    }

    /**
     * Opens the in-app map picker for a trigger. External maps apps are a
     * dead end for picking: modern Google Maps no longer handles ACTION_PICK
     * (and ACTION_VIEW never returns a point), so the builder now opens its
     * own OpenStreetMap screen where a crosshair + confirm button return the
     * exact coordinates via the shared savedStateHandle.
     */
    fun launchMapPicker(index: Int) {
        // Remember which trigger the picker was opened for. It lives in the
        // builder's own savedStateHandle (survives the navigation round-trip
        // and process death) so the returned point lands on the right row.
        stableSavedStateHandle?.set("map_picker_target", index)
        // Seed the embedded map with the trigger's current point + radius.
        val cfg = triggers.getOrNull(index)?.config.orEmpty()
        val lat = cfg["lat"]?.toDoubleOrNull() ?: 0.0
        val lng = cfg["lng"]?.toDoubleOrNull() ?: 0.0
        val radius = cfg["radius"]?.toIntOrNull() ?: 100
        stableSavedStateHandle?.set(
            "map_picker_init",
            String.format(Locale.US, "%f,%f,%d", lat, lng, radius)
        )
        navController.navigate("map_picker")
    }

    // ── "Use my current location": silently enable location (privileged),
    // grab a fix, fill the coordinates, then restore the previous mode. When no
    // elevated runtime exists, opens the system location settings and resumes
    // on return — the whole flow needs no permission dialogs beyond the one-time
    // runtime location permission.
    var pendingUseLocationIndex by remember { mutableStateOf<Int?>(null) }
    // Toggled by the permission/settings launchers so their callbacks can resume
    // the locate flow without a forward reference to the local function below.
    var resumeLocationFill by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) resumeLocationFill = true
    }
    val locationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Back from the system location settings: retry the pending fill.
        resumeLocationFill = true
    }
    fun locateAndFill(index: Int) {
        if (index !in triggers.indices) return
        if (!LocationAccess.hasLocationPermission(context)) {
            pendingUseLocationIndex = index
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        scope.launch {
            // Everything below must never crash the builder: location services,
            // elevated shells and the single-shot fix can all throw on odd ROM
            // states, and an uncaught exception here would FC the whole app.
            try {
                val wasEnabled = LocationAccess.isLocationEnabled(context)
                val previousMode = LocationAccess.currentLocationMode(context)
                val enabled = if (wasEnabled) true else LocationAccess.enableLocationSilently(context)
                if (!enabled) {
                    // No privileged path: one tap in the system settings screen.
                    pendingUseLocationIndex = index
                    runCatching {
                        locationSettingsLauncher.launch(
                            Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        )
                    }
                    return@launch
                }
                val fix = LocationAccess.getCurrentLocation(context, 12_000)
                if (fix != null && index in triggers.indices) {
                    triggers[index] = triggers[index].copy(
                        config = triggers[index].config +
                            ("lat" to fix.latitude.toString()) +
                            ("lng" to fix.longitude.toString()) +
                            ("source" to "current")
                    )
                } else {
                    scope.launch { snackbarHostState.showSnackbar(stringLocationFixFailed) }
                }
                if (!wasEnabled) LocationAccess.restoreLocationModeIfWeChanged(context, previousMode)
            } catch (t: Throwable) {
                Log.w("LocateFill", "Current-location fill failed", t)
                scope.launch { snackbarHostState.showSnackbar(stringLocationFixFailed) }
            }
        }
    }
    // Resumes a locate flow interrupted by the permission or settings screens.
    LaunchedEffect(resumeLocationFill) {
        if (resumeLocationFill) {
            resumeLocationFill = false
            pendingUseLocationIndex?.let { locateAndFill(it) }
        }
    }
    val scrollState = rememberScrollState()

    // Receive the icon picked in IconPickerScreen. The handle must stay bound
    // to THIS builder entry (see stableSavedStateHandle above).
    DisposableEffect(stableSavedStateHandle) {
        val observer = Observer<Int> { index ->
            selectedIconIndex = index
        }
        stableSavedStateHandle?.getLiveData<Int>("selected_icon")?.observeForever(observer)
        val colorObserver = Observer<Long> { color ->
            selectedIconColor = color
        }
        stableSavedStateHandle?.getLiveData<Long>("selected_color")?.observeForever(colorObserver)
        val locationObserver = Observer<String> { value ->
            val coords = value.split(',')
            val lat = coords.getOrNull(0)?.toDoubleOrNull()
            val lng = coords.getOrNull(1)?.toDoubleOrNull()
            val index = stableSavedStateHandle?.get<Int>("map_picker_target") ?: return@Observer
            if (lat != null && lng != null && index in triggers.indices) {
                val radius = stableSavedStateHandle?.get<String>("picked_radius")?.toIntOrNull()
                triggers[index] = triggers[index].copy(
                    config = triggers[index].config +
                        ("lat" to lat.toString()) +
                        ("lng" to lng.toString()) +
                        (if (radius != null) mapOf("radius" to radius.toString()) else emptyMap()) +
                        ("source" to "map")
                )
                stableSavedStateHandle?.set("map_picker_target", null)
            }
        }
        stableSavedStateHandle?.getLiveData<String>("picked_location")?.observeForever(locationObserver)
        onDispose {
            stableSavedStateHandle?.getLiveData<Int>("selected_icon")?.removeObserver(observer)
            stableSavedStateHandle?.getLiveData<Long>("selected_color")?.removeObserver(colorObserver)
            stableSavedStateHandle?.getLiveData<String>("picked_location")?.removeObserver(locationObserver)
        }
    }

    // Live elevated-permission status (root/Shizuku): re-probe the action
    // cards every time the screen resumes — e.g. after returning from the
    // Magisk/KernelSU grant dialog or the Shizuku grant screen — so the
    // colour-coded badge reflects the freshly granted state without leaving
    // and re-opening the task.
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefreshTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshTick++
                // Coming back from the picker: the handle always holds the
                // latest pick even if the LiveData observer missed the event,
                // so re-apply it to keep the badge in sync.
                stableSavedStateHandle?.get<Int>("selected_icon")?.let {
                    if (it in NexaFlowIcons.all.indices) selectedIconIndex = it
                }
                stableSavedStateHandle?.get<Long>("selected_color")?.let {
                    selectedIconColor = it
                }
                // Same for a location picked on the embedded map.
                stableSavedStateHandle?.get<String>("picked_location")?.let { value ->
                    val coords = value.split(',')
                    val lat = coords.getOrNull(0)?.toDoubleOrNull()
                    val lng = coords.getOrNull(1)?.toDoubleOrNull()
                    val index = stableSavedStateHandle?.get<Int>("map_picker_target")
                    if (lat != null && lng != null && index != null && index in triggers.indices) {
                        val radius = stableSavedStateHandle?.get<String>("picked_radius")?.toIntOrNull()
                        triggers[index] = triggers[index].copy(
                            config = triggers[index].config +
                                ("lat" to lat.toString()) +
                                ("lng" to lng.toString()) +
                                (if (radius != null) mapOf("radius" to radius.toString()) else emptyMap()) +
                                ("source" to "map")
                        )
                        stableSavedStateHandle?.set("map_picker_target", null)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isEditing = automationId != null

    val stringPermissionDenied = stringResource(R.string.permission_denied_hint)
    // Permission request currently waiting for the user to confirm the Samsung-style
    // explain screen. The system dialog only opens after the user taps Continue.
    var pendingPermissions by remember { mutableStateOf<Array<String>?>(null) }
    // Special permission (settings-screen) request awaiting the explain screen.
    var pendingSpecialPermission by remember { mutableStateOf<SpecialPermission?>(null) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Surface denied permissions so the user knows why the feature may not work.
        if (grants.values.any { !it }) {
            scope.launch {
                snackbarHostState.showSnackbar(stringPermissionDenied)
            }
        }
    }

    fun requestPermissions(permissions: Array<String>) {
        // Already-granted permissions need neither the explain screen nor the
        // system dialog, so skip straight past them on repeat visits.
        val allGranted = permissions.all {
            context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) return
        // Explain why the permission is needed BEFORE opening the system dialog.
        pendingPermissions = permissions
    }

    fun explainSpecialPermission(special: SpecialPermission) {
        // Explain why the permission is needed BEFORE opening its settings screen.
        pendingSpecialPermission = special
    }


    fun moveAction(from: Int, to: Int) {
        if (from !in selectedActions.indices || to !in selectedActions.indices) return
        if (from == to) return
        val item = selectedActions.removeAt(from)
        selectedActions.add(to, item)
    }

    fun moveTrigger(from: Int, to: Int) {
        if (from !in triggers.indices || to !in triggers.indices || from == to) return
        val item = triggers.removeAt(from)
        triggers.add(to, item)
    }

    fun moveConstraint(from: Int, to: Int) {
        if (from !in constraints.indices || to !in constraints.indices || from == to) return
        val item = constraints.removeAt(from)
        constraints.add(to, item)
    }

    fun showSnackbar(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun save(closeAfterSave: Boolean = true) {
        if (triggers.isEmpty()) {
            showSnackbar(stringNextNeedsTrigger)
            return
        }
        if (selectedActions.isEmpty()) {
            showSnackbar(stringNextNeedsAction)
            return
        }
        // The picker writes the selection into this entry's savedStateHandle.
        // Re-read it here as the source of truth: the LiveData observer may
        // miss the event under device recomposition timing, but the handle
        // itself always holds the latest pick.
        stableSavedStateHandle?.get<Int>("selected_icon")?.let {
            if (it in NexaFlowIcons.all.indices) selectedIconIndex = it
        }
        stableSavedStateHandle?.get<Long>("selected_color")?.let {
            selectedIconColor = it
        }
        val builtTriggers = triggers.map { draft ->
            Trigger(draft.type, draft.config)
        }
        val actions = selectedActions.map {
            Action(
                type = it.actionType,
                config = actionConfigs[it.actionType] ?: emptyMap(),
                endBehavior = actionEndBehaviors[it.actionType]
            )
        }
        val builtConstraints = constraints.map { Constraint(it.type, it.config) }
        val exitActions = selectedExitActions.map { Action(it.actionType, exitActionConfigs[it.actionType] ?: emptyMap()) }
        viewModel.saveAutomation(
            name = name,
            icon = NexaFlowIcons.all[selectedIconIndex].first,
            iconColor = selectedIconColor,
            triggers = builtTriggers,
            actions = actions,
            constraints = builtConstraints,
            exitActions = exitActions,
            // Unified end behavior: each action carries its own end behavior
            // (leave / restore / set value), so the global toggle stays off.
            // Runs are always immediate: the cooldown UI was removed entirely
            // and the engine gate is pinned to zero so every trigger fires at
            // once, no matter how often the event repeats.
            revertOnExit = false,
            cooldownSeconds = 0
        )
        // Aggressive permission flow: right after saving, request any missing
        // runtime permission through the system dialog immediately, and explain
        // the first missing special (settings-screen) permission — no detour.
        val missingRuntime = PermissionCatalog.allRuntimePermissions(builtTriggers, actions, exitActions)
            .filter {
                context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        if (missingRuntime.isNotEmpty()) {
            requestPermissions(missingRuntime.toTypedArray())
        } else {
            val missingSpecial = PermissionCatalog.allSpecialPermissions(builtTriggers, actions, exitActions)
                .firstOrNull { !PermissionShortcuts.isGranted(context, it) }
            if (missingSpecial != null) {
                explainSpecialPermission(missingSpecial)
            } else {
                // All runtime/special permissions satisfied: keep the monitoring
                // service alive in the background by requesting the battery
                // optimization exemption right away (system dialog, one tap).
                ElevatedAccessShortcuts.requestBatteryOptimizationExemption(context)
            }
        }
        if (closeAfterSave) {
            navController.popBackStack()
        } else {
            showSnackbar(stringSavedSuccessfully)
        }
    }

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = if (isEditing) stringResource(R.string.edit_task_title) else stringResource(R.string.builder_title),
                onBack = {
                    // Wizard: back on step 2 returns to step 1, then exits.
                    if (step == 1) step = 0 else navController.popBackStack()
                },
                actions = {
                    IconButton(onClick = { save(closeAfterSave = false) }) {
                        Icon(imageVector = Icons.Filled.Save, contentDescription = stringResource(R.string.quick_save))
                    }
                    IconButton(onClick = { save() }) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                    }
                }
            )
        },
        floatingActionButton = {
            // Step 1: continue to actions once a trigger is chosen.
            // Step 2: create the task once at least one action is selected.
            when {
                step == 0 && triggers.isNotEmpty() -> NexaFlowFloatingActionButton(
                    onClick = { step = 1 },
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    label = stringResource(R.string.permission_continue)
                )
                step == 1 && selectedActions.isNotEmpty() -> NexaFlowFloatingActionButton(
                    onClick = { save() },
                    icon = Icons.Filled.Check,
                    label = stringResource(R.string.create_task)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Wizard progress: numbered step bar (1/2) ─────────────
            // Sits above the name card so the user always knows which step
            // of the wizard they are on. The bar fills with the M3 spatial
            // spring as they move between triggers and actions.
            val stepProgress by animateFloatAsState(
                targetValue = (step + 1) / 2f,
                animationSpec = nexaFlowSpatialSpec()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${step + 1} / 2",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                LinearProgressIndicator(
                    progress = { stepProgress },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            // ── Name + icon (small card: icon beside the name box) ────
            NexaFlowCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconBadge(
                        icon = iconVector(NexaFlowIcons.all[selectedIconIndex].first),
                        containerColor = Color(selectedIconColor),
                        size = 48,
                        modifier = Modifier.clickable {
                            // Preseed the picker with the current color so the
                            // palette opens on the task's own accent.
                            stableSavedStateHandle?.set("selected_color", selectedIconColor)
                            navController.navigate("icon_picker")
                        }
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(text = stringResource(R.string.name_hint)) },
                        singleLine = true
                    )
                }
            }

            // ── Sequential wizard: step indicator ───────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectChip(
                    selected = step == 0,
                    onClick = { step = 0 },
                    label = stringResource(R.string.step_triggers)
                )
                SelectChip(
                    selected = step == 1,
                    onClick = { step = 1 },
                    label = stringResource(R.string.step_actions)
                )
            }

            // The transitionSpec lambda is not composable, so the specs are
            // read here in the composable body (transitionSpec captures them).
            val stepSpatial = nexaFlowSpatialSpec<IntOffset>()
            val stepEffects = nexaFlowEffectsSpec<Float>()
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    // Google 2026 directional step transition: content slides
                    // with the M3 Expressive spatial spring while fading.
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = stepSpatial,
                        initialOffsetX = { it / 3 * direction }
                    ) + fadeIn(animationSpec = stepEffects)) togetherWith
                        (slideOutHorizontally(
                            animationSpec = stepSpatial,
                            targetOffsetX = { -it / 3 * direction }
                        ) + fadeOut(animationSpec = stepEffects))
                },
                label = "wizardStep"
            ) { currentStep ->
                if (currentStep == 0) {
                // ── Step 1: trigger + its constraints ───────────────
                NexaFlowCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(text = stringResource(R.string.section_when))
                    // Single-select trigger picker, visible only while no
                    // trigger is chosen: a category accordion where only ONE
                    // chip is open at a time. Picking a type folds the picker
                    // away and leaves just its expanded editor (which keeps
                    // its own type chips for later changes).
                    NexaFlowAnimatedVisibility(visible = triggers.isEmpty()) {
                        CategoryAccordion(
                            tabs = triggerCategories.map { category ->
                                stringResource(category.headerRes) to category.icon()
                            },
                            expandedIndex = expandedTriggerCategory,
                            onExpandedChange = { expandedTriggerCategory = it }
                        ) { index ->
                            val category = triggerCategories[index]
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                triggerTypeOptions
                                    .filter { triggerCategoryOf[it] == category }
                                    .forEach { type ->
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = Color.Transparent,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    triggers.clear()
                                                    triggers.add(TriggerDraft(type, defaultTriggerConfig(type)))
                                                    lastAddedTrigger = triggers.lastIndex
                                                    expandedTriggerCategory = null
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                IconBadge(
                                                    icon = type.icon(),
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = stringResource(type.labelRes()),
                                                    modifier = Modifier.weight(1f),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Normal,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                            }
                        }
                    }
                    triggers.forEachIndexed { index, draft ->
                val triggerDragging = triggerDrag.draggedIndex == index
                TriggerEditorCard(
                    draft = draft,
                    index = index,
                    total = triggers.size,
                    modifier = Modifier.taskDragOffset(triggerDrag, draft, triggerDragging),
                    isDragging = triggerDragging,
                    onMoveUp = { moveTrigger(index, index - 1) },
                    onMoveDown = { moveTrigger(index, index + 1) },
                    onDragStart = { startDrag(triggerDrag, index) },
                    onDragDelta = { dragBy(triggerDrag, triggers, it) { f, t -> moveTrigger(f, t) } },
                    onDragEnd = { endDrag(triggerDrag) },
                    onConfigChange = { updated ->
                        triggers[index] = updated
                    },
                    onRemove = { triggers.removeAt(index) },
                    initiallyExpanded = index == lastAddedTrigger,
                    onPickApp = { appPickerTarget = "trigger:$index" },
                    onPickBluetooth = { bluetoothPickerTarget = index },
                    onPickCalendar = { calendarPickerTarget = index },
                    onRequestPermission = { requestPermissions(it) },
                    onExplainSpecial = { explainSpecialPermission(it) },
                    refreshKey = permissionRefreshTick,
                    onPickFromMap = { launchMapPicker(index) },
                    onUseCurrentLocation = { locateAndFill(index) }
                )
            }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // ── IF (constraints) ────────────────────────────
                    SectionHeader(
                        text = stringResource(R.string.section_constraints),
                trailing = {
                    IconButton(onClick = { showConstraintPicker = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_constraint)
                        )
                    }
                }
            )
            if (constraints.isEmpty()) {
                Text(
                    text = stringResource(R.string.constraints_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                constraints.forEachIndexed { index, draft ->
                    val constraintDragging = constraintDrag.draggedIndex == index
                    ConstraintEditorCard(
                        draft = draft,
                        index = index,
                        total = constraints.size,
                        modifier = Modifier.taskDragOffset(constraintDrag, draft, constraintDragging),
                        isDragging = constraintDragging,
                        onMoveUp = { moveConstraint(index, index - 1) },
                        onMoveDown = { moveConstraint(index, index + 1) },
                        onDragStart = { startDrag(constraintDrag, index) },
                        onDragDelta = { dragBy(constraintDrag, constraints, it) { f, t -> moveConstraint(f, t) } },
                        onDragEnd = { endDrag(constraintDrag) },
                        initiallyExpanded = index == lastAddedConstraint,
                        onConfigChange = { constraints[index] = it },
                        onRemove = { constraints.removeAt(index) }
                    )
                }
            }
                    }
                }
            } else {
                // ── Step 2: actions + end behavior ───────────────────
                NexaFlowCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {                        // ── THEN (actions) ──────────────────────────
                            SectionHeader(text = stringResource(R.string.section_actions))
                        // Category accordion: only ONE chip is open at a time.
                        // Tapping an option adds it immediately and folds the
                        // accordion away so the new card's configuration is
                        // right there — never stuck inside the picker list.
                        CategoryAccordion(
                            tabs = actionCategories.map { category ->
                                stringResource(category.headerRes) to category.icon()
                            },
                            expandedIndex = expandedActionCategory,
                            onExpandedChange = { expandedActionCategory = it }
                        ) { index ->
                            val category = actionCategories[index]
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                actionOptions
                                    .filter { it.category == category }
                                    .forEach { option ->
                                        ActionOptionRow(
                                            option = option,
                                            checked = option in selectedActions,
                                            onToggle = {
                                                if (option !in selectedActions) {
                                                    selectedActions.add(option)
                                                    lastAddedAction = selectedActions.lastIndex
                                                    // A picked plugin action immediately asks which plugin.
                                                    if (option.actionType == ActionType.PLUGIN_FIRE) {
                                                        pluginPickerTarget = true
                                                    }
                                                }
                                                // Collapse so the user sees the new card's config.
                                                expandedActionCategory = null
                                            }
                                        )
                                    }
                            }
                        }
                        selectedActions.forEachIndexed { index, option ->
                            val actionDragging = actionDrag.draggedIndex == index
                            SelectedActionCard(
                    modifier = Modifier.taskDragOffset(actionDrag, option, actionDragging),
                    isDragging = actionDragging,
                    onDragStart = { startDrag(actionDrag, index) },
                    onDragDelta = { dragBy(actionDrag, selectedActions, it) { f, t -> moveAction(f, t) } },
                    onDragEnd = { endDrag(actionDrag) },
                    option = option,
                    index = index,
                    total = selectedActions.size,
                    config = actionConfigs[option.actionType] ?: emptyMap(),
                    onConfigChange = { actionConfigs[option.actionType] = it },
                    onMoveUp = { moveAction(index, index - 1) },
                    onMoveDown = { moveAction(index, index + 1) },
                    onRemove = {
                        selectedActions.remove(option)
                        actionConfigs.remove(option.actionType)
                        actionEndBehaviors.remove(option.actionType)
                    },
                    onPickApp = { appPickerTarget = "action:${option.actionType.name}" },
                    onRequestPermission = { requestPermissions(it) },
                    onExplainSpecial = { explainSpecialPermission(it) },
                    refreshKey = permissionRefreshTick,
                    context = context,
                    availableVariables = availableVariables,
                    automations = automations,
                    onPluginConfigure = {
                        configurePlugin(
                            actionConfigs[ActionType.PLUGIN_FIRE]?.get("package"),
                            actionConfigs[ActionType.PLUGIN_FIRE]?.get("receiver"),
                            actionConfigs[ActionType.PLUGIN_FIRE] ?: emptyMap()
                        )
                    },
                    initiallyExpanded = index == lastAddedAction
                )
            }

                    // ── When the task ends: anchored at the absolute bottom of
                    //    the builder and structurally linked to every execution
                    //    action above — the final step of the work path. Once
                    //    any card above is opened it stays open, so this section
                    //    never hides or changes expanded content.
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SectionHeader(text = stringResource(R.string.section_exit_behavior))
                    // Every executed action appears here with its own end options, so the
                    // section truly gathers all the tasks the user just configured.
                    val endActions = selectedActions
                    if (endActions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.exit_nothing_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        endActions.forEach { option ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(option.titleRes),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                EndBehaviorEditor(
                                    actionType = option.actionType,
                                    behavior = actionEndBehaviors[option.actionType],
                                    onBehaviorChange = { actionEndBehaviors[option.actionType] = it },
                                    showLabel = false
                                )
                            }
                        }
                    }
                    }
                }
            }
            }
        }
    }

    if (showConstraintPicker) {
        ConstraintTypePickerDialog(
            onPick = { type ->
                constraints.add(ConstraintDraft(type, defaultConstraintConfig(type)))
                lastAddedConstraint = constraints.lastIndex
                showConstraintPicker = false
            },
            onDismiss = { showConstraintPicker = false }
        )
    }

    if (pluginPickerTarget) {
        PluginPickerDialog(
            plugins = plugins,
            onRefresh = { viewModel.refreshPlugins() },
            onPick = { plugin ->
                pluginPickerTarget = false
                configurePlugin(plugin.packageName, plugin.receiverClass, emptyMap())
            },
            onDismiss = {
                pluginPickerTarget = false
                // Dropping the picker without configuring removes the stub action.
                if ((actionConfigs[ActionType.PLUGIN_FIRE] ?: emptyMap()).isEmpty()) {
                    selectedActions.removeAll { it.actionType == ActionType.PLUGIN_FIRE }
                }
            }
        )
    }

    bluetoothPickerTarget?.let { index ->
        if (index in triggers.indices) {
            BluetoothDevicePickerDialog(
                onPick = { device ->
                    triggers[index] = triggers[index].copy(
                        config = mapOf(
                            "deviceName" to device.name,
                            "deviceAddress" to device.address,
                            "event" to (triggers[index].config["event"] ?: "CONNECTED")
                        )
                    )
                    bluetoothPickerTarget = null
                },
                onDismiss = { bluetoothPickerTarget = null }
            )
        } else {
            bluetoothPickerTarget = null
        }
    }

    calendarPickerTarget?.let { index ->
        if (index in triggers.indices) {
            CalendarPickerDialog(
                onPick = { calendar ->
                    triggers[index] = triggers[index].copy(
                        config = mapOf(
                            "calendar" to calendar.name,
                            "contains" to (triggers[index].config["contains"] ?: ""),
                            "event" to (triggers[index].config["event"] ?: "EVENT_START"),
                            "beforeMinutes" to (triggers[index].config["beforeMinutes"] ?: "0")
                        )
                    )
                    calendarPickerTarget = null
                },
                onDismiss = { calendarPickerTarget = null }
            )
        } else {
            calendarPickerTarget = null
        }
    }

    appPickerTarget?.let { target ->
        val triggerIndex = target.removePrefix("trigger:").toIntOrNull()
        if (triggerIndex != null) {
            val triggerPackages = (triggers[triggerIndex].config["packages"] ?: triggers[triggerIndex].config["package"] ?: "")
                .split(',').map { it.trim() }.filter { it.isNotEmpty() }
            AppPickerDialog(
                onPickSingle = { app ->
                    val merged = (triggerPackages + app.packageName).distinct()
                    val current = triggers[triggerIndex]
                    triggers[triggerIndex] = current.copy(
                        config = mapOf("packages" to merged.joinToString(","))
                    )
                    appPickerTarget = null
                },
                onPickMultiple = { apps ->
                    val current = triggers[triggerIndex]
                    triggers[triggerIndex] = current.copy(
                        config = mapOf("packages" to apps.joinToString(",") { it.packageName })
                    )
                    appPickerTarget = null
                },
                multiSelect = true,
                preSelectedPackages = triggerPackages,
                onDismiss = { appPickerTarget = null }
            )
        } else {
            val actionTypeName = target.removePrefix("action:")
            val isOpenApp = actionTypeName == ActionType.SYSTEM_OPEN_APP.name
            val singlePickType = when (actionTypeName) {
                ActionType.APPLICATION_CLOSE_APP.name -> ActionType.APPLICATION_CLOSE_APP
                ActionType.APPLICATION_OPEN_APP_SETTINGS.name -> ActionType.APPLICATION_OPEN_APP_SETTINGS
                ActionType.SYSTEM_BLOCK_NOTIFICATION.name -> ActionType.SYSTEM_BLOCK_NOTIFICATION
                ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS.name -> ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS
                else -> null
            }
            when {
                isOpenApp -> {
                    val existing = actionConfigs[ActionType.SYSTEM_OPEN_APP]
                    val pre = (existing?.get("packages") ?: existing?.get("package") ?: "")
                        .split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    AppPickerDialog(
                        onPickSingle = { app ->
                            val merged = (pre + app.packageName).distinct()
                            actionConfigs[ActionType.SYSTEM_OPEN_APP] = mapOf("packages" to merged.joinToString(","))
                            appPickerTarget = null
                        },
                        onPickMultiple = { packages ->
                            actionConfigs[ActionType.SYSTEM_OPEN_APP] = mapOf("packages" to packages.joinToString(",") { it.packageName })
                            appPickerTarget = null
                        },
                        multiSelect = true,
                        preSelectedPackages = pre,
                        onDismiss = { appPickerTarget = null }
                    )
                }
                singlePickType != null -> AppPickerDialog(
                    onPickSingle = { app ->
                        actionConfigs[singlePickType] = mapOf("package" to app.packageName)
                        appPickerTarget = null
                    },
                    onDismiss = { appPickerTarget = null }
                )
                else -> appPickerTarget = null
            }
        }
    }

    // Samsung-style explain screens shown before granting a permission.
    pendingPermissions?.let { permissions ->
        PermissionExplainDialog(
            info = remember(permissions) { permissionExplainInfo(permissions) },
            onContinue = {
                pendingPermissions = null
                permissionLauncher.launch(permissions)
            },
            onDismiss = { pendingPermissions = null }
        )
    }
    pendingSpecialPermission?.let { special ->
        PermissionExplainDialog(
            info = specialPermissionExplainInfo(special),
            onContinue = {
                pendingSpecialPermission = null
                PermissionShortcuts.openSpecial(context, special)
            },
            onDismiss = { pendingSpecialPermission = null }
        )
    }
}
