package com.nexaflow.feature.builder

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberCoroutineScope
import com.nexaflow.core.engine.currentCellularGeneration
import com.nexaflow.core.rom.CustomSettingsBridge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.SelectChip
import com.nexaflow.domain.catalog.AutomationNodeCatalog
import com.nexaflow.domain.catalog.AutomationNodeFamily
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Trigger types that use ON/OFF state but with custom labels. */
internal val ON_OFF_TRIGGER_LABELS = mapOf(
    TriggerType.POWER_SAVER to R.string.trigger_power_saver_state,
    TriggerType.BLUETOOTH_STATE to R.string.trigger_bluetooth_state_hint,
    TriggerType.AUTO_ROTATE to R.string.trigger_auto_rotate_state,
    TriggerType.DATA_SAVER_STATE to R.string.trigger_data_saver_state,
    TriggerType.WIFI_STATE to R.string.trigger_wifi_state_hint,
    TriggerType.NFC_STATE to R.string.trigger_nfc_state_hint
)

internal const val LOCATION_RADIUS_MIN_M = 50
internal const val LOCATION_RADIUS_MAX_M = 2000
// (max - min) / step - 1: 50 m granularity between the endpoints.
internal const val LOCATION_RADIUS_STEPS = (LOCATION_RADIUS_MAX_M - LOCATION_RADIUS_MIN_M) / 50 - 1

/**
 * Google-style grouping of trigger types by what they watch, so the picker
 * surfaces the everyday options (time, battery, Wi-Fi…) first and the user
 * reaches the right trigger without scrolling one long flat list.
 */
enum class TriggerCategory(val headerRes: Int, val color: Color) {
    SCHEDULE(R.string.trigger_cat_schedule, Color(0xFF6750A4)),
    DEVICE(R.string.trigger_cat_device, Color(0xFF455A64)),
    CONNECTIVITY(R.string.trigger_cat_connectivity, Color(0xFF006A6C)),
    LOCATION(R.string.trigger_cat_location, Color(0xFF0B57D0)),
    APPS(R.string.trigger_cat_apps, Color(0xFF006D3C)),
    COMMUNICATION(R.string.trigger_cat_communication, Color(0xFF8F4C00))
}

internal val triggerCategories: List<TriggerCategory> = TriggerCategory.entries.toList()

/** Representative icon per trigger category for the accordion chips. */
internal fun TriggerCategory.icon(): ImageVector = when (this) {
    TriggerCategory.SCHEDULE -> Icons.Filled.Schedule
    TriggerCategory.DEVICE -> Icons.Filled.Bolt
    TriggerCategory.CONNECTIVITY -> Icons.Filled.Wifi
    TriggerCategory.LOCATION -> Icons.Filled.Place
    TriggerCategory.APPS -> Icons.Filled.Apps
    TriggerCategory.COMMUNICATION -> Icons.AutoMirrored.Filled.Message
}

/** Trigger type → presentation category, derived from the domain catalog. */
internal val triggerCategoryOf: Map<TriggerType, TriggerCategory> =
    TriggerType.entries.associateWith { type ->
        AutomationNodeCatalog.definitionFor(type).family.toTriggerCategory()
    }

/**
 * The domain catalog keeps richer semantic families than this compact picker.
 * Several families intentionally collapse into DEVICE here to preserve the
 * existing six-category UX while removing duplicate classification data.
 */
internal fun AutomationNodeFamily.toTriggerCategory(): TriggerCategory = when (this) {
    AutomationNodeFamily.SCHEDULE -> TriggerCategory.SCHEDULE
    AutomationNodeFamily.CONNECTIVITY,
    AutomationNodeFamily.NETWORK -> TriggerCategory.CONNECTIVITY
    AutomationNodeFamily.LOCATION -> TriggerCategory.LOCATION
    AutomationNodeFamily.APPLICATIONS -> TriggerCategory.APPS
    AutomationNodeFamily.COMMUNICATION -> TriggerCategory.COMMUNICATION
    AutomationNodeFamily.DEVICE,
    AutomationNodeFamily.DISPLAY,
    AutomationNodeFamily.SOUND,
    AutomationNodeFamily.MEDIA,
    AutomationNodeFamily.NOTIFICATIONS,
    AutomationNodeFamily.BATTERY,
    AutomationNodeFamily.SYSTEM,
    AutomationNodeFamily.ROM,
    AutomationNodeFamily.DATA,
    AutomationNodeFamily.FILES,
    AutomationNodeFamily.FLOW,
    AutomationNodeFamily.PLUGINS,
    AutomationNodeFamily.DEVELOPER -> TriggerCategory.DEVICE
}

/** Trigger types ordered by category — the picker walks [triggerCategories] over it. */
val triggerTypeOptions = listOf(
    // SCHEDULE
    TriggerType.TIME,
    TriggerType.CALENDAR,
    // DEVICE
    TriggerType.BATTERY,
    TriggerType.DEVICE,
    TriggerType.RINGER_MODE,
    TriggerType.NOTIFICATION,
    TriggerType.SENSOR,
    TriggerType.ROM_SETTING,
    TriggerType.HEADPHONE,
    TriggerType.CHARGER,
    TriggerType.AIRPLANE_MODE,
    TriggerType.DARK_MODE,
    TriggerType.CALL_STATE,
    TriggerType.MEDIA_PLAYING,
    TriggerType.VOLUME_CHANGED,
    TriggerType.POWER_SAVER,
    TriggerType.BRIGHTNESS_LEVEL,
    TriggerType.STORAGE_LOW,
    TriggerType.AUTO_ROTATE,
    TriggerType.DEVICE_LOCKED,
    TriggerType.SCREEN_ROTATION_STATE,
    TriggerType.WEAR_EVENT,
    // CONNECTIVITY is intentionally not offered: Wi-Fi and mobile data are
    // separate triggers (WIFI_CONNECTED / MOBILE_DATA_CONNECTED). The legacy
    // combined type remains available for saved tasks.
    TriggerType.WIFI_CONNECTED,
    TriggerType.MOBILE_DATA_CONNECTED,
    TriggerType.HOTSPOT,
    TriggerType.NETWORK_MODE,
    TriggerType.BLUETOOTH_DEVICE,
    TriggerType.BLUETOOTH_STATE,
    TriggerType.WIFI_STATE,
    TriggerType.NFC_STATE,
    TriggerType.DATA_SAVER_STATE,
    TriggerType.WEBHOOK,
    // LOCATION — GPS geofence plus the device location-mode state trigger.
    TriggerType.LOCATION,
    TriggerType.LOCATION_STATE,
    // APPS
    TriggerType.APPLICATION,
    TriggerType.APP_INSTALLED,
    // COMMUNICATION
    TriggerType.SMS,
    TriggerType.INCOMING_CALL,
    // DEVICE (v3.28)
    TriggerType.BATTERY_TEMPERATURE,
    TriggerType.USB_CONNECTED,
    TriggerType.HDMI_CONNECTED,
    TriggerType.CLIPBOARD_CHANGED,
    TriggerType.DND_STATE,
    TriggerType.STAY_AWAKE_STATE,
    TriggerType.AUTO_BRIGHTNESS_STATE,
    TriggerType.SCREEN_TIMEOUT_CHANGED,
    TriggerType.TIMEZONE_CHANGED,
    TriggerType.BOOT_COMPLETED,
    TriggerType.ALARM_SET_CHANGED,
    // CONNECTIVITY (v3.28)
    TriggerType.WIFI_SIGNAL_STRENGTH,
    TriggerType.CELL_SIGNAL_STRENGTH,
    TriggerType.ETHERNET_CONNECTED,
    TriggerType.VPN_CONNECTED,
    TriggerType.DATA_ROAMING_STATE,
    TriggerType.NFC_TAG_SCANNED
)

internal val repeatOptions = listOf(
    "ONCE" to R.string.repeat_once,
    "INTERVAL" to R.string.repeat_custom
)

/** Google-Tasks-style occurrence of a weekday inside a month (1st..4th / Last). */
internal val occurrenceOptions = listOf(
    "1" to R.string.occurrence_first,
    "2" to R.string.occurrence_second,
    "3" to R.string.occurrence_third,
    "4" to R.string.occurrence_fourth,
    "LAST" to R.string.occurrence_last
)

internal val weekdayOptions = listOf(
    1 to R.string.day_mon,
    2 to R.string.day_tue,
    3 to R.string.day_wed,
    4 to R.string.day_thu,
    5 to R.string.day_fri,
    6 to R.string.day_sat,
    7 to R.string.day_sun
)

/** Sensible default config for a freshly added trigger of the given type. */
internal fun defaultTriggerConfig(type: TriggerType): Map<String, String> {
    // Start from the canonical domain contract so new schema defaults are
    // automatically reflected in the builder instead of drifting into a
    // second, UI-only source of truth.
    val schemaDefaults = AutomationNodeCatalog.definitionFor(type)
        .configuration
        .fields
        .mapNotNull { field -> field.defaultValue?.let { field.key to it } }
        .toMap()

    val editorDefaults = when (type) {
        TriggerType.TIME -> mapOf("time" to "08:00")
        TriggerType.BATTERY -> mapOf("direction" to "ABOVE", "above" to "80", "chargerType" to "ANY", "chargingState" to "ANY")
        TriggerType.APPLICATION -> mapOf("packages" to "")
        TriggerType.DEVICE -> mapOf("event" to "SCREEN_ON")
        TriggerType.CONNECTIVITY -> mapOf("network" to "WIFI", "state" to "CONNECTED")
        TriggerType.WIFI_CONNECTED -> mapOf("state" to "CONNECTED")
        TriggerType.MOBILE_DATA_CONNECTED -> mapOf("state" to "CONNECTED")
        TriggerType.HOTSPOT -> mapOf("state" to "ON")
        TriggerType.NETWORK_MODE -> mapOf("state" to "4G")
        TriggerType.LOCATION -> mapOf("lat" to "", "lng" to "", "radius" to "100", "event" to "ENTER", "source" to "current")
        TriggerType.SMS -> mapOf("from" to "", "contains" to "", "matchMode" to "CONTAINS")
        TriggerType.INCOMING_CALL -> mapOf("from" to "", "matchMode" to "ANY", "category" to "ANY")
        TriggerType.BLUETOOTH_DEVICE -> mapOf("deviceName" to "", "deviceAddress" to "", "event" to "CONNECTED")
        TriggerType.RINGER_MODE -> mapOf("mode" to "NORMAL")
        TriggerType.NOTIFICATION -> mapOf("packages" to "", "contains" to "", "event" to "POSTED")
        TriggerType.CALENDAR -> mapOf("calendar" to "", "contains" to "", "event" to "EVENT_START", "beforeMinutes" to "0")
        TriggerType.SENSOR -> mapOf("sensor" to "PROXIMITY", "event" to "COVERED", "threshold" to "200", "sensitivity" to "14")
        TriggerType.WEBHOOK -> mapOf(
            "path" to "/nexaflow",
            "method" to "POST",
            // Tokens are per-trigger secrets and can never be static schema defaults.
            "token" to com.nexaflow.domain.security.ExternalAccessPolicy.newToken()
        )
        TriggerType.ROM_SETTING -> mapOf("namespace" to "SYSTEM", "key" to "", "operator" to "EQUALS", "value" to "")
        TriggerType.HEADPHONE -> mapOf("event" to "CONNECTED")
        TriggerType.CHARGER -> mapOf("event" to "CONNECTED")
        TriggerType.AIRPLANE_MODE -> mapOf("state" to "ON")
        TriggerType.DARK_MODE -> mapOf("state" to "ON")
        TriggerType.CALL_STATE -> mapOf("event" to "INCOMING")
        TriggerType.APP_INSTALLED -> mapOf("event" to "INSTALLED", "package" to "")
        TriggerType.MEDIA_PLAYING -> mapOf("event" to "STARTED")
        TriggerType.VOLUME_CHANGED -> mapOf("stream" to "MUSIC", "threshold" to "50", "direction" to "ABOVE")
        TriggerType.POWER_SAVER -> mapOf("state" to "ON")
        TriggerType.BLUETOOTH_STATE -> mapOf("state" to "ON")
        TriggerType.BRIGHTNESS_LEVEL -> mapOf("threshold" to "128", "direction" to "ABOVE")
        TriggerType.STORAGE_LOW -> mapOf("threshold" to "1024", "direction" to "BELOW")
        TriggerType.AUTO_ROTATE -> mapOf("state" to "ON")
        TriggerType.DATA_SAVER_STATE -> mapOf("state" to "ON")
        TriggerType.DEVICE_LOCKED -> mapOf("state" to "LOCKED")
        TriggerType.WIFI_STATE -> mapOf("state" to "ON")
        TriggerType.NFC_STATE -> mapOf("state" to "ON")
        TriggerType.LOCATION_STATE -> mapOf("mode" to "ON")
        TriggerType.SCREEN_ROTATION_STATE -> mapOf("state" to "PORTRAIT")
        TriggerType.WIFI_SIGNAL_STRENGTH -> mapOf("threshold" to "3", "direction" to "ABOVE")
        TriggerType.CELL_SIGNAL_STRENGTH -> mapOf("threshold" to "3", "direction" to "ABOVE")
        TriggerType.BATTERY_TEMPERATURE -> mapOf("threshold" to "40", "direction" to "ABOVE")
        TriggerType.USB_CONNECTED -> mapOf("state" to "ON")
        TriggerType.HDMI_CONNECTED -> mapOf("state" to "ON")
        TriggerType.ETHERNET_CONNECTED -> mapOf("state" to "ON")
        TriggerType.VPN_CONNECTED -> mapOf("state" to "ON")
        TriggerType.CLIPBOARD_CHANGED -> mapOf("contains" to "")
        TriggerType.DND_STATE -> mapOf("state" to "ON")
        TriggerType.STAY_AWAKE_STATE -> mapOf("state" to "ON")
        TriggerType.AUTO_BRIGHTNESS_STATE -> mapOf("state" to "ON")
        TriggerType.SCREEN_TIMEOUT_CHANGED -> mapOf("seconds" to "")
        TriggerType.DATA_ROAMING_STATE -> mapOf("state" to "ON")
        TriggerType.TIMEZONE_CHANGED -> mapOf("zone" to "")
        TriggerType.BOOT_COMPLETED -> emptyMap()
        TriggerType.NFC_TAG_SCANNED -> mapOf("contains" to "")
        TriggerType.ALARM_SET_CHANGED -> mapOf("event" to "SET")
        TriggerType.WEAR_EVENT -> mapOf("watchInstallId" to "", "state" to "CONNECTED")
        // Created only by the verified plugin configuration path, never the generic picker.
        TriggerType.PLUGIN_EVENT -> emptyMap()
    }
    return schemaDefaults + editorDefaults
}

internal fun TriggerType.labelRes(): Int = when (this) {
    TriggerType.TIME -> R.string.trigger_type_time
    TriggerType.BATTERY -> R.string.trigger_type_battery
    TriggerType.APPLICATION -> R.string.trigger_type_app
    TriggerType.DEVICE -> R.string.trigger_type_device
    TriggerType.CONNECTIVITY -> R.string.trigger_type_connectivity
    TriggerType.WIFI_CONNECTED -> R.string.trigger_type_wifi_connected
    TriggerType.MOBILE_DATA_CONNECTED -> R.string.trigger_type_mobile_data
    TriggerType.HOTSPOT -> R.string.action_hotspot
    TriggerType.NETWORK_MODE -> R.string.trigger_type_network_mode
    TriggerType.LOCATION -> R.string.trigger_type_location
    TriggerType.SMS -> R.string.trigger_type_sms
    TriggerType.INCOMING_CALL -> R.string.trigger_type_incoming_call
    TriggerType.BLUETOOTH_DEVICE -> R.string.trigger_type_bluetooth
    TriggerType.RINGER_MODE -> R.string.trigger_type_ringer
    TriggerType.NOTIFICATION -> R.string.trigger_type_notification
    TriggerType.CALENDAR -> R.string.trigger_type_calendar
    TriggerType.SENSOR -> R.string.trigger_type_sensor
    TriggerType.WEBHOOK -> R.string.trigger_type_webhook
    TriggerType.ROM_SETTING -> R.string.trigger_type_rom_setting
    TriggerType.HEADPHONE -> R.string.trigger_type_headphone
    TriggerType.CHARGER -> R.string.trigger_type_charger
    TriggerType.AIRPLANE_MODE -> R.string.trigger_type_airplane
    TriggerType.DARK_MODE -> R.string.trigger_type_dark_mode
    TriggerType.CALL_STATE -> R.string.trigger_type_call_state
    TriggerType.APP_INSTALLED -> R.string.trigger_type_app_installed
    TriggerType.MEDIA_PLAYING -> R.string.trigger_type_media_playing
    TriggerType.VOLUME_CHANGED -> R.string.trigger_type_volume_changed
    TriggerType.POWER_SAVER -> R.string.trigger_type_power_saver
    TriggerType.BLUETOOTH_STATE -> R.string.trigger_type_bluetooth_state
    TriggerType.BRIGHTNESS_LEVEL -> R.string.trigger_type_brightness_level
    TriggerType.STORAGE_LOW -> R.string.trigger_type_storage_low
    TriggerType.AUTO_ROTATE -> R.string.trigger_type_auto_rotate
    TriggerType.DATA_SAVER_STATE -> R.string.trigger_type_data_saver_state
    TriggerType.DEVICE_LOCKED -> R.string.trigger_type_device_locked
    TriggerType.WIFI_STATE -> R.string.trigger_type_wifi_state
    TriggerType.NFC_STATE -> R.string.trigger_type_nfc_state
    TriggerType.LOCATION_STATE -> R.string.trigger_type_location_state
    TriggerType.SCREEN_ROTATION_STATE -> R.string.trigger_type_screen_rotation_state
    TriggerType.WIFI_SIGNAL_STRENGTH -> R.string.trigger_type_wifi_signal_strength
    TriggerType.CELL_SIGNAL_STRENGTH -> R.string.trigger_type_cell_signal_strength
    TriggerType.BATTERY_TEMPERATURE -> R.string.trigger_type_battery_temperature
    TriggerType.USB_CONNECTED -> R.string.trigger_type_usb_connected
    TriggerType.HDMI_CONNECTED -> R.string.trigger_type_hdmi_connected
    TriggerType.ETHERNET_CONNECTED -> R.string.trigger_type_ethernet_connected
    TriggerType.VPN_CONNECTED -> R.string.trigger_type_vpn_connected
    TriggerType.CLIPBOARD_CHANGED -> R.string.trigger_type_clipboard_changed
    TriggerType.DND_STATE -> R.string.trigger_type_dnd_state
    TriggerType.STAY_AWAKE_STATE -> R.string.trigger_type_stay_awake_state
    TriggerType.AUTO_BRIGHTNESS_STATE -> R.string.trigger_type_auto_brightness_state
    TriggerType.SCREEN_TIMEOUT_CHANGED -> R.string.trigger_type_screen_timeout_changed
    TriggerType.DATA_ROAMING_STATE -> R.string.trigger_type_data_roaming_state
    TriggerType.TIMEZONE_CHANGED -> R.string.trigger_type_timezone_changed
    TriggerType.BOOT_COMPLETED -> R.string.trigger_type_boot_completed
    TriggerType.NFC_TAG_SCANNED -> R.string.trigger_type_nfc_tag_scanned
    TriggerType.ALARM_SET_CHANGED -> R.string.trigger_type_alarm_set_changed
    TriggerType.WEAR_EVENT -> R.string.trigger_wear_event
    TriggerType.PLUGIN_EVENT -> R.string.action_plugin
}

internal fun TriggerType.descRes(): Int = when (this) {
    TriggerType.TIME -> R.string.trigger_type_schedule_sub
    TriggerType.BATTERY -> R.string.trigger_type_battery_sub
    TriggerType.APPLICATION -> R.string.trigger_type_app_sub
    TriggerType.DEVICE -> R.string.trigger_type_device_sub
    TriggerType.CONNECTIVITY -> R.string.trigger_type_connectivity_sub
    TriggerType.WIFI_CONNECTED -> R.string.trigger_type_wifi_connected_sub
    TriggerType.MOBILE_DATA_CONNECTED -> R.string.trigger_type_mobile_data_sub
    TriggerType.HOTSPOT -> R.string.action_hotspot_sub
    TriggerType.NETWORK_MODE -> R.string.trigger_type_network_mode_sub
    TriggerType.LOCATION -> R.string.trigger_type_location_sub
    TriggerType.SMS -> R.string.trigger_type_sms
    TriggerType.INCOMING_CALL -> R.string.trigger_type_incoming_call_sub
    TriggerType.BLUETOOTH_DEVICE -> R.string.trigger_type_bluetooth_sub
    TriggerType.RINGER_MODE -> R.string.trigger_type_ringer_sub
    TriggerType.NOTIFICATION -> R.string.trigger_type_notification_sub
    TriggerType.CALENDAR -> R.string.trigger_type_calendar_sub
    TriggerType.SENSOR -> R.string.trigger_type_sensor_sub
    TriggerType.WEBHOOK -> R.string.trigger_type_webhook_sub
    TriggerType.ROM_SETTING -> R.string.trigger_type_rom_setting_sub
    TriggerType.HEADPHONE -> R.string.trigger_type_headset_sub
    TriggerType.CHARGER -> R.string.trigger_type_charger_sub
    TriggerType.AIRPLANE_MODE -> R.string.trigger_type_airplane_sub
    TriggerType.DARK_MODE -> R.string.trigger_type_dark_mode_sub
    TriggerType.CALL_STATE -> R.string.trigger_type_call_sub
    TriggerType.APP_INSTALLED -> R.string.trigger_type_app_installed_sub
    TriggerType.MEDIA_PLAYING -> R.string.trigger_type_media_sub
    TriggerType.VOLUME_CHANGED -> R.string.trigger_type_volume_changed_sub
    TriggerType.POWER_SAVER -> R.string.trigger_type_power_saver_sub
    TriggerType.BLUETOOTH_STATE -> R.string.trigger_type_bluetooth_state_sub
    TriggerType.BRIGHTNESS_LEVEL -> R.string.trigger_type_brightness_sub
    TriggerType.STORAGE_LOW -> R.string.trigger_type_storage_sub
    TriggerType.AUTO_ROTATE -> R.string.trigger_type_auto_rotate_sub
    TriggerType.DATA_SAVER_STATE -> R.string.trigger_type_data_saver_sub
    TriggerType.DEVICE_LOCKED -> R.string.trigger_type_device_locked_sub
    TriggerType.WIFI_STATE -> R.string.trigger_type_wifi_state_sub
    TriggerType.NFC_STATE -> R.string.trigger_type_nfc_state_sub
    TriggerType.LOCATION_STATE -> R.string.trigger_type_location_state_sub
    TriggerType.SCREEN_ROTATION_STATE -> R.string.trigger_type_screen_rotation_sub
    TriggerType.WIFI_SIGNAL_STRENGTH -> R.string.trigger_type_wifi_signal_sub
    TriggerType.CELL_SIGNAL_STRENGTH -> R.string.trigger_type_cell_signal_sub
    TriggerType.BATTERY_TEMPERATURE -> R.string.trigger_type_battery_temp_sub
    TriggerType.USB_CONNECTED -> R.string.trigger_type_usb_sub
    TriggerType.HDMI_CONNECTED -> R.string.trigger_type_hdmi_sub
    TriggerType.ETHERNET_CONNECTED -> R.string.trigger_type_ethernet_sub
    TriggerType.VPN_CONNECTED -> R.string.trigger_type_vpn_sub
    TriggerType.CLIPBOARD_CHANGED -> R.string.trigger_type_clipboard_sub
    TriggerType.DND_STATE -> R.string.trigger_type_dnd_sub
    TriggerType.STAY_AWAKE_STATE -> R.string.trigger_type_stay_awake_sub
    TriggerType.AUTO_BRIGHTNESS_STATE -> R.string.trigger_type_auto_brightness_sub
    TriggerType.SCREEN_TIMEOUT_CHANGED -> R.string.trigger_type_screen_timeout_sub
    TriggerType.DATA_ROAMING_STATE -> R.string.trigger_type_data_roaming_sub
    TriggerType.TIMEZONE_CHANGED -> R.string.trigger_type_timezone_sub
    TriggerType.BOOT_COMPLETED -> R.string.trigger_type_boot_sub
    TriggerType.NFC_TAG_SCANNED -> R.string.trigger_type_nfc_sub
    TriggerType.ALARM_SET_CHANGED -> R.string.trigger_type_alarm_sub
    TriggerType.WEAR_EVENT -> R.string.trigger_wear_event_sub
    TriggerType.PLUGIN_EVENT -> R.string.action_plugin_sub
}

internal fun TriggerType.icon(): ImageVector = when (this) {
    TriggerType.TIME -> Icons.Filled.Schedule
    TriggerType.BATTERY -> Icons.Filled.BatteryChargingFull
    TriggerType.APPLICATION -> Icons.Filled.Apps
    TriggerType.DEVICE -> Icons.Filled.Bolt
    TriggerType.CONNECTIVITY -> Icons.Filled.Wifi
    TriggerType.WIFI_CONNECTED -> Icons.Filled.Wifi
    TriggerType.MOBILE_DATA_CONNECTED -> Icons.Filled.SignalCellularAlt
    TriggerType.HOTSPOT -> Icons.Filled.Router
    TriggerType.NETWORK_MODE -> Icons.Filled.SignalCellularAlt
    TriggerType.LOCATION -> Icons.Filled.Place
    TriggerType.SMS -> Icons.AutoMirrored.Filled.Message
    TriggerType.BLUETOOTH_DEVICE -> Icons.Filled.Bluetooth
    TriggerType.RINGER_MODE -> Icons.Filled.NotificationsActive
    TriggerType.NOTIFICATION -> Icons.Filled.Notifications
    TriggerType.CALENDAR -> Icons.Filled.DateRange
    TriggerType.SENSOR -> Icons.Filled.Sensors
    TriggerType.WEBHOOK -> Icons.Filled.Web
    TriggerType.ROM_SETTING -> Icons.Filled.Bolt
    TriggerType.HEADPHONE -> Icons.Filled.Headphones
    TriggerType.CHARGER -> Icons.Filled.BatteryChargingFull
    TriggerType.AIRPLANE_MODE -> Icons.Filled.AirplanemodeActive
    TriggerType.DARK_MODE -> Icons.Filled.DarkMode
    TriggerType.CALL_STATE -> Icons.Filled.PhoneAndroid
    TriggerType.INCOMING_CALL -> Icons.Filled.PhoneInTalk
    TriggerType.APP_INSTALLED -> Icons.Filled.Download
    TriggerType.MEDIA_PLAYING -> Icons.Filled.MusicNote
    TriggerType.VOLUME_CHANGED -> Icons.AutoMirrored.Filled.VolumeUp
    TriggerType.POWER_SAVER -> Icons.Filled.BatteryChargingFull
    TriggerType.BLUETOOTH_STATE -> Icons.Filled.Bluetooth
    TriggerType.BRIGHTNESS_LEVEL -> Icons.Filled.BrightnessHigh
    TriggerType.STORAGE_LOW -> Icons.Filled.Storage
    TriggerType.AUTO_ROTATE -> Icons.Filled.ScreenRotation
    TriggerType.DATA_SAVER_STATE -> Icons.Filled.DataUsage
    TriggerType.DEVICE_LOCKED -> Icons.Filled.Lock
    TriggerType.WIFI_STATE -> Icons.Filled.Wifi
    TriggerType.NFC_STATE -> Icons.Filled.Nfc
    TriggerType.LOCATION_STATE -> Icons.Filled.LocationOn
    TriggerType.SCREEN_ROTATION_STATE -> Icons.Filled.ScreenRotation
    TriggerType.WIFI_SIGNAL_STRENGTH -> Icons.Filled.Wifi
    TriggerType.CELL_SIGNAL_STRENGTH -> Icons.Filled.SignalCellularAlt
    TriggerType.BATTERY_TEMPERATURE -> Icons.Filled.DeviceThermostat
    TriggerType.USB_CONNECTED -> Icons.Filled.Usb
    TriggerType.HDMI_CONNECTED -> Icons.Filled.Monitor
    TriggerType.ETHERNET_CONNECTED -> Icons.Filled.Router
    TriggerType.VPN_CONNECTED -> Icons.Filled.Lock
    TriggerType.CLIPBOARD_CHANGED -> Icons.Filled.ContentPaste
    TriggerType.DND_STATE -> Icons.Filled.DoNotDisturbOn
    TriggerType.STAY_AWAKE_STATE -> Icons.Filled.Bedtime
    TriggerType.AUTO_BRIGHTNESS_STATE -> Icons.Filled.BrightnessAuto
    TriggerType.SCREEN_TIMEOUT_CHANGED -> Icons.Filled.Timer
    TriggerType.DATA_ROAMING_STATE -> Icons.Filled.DataUsage
    TriggerType.TIMEZONE_CHANGED -> Icons.Filled.AccessTime
    TriggerType.BOOT_COMPLETED -> Icons.Filled.PowerSettingsNew
    TriggerType.NFC_TAG_SCANNED -> Icons.Filled.Nfc
    TriggerType.ALARM_SET_CHANGED -> Icons.Filled.Alarm
    TriggerType.WEAR_EVENT -> Icons.Filled.Watch
    TriggerType.PLUGIN_EVENT -> Icons.Filled.Extension
}

