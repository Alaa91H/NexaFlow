package com.nexaflow.feature.builder

import android.app.Activity
import android.content.Context
import android.content.ComponentName
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.nexaflow.core.execution.compat.CommandRequirementCatalog
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
import com.nexaflow.domain.models.RoutineTemplateCatalog
import com.nexaflow.domain.models.EndBehaviorCatalog
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private const val TAG = "AutomationBuilder"

enum class ActionCategory(val headerRes: Int, val color: Color) {
    ROUTINES(R.string.category_routines, Color(0xFF5F6368)),
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
    ActionOption(R.string.action_media_search, R.string.action_media_search_sub, Icons.Filled.MusicNote, ActionType.SYSTEM_MEDIA_PLAY_FROM_SEARCH, ActionCategory.MEDIA),
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
    ActionOption(R.string.action_update_google_play_apps, R.string.action_update_google_play_apps_sub, Icons.Filled.Storefront, ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS, ActionCategory.APPS),
    ActionOption(R.string.action_play_updates, R.string.action_play_updates_sub, Icons.Filled.Storefront, ActionType.SYSTEM_OPEN_PLAY_UPDATES, ActionCategory.APPS),
    ActionOption(R.string.action_system_update, R.string.action_system_update_sub, Icons.Filled.Settings, ActionType.SYSTEM_OPEN_SYSTEM_UPDATE_SETTINGS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_launch_app, R.string.action_launch_app_sub, Icons.Filled.Apps, ActionType.APPLICATION_LAUNCH_APP, ActionCategory.APPS),
    ActionOption(R.string.action_galaxy_store, R.string.action_galaxy_store_sub, Icons.Filled.Store, ActionType.SYSTEM_OPEN_GALAXY_STORE, ActionCategory.APPS),
    // SYSTEM
    ActionOption(R.string.action_flashlight, R.string.action_flashlight_sub, Icons.Filled.FlashlightOn, ActionType.SYSTEM_FLASHLIGHT, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_url, R.string.action_open_url_sub, Icons.Filled.Link, ActionType.SYSTEM_OPEN_URL, ActionCategory.SYSTEM),
    ActionOption(R.string.action_http_request, R.string.action_http_request_sub, Icons.Filled.Public, ActionType.SYSTEM_HTTP_REQUEST, ActionCategory.SYSTEM),
    ActionOption(R.string.action_power_saver, R.string.action_power_saver_sub, Icons.Filled.EnergySavingsLeaf, ActionType.SYSTEM_POWER_SAVER, ActionCategory.SYSTEM),
    ActionOption(R.string.action_animations, R.string.action_animations_sub, Icons.Filled.Palette, ActionType.SYSTEM_ANIMATIONS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_lock_screen, R.string.action_lock_screen_sub, Icons.Filled.Lock, ActionType.SYSTEM_LOCK_SCREEN, ActionCategory.SYSTEM),
    ActionOption(R.string.action_set_alarm, R.string.action_set_alarm_sub, Icons.Filled.Schedule, ActionType.SYSTEM_SET_ALARM, ActionCategory.SYSTEM),
    ActionOption(R.string.action_timer, R.string.action_timer_sub, Icons.Filled.HourglassEmpty, ActionType.SYSTEM_SET_TIMER, ActionCategory.SYSTEM),
    ActionOption(R.string.action_wait, R.string.action_wait_sub, Icons.Filled.HourglassEmpty, ActionType.SYSTEM_WAIT, ActionCategory.SYSTEM),
    ActionOption(R.string.action_go_home, R.string.action_go_home_sub, Icons.Filled.Home, ActionType.SYSTEM_GO_HOME, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_settings, R.string.action_open_settings_sub, Icons.Filled.Settings, ActionType.SYSTEM_OPEN_SETTINGS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_quick_settings, R.string.action_open_quick_settings_sub, Icons.Filled.Tune, ActionType.SYSTEM_OPEN_QUICK_SETTINGS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_wake_screen, R.string.action_wake_screen_sub, Icons.Filled.WbSunny, ActionType.SYSTEM_WAKE_SCREEN, ActionCategory.DISPLAY),
    ActionOption(R.string.action_clipboard, R.string.action_clipboard_sub, Icons.Filled.ContentPaste, ActionType.SYSTEM_CLIPBOARD_SET, ActionCategory.SYSTEM),
    ActionOption(R.string.action_set_setting, R.string.action_set_setting_sub, Icons.Filled.Tune, ActionType.SYSTEM_SET_SETTING, ActionCategory.SYSTEM),
    ActionOption(R.string.action_screenshot, R.string.action_screenshot_sub, Icons.Filled.CameraAlt, ActionType.SYSTEM_SCREENSHOT, ActionCategory.SYSTEM),
    ActionOption(R.string.action_input_text, R.string.action_input_text_sub, Icons.AutoMirrored.Filled.Chat, ActionType.SYSTEM_INPUT_TEXT, ActionCategory.SYSTEM),
    ActionOption(R.string.action_key_event, R.string.action_key_event_sub, Icons.Filled.Build, ActionType.SYSTEM_KEY_EVENT, ActionCategory.SYSTEM),
    ActionOption(R.string.action_input_tap, R.string.action_input_tap_sub, Icons.Filled.GpsFixed, ActionType.SYSTEM_INPUT_TAP, ActionCategory.SYSTEM),
    ActionOption(R.string.action_input_swipe, R.string.action_input_swipe_sub, Icons.AutoMirrored.Filled.ArrowForward, ActionType.SYSTEM_INPUT_SWIPE, ActionCategory.SYSTEM),
    ActionOption(R.string.action_color_inversion, R.string.action_color_inversion_sub, Icons.Filled.Contrast, ActionType.SYSTEM_COLOR_INVERSION, ActionCategory.DISPLAY),
    ActionOption(R.string.action_grayscale, R.string.action_grayscale_sub, Icons.Filled.Gradient, ActionType.SYSTEM_GRAYSCALE, ActionCategory.DISPLAY),
    ActionOption(R.string.action_extra_dim, R.string.action_extra_dim_sub, Icons.Filled.BrightnessLow, ActionType.SYSTEM_EXTRA_DIM, ActionCategory.DISPLAY),
    ActionOption(R.string.action_night_light, R.string.action_night_light_sub, Icons.Filled.NightsStay, ActionType.SYSTEM_NIGHT_LIGHT, ActionCategory.DISPLAY),
    ActionOption(R.string.action_haptic_feedback, R.string.action_haptic_feedback_sub, Icons.Filled.TouchApp, ActionType.SYSTEM_HAPTIC_FEEDBACK, ActionCategory.SOUND),
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
    ActionOption(R.string.action_haptic_intensity, R.string.action_haptic_intensity_sub, Icons.Filled.Equalizer, ActionType.SYSTEM_HAPTIC_INTENSITY, ActionCategory.SOUND),
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
    ActionOption(R.string.action_restart_system_ui, R.string.action_restart_system_ui_sub, Icons.Filled.Restore, ActionType.SYSTEM_RESTART_SYSTEM_UI, ActionCategory.SYSTEM),
    // BATTERY
    ActionOption(R.string.action_battery_alert, R.string.action_battery_alert_sub, Icons.Filled.BatteryAlert, ActionType.BATTERY_ALERTS, ActionCategory.BATTERY),
    ActionOption(R.string.action_charging_alert, R.string.action_charging_alert_sub, Icons.Filled.BatteryChargingFull, ActionType.BATTERY_CHARGING_NOTIFICATIONS, ActionCategory.BATTERY),
    // v3.28 wave
    ActionOption(R.string.action_toast, R.string.action_toast_sub, Icons.Filled.Info, ActionType.SYSTEM_TOAST, ActionCategory.SYSTEM),
    ActionOption(R.string.action_alert, R.string.action_alert_sub, Icons.Filled.Warning, ActionType.SYSTEM_ALERT, ActionCategory.SYSTEM),
    ActionOption(R.string.action_vibrate_pattern, R.string.action_vibrate_pattern_sub, Icons.Filled.Vibration, ActionType.SYSTEM_VIBRATE_PATTERN, ActionCategory.SOUND),
    ActionOption(R.string.action_paste, R.string.action_paste_sub, Icons.Filled.ContentPaste, ActionType.SYSTEM_PASTE, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_app_drawer, R.string.action_open_app_drawer_sub, Icons.Filled.Apps, ActionType.SYSTEM_OPEN_APP_DRAWER, ActionCategory.APPS),
    ActionOption(R.string.action_toggle_pip, R.string.action_toggle_pip_sub, Icons.Filled.PictureInPicture, ActionType.SYSTEM_TOGGLE_PIP, ActionCategory.DISPLAY),
    ActionOption(R.string.action_wifi_connect, R.string.action_wifi_connect_sub, Icons.Filled.Wifi, ActionType.SYSTEM_WIFI_CONNECT, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_wifi_forget, R.string.action_wifi_forget_sub, Icons.Filled.WifiOff, ActionType.SYSTEM_WIFI_FORGET, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_data_roaming, R.string.action_data_roaming_sub, Icons.Filled.DataUsage, ActionType.SYSTEM_DATA_ROAMING, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_screensaver_timeout, R.string.action_screensaver_timeout_sub, Icons.Filled.Timelapse, ActionType.SYSTEM_SCREENSAVER_TIMEOUT, ActionCategory.DISPLAY),
    ActionOption(R.string.action_pointer_speed, R.string.action_pointer_speed_sub, Icons.Filled.GpsFixed, ActionType.SYSTEM_POINTER_SPEED, ActionCategory.DISPLAY),
    ActionOption(R.string.action_install_apk, R.string.action_install_apk_sub, Icons.Filled.Download, ActionType.SYSTEM_INSTALL_APK, ActionCategory.APPS),
    ActionOption(R.string.action_uninstall_app, R.string.action_uninstall_app_sub, Icons.Filled.Delete, ActionType.SYSTEM_UNINSTALL_APP, ActionCategory.APPS),
    ActionOption(R.string.action_disable_app, R.string.action_disable_app_sub, Icons.Filled.Block, ActionType.SYSTEM_DISABLE_APP, ActionCategory.APPS),
    ActionOption(R.string.action_enable_app, R.string.action_enable_app_sub, Icons.Filled.CheckCircle, ActionType.SYSTEM_ENABLE_APP, ActionCategory.APPS),
    ActionOption(R.string.action_set_notification_tone, R.string.action_set_notification_tone_sub, Icons.Filled.MusicNote, ActionType.SYSTEM_SET_NOTIFICATION_TONE, ActionCategory.SOUND),
    ActionOption(R.string.action_call_vibration, R.string.action_call_vibration_sub, Icons.Filled.Vibration, ActionType.SYSTEM_CALL_VIBRATION, ActionCategory.SOUND),
    ActionOption(R.string.action_open_network_settings, R.string.action_open_network_settings_sub, Icons.Filled.Wifi, ActionType.SYSTEM_OPEN_NETWORK_SETTINGS, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_open_nfc_settings, R.string.action_open_nfc_settings_sub, Icons.Filled.Nfc, ActionType.SYSTEM_OPEN_NFC_SETTINGS, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_open_data_saver_settings, R.string.action_open_data_saver_settings_sub, Icons.Filled.DataUsage, ActionType.SYSTEM_OPEN_DATA_SAVER_SETTINGS, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_open_developer_settings, R.string.action_open_developer_settings_sub, Icons.Filled.Build, ActionType.SYSTEM_OPEN_DEVELOPER_SETTINGS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_maps, R.string.action_open_maps_sub, Icons.Filled.Map, ActionType.SYSTEM_OPEN_MAPS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_soft_restart, R.string.action_soft_restart_sub, Icons.Filled.RestartAlt, ActionType.SYSTEM_SOFT_RESTART, ActionCategory.SYSTEM),
    ActionOption(R.string.action_status_bar_toggle, R.string.action_status_bar_toggle_sub, Icons.Filled.Visibility, ActionType.SYSTEM_STATUS_BAR_TOGGLE, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_open_contacts, R.string.action_open_contacts_sub, Icons.Filled.Contacts, ActionType.SYSTEM_OPEN_CONTACTS, ActionCategory.APPS),
    ActionOption(R.string.action_send_email, R.string.action_send_email_sub, Icons.Filled.Email, ActionType.SYSTEM_SEND_EMAIL, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_open_notification_settings, R.string.action_open_notification_settings_sub, Icons.Filled.Notifications, ActionType.SYSTEM_OPEN_NOTIFICATION_SETTINGS, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_open_privacy_settings, R.string.action_open_privacy_settings_sub, Icons.Filled.Lock, ActionType.SYSTEM_OPEN_PRIVACY_SETTINGS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_cast_settings, R.string.action_open_cast_settings_sub, Icons.Filled.Cast, ActionType.SYSTEM_OPEN_CAST_SETTINGS, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_open_input_method_settings, R.string.action_open_input_method_settings_sub, Icons.Filled.Keyboard, ActionType.SYSTEM_OPEN_INPUT_METHOD_SETTINGS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_default_apps_settings, R.string.action_open_default_apps_settings_sub, Icons.Filled.Apps, ActionType.SYSTEM_OPEN_DEFAULT_APPS_SETTINGS, ActionCategory.APPS),
    ActionOption(R.string.action_open_vpn_settings, R.string.action_open_vpn_settings_sub, Icons.Filled.Lock, ActionType.SYSTEM_OPEN_VPN_SETTINGS, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_open_date_settings, R.string.action_open_date_settings_sub, Icons.Filled.DateRange, ActionType.SYSTEM_OPEN_DATE_SETTINGS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_print_settings, R.string.action_open_print_settings_sub, Icons.Filled.Print, ActionType.SYSTEM_OPEN_PRINT_SETTINGS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_device_admin_settings, R.string.action_open_device_admin_settings_sub, Icons.Filled.Security, ActionType.SYSTEM_OPEN_DEVICE_ADMIN_SETTINGS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_usage_access_settings, R.string.action_open_usage_access_settings_sub, Icons.Filled.BarChart, ActionType.SYSTEM_OPEN_USAGE_ACCESS_SETTINGS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_airplane_settings, R.string.action_open_airplane_settings_sub, Icons.Filled.AirplanemodeActive, ActionType.SYSTEM_OPEN_AIRPLANE_MODE_SETTINGS, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_bluetooth_scan, R.string.action_bluetooth_scan_sub, Icons.Filled.Bluetooth, ActionType.SYSTEM_BLUETOOTH_SCAN, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_wifi_scan_now, R.string.action_wifi_scan_now_sub, Icons.Filled.Wifi, ActionType.SYSTEM_WIFI_SCAN_NOW, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_set_timezone, R.string.action_set_timezone_sub, Icons.Filled.Schedule, ActionType.SYSTEM_SET_TIMEZONE, ActionCategory.SYSTEM),
    // PLUGINS
    ActionOption(R.string.action_plugin, R.string.action_plugin_sub, Icons.Filled.Extension, ActionType.PLUGIN_FIRE, ActionCategory.PLUGINS),
    // ADVANCED — shown only when the matching elevated channel exists
    // (the compatibility engine hides them otherwise).
    ActionOption(R.string.action_shizuku, R.string.action_shizuku_sub, Icons.Filled.Terminal, ActionType.ADVANCED_SHIZUKU, ActionCategory.SYSTEM),
    ActionOption(R.string.action_root, R.string.action_root_sub, Icons.Filled.Terminal, ActionType.ADVANCED_ROOT, ActionCategory.SYSTEM)
)

/**
 * A focused, ordered view over the canonical catalog. Routine options keep
 * their native category and are never persisted as a distinct action kind.
 */
internal fun optionsForActionCategory(
    category: ActionCategory,
    options: List<ActionOption> = actionOptions
): List<ActionOption> = if (category == ActionCategory.ROUTINES) {
    AutomationOptionCatalog.recurringActionOrder
        .mapNotNull { type -> options.firstOrNull { it.actionType == type } }
} else {
    options.filter { it.category == category }
}

internal val actionCategories: List<ActionCategory> = ActionCategory.entries.toList()

/** Representative icon per action category for the accordion chips. */
internal fun ActionCategory.icon(): ImageVector = when (this) {
    ActionCategory.ROUTINES -> Icons.Filled.Schedule
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

/** Action types whose summary is simply On/Off based on `config["enabled"]`. */
private val TOGGLE_SUMMARY_ACTIONS = setOf(
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
    ActionType.SYSTEM_DARK_MODE,
    ActionType.SYSTEM_COLOR_INVERSION,
    ActionType.SYSTEM_GRAYSCALE,
    ActionType.SYSTEM_EXTRA_DIM,
    ActionType.SYSTEM_NIGHT_LIGHT,
    ActionType.SYSTEM_HAPTIC_FEEDBACK,
    ActionType.SYSTEM_SOUND_EFFECTS,
    ActionType.SYSTEM_DATA_SAVER,
    ActionType.SYSTEM_SCREENSAVER,
    ActionType.SYSTEM_ALWAYS_ON_DISPLAY,
    ActionType.SYSTEM_SHOW_TAPS,
    ActionType.SYSTEM_POINTER_LOCATION,
    ActionType.SYSTEM_ADAPTIVE_BATTERY,
    ActionType.SYSTEM_AUTO_TIME,
    ActionType.SYSTEM_AUTO_TIMEZONE,
    ActionType.SYSTEM_CAMERA_SHUTTER_SOUND,
    ActionType.SYSTEM_WIFI_SCANNING,
    ActionType.SYSTEM_DATA_ROAMING,
    ActionType.SYSTEM_CALL_VIBRATION,
    ActionType.SYSTEM_STATUS_BAR_TOGGLE
)

/** Settings-open actions whose summary is a static localized label. */
private val SETTINGS_OPEN_SUMMARY = mapOf(
    ActionType.SYSTEM_OPEN_WIFI_SETTINGS to R.string.settings_wifi,
    ActionType.SYSTEM_OPEN_BLUETOOTH_SETTINGS to R.string.settings_bluetooth,
    ActionType.SYSTEM_OPEN_LOCATION_SETTINGS to R.string.settings_location,
    ActionType.SYSTEM_OPEN_DATA_USAGE_SETTINGS to R.string.settings_data_usage,
    ActionType.SYSTEM_OPEN_BATTERY_SETTINGS to R.string.settings_battery,
    ActionType.SYSTEM_OPEN_DISPLAY_SETTINGS to R.string.settings_display,
    ActionType.SYSTEM_OPEN_SOUND_SETTINGS to R.string.settings_sound,
    ActionType.SYSTEM_OPEN_STORAGE_SETTINGS to R.string.settings_storage,
    ActionType.SYSTEM_OPEN_SECURITY_SETTINGS to R.string.settings_security,
    ActionType.SYSTEM_OPEN_ACCESSIBILITY_SETTINGS to R.string.settings_accessibility,
    ActionType.SYSTEM_OPEN_APP_SETTINGS_LIST to R.string.settings_apps,
    ActionType.SYSTEM_OPEN_ABOUT_PHONE to R.string.settings_about,
    ActionType.SYSTEM_OPEN_NETWORK_SETTINGS to R.string.settings_network,
    ActionType.SYSTEM_OPEN_NFC_SETTINGS to R.string.settings_nfc,
    ActionType.SYSTEM_OPEN_DATA_SAVER_SETTINGS to R.string.settings_data_saver,
    ActionType.SYSTEM_OPEN_DEVELOPER_SETTINGS to R.string.settings_developer,
    ActionType.SYSTEM_OPEN_NOTIFICATION_SETTINGS to R.string.settings_notifications,
    ActionType.SYSTEM_OPEN_PRIVACY_SETTINGS to R.string.settings_privacy,
    ActionType.SYSTEM_OPEN_CAST_SETTINGS to R.string.settings_cast,
    ActionType.SYSTEM_OPEN_INPUT_METHOD_SETTINGS to R.string.settings_input_method,
    ActionType.SYSTEM_OPEN_DEFAULT_APPS_SETTINGS to R.string.settings_default_apps,
    ActionType.SYSTEM_OPEN_VPN_SETTINGS to R.string.settings_vpn,
    ActionType.SYSTEM_OPEN_DATE_SETTINGS to R.string.settings_date,
    ActionType.SYSTEM_OPEN_PRINT_SETTINGS to R.string.settings_print,
    ActionType.SYSTEM_OPEN_DEVICE_ADMIN_SETTINGS to R.string.settings_device_admin,
    ActionType.SYSTEM_OPEN_USAGE_ACCESS_SETTINGS to R.string.settings_usage_access,
    ActionType.SYSTEM_OPEN_AIRPLANE_MODE_SETTINGS to R.string.settings_airplane
)

/** Action types whose summary is the `config["package"]` value. */
private val PACKAGE_SUMMARY_ACTIONS = setOf(
    ActionType.APPLICATION_OPEN_APP_SETTINGS,
    ActionType.SYSTEM_BLOCK_NOTIFICATION,
    ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS,
    ActionType.APPLICATION_CLOSE_APP,
    ActionType.SYSTEM_FORCE_STOP_APP,
    ActionType.SYSTEM_CLEAR_APP_DATA,
    ActionType.SYSTEM_UNINSTALL_APP,
    ActionType.SYSTEM_DISABLE_APP,
    ActionType.SYSTEM_ENABLE_APP
)

/** Actions with no config summary (show only the name). */
private val NO_SUMMARY_ACTIONS = setOf(
    ActionType.SYSTEM_PASTE,
    ActionType.SYSTEM_OPEN_APP_DRAWER,
    ActionType.SYSTEM_TOGGLE_PIP,
    ActionType.SYSTEM_SOFT_RESTART,
    ActionType.SYSTEM_OPEN_CONTACTS,
    ActionType.SYSTEM_BLUETOOTH_SCAN,
    ActionType.SYSTEM_WIFI_SCAN_NOW
)

/**
 * One-line summary of the chosen action values for the collapsed header,
 * mirroring triggerSummary/constraintSummary so every builder row reads
 * "Execution N · <name · chosen values>". Falls back to the action name
 * alone when nothing is configured yet (immediate one-shot actions).
 */
@Composable
private fun actionSummary(option: ActionOption, config: Map<String, String>): String {
    val name = stringResource(option.titleRes)
    val value: String? = when {
        option.actionType in TOGGLE_SUMMARY_ACTIONS ->
            if (config["enabled"]?.toBoolean() ?: true) stringResource(R.string.state_on)
            else stringResource(R.string.state_off)

        option.actionType in SETTINGS_OPEN_SUMMARY ->
            stringResource(SETTINGS_OPEN_SUMMARY[option.actionType]!!)

        option.actionType in PACKAGE_SUMMARY_ACTIONS ->
            config["package"].orEmpty().trim().ifEmpty { null }

        option.actionType in NO_SUMMARY_ACTIONS -> null

        else -> actionSummaryDetail(option, config)
    }
    return if (value.isNullOrBlank()) name else "$name · $value"
}

/** Type-specific detail for actions that need custom summary logic. */
@Composable
private fun actionSummaryDetail(option: ActionOption, config: Map<String, String>): String? =
    when (option.actionType) {
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
        ActionType.SYSTEM_SET_TIMER -> "${config["seconds"] ?: "300"}s"
        ActionType.SYSTEM_MEDIA_PLAY_FROM_SEARCH -> config["query"].orEmpty().trim().ifEmpty { null }
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
        ActionType.BATTERY_ALERTS ->
            stringResource(R.string.alert_below, config["below"]?.toIntOrNull() ?: 20)
        ActionType.BATTERY_CHARGING_NOTIFICATIONS -> config["sound"] ?: "DEFAULT"
        ActionType.ADVANCED_ROOT,
        ActionType.ADVANCED_SHIZUKU -> config["command"].orEmpty().trim().ifEmpty { null }
        ActionType.PLUGIN_FIRE -> config["blurb"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_VIBRATE -> "${config["seconds"] ?: "1"}s"
        ActionType.SYSTEM_CLIPBOARD_SET -> config["text"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_SET_SETTING -> {
            val key = config["key"].orEmpty().trim()
            if (key.isEmpty()) null else "$key = ${config["value"] ?: ""}"
        }
        ActionType.SYSTEM_SCREENSHOT -> config["filename"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_INPUT_TEXT -> config["text"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_KEY_EVENT -> config["key"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_INPUT_TAP -> "${config["x"] ?: "0"}, ${config["y"] ?: "0"}"
        ActionType.SYSTEM_INPUT_SWIPE ->
            "(${config["x1"] ?: "0"},${config["y1"] ?: "0"}) → (${config["x2"] ?: "0"},${config["y2"] ?: "0"})"
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
        ActionType.SYSTEM_DIAL_NUMBER -> config["number"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_TOAST -> config["text"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_ALERT -> {
            val title = config["title"].orEmpty().trim()
            val text = config["text"].orEmpty().trim()
            listOf(title, text).firstOrNull { it.isNotEmpty() }
        }
        ActionType.SYSTEM_VIBRATE_PATTERN -> config["pattern"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_WIFI_CONNECT -> config["ssid"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_WIFI_FORGET -> config["ssid"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_SCREENSAVER_TIMEOUT -> "${config["minutes"] ?: "30"} min"
        ActionType.SYSTEM_POINTER_SPEED -> config["speed"] ?: "1.0"
        ActionType.SYSTEM_INSTALL_APK -> config["path"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_SET_NOTIFICATION_TONE -> config["tone"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_OPEN_MAPS -> {
            val lat = config["lat"].orEmpty().trim()
            val lng = config["lng"].orEmpty().trim()
            if (lat.isEmpty() || lng.isEmpty()) null else "$lat, $lng"
        }
        ActionType.SYSTEM_SEND_EMAIL -> config["to"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_SET_TIMEZONE -> config["zone"].orEmpty().trim().ifEmpty { null }
        else -> null
    }

/** The sole lower navigation action for the current builder station. */
@Composable
internal fun BuilderBottomPrimaryAction(
    step: Int,
    triggerCount: Int,
    actionCount: Int,
    onAdvance: (Int) -> Unit,
    onSave: () -> Unit
) {
    // One clear lower action per station: When → Do → Review → Save.
    when {
        step == 0 && triggerCount > 0 -> NexaFlowFloatingActionButton(
            onClick = { onAdvance(1) },
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            label = stringResource(R.string.permission_continue)
        )
        step == 1 && actionCount > 0 -> NexaFlowFloatingActionButton(
            onClick = { onAdvance(2) },
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            label = stringResource(R.string.quick_save)
        )
        step == 2 && triggerCount > 0 && actionCount > 0 -> NexaFlowFloatingActionButton(
            onClick = onSave,
            icon = Icons.Filled.Check,
            label = stringResource(R.string.create_task)
        )
    }
}

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
    /** Controlled by the builder so one execution card is open at a time. */
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    // Real drag-and-drop: the reorder handle (arrow column) drives these
    // callbacks; the arrow buttons stay as a secondary tap-to-move option.
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    val accent = builderCardAccent(index)
    NexaFlowCard(
        modifier = modifier,
        containerColor = builderCardContainerColor(index),
        contentColor = builderCardContentColor
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // X + reorder handle pinned to the LEFT and the row number
                    // pinned to the RIGHT regardless of the locale direction.
                    // Expand-only: once opened, the card never collapses again,
                    // so the details only ever grow downward.
                    .clickable { onExpandedChange(!expanded) },
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
                TaskNumberBadge(
                    number = index + 1,
                    containerColor = accent,
                    contentColor = Color.White
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(if (expanded) R.string.collapse_options else R.string.expand_options),
                    tint = MaterialTheme.colorScheme.outline
                )
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
    templateId: String? = null,
    savedStateHandle: SavedStateHandle? = null
) {
    val viewModel: AutomationBuilderViewModel = hiltViewModel()
    val context = LocalContext.current
    // Search labels must observe configuration changes as they are localized.
    val configuration = LocalConfiguration.current
    val configurationContext = remember(context, configuration) {
        context.createConfigurationContext(configuration)
    }
    // One reactive capability-engine snapshot is composed with Android/ROM
    // compatibility. Options with no executable backend are not rendered in
    // picker, browse, common or search paths.
    val capabilitySnapshot by viewModel.capabilitySnapshot.collectAsStateWithLifecycle()
    val supportedActions = remember(context, capabilitySnapshot) {
        CompatibilityGate.supportedActionOptions(context, capabilitySnapshot)
    }
    val supportedTriggers = remember(context, capabilitySnapshot) {
        CompatibilityGate.supportedTriggerOptions(context, capabilitySnapshot)
    }
    val availableTemplateIds = remember(capabilitySnapshot) {
        RoutineTemplateCatalog.availableTemplates(
            snapshot = capabilitySnapshot,
            actionRequirement = CommandRequirementCatalog::requirementFor,
            triggerRequirement = CommandRequirementCatalog::requirementFor
        ).mapTo(linkedSetOf()) { it.id }
    }
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
    val stringDefaultTaskName = stringResource(R.string.builder_title)
    val stringLocationFixFailed = stringResource(R.string.location_fix_failed)
    // P2-11: the editable draft survives rotation AND process death via
    // rememberSaveable (custom savers serialize the immutable drafts to Bundle).
    var name by rememberSaveable { mutableStateOf("") }
    // Guided creation: 0 = when, 1 = do, 2 = review and save.
    // The data model is unchanged; only the order in which decisions are shown changes.
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
    // Category chips highlight the FIRST category on entry so the fixed menu
    // shows at a glance; every category's options are always rendered below
    // (strict no-collapse), so these only track the highlighted chip.
    var expandedTriggerCategory by rememberSaveable { mutableStateOf<Int?>(0) }
    var expandedActionCategory by rememberSaveable { mutableStateOf<Int?>(0) }
    var triggerSearchQuery by rememberSaveable { mutableStateOf("") }
    var actionSearchQuery by rememberSaveable { mutableStateOf("") }
    // Fixed multi-select catalogues. A choice is only materialised as a card
    // after the user presses the dedicated Add button below its catalogue.
    val selectedTriggerTypes = rememberSaveable(saver = TriggerTypeSelectionSaver) {
        mutableStateListOf<TriggerType>()
    }
    val selectedActionTypes = rememberSaveable(saver = ActionTypeSelectionSaver) {
        mutableStateListOf<ActionType>()
    }
    // Expansion belongs to the section, not individual card-local state: one
    // selected trigger and one selected execution may be open at any moment.
    var expandedTriggerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var expandedActionCardId by rememberSaveable { mutableStateOf<String?>(null) }
    val actionDrafts = rememberSaveable(saver = ActionDraftListSaver) { mutableStateListOf<ActionDraft>() }
    // Real drag-and-drop reorder state — one per reorderable list so
    // dragging in one section never disturbs the others (↕️ handle).
    val actionDrag = remember { TaskDragState<ActionDraft>() }
    val triggerDrag = remember { TaskDragState<TriggerDraft>() }
    val constraintDrag = remember { TaskDragState<ConstraintDraft>() }
    val exitActionConfigs = rememberSaveable(saver = ActionConfigMapSaver) { mutableStateMapOf<ActionType, Map<String, String>>() }
    val selectedExitActions = rememberSaveable(saver = ActionOptionListSaver) { mutableStateListOf<ActionOption>() }
    var appliedTemplateId by rememberSaveable { mutableStateOf<String?>(null) }

    // ── External plugins (Locale protocol) ─────────────────────────
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    var pluginPickerTarget by remember { mutableStateOf<String?>(null) }
    var pluginLauncherActionId by remember { mutableStateOf<String?>(null) }
    var pluginLauncherPackage by remember { mutableStateOf<String?>(null) }
    var pluginLauncherReceiver by remember { mutableStateOf<String?>(null) }
    var pluginLauncherEditActivity by remember { mutableStateOf<String?>(null) }
    val pluginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val actionId = pluginLauncherActionId
        val pkg = pluginLauncherPackage
        val receiver = pluginLauncherReceiver
        val editActivity = pluginLauncherEditActivity
        pluginLauncherActionId = null
        pluginLauncherPackage = null
        pluginLauncherReceiver = null
        pluginLauncherEditActivity = null
        val bundle: Bundle? = result.data?.getBundleExtra(LocaleContract.EXTRA_BUNDLE)
        val blurb = result.data?.getStringExtra(LocaleContract.EXTRA_STRING_BLURB)
            ?: result.data?.getStringExtra(LocaleContract.EXTRA_BLURB).orEmpty()
        val draftIndex = actionId?.let { id -> actionDrafts.indexOfFirst { it.id == id } } ?: -1
        if (result.resultCode == Activity.RESULT_OK && pkg != null && receiver != null && bundle != null && blurb.isNotBlank() && draftIndex >= 0) {
            // Prefer the JSON convention; fall back to legacy flat extras.
            val configMap = PluginConfigParser.fromBundle(bundle).ifEmpty {
                PluginConfigParser.flattenBundle(bundle)
            }
            actionDrafts[draftIndex] = actionDrafts[draftIndex].copy(
                config = buildMap {
                    put("package", pkg)
                    put("receiver", receiver)
                    put("blurb", blurb)
                    put("bundleJson", PluginConfigParser.toJson(configMap))
                    // CapabilityRequest carries only this opaque reference; the
                    // backend reloads the persisted action config by workflow id.
                    put("pluginInstance", "plugin:${UUID.randomUUID()}")
                    // The user just completed the external configuration Activity.
                    put("pluginApproval", "approved")
                    editActivity?.takeIf { it.isNotBlank() }?.let { put("editActivity", it) }
                }
            )
        } else if (draftIndex >= 0 && actionDrafts[draftIndex].config.isEmpty()) {
            // A cancelled initial plugin setup removes only its own stub card.
            actionDrafts.removeAt(draftIndex)
        }
    }
    val stringPluginNoEdit = stringResource(R.string.plugin_no_edit)
    fun configurePlugin(
        actionId: String,
        packageName: String?,
        receiver: String?,
        editActivityClass: String?,
        config: Map<String, String>
    ) {
        val pkg = packageName ?: return
        val rec = receiver ?: return
        pluginLauncherActionId = actionId
        val editActivity = editActivityClass?.takeIf { it.isNotBlank() }
            ?: config["editActivity"]?.takeIf { it.isNotBlank() }
        pluginLauncherPackage = pkg
        pluginLauncherReceiver = rec
        pluginLauncherEditActivity = editActivity
        val intent = Intent(LocaleContract.ACTION_EDIT_SETTING).apply {
            if (editActivity != null) {
                component = ComponentName(pkg, editActivity)
            } else {
                // Legacy persisted entry: no stable edit component was stored.
                // Keep previous package-scoped behaviour only for reconfiguration;
                // newly discovered plug-ins always use an explicit component.
                `package` = pkg
            }
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
            pluginLauncherActionId = null
            pluginLauncherPackage = null
            pluginLauncherReceiver = null
            pluginLauncherEditActivity = null
            scope.launch { snackbarHostState.showSnackbar(stringPluginNoEdit) }
            // The plugin has no edit screen: never leave a stuck, unconfigured
            // action behind (the user can still add other plugins).
            val draftIndex = actionDrafts.indexOfFirst { it.id == actionId }
            if (draftIndex >= 0 && actionDrafts[draftIndex].config.isEmpty()) {
                actionDrafts.removeAt(draftIndex)
            }
        }
    }

    // A template fills a new editable draft once. It never overwrites an edit.
    LaunchedEffect(templateId, automationId, availableTemplateIds) {
        if (automationId == null && templateId != null && appliedTemplateId != templateId) {
            RoutineTemplateCatalog.find(templateId)
                ?.takeIf { it.id in availableTemplateIds }
                ?.let { template ->
                triggers.clear()
                template.triggers.forEach { triggers.add(TriggerDraft(it.type, it.config)) }
                actionDrafts.clear()
                template.actions.forEach { action ->
                    actionOptions.find { it.actionType == action.type }?.let { option ->
                        actionDrafts.add(
                            ActionDraft(
                                option = option,
                                config = action.config,
                                endBehavior = action.endBehavior
                            )
                        )
                    }
                }
                expandedTriggerIndex = null
                expandedActionCardId = null
                appliedTemplateId = templateId
                step = if (triggers.isNotEmpty() && actionDrafts.isNotEmpty()) 2 else 0
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
        selectedTriggerTypes.clear()
        selectedActionTypes.clear()
        expandedTriggerIndex = null
        expandedActionCardId = null
        constraints.clear()
        loaded.constraints.forEach { constraints.add(ConstraintDraft(it.type, it.config)) }
        actionDrafts.clear()
        loaded.actions.forEach { action ->
            actionOptions.find { it.actionType == action.type }?.let { option ->
                val migratedEndBehavior = when {
                    action.endBehavior != null -> action.endBehavior
                    loaded.revertOnExit && EndBehaviorCatalog.supportsRevert(action.type) ->
                        EndBehavior(EndMode.REVERT)
                    else -> null
                }
                actionDrafts.add(
                    ActionDraft(
                        option = option,
                        config = action.config,
                        endBehavior = migratedEndBehavior
                    )
                )
            }
        }
        // Backward compatibility: the old global revert-on-exit toggle is now
        // expanded for every matching card independently, preserving duplicate
        // actions instead of merging their end behavior by type.
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
                Log.w(TAG, "Current-location fill failed", t)
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
        val handle = stableSavedStateHandle
        if (handle == null) {
            onDispose { }
        } else {
            val observer = Observer<Int> { index ->
                selectedIconIndex = index
            }
            handle.getLiveData<Int>("selected_icon").observeForever(observer)
            val colorObserver = Observer<Long> { color ->
                selectedIconColor = color
            }
            handle.getLiveData<Long>("selected_color").observeForever(colorObserver)
            val locationObserver = Observer<String> { value ->
                val coords = value.split(',')
                val lat = coords.getOrNull(0)?.toDoubleOrNull()
                val lng = coords.getOrNull(1)?.toDoubleOrNull()
                val index = handle.get<Int>("map_picker_target") ?: return@Observer
                if (lat != null && lng != null && index in triggers.indices) {
                    val radius = handle.get<String>("picked_radius")?.toIntOrNull()
                    triggers[index] = triggers[index].copy(
                        config = triggers[index].config +
                            ("lat" to lat.toString()) +
                            ("lng" to lng.toString()) +
                            (if (radius != null) mapOf("radius" to radius.toString()) else emptyMap()) +
                            ("source" to "map")
                    )
                    handle.set("map_picker_target", null)
                }
            }
            handle.getLiveData<String>("picked_location").observeForever(locationObserver)
            onDispose {
                handle.getLiveData<Int>("selected_icon").removeObserver(observer)
                handle.getLiveData<Long>("selected_color").removeObserver(colorObserver)
                handle.getLiveData<String>("picked_location").removeObserver(locationObserver)
            }
        }
    }

    // Live elevated-permission status (root/Shizuku): re-probe the action
    // cards every time the screen resumes — e.g. after returning from the
    // Magisk/KernelSU grant dialog or the Shizuku grant screen — so the
    // colour-coded badge reflects the freshly granted state without leaving
    // and re-opening the task.
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefreshTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner, stableSavedStateHandle) {
        val handle = stableSavedStateHandle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshTick++
                // Coming back from the picker: re-read the latest value from
                // the builder entry whenever a stable handle is available.
                handle?.get<Int>("selected_icon")?.let {
                    if (it in NexaFlowIcons.all.indices) selectedIconIndex = it
                }
                handle?.get<Long>("selected_color")?.let {
                    selectedIconColor = it
                }
                handle?.get<String>("picked_location")?.let { value ->
                    val coords = value.split(',')
                    val lat = coords.getOrNull(0)?.toDoubleOrNull()
                    val lng = coords.getOrNull(1)?.toDoubleOrNull()
                    val index = handle.get<Int>("map_picker_target")
                    if (lat != null && lng != null && index != null && index in triggers.indices) {
                        val radius = handle.get<String>("picked_radius")?.toIntOrNull()
                        triggers[index] = triggers[index].copy(
                            config = triggers[index].config +
                                ("lat" to lat.toString()) +
                                ("lng" to lng.toString()) +
                                (if (radius != null) mapOf("radius" to radius.toString()) else emptyMap()) +
                                ("source" to "map")
                        )
                        handle.set("map_picker_target", null)
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
        if (from !in actionDrafts.indices || to !in actionDrafts.indices) return
        if (from == to) return
        val item = actionDrafts.removeAt(from)
        actionDrafts.add(to, item)
    }

    fun moveTrigger(from: Int, to: Int) {
        if (from !in triggers.indices || to !in triggers.indices || from == to) return
        val expanded = expandedTriggerIndex
        val item = triggers.removeAt(from)
        triggers.add(to, item)
        expandedTriggerIndex = when {
            expanded == from -> to
            expanded != null && from < expanded && to >= expanded -> expanded - 1
            expanded != null && from > expanded && to <= expanded -> expanded + 1
            else -> expanded
        }
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
        if (actionDrafts.isEmpty()) {
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
        val actions = actionDrafts.map { it.toAction() }
        val builtConstraints = constraints.map { Constraint(it.type, it.config) }
        val exitActions = selectedExitActions.map { Action(it.actionType, exitActionConfigs[it.actionType] ?: emptyMap()) }
        viewModel.saveAutomation(
            // A task can be created without making naming the first decision.
            // The default stays localized and users can still refine it in review.
            name = name.trim().ifBlank { stringDefaultTaskName },
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
            cooldownSeconds = 0,
            maintenanceProfile = RoutineTemplateCatalog.find(appliedTemplateId)?.maintenanceProfile
                ?: loadedAutomation?.maintenanceProfile
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
                    // Walk through the guided creation flow before leaving it.
                    if (step > 0) step -= 1 else navController.popBackStack()
                },
                // A single primary action is kept at the bottom of each station.
                // This avoids competing save actions while the routine is incomplete.
                actions = { }
            )
        },
        floatingActionButton = {
            BuilderBottomPrimaryAction(
                step = step,
                triggerCount = triggers.size,
                actionCount = actionDrafts.size,
                onAdvance = { nextStep -> step = nextStep },
                onSave = { save() }
            )
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
                targetValue = (step + 1) / 3f,
                animationSpec = nexaFlowSpatialSpec()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${step + 1} / 3",
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

            // ── Name + icon belong to review, after the routine has meaning ─
            if (step == 2) NexaFlowCard {
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
                    // Fixed catalogue: choices remain visible and can be toggled
                    // freely. The dedicated Add button below materialises the
                    // selected choices as individual editable cards.
                    val commonTriggers = remember(supportedTriggers) {
                        AutomationOptionCatalog.commonTriggers(supportedTriggers)
                    }
                    if (commonTriggers.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            commonTriggers.forEach { type ->
                                TriggerOptionRow(
                                    type = type,
                                    checked = type in selectedTriggerTypes,
                                    onSelect = {
                                        if (type in selectedTriggerTypes) selectedTriggerTypes.remove(type)
                                        else selectedTriggerTypes.add(type)
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = triggerSearchQuery,
                        onValueChange = { triggerSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.search)) }
                    )
                    val visibleTriggers = remember(supportedTriggers, triggerSearchQuery, configurationContext) {
                        if (triggerSearchQuery.isBlank()) {
                            supportedTriggers
                        } else {
                            supportedTriggers.filter { type ->
                                configurationContext.getString(type.labelRes())
                                    .contains(triggerSearchQuery, ignoreCase = true)
                            }
                        }
                    }
                    if (triggerSearchQuery.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            visibleTriggers.forEach { type ->
                                TriggerOptionRow(
                                    type = type,
                                    checked = type in selectedTriggerTypes,
                                    onSelect = {
                                        if (type in selectedTriggerTypes) selectedTriggerTypes.remove(type)
                                        else selectedTriggerTypes.add(type)
                                    }
                                )
                            }
                        }
                    } else CategoryAccordion(
                        tabs = triggerCategories.map { category ->
                            stringResource(category.headerRes) to category.icon()
                        },
                        expandedIndex = expandedTriggerCategory,
                        onExpandedChange = { expandedTriggerCategory = it }
                    ) { index ->
                        val category = triggerCategories[index]
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            visibleTriggers
                                .filter { triggerCategoryOf[it] == category }
                                .forEach { type ->
                                    TriggerOptionRow(
                                        type = type,
                                        checked = type in selectedTriggerTypes,
                                        onSelect = {
                                            if (type in selectedTriggerTypes) selectedTriggerTypes.remove(type)
                                            else selectedTriggerTypes.add(type)
                                        }
                                    )
                                }
                        }
                    }
                    Button(
                        onClick = {
                            selectedTriggerTypes.forEach { type ->
                                triggers.add(TriggerDraft(type, defaultTriggerConfig(type)))
                            }
                            expandedTriggerIndex = triggers.lastIndex.takeIf { it >= 0 }
                            selectedTriggerTypes.clear()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedTriggerTypes.isNotEmpty()
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                        Text(
                            text = stringResource(R.string.add_trigger),
                            modifier = Modifier.padding(start = 8.dp)
                        )
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
                    onRemove = {
                        triggers.removeAt(index)
                        expandedTriggerIndex = when {
                            expandedTriggerIndex == index -> null
                            expandedTriggerIndex != null && expandedTriggerIndex!! > index -> expandedTriggerIndex!! - 1
                            else -> expandedTriggerIndex
                        }
                    },
                    expanded = expandedTriggerIndex == index,
                    onExpandedChange = { expanded ->
                        expandedTriggerIndex = if (expanded) index else null
                    },
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

                    }
                }
            } else if (currentStep == 1) {
                // ── Step 2: actions + end behavior ───────────────────
                NexaFlowCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {                        // ── THEN (actions) ──────────────────────────
                            SectionHeader(text = stringResource(R.string.section_actions))
                        // Fixed multi-select catalogue. Choices persist while
                        // browsing/searching and become cards only via Add.
                        val commonActions = remember(supportedActions) {
                            AutomationOptionCatalog.commonActions(supportedActions)
                        }
                        if (commonActions.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                commonActions.forEach { option ->
                                    ActionOptionRow(
                                        option = option,
                                        checked = option.actionType in selectedActionTypes,
                                        onToggle = {
                                            if (option.actionType in selectedActionTypes) selectedActionTypes.remove(option.actionType)
                                            else selectedActionTypes.add(option.actionType)
                                        }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = actionSearchQuery,
                            onValueChange = { actionSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.search)) }
                        )
                        val visibleActions = remember(supportedActions, actionSearchQuery, configurationContext) {
                            if (actionSearchQuery.isBlank()) {
                                supportedActions
                            } else {
                                supportedActions.filter { option ->
                                    configurationContext.getString(option.titleRes)
                                        .contains(actionSearchQuery, ignoreCase = true) ||
                                        configurationContext.getString(option.subtitleRes)
                                            .contains(actionSearchQuery, ignoreCase = true)
                                }
                            }
                        }
                        if (actionSearchQuery.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                visibleActions.forEach { option ->
                                    ActionOptionRow(
                                        option = option,
                                        checked = option.actionType in selectedActionTypes,
                                        onToggle = {
                                            if (option.actionType in selectedActionTypes) selectedActionTypes.remove(option.actionType)
                                            else selectedActionTypes.add(option.actionType)
                                        }
                                    )
                                }
                            }
                        } else CategoryAccordion(
                            tabs = actionCategories.map { category ->
                                stringResource(category.headerRes) to category.icon()
                            },
                            expandedIndex = expandedActionCategory,
                            onExpandedChange = { expandedActionCategory = it }
                        ) { index ->
                            val category = actionCategories[index]
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                optionsForActionCategory(category, visibleActions)
                                    .forEach { option ->
                                        ActionOptionRow(
                                            option = option,
                                            checked = option.actionType in selectedActionTypes,
                                            onToggle = {
                                                if (option.actionType in selectedActionTypes) selectedActionTypes.remove(option.actionType)
                                                else selectedActionTypes.add(option.actionType)
                                            }
                                        )
                                    }
                            }
                        }
                        Button(
                            onClick = {
                                selectedActionTypes.forEach { type ->
                                    supportedActions.firstOrNull { it.actionType == type }?.let { option ->
                                        actionDrafts.add(ActionDraft(option = option))
                                    }
                                }
                                expandedActionCardId = actionDrafts.lastOrNull()?.id
                                selectedActionTypes.clear()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedActionTypes.isNotEmpty()
                        ) {
                            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                            Text(
                                text = stringResource(R.string.add_action),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        actionDrafts.forEachIndexed { index, draft ->
                            key(draft.id) {
                                val actionDragging = actionDrag.draggedIndex == index
                                SelectedActionCard(
                                modifier = Modifier.taskDragOffset(actionDrag, draft, actionDragging),
                                isDragging = actionDragging,
                                onDragStart = { startDrag(actionDrag, index) },
                                onDragDelta = { dragBy(actionDrag, actionDrafts, it) { from, to -> moveAction(from, to) } },
                                onDragEnd = { endDrag(actionDrag) },
                                option = draft.option,
                                index = index,
                                total = actionDrafts.size,
                                config = draft.config,
                                onConfigChange = { config ->
                                    val current = actionDrafts.indexOfFirst { it.id == draft.id }
                                    if (current >= 0) actionDrafts[current] = actionDrafts[current].copy(config = config)
                                },
                                onMoveUp = { moveAction(index, index - 1) },
                                onMoveDown = { moveAction(index, index + 1) },
                                onRemove = {
                                    val current = actionDrafts.indexOfFirst { it.id == draft.id }
                                    if (current >= 0) actionDrafts.removeAt(current)
                                    if (expandedActionCardId == draft.id) expandedActionCardId = null
                                },
                                onPickApp = { appPickerTarget = "action:${draft.id}" },
                                onRequestPermission = { requestPermissions(it) },
                                onExplainSpecial = { explainSpecialPermission(it) },
                                refreshKey = permissionRefreshTick,
                                context = context,
                                availableVariables = availableVariables,
                                automations = automations,
                                onPluginConfigure = {
                                    if (draft.config["package"].isNullOrBlank() || draft.config["receiver"].isNullOrBlank()) {
                                        pluginPickerTarget = draft.id
                                    } else {
                                        configurePlugin(
                                            actionId = draft.id,
                                            packageName = draft.config["package"],
                                            receiver = draft.config["receiver"],
                                            editActivityClass = draft.config["editActivity"],
                                            config = draft.config
                                        )
                                    }
                                },
                                expanded = expandedActionCardId == draft.id,
                                onExpandedChange = { expanded ->
                                    expandedActionCardId = if (expanded) draft.id else null
                                }
                                )
                            }
                        }

                    }
                }
            } else {
                // ── Step 3: review before saving ─────────────────────
                NexaFlowCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(text = stringResource(R.string.save))
                        Text(
                            text = stringResource(R.string.section_when),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        triggers.forEach { draft ->
                            Text(
                                text = stringResource(draft.type.labelRes()),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = stringResource(R.string.section_actions),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        actionDrafts.forEach { draft ->
                            Text(
                                text = actionSummary(draft.option, draft.config),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        val actionsWithEndOptions = actionDrafts.filter { draft ->
                            draft.option.actionType in EndBehaviorCatalog.toggleActions ||
                                draft.option.actionType in EndBehaviorCatalog.valueActions ||
                                draft.option.actionType in EndBehaviorCatalog.revertOnlyActions
                        }
                        if (actionsWithEndOptions.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            SectionHeader(text = stringResource(R.string.end_behavior_label))
                            actionsWithEndOptions.forEach { draft ->
                                Text(
                                    text = stringResource(draft.option.titleRes),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                EndBehaviorEditor(
                                    actionType = draft.option.actionType,
                                    behavior = draft.endBehavior,
                                    onBehaviorChange = { behavior ->
                                        val current = actionDrafts.indexOfFirst { it.id == draft.id }
                                        if (current >= 0) actionDrafts[current] = actionDrafts[current].copy(endBehavior = behavior)
                                    },
                                    showLabel = false
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = stringResource(R.string.section_constraints),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (constraints.isEmpty()) {
                                stringResource(R.string.constraints_empty_hint)
                            } else {
                                stringResource(R.string.section_constraints)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
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

    pluginPickerTarget?.let { actionId ->
        PluginPickerDialog(
            plugins = plugins,
            onRefresh = { viewModel.refreshPlugins() },
            onPick = { plugin ->
                pluginPickerTarget = null
                configurePlugin(
                    actionId = actionId,
                    packageName = plugin.packageName,
                    receiver = plugin.receiverClass,
                    editActivityClass = plugin.editActivityClass,
                    config = emptyMap()
                )
            },
            onDismiss = {
                pluginPickerTarget = null
                // Dropping the picker without configuring removes only the
                // unconfigured plugin card that opened this dialog.
                val draftIndex = actionDrafts.indexOfFirst { it.id == actionId }
                if (draftIndex >= 0 && actionDrafts[draftIndex].config.isEmpty()) {
                    actionDrafts.removeAt(draftIndex)
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
            val actionId = target.removePrefix("action:")
            val actionIndex = actionDrafts.indexOfFirst { it.id == actionId }
            val draft = actionDrafts.getOrNull(actionIndex)
            if (draft == null) {
                appPickerTarget = null
            } else {
                val isOpenApp = draft.option.actionType == ActionType.SYSTEM_OPEN_APP
                val isSinglePickAction = draft.option.actionType in setOf(
                    ActionType.APPLICATION_CLOSE_APP,
                    ActionType.APPLICATION_OPEN_APP_SETTINGS,
                    ActionType.SYSTEM_BLOCK_NOTIFICATION,
                    ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS
                )
                when {
                    isOpenApp -> {
                        val pre = (draft.config["packages"] ?: draft.config["package"] ?: "")
                            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        AppPickerDialog(
                            onPickSingle = { app ->
                                val merged = (pre + app.packageName).distinct()
                                val current = actionDrafts.getOrNull(actionIndex)
                                if (current?.id == actionId) {
                                    actionDrafts[actionIndex] = current.copy(
                                        config = current.config + ("packages" to merged.joinToString(","))
                                    )
                                }
                                appPickerTarget = null
                            },
                            onPickMultiple = { packages ->
                                val current = actionDrafts.getOrNull(actionIndex)
                                if (current?.id == actionId) {
                                    actionDrafts[actionIndex] = current.copy(
                                        config = current.config + ("packages" to packages.joinToString(",") { it.packageName })
                                    )
                                }
                                appPickerTarget = null
                            },
                            multiSelect = true,
                            preSelectedPackages = pre,
                            onDismiss = { appPickerTarget = null }
                        )
                    }
                    isSinglePickAction -> AppPickerDialog(
                        onPickSingle = { app ->
                            val current = actionDrafts.getOrNull(actionIndex)
                            if (current?.id == actionId) {
                                actionDrafts[actionIndex] = current.copy(
                                    config = current.config + ("package" to app.packageName)
                                )
                            }
                            appPickerTarget = null
                        },
                        onDismiss = { appPickerTarget = null }
                    )
                    else -> appPickerTarget = null
                }
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
