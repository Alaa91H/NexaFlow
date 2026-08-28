package com.nexaflow.feature.builder

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
import com.nexaflow.core.rom.EvolutionXSettingsBridge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.SelectChip
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Trigger types that use ON/OFF state but with custom labels. */
private val ON_OFF_TRIGGER_LABELS = mapOf(
    TriggerType.POWER_SAVER to R.string.trigger_power_saver_state,
    TriggerType.BLUETOOTH_STATE to R.string.trigger_bluetooth_state_hint,
    TriggerType.AUTO_ROTATE to R.string.trigger_auto_rotate_state,
    TriggerType.DATA_SAVER_STATE to R.string.trigger_data_saver_state,
    TriggerType.WIFI_STATE to R.string.trigger_wifi_state_hint,
    TriggerType.NFC_STATE to R.string.trigger_nfc_state_hint
)

private const val LOCATION_RADIUS_MIN_M = 50
private const val LOCATION_RADIUS_MAX_M = 2000
// (max - min) / step - 1: 50 m granularity between the endpoints.
private const val LOCATION_RADIUS_STEPS = (LOCATION_RADIUS_MAX_M - LOCATION_RADIUS_MIN_M) / 50 - 1

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

/** Trigger type → category; the grouped picker renders headers from it. */
internal val triggerCategoryOf: Map<TriggerType, TriggerCategory> = mapOf(
    TriggerType.TIME to TriggerCategory.SCHEDULE,
    TriggerType.CALENDAR to TriggerCategory.SCHEDULE,
    TriggerType.BATTERY to TriggerCategory.DEVICE,
    TriggerType.DEVICE to TriggerCategory.DEVICE,
    TriggerType.RINGER_MODE to TriggerCategory.DEVICE,
    TriggerType.NOTIFICATION to TriggerCategory.DEVICE,
    TriggerType.CONNECTIVITY to TriggerCategory.CONNECTIVITY,
    TriggerType.HOTSPOT to TriggerCategory.CONNECTIVITY,
    TriggerType.NETWORK_MODE to TriggerCategory.CONNECTIVITY,
    TriggerType.BLUETOOTH_DEVICE to TriggerCategory.CONNECTIVITY,
    TriggerType.WEBHOOK to TriggerCategory.CONNECTIVITY,
    TriggerType.LOCATION to TriggerCategory.LOCATION,
    TriggerType.APPLICATION to TriggerCategory.APPS,
    TriggerType.SMS to TriggerCategory.COMMUNICATION,
    TriggerType.SENSOR to TriggerCategory.DEVICE,
    TriggerType.ROM_SETTING to TriggerCategory.DEVICE,
    TriggerType.HEADPHONE to TriggerCategory.DEVICE,
    TriggerType.CHARGER to TriggerCategory.DEVICE,
    TriggerType.AIRPLANE_MODE to TriggerCategory.DEVICE,
    TriggerType.DARK_MODE to TriggerCategory.DEVICE,
    TriggerType.CALL_STATE to TriggerCategory.DEVICE,
    TriggerType.MEDIA_PLAYING to TriggerCategory.DEVICE,
    TriggerType.VOLUME_CHANGED to TriggerCategory.DEVICE,
    TriggerType.APP_INSTALLED to TriggerCategory.APPS,
    TriggerType.POWER_SAVER to TriggerCategory.DEVICE,
    TriggerType.BLUETOOTH_STATE to TriggerCategory.CONNECTIVITY,
    TriggerType.BRIGHTNESS_LEVEL to TriggerCategory.DEVICE,
    TriggerType.STORAGE_LOW to TriggerCategory.DEVICE,
    TriggerType.AUTO_ROTATE to TriggerCategory.DEVICE,
    TriggerType.DATA_SAVER_STATE to TriggerCategory.CONNECTIVITY,
    TriggerType.DEVICE_LOCKED to TriggerCategory.DEVICE,
    TriggerType.WIFI_STATE to TriggerCategory.CONNECTIVITY,
    TriggerType.NFC_STATE to TriggerCategory.CONNECTIVITY,
    TriggerType.LOCATION_STATE to TriggerCategory.LOCATION,
    TriggerType.SCREEN_ROTATION_STATE to TriggerCategory.DEVICE,
    TriggerType.WIFI_SIGNAL_STRENGTH to TriggerCategory.CONNECTIVITY,
    TriggerType.CELL_SIGNAL_STRENGTH to TriggerCategory.CONNECTIVITY,
    TriggerType.BATTERY_TEMPERATURE to TriggerCategory.DEVICE,
    TriggerType.USB_CONNECTED to TriggerCategory.DEVICE,
    TriggerType.HDMI_CONNECTED to TriggerCategory.DEVICE,
    TriggerType.ETHERNET_CONNECTED to TriggerCategory.CONNECTIVITY,
    TriggerType.VPN_CONNECTED to TriggerCategory.CONNECTIVITY,
    TriggerType.CLIPBOARD_CHANGED to TriggerCategory.DEVICE,
    TriggerType.DND_STATE to TriggerCategory.DEVICE,
    TriggerType.STAY_AWAKE_STATE to TriggerCategory.DEVICE,
    TriggerType.AUTO_BRIGHTNESS_STATE to TriggerCategory.DEVICE,
    TriggerType.SCREEN_TIMEOUT_CHANGED to TriggerCategory.DEVICE,
    TriggerType.DATA_ROAMING_STATE to TriggerCategory.CONNECTIVITY,
    TriggerType.TIMEZONE_CHANGED to TriggerCategory.DEVICE,
    TriggerType.BOOT_COMPLETED to TriggerCategory.DEVICE,
    TriggerType.NFC_TAG_SCANNED to TriggerCategory.CONNECTIVITY,
    TriggerType.ALARM_SET_CHANGED to TriggerCategory.SCHEDULE
)

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
    // CONNECTIVITY — CONNECTIVITY itself remains supported for saved tasks but
    // is intentionally not addable because it duplicated dedicated triggers.
    TriggerType.HOTSPOT,
    TriggerType.NETWORK_MODE,
    TriggerType.BLUETOOTH_DEVICE,
    TriggerType.BLUETOOTH_STATE,
    TriggerType.WIFI_STATE,
    TriggerType.NFC_STATE,
    TriggerType.DATA_SAVER_STATE,
    TriggerType.WEBHOOK,
    // LOCATION — retain the geographic enter/exit trigger only. LOCATION_STATE
    // remains readable for legacy tasks but is intentionally not addable from
    // the picker because it duplicates no useful user workflow.
    TriggerType.LOCATION,
    // APPS
    TriggerType.APPLICATION,
    TriggerType.APP_INSTALLED,
    // COMMUNICATION
    TriggerType.SMS,
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

private val repeatOptions = listOf(
    "ONCE" to R.string.repeat_once,
    "INTERVAL" to R.string.repeat_custom
)

/** Google-Tasks-style occurrence of a weekday inside a month (1st..4th / Last). */
private val occurrenceOptions = listOf(
    "1" to R.string.occurrence_first,
    "2" to R.string.occurrence_second,
    "3" to R.string.occurrence_third,
    "4" to R.string.occurrence_fourth,
    "LAST" to R.string.occurrence_last
)

private val weekdayOptions = listOf(
    1 to R.string.day_mon,
    2 to R.string.day_tue,
    3 to R.string.day_wed,
    4 to R.string.day_thu,
    5 to R.string.day_fri,
    6 to R.string.day_sat,
    7 to R.string.day_sun
)

/** Sensible default config for a freshly added trigger of the given type. */
internal fun defaultTriggerConfig(type: TriggerType): Map<String, String> = when (type) {
    TriggerType.TIME -> mapOf("time" to "08:00")
    TriggerType.BATTERY -> mapOf("direction" to "ABOVE", "above" to "80", "chargerType" to "ANY")
    TriggerType.APPLICATION -> mapOf("packages" to "")
    TriggerType.DEVICE -> mapOf("event" to "SCREEN_ON")
    TriggerType.CONNECTIVITY -> mapOf("network" to "WIFI", "state" to "CONNECTED")
    TriggerType.HOTSPOT -> mapOf("state" to "ON")
    TriggerType.NETWORK_MODE -> mapOf("state" to "4G")
    TriggerType.LOCATION -> mapOf("lat" to "", "lng" to "", "radius" to "100", "event" to "ENTER")
    TriggerType.SMS -> mapOf("from" to "", "contains" to "")
    TriggerType.BLUETOOTH_DEVICE -> mapOf("deviceName" to "", "deviceAddress" to "", "event" to "CONNECTED")
    TriggerType.RINGER_MODE -> mapOf("mode" to "NORMAL")
    TriggerType.NOTIFICATION -> mapOf("packages" to "", "contains" to "", "event" to "POSTED")
    TriggerType.CALENDAR -> mapOf("calendar" to "", "contains" to "", "event" to "EVENT_START", "beforeMinutes" to "0")
    TriggerType.SENSOR -> mapOf("sensor" to "PROXIMITY", "event" to "COVERED", "threshold" to "200", "sensitivity" to "14")
    TriggerType.WEBHOOK -> mapOf("path" to "/nexaflow", "method" to "POST", "token" to "")
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
    TriggerType.LOCATION_STATE -> mapOf("mode" to "HIGH")
    TriggerType.SCREEN_ROTATION_STATE -> mapOf("state" to "PORTRAIT")
    TriggerType.WIFI_SIGNAL_STRENGTH -> mapOf("threshold" to "3", "direction" to "ABOVE")
    TriggerType.CELL_SIGNAL_STRENGTH -> mapOf("threshold" to "3", "direction" to "ABOVE")
    TriggerType.BATTERY_TEMPERATURE -> mapOf("threshold" to "40", "direction" to "ABOVE")
    TriggerType.USB_CONNECTED -> mapOf("state" to "ON")
    TriggerType.HDMI_CONNECTED -> mapOf("state" to "ON")
    TriggerType.ETHERNET_CONNECTED -> mapOf("state" to "ON")
    TriggerType.VPN_CONNECTED -> mapOf("state" to "ON")
    TriggerType.CLIPBOARD_CHANGED -> mapOf()
    TriggerType.DND_STATE -> mapOf("state" to "ON")
    TriggerType.STAY_AWAKE_STATE -> mapOf("state" to "ON")
    TriggerType.AUTO_BRIGHTNESS_STATE -> mapOf("state" to "ON")
    TriggerType.SCREEN_TIMEOUT_CHANGED -> mapOf()
    TriggerType.DATA_ROAMING_STATE -> mapOf("state" to "ON")
    TriggerType.TIMEZONE_CHANGED -> mapOf()
    TriggerType.BOOT_COMPLETED -> mapOf()
    TriggerType.NFC_TAG_SCANNED -> mapOf()
    TriggerType.ALARM_SET_CHANGED -> mapOf()
    // Created only by the verified plugin configuration path, never the generic picker.
    TriggerType.PLUGIN_EVENT -> emptyMap()
}

internal fun TriggerType.labelRes(): Int = when (this) {
    TriggerType.TIME -> R.string.trigger_type_time
    TriggerType.BATTERY -> R.string.trigger_type_battery
    TriggerType.APPLICATION -> R.string.trigger_type_app
    TriggerType.DEVICE -> R.string.trigger_type_device
    TriggerType.CONNECTIVITY -> R.string.trigger_type_connectivity
    TriggerType.HOTSPOT -> R.string.action_hotspot
    TriggerType.NETWORK_MODE -> R.string.trigger_type_network_mode
    TriggerType.LOCATION -> R.string.trigger_type_location
    TriggerType.SMS -> R.string.trigger_type_sms
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
    TriggerType.PLUGIN_EVENT -> R.string.action_plugin
}

internal fun TriggerType.descRes(): Int = when (this) {
    TriggerType.TIME -> R.string.trigger_type_schedule_sub
    TriggerType.BATTERY -> R.string.trigger_type_battery_sub
    TriggerType.APPLICATION -> R.string.trigger_type_app_sub
    TriggerType.DEVICE -> R.string.trigger_type_device_sub
    TriggerType.CONNECTIVITY -> R.string.trigger_type_connectivity_sub
    TriggerType.HOTSPOT -> R.string.action_hotspot_sub
    TriggerType.NETWORK_MODE -> R.string.trigger_type_network_mode_sub
    TriggerType.LOCATION -> R.string.trigger_type_location_sub
    TriggerType.SMS -> R.string.trigger_type_sms_sub
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
    TriggerType.PLUGIN_EVENT -> R.string.action_plugin_sub
}

internal fun TriggerType.icon(): ImageVector = when (this) {
    TriggerType.TIME -> Icons.Filled.Schedule
    TriggerType.BATTERY -> Icons.Filled.BatteryChargingFull
    TriggerType.APPLICATION -> Icons.Filled.Apps
    TriggerType.DEVICE -> Icons.Filled.Bolt
    TriggerType.CONNECTIVITY -> Icons.Filled.Wifi
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
    TriggerType.PLUGIN_EVENT -> Icons.Filled.Extension
}

private fun parseDateMillis(value: String): Long? {
    return runCatching {
        LocalDate.parse(value)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

private fun millisToDateString(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()
}

/** Samsung-style tappable field that opens a Material3 date picker. */
@Composable
private fun DateField(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.DateRange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = value.ifBlank { stringResource(R.string.pick_date) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

/** Samsung-style tappable row that opens the time picker. */
@Composable
private fun TimeField(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = value.ifBlank { "08:00" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * Google-Tasks-style recurrence for both single-time and time-range triggers.
 * Legacy repeat values remain readable; entering the custom editor normalizes
 * them to INTERVAL while preserving their selected weekday or month-day data.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TimeRepeatSection(
    draft: TriggerDraft,
    onConfigChange: (TriggerDraft) -> Unit,
    onPickDate: (String) -> Unit
) {
    val storedRepeat = draft.config["repeat"] ?: "DAILY"
    val isOnce = storedRepeat == "ONCE"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = stringResource(R.string.repeat_label), style = MaterialTheme.typography.titleSmall)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeatOptions.forEach { (value, labelRes) ->
                SelectChip(
                    selected = if (value == "ONCE") isOnce else !isOnce,
                    onClick = {
                        val config = if (value == "ONCE") {
                            draft.config.filterKeys { it !in setOf("endMode", "endCount") } + ("repeat" to "ONCE")
                        } else {
                            intervalConfig(draft.config)
                        }
                        onConfigChange(draft.copy(config = config))
                    },
                    label = stringResource(labelRes)
                )
            }
        }
        if (!isOnce) {
            val intervalConfig = intervalConfig(draft.config)
            val interval = (intervalConfig["interval"]?.toIntOrNull() ?: 1).coerceIn(1, 99)
            val unit = intervalConfig["intervalUnit"] ?: "DAY"
            var unitMenuExpanded by rememberSaveable { mutableStateOf(false) }
            val unitOptions = listOf(
                "DAY" to R.string.repeat_unit_day,
                "WEEK" to R.string.repeat_unit_week,
                "MONTH" to R.string.repeat_unit_month,
                "YEAR" to R.string.repeat_unit_year
            )
            // Mirrors the compact Google Tasks flow: the repeat number and
            // period remain in one row instead of splitting the decision across
            // a full-width input and a second row of category chips.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = interval.toString(),
                    onValueChange = { input ->
                        val digits = input.filter(Char::isDigit).take(2)
                        val value = digits.toIntOrNull()?.coerceIn(1, 99) ?: 1
                        onConfigChange(draft.copy(config = intervalConfig + ("interval" to value.toString())))
                    },
                    modifier = Modifier.weight(0.28f),
                    label = { Text(stringResource(R.string.repeat_every)) },
                    placeholder = { Text(stringResource(R.string.repeat_interval_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Box(modifier = Modifier.weight(0.72f)) {
                    OutlinedButton(
                        onClick = { unitMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(unitOptions.first { it.first == unit }.second),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                    DropdownMenu(
                        expanded = unitMenuExpanded,
                        onDismissRequest = { unitMenuExpanded = false }
                    ) {
                        unitOptions.forEach { (value, labelRes) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                onClick = {
                                    val updates = if (
                                        (value == "WEEK" || value == "MONTH") &&
                                        intervalConfig["days"].isNullOrBlank()
                                    ) {
                                        intervalConfig + ("intervalUnit" to value) +
                                            ("days" to LocalDate.now().dayOfWeek.value.toString())
                                    } else {
                                        intervalConfig + ("intervalUnit" to value)
                                    }
                                    unitMenuExpanded = false
                                    onConfigChange(draft.copy(config = updates))
                                }
                            )
                        }
                    }
                }
            }
            if (unit == "WEEK" || unit == "MONTH") {
                Text(
                    text = stringResource(
                        if (unit == "MONTH") R.string.monthly_weekday_filter else R.string.select_days
                    ),
                    style = MaterialTheme.typography.titleSmall
                )
                val selectedDays = intervalConfig["days"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
                    ?.filter { it in 1..7 }.orEmpty()
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    weekdayOptions.forEach { (day, labelRes) ->
                        SelectChip(
                            selected = day in selectedDays,
                            onClick = {
                                val updated = if (day in selectedDays) selectedDays - day else selectedDays + day
                                onConfigChange(
                                    draft.copy(config = intervalConfig + ("days" to updated.sorted().joinToString(",")))
                                )
                            },
                            label = stringResource(labelRes),
                            showCheck = false
                        )
                    }
                }
            }
            if (unit == "MONTH") {
                Text(text = stringResource(R.string.monthly_date_rule), style = MaterialTheme.typography.titleSmall)
                val monthlyDayMode = intervalConfig["monthlyDayMode"] ?: "DAY_OF_MONTH"
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "DAY_OF_MONTH" to R.string.monthly_day_of_month,
                        "FIRST_DAY" to R.string.monthly_first_day,
                        "LAST_DAY" to R.string.monthly_last_day
                    ).forEach { (value, labelRes) ->
                        SelectChip(
                            selected = monthlyDayMode == value,
                            onClick = {
                                onConfigChange(
                                    draft.copy(config = intervalConfig + ("monthlyDayMode" to value))
                                )
                            },
                            label = stringResource(labelRes)
                        )
                    }
                }
                if (monthlyDayMode == "DAY_OF_MONTH") {
                    OutlinedTextField(
                        value = (intervalConfig["monthDay"]?.toIntOrNull() ?: LocalDate.now().dayOfMonth)
                            .coerceIn(1, 31).toString(),
                        onValueChange = { input ->
                            val digits = input.filter(Char::isDigit).take(2)
                            val value = digits.toIntOrNull()?.coerceIn(1, 31) ?: 1
                            onConfigChange(draft.copy(config = intervalConfig + ("monthDay" to value.toString())))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.month_day_label_short)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
            DateField(
                label = stringResource(R.string.repeat_starts),
                value = intervalConfig["startDate"] ?: "",
                onClick = { onPickDate("startDate") }
            )
            Text(text = stringResource(R.string.repeat_ends), style = MaterialTheme.typography.titleSmall)
            val endMode = intervalConfig["endMode"] ?: "NEVER"
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "NEVER" to R.string.repeat_end_never,
                    "ON_DATE" to R.string.repeat_end_on,
                    "AFTER_OCCURRENCES" to R.string.repeat_end_after
                ).forEach { (value, labelRes) ->
                    SelectChip(
                        selected = endMode == value,
                        onClick = {
                            onConfigChange(draft.copy(config = intervalConfig + ("endMode" to value)))
                        },
                        label = stringResource(labelRes)
                    )
                }
            }
            when (endMode) {
                "ON_DATE" -> DateField(
                    label = stringResource(R.string.end_date),
                    value = intervalConfig["endDate"] ?: "",
                    onClick = { onPickDate("endDate") }
                )
                "AFTER_OCCURRENCES" -> OutlinedTextField(
                    value = (intervalConfig["endCount"]?.toIntOrNull() ?: 1).coerceIn(1, 999).toString(),
                    onValueChange = { input ->
                        val digits = input.filter(Char::isDigit).take(3)
                        val value = digits.toIntOrNull()?.coerceIn(1, 999) ?: 1
                        onConfigChange(draft.copy(config = intervalConfig + ("endCount" to value.toString())))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.repeat_occurrences)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            val start = intervalConfig["startDate"]?.let(::parseDateMillis)
            val end = intervalConfig["endDate"]?.let(::parseDateMillis)
            if (endMode == "ON_DATE" && start != null && end != null && start > end) {
                Text(
                    text = stringResource(R.string.date_range_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        RepeatSummary(draft = draft)
    }
}

private fun intervalConfig(config: Map<String, String>): Map<String, String> {
    if (config["repeat"] == "INTERVAL") return config
    val legacyRepeat = config["repeat"] ?: "DAILY"
    val unit = when (legacyRepeat) {
        "SPECIFIC_DAYS", "WEEKDAYS", "WEEKENDS" -> "WEEK"
        "MONTHLY", "MONTHLY_WEEKDAY" -> "MONTH"
        else -> "DAY"
    }
    val inheritedDays = when (legacyRepeat) {
        "WEEKDAYS" -> "1,2,3,4,5"
        "WEEKENDS" -> "6,7"
        "MONTHLY_WEEKDAY" -> config["weekday"].orEmpty()
        else -> config["days"].orEmpty()
    }
    return config + mapOf(
        "repeat" to "INTERVAL",
        "interval" to "1",
        "intervalUnit" to unit,
        "startDate" to (config["startDate"] ?: LocalDate.now().toString()),
        "endMode" to (if (config["endDate"].isNullOrBlank()) "NEVER" else "ON_DATE"),
        "days" to inheritedDays
    )
}

/** Human-readable label of the current repeat choice (no "Selected:" prefix). */
@Composable
private fun repeatLabel(draft: TriggerDraft): String {
    val config = draft.config
    return when (config["repeat"] ?: "DAILY") {
        "ONCE" -> stringResource(R.string.repeat_once)
        "DAILY" -> stringResource(R.string.repeat_daily)
        "SPECIFIC_DAYS" -> {
            val days = config["days"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
            if (days.isEmpty()) stringResource(R.string.select_days)
            else days.mapNotNull { day ->
                weekdayOptions.firstOrNull { it.first == day }?.let { (_, res) -> stringResource(res) }
            }.joinToString(", ")
        }
        "MONTHLY" -> {
            val day = (config["monthDay"] ?: "1").toIntOrNull() ?: 1
            stringResource(R.string.month_day_label, day)
        }
        "MONTHLY_WEEKDAY" -> {
            val weekday = (config["weekday"] ?: "1").toIntOrNull() ?: 1
            val occurrence = config["weekOfMonth"] ?: "1"
            val dayLabel = weekdayOptions.firstOrNull { it.first == weekday }
                ?.let { (_, res) -> stringResource(res) }.orEmpty()
            val occLabel = occurrenceOptions.firstOrNull { it.first == occurrence }
                ?.let { (_, res) -> stringResource(res) } ?: occurrence
            stringResource(R.string.monthly_weekday_summary, occLabel, dayLabel)
        }
        "INTERVAL" -> {
            val interval = (config["interval"]?.toIntOrNull() ?: 1).coerceIn(1, 99)
            val unitRes = when (config["intervalUnit"] ?: "DAY") {
                "WEEK" -> R.string.repeat_unit_week
                "MONTH" -> R.string.repeat_unit_month
                "YEAR" -> R.string.repeat_unit_year
                else -> R.string.repeat_unit_day
            }
            val days = config["days"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
                ?.mapNotNull { day -> weekdayOptions.firstOrNull { it.first == day }?.let { (_, res) -> stringResource(res) } }
                .orEmpty()
            buildString {
                append(interval)
                append(" ")
                append(stringResource(unitRes))
                if ((config["intervalUnit"] ?: "DAY") == "WEEK" && days.isNotEmpty()) {
                    append(" · ")
                    append(days.joinToString(", "))
                }
                if ((config["intervalUnit"] ?: "DAY") == "MONTH") {
                    val mode = config["monthlyDayMode"] ?: "DAY_OF_MONTH"
                    val monthLabel = when (mode) {
                        "FIRST_DAY" -> stringResource(R.string.monthly_first_day)
                        "LAST_DAY" -> stringResource(R.string.monthly_last_day)
                        else -> stringResource(
                            R.string.month_day_label,
                            (config["monthDay"]?.toIntOrNull() ?: LocalDate.now().dayOfMonth).coerceIn(1, 31)
                        )
                    }
                    append(" · ")
                    append(monthLabel)
                    if (days.isNotEmpty()) {
                        append(" · ")
                        append(days.joinToString(", "))
                    }
                }
            }
        }
        "DATE_RANGE" -> {
            val start = config["startDate"] ?: ""
            val end = config["endDate"] ?: ""
            if (start.isEmpty() && end.isEmpty()) stringResource(R.string.repeat_date_range)
            else "$start → $end"
        }
        else -> stringResource(R.string.repeat_daily)
    }
}

/** Live "Selected: …" line that makes the current repeat choice unambiguous. */
@Composable
private fun RepeatSummary(draft: TriggerDraft) {
    Text(
        text = stringResource(R.string.repeat_selected, repeatLabel(draft)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium
    )
}

/** One-line summary of the chosen trigger values for the collapsed card header. */
@Composable
private fun triggerSummary(draft: TriggerDraft): String {
    val c = draft.config
    return when (draft.type) {
        TriggerType.TIME -> {
            val time = if (c["timeMode"] == "RANGE") {
                "${c["rangeStart"] ?: "08:00"} – ${c["rangeEnd"] ?: "18:00"}"
            } else {
                c["time"] ?: "08:00"
            }
            "${repeatLabel(draft)} · $time"
        }
        TriggerType.BATTERY -> {
            val threshold = (c["above"] ?: "80").toIntOrNull() ?: 80
            val level = if ((c["direction"] ?: "ABOVE") == "ABOVE") "≥ $threshold%" else "≤ $threshold%"
            val charging = when (c["chargingState"] ?: "ANY") {
                "CHARGING" -> stringResource(R.string.charging_yes)
                "NOT_CHARGING" -> stringResource(R.string.charging_no)
                else -> null
            }
            listOfNotNull(level, charging).joinToString(" · ")
        }
        TriggerType.APPLICATION -> {
            val packages = (c["packages"] ?: c["package"] ?: "")
                .split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (packages.isEmpty()) stringResource(R.string.any_app)
            else stringResource(R.string.selected_apps_count, packages.size)
        }
        TriggerType.DEVICE -> when (c["event"] ?: "SCREEN_ON") {
            "SCREEN_OFF" -> stringResource(R.string.device_screen_off)
            "POWER_CONNECTED" -> stringResource(R.string.device_power_connected)
            "POWER_DISCONNECTED" -> stringResource(R.string.device_power_disconnected)
            "HEADSET_CONNECTED" -> stringResource(R.string.device_headset_connected)
            "HEADSET_DISCONNECTED" -> stringResource(R.string.device_headset_disconnected)
            "BLUETOOTH_CONNECTED" -> (c["deviceName"] ?: "").ifBlank { stringResource(R.string.device_bluetooth) }
            "BLUETOOTH_DISCONNECTED" -> stringResource(R.string.device_bluetooth)
            else -> stringResource(R.string.device_screen_on)
        }
        TriggerType.CONNECTIVITY -> {
            val network = c["network"] ?: "WIFI"
            val networkLabel = when (network) {
                "MOBILE" -> stringResource(R.string.network_mobile)
                "HOTSPOT" -> stringResource(R.string.network_hotspot)
                "NETWORK_MODE" -> stringResource(R.string.network_mode)
                else -> stringResource(R.string.network_wifi)
            }
            val state = c["state"] ?: "CONNECTED"
            val stateLabel = when {
                network == "NETWORK_MODE" -> when (state) {
                    "AUTO" -> stringResource(R.string.network_mode_auto)
                    "2G" -> stringResource(R.string.network_mode_2g)
                    "3G" -> stringResource(R.string.network_mode_3g)
                    "5G" -> stringResource(R.string.network_mode_5g)
                    else -> stringResource(R.string.network_mode_4g)
                }
                network == "HOTSPOT" -> if (state == "ON") stringResource(R.string.state_on) else stringResource(R.string.state_off)
                state == "CONNECTED" -> stringResource(R.string.state_connected)
                else -> stringResource(R.string.state_disconnected)
            }
            "$networkLabel · $stateLabel"
        }
        TriggerType.HOTSPOT -> {
            val state = if ((c["state"] ?: "ON") == "ON") {
                stringResource(R.string.state_on)
            } else {
                stringResource(R.string.state_off)
            }
            "${stringResource(R.string.network_hotspot)} · $state"
        }
        TriggerType.NETWORK_MODE -> when (c["state"] ?: "4G") {
            "AUTO" -> stringResource(R.string.network_mode_auto)
            "2G" -> stringResource(R.string.network_mode_2g)
            "3G" -> stringResource(R.string.network_mode_3g)
            "5G" -> stringResource(R.string.network_mode_5g)
            else -> stringResource(R.string.network_mode_4g)
        }
        TriggerType.LOCATION -> {
            val lat = c["lat"] ?: ""
            val lng = c["lng"] ?: ""
            if (lat.isBlank() || lng.isBlank()) {
                stringResource(R.string.location_not_set)
            } else {
                val radius = (c["radius"]?.toIntOrNull() ?: 100)
                    .coerceIn(LOCATION_RADIUS_MIN_M, LOCATION_RADIUS_MAX_M)
                val inside = if ((c["event"] ?: "ENTER") == "ENTER") {
                    stringResource(R.string.location_inside)
                } else {
                    stringResource(R.string.location_outside)
                }
                "$inside · ${stringResource(R.string.radius_meters_format, radius)}"
            }
        }
        TriggerType.SMS -> {
            val from = (c["from"] ?: "").trim()
            val contains = (c["contains"] ?: "").trim()
            when {
                from.isNotEmpty() -> "${stringResource(R.string.sms_from)}: $from"
                contains.isNotEmpty() -> "${stringResource(R.string.sms_contains)}: $contains"
                else -> stringResource(R.string.trigger_type_sms)
            }
        }
        TriggerType.RINGER_MODE -> when (c["mode"] ?: "NORMAL") {
            "VIBRATE" -> stringResource(R.string.ringer_vibrate)
            "SILENT" -> stringResource(R.string.ringer_silent)
            else -> stringResource(R.string.ringer_normal)
        }
        TriggerType.BLUETOOTH_DEVICE -> {
            val name = (c["deviceName"] ?: "").ifBlank { stringResource(R.string.no_bluetooth_device) }
            val state = if ((c["event"] ?: "CONNECTED") == "CONNECTED") {
                stringResource(R.string.state_connected)
            } else {
                stringResource(R.string.state_disconnected)
            }
            "$name · $state"
        }
        TriggerType.CALENDAR -> {
            val name = (c["calendar"] ?: "").ifBlank { stringResource(R.string.any_calendar) }
            val eventLabel = when (c["event"] ?: "EVENT_START") {
                "EVENT_END" -> stringResource(R.string.calendar_event_end)
                "EVENT_CREATED" -> stringResource(R.string.calendar_event_created)
                else -> stringResource(R.string.calendar_event_start)
            }
            "$name · $eventLabel"
        }
        TriggerType.SENSOR -> {
            val kind = when (c["sensor"] ?: "PROXIMITY") {
                "SHAKE" -> stringResource(R.string.sensor_shake)
                "LIGHT" -> stringResource(R.string.sensor_light)
                "STEP" -> stringResource(R.string.sensor_step)
                else -> stringResource(R.string.sensor_proximity)
            }
            val detail = when (c["sensor"] ?: "PROXIMITY") {
                "PROXIMITY" -> when (c["event"] ?: "COVERED") {
                    "UNCOVERED" -> stringResource(R.string.sensor_event_uncovered)
                    else -> stringResource(R.string.sensor_event_covered)
                }
                "LIGHT" -> when (c["event"] ?: "ABOVE") {
                    "BELOW" -> stringResource(R.string.sensor_event_below)
                    else -> stringResource(R.string.sensor_event_above)
                }
                else -> null
            }
            listOfNotNull(kind, detail).joinToString(" · ")
        }
        TriggerType.NOTIFICATION -> {
            val packages = (c["packages"] ?: c["package"] ?: "")
                .split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val app = if (packages.isEmpty()) stringResource(R.string.any_app)
            else stringResource(R.string.selected_apps_count, packages.size)
            "$app · ${c["event"] ?: "POSTED"}"
        }
        TriggerType.WEBHOOK -> "${c["method"] ?: "POST"} ${c["path"] ?: "/nexaflow"}"
        TriggerType.ROM_SETTING -> {
            val key = (c["key"] ?: "").ifBlank { stringResource(R.string.rom_setting_key) }
            val value = c["value"] ?: ""
            if (value.isEmpty()) key else "$key = $value"
        }
        TriggerType.HEADPHONE -> when (c["event"] ?: "CONNECTED") {
            "DISCONNECTED" -> stringResource(R.string.state_disconnected)
            else -> stringResource(R.string.state_connected)
        }
        TriggerType.CHARGER -> when (c["event"] ?: "CONNECTED") {
            "DISCONNECTED" -> stringResource(R.string.state_disconnected)
            else -> stringResource(R.string.state_connected)
        }
        TriggerType.AIRPLANE_MODE ->
            if ((c["state"] ?: "ON") == "ON") stringResource(R.string.state_on)
            else stringResource(R.string.state_off)
        TriggerType.DARK_MODE ->
            if ((c["state"] ?: "ON") == "ON") stringResource(R.string.state_on)
            else stringResource(R.string.state_off)
        TriggerType.CALL_STATE -> when (c["event"] ?: "INCOMING") {
            "OUTGOING" -> stringResource(R.string.call_outgoing)
            "ENDED" -> stringResource(R.string.call_ended)
            else -> stringResource(R.string.call_incoming)
        }
        TriggerType.APP_INSTALLED -> {
            val event = when (c["event"] ?: "INSTALLED") {
                "REMOVED" -> stringResource(R.string.app_removed)
                "UPDATED" -> stringResource(R.string.app_updated)
                else -> stringResource(R.string.app_installed)
            }
            val pkg = (c["package"] ?: "").trim()
            if (pkg.isEmpty()) event else "$event · $pkg"
        }
        TriggerType.MEDIA_PLAYING ->
            if ((c["event"] ?: "STARTED") == "STARTED") stringResource(R.string.media_started)
            else stringResource(R.string.media_stopped)
        TriggerType.VOLUME_CHANGED -> {
            val stream = when (c["stream"] ?: "MUSIC") {
                "RING" -> stringResource(R.string.volume_stream_ring)
                "ALARM" -> stringResource(R.string.volume_stream_alarm)
                "NOTIFICATION" -> stringResource(R.string.volume_stream_notification)
                else -> stringResource(R.string.volume_stream_music)
            }
            val direction = if ((c["direction"] ?: "ABOVE") == "ABOVE") {
                stringResource(R.string.volume_above)
            } else {
                stringResource(R.string.volume_below)
            }
            "$stream · $direction ${c["threshold"] ?: "50"}"
        }
        // Simple ON/OFF state triggers (custom labels + plain toggle).
        TriggerType.POWER_SAVER, TriggerType.BLUETOOTH_STATE, TriggerType.AUTO_ROTATE,
        TriggerType.DATA_SAVER_STATE, TriggerType.WIFI_STATE, TriggerType.NFC_STATE,
        TriggerType.USB_CONNECTED, TriggerType.HDMI_CONNECTED, TriggerType.ETHERNET_CONNECTED,
        TriggerType.VPN_CONNECTED, TriggerType.DND_STATE, TriggerType.STAY_AWAKE_STATE,
        TriggerType.AUTO_BRIGHTNESS_STATE, TriggerType.DATA_ROAMING_STATE ->
            if ((c["state"] ?: "ON") == "ON") stringResource(R.string.state_on)
            else stringResource(R.string.state_off)
        TriggerType.BRIGHTNESS_LEVEL -> {
            val direction = if ((c["direction"] ?: "ABOVE") == "ABOVE") {
                stringResource(R.string.volume_above)
            } else {
                stringResource(R.string.volume_below)
            }
            "$direction ${c["threshold"] ?: "128"}"
        }
        TriggerType.STORAGE_LOW -> {
            val direction = if ((c["direction"] ?: "BELOW") == "BELOW") {
                stringResource(R.string.storage_below)
            } else {
                stringResource(R.string.storage_above)
            }
            "$direction ${c["threshold"] ?: "1024"} MB"
        }
        TriggerType.DEVICE_LOCKED ->
            if ((c["state"] ?: "LOCKED") == "LOCKED") stringResource(R.string.device_locked)
            else stringResource(R.string.device_unlocked)
        TriggerType.LOCATION_STATE -> when (c["mode"] ?: "HIGH") {
            "OFF" -> stringResource(R.string.location_mode_off)
            "SENSORS" -> stringResource(R.string.location_mode_sensors)
            "BATTERY" -> stringResource(R.string.location_mode_battery)
            else -> stringResource(R.string.location_mode_high)
        }
        TriggerType.SCREEN_ROTATION_STATE ->
            if ((c["state"] ?: "PORTRAIT") == "PORTRAIT") stringResource(R.string.rotation_portrait)
            else stringResource(R.string.rotation_landscape)
        TriggerType.WIFI_SIGNAL_STRENGTH -> {
            val direction = if ((c["direction"] ?: "ABOVE") == "ABOVE") {
                stringResource(R.string.volume_above)
            } else {
                stringResource(R.string.volume_below)
            }
            "$direction ${c["threshold"] ?: "3"}"
        }
        TriggerType.CELL_SIGNAL_STRENGTH -> {
            val direction = if ((c["direction"] ?: "ABOVE") == "ABOVE") {
                stringResource(R.string.volume_above)
            } else {
                stringResource(R.string.volume_below)
            }
            "$direction ${c["threshold"] ?: "3"}"
        }
        TriggerType.BATTERY_TEMPERATURE -> {
            val direction = if ((c["direction"] ?: "ABOVE") == "ABOVE") {
                stringResource(R.string.volume_above)
            } else {
                stringResource(R.string.volume_below)
            }
            "$direction ${c["threshold"] ?: "40"}°C"
        }
        TriggerType.CLIPBOARD_CHANGED -> stringResource(R.string.trigger_events_on_change)
        TriggerType.SCREEN_TIMEOUT_CHANGED -> stringResource(R.string.trigger_events_on_change)
        TriggerType.TIMEZONE_CHANGED -> stringResource(R.string.trigger_events_on_change)
        TriggerType.BOOT_COMPLETED -> stringResource(R.string.trigger_events_on_change)
        TriggerType.NFC_TAG_SCANNED -> stringResource(R.string.trigger_events_on_change)
        TriggerType.ALARM_SET_CHANGED -> stringResource(R.string.trigger_events_on_change)
        TriggerType.PLUGIN_EVENT -> stringResource(R.string.action_plugin)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TriggerEditorCard(
    draft: TriggerDraft,
    index: Int,
    total: Int,
    onConfigChange: (TriggerDraft) -> Unit,
    onRemove: () -> Unit,
    /** Controlled by the builder so this card is the only expanded trigger. */
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    // Shared builder-row structure: ✕ remove, ↕️ reorder handle (long-press
    // drag + tap arrows), then the name. Wired by the reorderable list.
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onPickApp: () -> Unit,
    onPickFromMap: () -> Unit = {},
    onUseCurrentLocation: () -> Unit = {},
    onPickBluetooth: () -> Unit = {},
    onPickCalendar: () -> Unit = {},
    onRequestPermission: (Array<String>) -> Unit = {},
    onExplainSpecial: (SpecialPermission) -> Unit = {},
    // Re-probes the live permission badges when the screen resumes (e.g. after
    // returning from the accessibility or notification-access settings screen).
    refreshKey: Int = 0
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    var timePickerTarget by remember { mutableStateOf("time") } // "time" | "rangeStart" | "rangeEnd"
    var datePickerTarget by remember { mutableStateOf<String?>(null) } // "date" | "startDate" | "endDate"
    // Fixed header row; the builder owns expansion so the selected card is
    // the sole open card and a new card can close the previous one.
    val accent = builderCardAccent(index)
    NexaFlowCard(
        modifier = modifier,
        containerColor = builderCardContainerColor(index),
        contentColor = builderCardContentColor
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // X + reorder handle pinned to the LEFT, row number to the
                    // RIGHT, regardless of the locale direction.
                    // يفتح الصف الإعداد عند الحاجة ويطويه بعد الضبط، فيبقى
                    // محرر المهمة منظماً حتى عند تعدد المحفزات والشروط.
                    .clickable { onExpandedChange(!expanded) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.remove_trigger),
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
                // Single horizontal line: the chosen values; the row number
                // lives in a badge pinned to the right end.
                Text(
                    text = triggerSummary(draft),
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
            // A task card configures its already-selected trigger only. Type
            // discovery lives in the builder search/category browser before
            // the card is added, so no category strip is rendered here.
            when (draft.type) {
                TriggerType.TIME -> {
                    val rangeMode = draft.config["timeMode"] == "RANGE"
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SelectChip(
                                selected = !rangeMode,
                                onClick = {
                                    onConfigChange(
                                        draft.copy(
                                            config = draft.config + ("timeMode" to "SINGLE")
                                        )
                                    )
                                },
                                label = stringResource(R.string.time_single)
                            )
                            SelectChip(
                                selected = rangeMode,
                                onClick = {
                                    onConfigChange(
                                        draft.copy(
                                            config = draft.config +
                                                ("timeMode" to "RANGE") +
                                                ("rangeStart" to (draft.config["rangeStart"] ?: "08:00")) +
                                                ("rangeEnd" to (draft.config["rangeEnd"] ?: "18:00"))
                                        )
                                    )
                                },
                                label = stringResource(R.string.time_range)
                            )
                        }
                        if (rangeMode) {
                            val start = draft.config["rangeStart"] ?: "08:00"
                            val end = draft.config["rangeEnd"] ?: "18:00"
                            TimeField(
                                label = stringResource(R.string.time_range_start),
                                value = start,
                                onClick = { timePickerTarget = "rangeStart"; showTimePicker = true }
                            )
                            TimeField(
                                label = stringResource(R.string.time_range_end),
                                value = end,
                                onClick = { timePickerTarget = "rangeEnd"; showTimePicker = true }
                            )
                            val overnight = parseTimeMinutes(end) < parseTimeMinutes(start)
                            Text(
                                text = if (overnight) stringResource(R.string.range_overnight) else stringResource(R.string.range_same_day),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (overnight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { timePickerTarget = "time"; showTimePicker = true }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.trigger_time),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = stringResource(R.string.repeat_daily),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                TextButton(onClick = { timePickerTarget = "time"; showTimePicker = true }) {
                                    Text(text = draft.config["time"] ?: "08:00")
                                }
                            }
                        }
                        SpecialPermissionStatusRow(
                            hintText = stringResource(R.string.special_exact_alarm_hint),
                            special = SpecialPermission.EXACT_ALARM,
                            context = context,
                            refreshKey = refreshKey,
                            onRequest = { onExplainSpecial(SpecialPermission.EXACT_ALARM) }
                        )
                        TimeRepeatSection(
                            draft = draft,
                            onConfigChange = onConfigChange,
                            onPickDate = { datePickerTarget = it }
                        )
                    }
                }
                TriggerType.BATTERY -> {
                    val direction = draft.config["direction"] ?: "ABOVE"
                    val chargerType = draft.config["chargerType"] ?: "ANY"
                    val chargingState = draft.config["chargingState"] ?: "ANY"
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val threshold = (draft.config["above"] ?: "80").toIntOrNull() ?: 80
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BatteryChargingFull,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.battery_trigger_title),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (direction == "ABOVE") {
                                        stringResource(R.string.battery_above_sub)
                                    } else {
                                        stringResource(R.string.battery_below_sub)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        // ── Battery level (independent of charging) ──────────
                        Text(
                            text = stringResource(R.string.battery_level_section),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectChip(
                                selected = direction == "ABOVE",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("direction" to "ABOVE")))
                                },
                                label = stringResource(R.string.battery_above)
                            )
                            SelectChip(
                                selected = direction == "BELOW",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("direction" to "BELOW")))
                                },
                                label = stringResource(R.string.battery_below)
                            )
                        }
                        SliderRow(
                            label = if (direction == "ABOVE") {
                                stringResource(R.string.minimum_battery, threshold)
                            } else {
                                stringResource(R.string.maximum_battery, threshold)
                            },
                            value = threshold.toFloat(),
                            onValueChange = { value ->
                                onConfigChange(
                                    draft.copy(config = draft.config + ("above" to value.toInt().toString()))
                                )
                            },
                            valueRange = 5f..100f
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        // ── Charging state (plug + charger type, separate) ────
                        Text(
                            text = stringResource(R.string.charging_state_section),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val chargingOptions = listOf(
                            "ANY" to R.string.charging_any,
                            "CHARGING" to R.string.charging_yes,
                            "NOT_CHARGING" to R.string.charging_no
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            chargingOptions.forEach { (value, labelRes) ->
                                SelectChip(
                                    selected = chargingState == value,
                                    onClick = {
                                        onConfigChange(draft.copy(config = draft.config + ("chargingState" to value)))
                                    },
                                    label = stringResource(labelRes)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.charger_type_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        val chargerOptions = listOf(
                            "ANY" to R.string.charger_any,
                            "AC" to R.string.charger_ac,
                            "USB" to R.string.charger_usb,
                            "WIRELESS" to R.string.charger_wireless
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            chargerOptions.forEach { (value, labelRes) ->
                                SelectChip(
                                    selected = chargerType == value,
                                    onClick = {
                                        onConfigChange(draft.copy(config = draft.config + ("chargerType" to value)))
                                    },
                                    label = stringResource(labelRes)
                                )
                            }
                        }
                    }
                }
                TriggerType.APPLICATION -> {
                    val packages = (draft.config["packages"] ?: draft.config["package"] ?: "")
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.trigger_app),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (packages.isEmpty()) {
                                        stringResource(R.string.no_apps_selected)
                                    } else {
                                        stringResource(R.string.selected_apps_count, packages.size)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onPickApp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Filled.Apps, contentDescription = null)
                            Text(
                                text = stringResource(R.string.choose_apps),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        // Samsung-style lifecycle hint: the task runs while the
                        // app stays open and its end options apply on close.
                        Text(
                            text = stringResource(R.string.trigger_app_while_open),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        // Live accessibility-service badge (granted / not granted)
                        // refreshed on resume; tapping it explains and opens the
                        // accessibility settings screen.
                        SpecialPermissionStatusRow(
                            hintText = stringResource(R.string.app_detection_hint),
                            special = SpecialPermission.ACCESSIBILITY,
                            context = context,
                            refreshKey = refreshKey,
                            onRequest = { onExplainSpecial(SpecialPermission.ACCESSIBILITY) }
                        )
                    }
                }
                TriggerType.DEVICE -> {
                    val event = draft.config["event"] ?: "SCREEN_ON"
                    val isBluetooth = event == "BLUETOOTH_CONNECTED" || event == "BLUETOOTH_DISCONNECTED"
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                        OptionChips(
                            options = listOf(
                                "SCREEN_ON",
                                "SCREEN_OFF",
                                "POWER_CONNECTED",
                                "POWER_DISCONNECTED",
                                "HEADSET_CONNECTED",
                                "HEADSET_DISCONNECTED",
                                "BLUETOOTH"
                            ),
                            labels = mapOf(
                                "SCREEN_ON" to stringResource(R.string.device_screen_on),
                                "SCREEN_OFF" to stringResource(R.string.device_screen_off),
                                "POWER_CONNECTED" to stringResource(R.string.device_power_connected),
                                "POWER_DISCONNECTED" to stringResource(R.string.device_power_disconnected),
                                "HEADSET_CONNECTED" to stringResource(R.string.device_headset_connected),
                                "HEADSET_DISCONNECTED" to stringResource(R.string.device_headset_disconnected),
                                "BLUETOOTH" to stringResource(R.string.device_bluetooth)
                            ),
                            selected = if (isBluetooth) "BLUETOOTH" else event,
                            onSelect = { value ->
                                if (value == "BLUETOOTH") {
                                    onConfigChange(
                                        draft.copy(
                                            config = draft.config +
                                                ("event" to "BLUETOOTH_CONNECTED") +
                                                ("deviceName" to (draft.config["deviceName"] ?: ""))
                                        )
                                    )
                                } else {
                                    onConfigChange(draft.copy(config = draft.config + ("event" to value)))
                                }
                            }
                        )
                        if (isBluetooth) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bluetooth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.trigger_bluetooth),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = (draft.config["deviceName"] ?: "").ifBlank {
                                            stringResource(R.string.no_bluetooth_device)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = onPickBluetooth,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Filled.Bluetooth, contentDescription = null)
                                Text(
                                    text = stringResource(R.string.choose_bluetooth_device),
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                            Text(text = stringResource(R.string.state), style = MaterialTheme.typography.titleSmall)
                            OptionChips(
                                options = listOf("BLUETOOTH_CONNECTED", "BLUETOOTH_DISCONNECTED"),
                                labels = mapOf(
                                    "BLUETOOTH_CONNECTED" to stringResource(R.string.state_connected),
                                    "BLUETOOTH_DISCONNECTED" to stringResource(R.string.state_disconnected)
                                ),
                                selected = event,
                                onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                            )
                            RuntimePermissionHint(
                                context = context,
                                permissions = listOf(android.Manifest.permission.BLUETOOTH_CONNECT),
                                text = stringResource(R.string.bluetooth_permission_hint),
                                buttonLabel = stringResource(R.string.enable),
                                onRequest = { onRequestPermission(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT)) }
                            )
                        }
                    }
                }
                TriggerType.HOTSPOT -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.state), style = MaterialTheme.typography.titleSmall)
                        OptionChips(
                            options = listOf("ON", "OFF"),
                            labels = mapOf(
                                "ON" to stringResource(R.string.state_on),
                                "OFF" to stringResource(R.string.state_off)
                            ),
                            selected = draft.config["state"] ?: "ON",
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                        )
                    }
                }
                TriggerType.CONNECTIVITY -> {
                    val network = draft.config["network"] ?: "WIFI"
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.network), style = MaterialTheme.typography.titleSmall)
                        // Legacy tasks retain their existing configuration, but
                        // new choices are limited to Wi-Fi/mobile. Hotspot and
                        // network mode are first-class trigger types.
                        OptionChips(
                            options = listOf("WIFI", "MOBILE"),
                            labels = mapOf(
                                "WIFI" to stringResource(R.string.network_wifi),
                                "MOBILE" to stringResource(R.string.network_mobile)
                            ),
                            selected = network,
                            onSelect = {
                                onConfigChange(
                                    draft.copy(
                                        config = draft.config + ("network" to it) +
                                            ("state" to when (it) {
                                                "HOTSPOT" -> "ON"
                                                "NETWORK_MODE" -> "4G"
                                                else -> "CONNECTED"
                                            })
                                    )
                                )
                            }
                        )
                        Text(text = stringResource(R.string.state), style = MaterialTheme.typography.titleSmall)
                        when (network) {
                            "HOTSPOT" -> OptionChips(
                                options = listOf("ON", "OFF"),
                                labels = mapOf(
                                    "ON" to stringResource(R.string.state_on),
                                    "OFF" to stringResource(R.string.state_off)
                                ),
                                selected = draft.config["state"] ?: "ON",
                                onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                            )
                            "NETWORK_MODE" -> OptionChips(
                                options = listOf("AUTO", "2G", "3G", "4G", "5G"),
                                labels = mapOf(
                                    "AUTO" to stringResource(R.string.network_mode_auto),
                                    "2G" to stringResource(R.string.network_mode_2g),
                                    "3G" to stringResource(R.string.network_mode_3g),
                                    "4G" to stringResource(R.string.network_mode_4g),
                                    "5G" to stringResource(R.string.network_mode_5g)
                                ),
                                selected = draft.config["state"] ?: "4G",
                                onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                            )
                            else -> OptionChips(
                                options = listOf("CONNECTED", "DISCONNECTED"),
                                labels = mapOf(
                                    "CONNECTED" to stringResource(R.string.state_connected),
                                    "DISCONNECTED" to stringResource(R.string.state_disconnected)
                                ),
                                selected = draft.config["state"] ?: "CONNECTED",
                                onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                            )
                        }
                    }
                }
                TriggerType.NETWORK_MODE -> {
                    var currentGen by remember { mutableStateOf<String?>(null) }
                    var currentGenLoading by remember { mutableStateOf(false) }
                    var currentGenRefreshToken by remember { mutableStateOf(0) }
                    LaunchedEffect(currentGenRefreshToken, refreshKey) {
                        currentGenLoading = true
                        currentGen = withContext(Dispatchers.IO) {
                            currentCellularGeneration(context)
                        }
                        currentGenLoading = false
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Live read of the device's actual cellular generation,
                        // using the same real-5G detection as the runtime monitor.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.network_mode_current),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = if (currentGenLoading) {
                                    stringResource(R.string.network_mode_unknown)
                                } else {
                                    currentGen ?: stringResource(R.string.network_mode_unknown)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                enabled = !currentGenLoading,
                                onClick = { currentGenRefreshToken++ }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.refresh)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.network_mode_current_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        RuntimePermissionHint(
                            context = context,
                            permissions = listOf(android.Manifest.permission.READ_PHONE_STATE),
                            text = stringResource(R.string.network_mode_permission_hint),
                            buttonLabel = stringResource(R.string.enable),
                            onRequest = {
                                onRequestPermission(arrayOf(android.Manifest.permission.READ_PHONE_STATE))
                            }
                        )
                        Text(
                            text = stringResource(R.string.network_mode_fire_when),
                            style = MaterialTheme.typography.titleSmall
                        )
                        OptionChips(
                            options = listOf("AUTO", "2G", "3G", "4G", "5G"),
                            labels = mapOf(
                                "AUTO" to stringResource(R.string.network_mode_auto),
                                "2G" to stringResource(R.string.network_mode_2g),
                                "3G" to stringResource(R.string.network_mode_3g),
                                "4G" to stringResource(R.string.network_mode_4g),
                                "5G" to stringResource(R.string.network_mode_5g)
                            ),
                            selected = draft.config["state"] ?: "4G",
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                        )
                    }
                }
                TriggerType.LOCATION -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Two distinct location modes share the existing location engine:
                        // current device location or a provider-independent fixed point.
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onUseCurrentLocation,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Filled.MyLocation, contentDescription = null)
                                Text(
                                    text = stringResource(R.string.use_current_location),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = onPickFromMap,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Filled.Map, contentDescription = null)
                                Text(
                                    text = stringResource(R.string.pick_on_map),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                        // Read-only summary of a selected fixed point. Coordinates are
                        // provider-independent and remain editable through the picker.
                        val lat = draft.config["lat"] ?: ""
                        val lng = draft.config["lng"] ?: ""
                        if (lat.isNotBlank() && lng.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.location_coordinates, lat, lng),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.location_not_set),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        // The current-location flow uses the shared location fix;
                        // selected-location stores a fixed coordinate and its radius.
                        val radius = (draft.config["radius"]?.toIntOrNull() ?: 100)
                            .coerceIn(LOCATION_RADIUS_MIN_M, LOCATION_RADIUS_MAX_M)
                        val locationSource = draft.config["source"] ?: "current"
                        if (locationSource == "current") {
                            // Current-location mode keeps the existing radius editor.
                            Text(
                                text = stringResource(R.string.radius_meters_format, radius),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Slider(
                                value = radius.toFloat(),
                                onValueChange = { value ->
                                    onConfigChange(
                                        draft.copy(
                                            config = draft.config + ("radius" to value.toInt().toString())
                                        )
                                    )
                                },
                                valueRange = LOCATION_RADIUS_MIN_M.toFloat()..LOCATION_RADIUS_MAX_M.toFloat(),
                                steps = LOCATION_RADIUS_STEPS
                            )
                        } else {
                            // Selected fixed-location mode keeps the saved radius visible.
                            Text(
                                text = stringResource(R.string.map_radius_summary, radius),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        // Inside / outside the defined location (both flows).
                        val event = draft.config["event"] ?: "ENTER"
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectChip(
                                selected = event == "ENTER",
                                onClick = { onConfigChange(draft.copy(config = draft.config + ("event" to "ENTER"))) },
                                label = stringResource(R.string.location_inside),
                                modifier = Modifier.weight(1f)
                            )
                            SelectChip(
                                selected = event == "EXIT",
                                onClick = { onConfigChange(draft.copy(config = draft.config + ("event" to "EXIT"))) },
                                label = stringResource(R.string.location_outside),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        RuntimePermissionHint(
                            context = context,
                            permissions = listOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            ),
                            text = stringResource(R.string.location_hint),
                            buttonLabel = stringResource(R.string.grant),
                            onRequest = {
                                onRequestPermission(
                                    arrayOf(
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        )
                    }
                }
                TriggerType.SMS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.config["from"] ?: "",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("from" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.sms_from)) },
                            placeholder = { Text(text = stringResource(R.string.sms_from_hint)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = draft.config["contains"] ?: "",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("contains" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.sms_contains)) },
                            placeholder = { Text(text = stringResource(R.string.sms_contains_hint)) },
                            singleLine = true
                        )
                        RuntimePermissionHint(
                            context = context,
                            permissions = listOf(android.Manifest.permission.RECEIVE_SMS),
                            text = stringResource(R.string.sms_permission_hint),
                            buttonLabel = stringResource(R.string.grant),
                            onRequest = { onRequestPermission(arrayOf(android.Manifest.permission.RECEIVE_SMS)) }
                        )
                    }
                }
                TriggerType.RINGER_MODE -> {
                    val mode = draft.config["mode"] ?: "NORMAL"
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.trigger_ringer),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = stringResource(R.string.trigger_ringer_sub),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Text(text = stringResource(R.string.ringer_mode_label), style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val modes = listOf(
                                "NORMAL" to R.string.ringer_normal,
                                "VIBRATE" to R.string.ringer_vibrate,
                                "SILENT" to R.string.ringer_silent
                            )
                            modes.forEach { (value, labelRes) ->
                                SelectChip(
                                    selected = mode == value,
                                    onClick = { onConfigChange(draft.copy(config = draft.config + ("mode" to value))) },
                                    label = stringResource(labelRes)
                                )
                            }
                        }
                        PermissionHint(
                            text = stringResource(R.string.ringer_mode_hint),
                            buttonLabel = stringResource(R.string.change_now),
                            onClick = {
                                runCatching {
                                    val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                                    when (mode) {
                                        "SILENT" -> audio.ringerMode = android.media.AudioManager.RINGER_MODE_SILENT
                                        "VIBRATE" -> audio.ringerMode = android.media.AudioManager.RINGER_MODE_VIBRATE
                                        else -> audio.ringerMode = android.media.AudioManager.RINGER_MODE_NORMAL
                                    }
                                }
                            }
                        )
                    }
                }
                TriggerType.BLUETOOTH_DEVICE -> {
                    val deviceName = draft.config["deviceName"] ?: ""
                    val event = draft.config["event"] ?: "CONNECTED"
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bluetooth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.trigger_bluetooth),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (deviceName.isBlank()) {
                                        stringResource(R.string.no_bluetooth_device)
                                    } else {
                                        deviceName
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onPickBluetooth,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Filled.Bluetooth, contentDescription = null)
                            Text(
                                text = stringResource(R.string.choose_bluetooth_device),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        Text(text = stringResource(R.string.state), style = MaterialTheme.typography.titleSmall)
                        OptionChips(
                            options = listOf("CONNECTED", "DISCONNECTED"),
                            selected = event,
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                        )
                        // Live badge for the BLUETOOTH_CONNECT runtime permission:
                        // tapping requests it through the system dialog (after the
                        // explain screen) instead of the Bluetooth settings screen,
                        // which cannot grant a runtime permission.
                        SpecialPermissionStatusRow(
                            hintText = stringResource(R.string.bluetooth_permission_hint),
                            special = SpecialPermission.BLUETOOTH,
                            context = context,
                            refreshKey = refreshKey,
                            onRequest = {
                                onRequestPermission(
                                    arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT)
                                )
                            }
                        )
                    }
                }
                TriggerType.CALENDAR -> {
                    val calendarName = draft.config["calendar"] ?: ""
                    val event = draft.config["event"] ?: "EVENT_START"
                    val beforeMinutes = (draft.config["beforeMinutes"] ?: "0").toIntOrNull() ?: 0
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.trigger_calendar),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (calendarName.isBlank()) {
                                        stringResource(R.string.any_calendar)
                                    } else {
                                        calendarName
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onPickCalendar,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Filled.DateRange, contentDescription = null)
                            Text(
                                text = stringResource(R.string.choose_calendar),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        OutlinedTextField(
                            value = draft.config["contains"] ?: "",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("contains" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.calendar_contains)) },
                            placeholder = { Text(text = stringResource(R.string.calendar_contains_hint)) },
                            singleLine = true
                        )
                        Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val events = listOf(
                                "EVENT_START" to R.string.calendar_event_start,
                                "EVENT_END" to R.string.calendar_event_end,
                                "EVENT_CREATED" to R.string.calendar_event_created
                            )
                            events.forEach { (value, labelRes) ->
                                SelectChip(
                                    selected = event == value,
                                    onClick = { onConfigChange(draft.copy(config = draft.config + ("event" to value))) },
                                    label = stringResource(labelRes)
                                )
                            }
                        }
                        if (event == "EVENT_START") {
                            SliderRow(
                                label = if (beforeMinutes == 0) {
                                    stringResource(R.string.calendar_before_none)
                                } else {
                                    stringResource(R.string.calendar_before_minutes, beforeMinutes)
                                },
                                value = beforeMinutes.toFloat(),
                                onValueChange = { value ->
                                    onConfigChange(
                                        draft.copy(config = draft.config + ("beforeMinutes" to value.toInt().toString()))
                                    )
                                },
                                valueRange = 0f..120f
                            )
                        }
                        RuntimePermissionHint(
                            context = context,
                            permissions = listOf(android.Manifest.permission.READ_CALENDAR),
                            text = stringResource(R.string.calendar_permission_hint),
                            buttonLabel = stringResource(R.string.grant),
                            onRequest = { onRequestPermission(arrayOf(android.Manifest.permission.READ_CALENDAR)) }
                        )
                    }
                }
                TriggerType.SENSOR -> {
                    val sensor = draft.config["sensor"] ?: "PROXIMITY"
                    val event = draft.config["event"] ?: "COVERED"
                    val threshold = (draft.config["threshold"] ?: "200").toIntOrNull() ?: 200
                    val sensitivity = (draft.config["sensitivity"] ?: "14").toIntOrNull() ?: 14
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.sensor_kind),
                            style = MaterialTheme.typography.titleSmall
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val kinds = listOf(
                                "PROXIMITY" to R.string.sensor_proximity,
                                "SHAKE" to R.string.sensor_shake,
                                "LIGHT" to R.string.sensor_light,
                                "STEP" to R.string.sensor_step
                            )
                            kinds.forEach { (value, labelRes) ->
                                SelectChip(
                                    selected = sensor == value,
                                    onClick = {
                                        onConfigChange(
                                            draft.copy(config = draft.config + ("sensor" to value))
                                        )
                                    },
                                    label = stringResource(labelRes)
                                )
                            }
                        }
                        when (sensor) {
                            "PROXIMITY" -> {
                                Text(
                                    text = stringResource(R.string.event),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val events = listOf(
                                        "COVERED" to R.string.sensor_event_covered,
                                        "UNCOVERED" to R.string.sensor_event_uncovered
                                    )
                                    events.forEach { (value, labelRes) ->
                                        SelectChip(
                                            selected = event == value,
                                            onClick = {
                                                onConfigChange(
                                                    draft.copy(config = draft.config + ("event" to value))
                                                )
                                            },
                                            label = stringResource(labelRes)
                                        )
                                    }
                                }
                            }
                            "LIGHT" -> {
                                Text(
                                    text = stringResource(R.string.event),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val events = listOf(
                                        "ABOVE" to R.string.sensor_event_above,
                                        "BELOW" to R.string.sensor_event_below
                                    )
                                    events.forEach { (value, labelRes) ->
                                        SelectChip(
                                            selected = event == value,
                                            onClick = {
                                                onConfigChange(
                                                    draft.copy(config = draft.config + ("event" to value))
                                                )
                                            },
                                            label = stringResource(labelRes)
                                        )
                                    }
                                }
                                SliderRow(
                                    label = stringResource(R.string.sensor_threshold_label, threshold),
                                    value = threshold.toFloat(),
                                    onValueChange = { value ->
                                        onConfigChange(
                                            draft.copy(config = draft.config + ("threshold" to value.toInt().toString()))
                                        )
                                    },
                                    valueRange = 5f..1000f
                                )
                            }
                            "SHAKE" -> {
                                SliderRow(
                                    label = stringResource(R.string.sensor_sensitivity_label, sensitivity),
                                    value = sensitivity.toFloat(),
                                    onValueChange = { value ->
                                        onConfigChange(
                                            draft.copy(config = draft.config + ("sensitivity" to value.toInt().toString()))
                                        )
                                    },
                                    valueRange = 5f..30f
                                )
                            }
                            "STEP" -> {
                                Text(
                                    text = stringResource(R.string.sensor_step_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                // Raw step-counter reads on Android 10+ need the
                                // ACTIVITY_RECOGNITION runtime permission.
                                RuntimePermissionHint(
                                    context = context,
                                    permissions = listOf(android.Manifest.permission.ACTIVITY_RECOGNITION),
                                    text = stringResource(R.string.sensor_permission_hint),
                                    buttonLabel = stringResource(R.string.grant),
                                    onRequest = {
                                        onRequestPermission(
                                            arrayOf(android.Manifest.permission.ACTIVITY_RECOGNITION)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                TriggerType.WEBHOOK -> {
                    val path = draft.config["path"] ?: "/nexaflow"
                    val method = draft.config["method"] ?: "POST"
                    val token = draft.config["token"] ?: ""
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = path,
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("path" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.webhook_path)) },
                            placeholder = { Text(text = "/nexaflow") },
                            singleLine = true
                        )
                        Text(
                            text = stringResource(R.string.event),
                            style = MaterialTheme.typography.titleSmall
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val methods = listOf("POST", "GET", "ANY")
                            methods.forEach { value ->
                                SelectChip(
                                    selected = method == value,
                                    onClick = {
                                        onConfigChange(draft.copy(config = draft.config + ("method" to value)))
                                    },
                                    label = value
                                )
                            }
                        }
                        OutlinedTextField(
                            value = token,
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("token" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.webhook_token)) },
                            placeholder = { Text(text = stringResource(R.string.webhook_token_hint)) },
                            singleLine = true
                        )
                        Text(
                            text = stringResource(R.string.webhook_url_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                TriggerType.ROM_SETTING -> {
                    val namespace = draft.config["namespace"] ?: "SYSTEM"
                    val operator = draft.config["operator"] ?: "EQUALS"
                    val key = draft.config["key"] ?: ""
                    val value = draft.config["value"] ?: ""
                    val scope = rememberCoroutineScope()
                    var showKeyPicker by remember { mutableStateOf(false) }
                    var liveKeys by remember { mutableStateOf<List<EvolutionXSettingsBridge.SettingEntry>>(emptyList()) }
                    var keysLoading by remember { mutableStateOf(false) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // ── Namespace (system / secure / global) ────────────
                        Text(
                            text = stringResource(R.string.rom_setting_namespace),
                            style = MaterialTheme.typography.titleSmall
                        )
                        OptionChips(
                            options = listOf("SYSTEM", "SECURE", "GLOBAL"),
                            labels = mapOf(
                                "SYSTEM" to stringResource(R.string.rom_setting_namespace_system),
                                "SECURE" to stringResource(R.string.rom_setting_namespace_secure),
                                "GLOBAL" to stringResource(R.string.rom_setting_namespace_global)
                            ),
                            selected = namespace,
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("namespace" to it))) }
                        )
                        // ── Key: free text + live picker from the ROM ──────
                        OutlinedTextField(
                            value = key,
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("key" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.rom_setting_key)) },
                            placeholder = { Text(text = "evo_…") },
                            singleLine = true
                        )
                        OutlinedButton(
                            onClick = {
                                keysLoading = true
                                scope.launch {
                                    liveKeys = withContext(Dispatchers.IO) {
                                        EvolutionXSettingsBridge.listCustomKeys(context)
                                    }
                                    keysLoading = false
                                    showKeyPicker = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !keysLoading
                        ) {
                            Icon(
                                imageVector = if (keysLoading) Icons.Filled.Refresh else Icons.Filled.Bolt,
                                contentDescription = null
                            )
                            Text(
                                text = stringResource(R.string.rom_setting_pick_from_rom),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        // ── Operator + target value ─────────────────────────
                        Text(
                            text = stringResource(R.string.rom_setting_operator),
                            style = MaterialTheme.typography.titleSmall
                        )
                        OptionChips(
                            options = listOf("EQUALS", "NOT_EQUALS"),
                            labels = mapOf(
                                "EQUALS" to stringResource(R.string.rom_setting_equals),
                                "NOT_EQUALS" to stringResource(R.string.rom_setting_not_equals)
                            ),
                            selected = operator,
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("operator" to it))) }
                        )
                        OutlinedTextField(
                            value = value,
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("value" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.rom_setting_value)) },
                            placeholder = { Text(text = "1") },
                            singleLine = true
                        )
                        Text(
                            text = stringResource(R.string.rom_setting_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (showKeyPicker) {
                        // Google 2026: selection tasks open as a full-height modal bottom sheet.
                        ModalBottomSheet(
                            onDismissRequest = { showKeyPicker = false },
                            sheetState = rememberBottomSheetState(
                                initialValue = SheetValue.Hidden,
                                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
                            )
                        ) {
                        Text(
                            text = stringResource(R.string.rom_setting_pick_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
                        )
                        if (liveKeys.isEmpty()) {
                            Text(
                                text = stringResource(R.string.rom_setting_pick_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .padding(horizontal = 24.dp)
                            ) {
                                items(liveKeys, key = { it.displayKey }) { entry ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        onConfigChange(
                                                            draft.copy(
                                                                config = draft.config +
                                                                    ("namespace" to entry.namespace.name) +
                                                                    ("key" to entry.key)
                                                            )
                                                        )
                                                        showKeyPicker = false
                                                    }
                                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Bolt,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(end = 10.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = entry.displayKey,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = stringResource(
                                                            R.string.rom_setting_current_value,
                                                            entry.value
                                                        ),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                            }
                                        }                                }
                            }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showKeyPicker = false }) {
                                Text(text = stringResource(R.string.cancel))
                            }
                        }
                        }
                    }
                }
                TriggerType.NOTIFICATION -> {
                    val packages = (draft.config["packages"] ?: draft.config["package"] ?: "")
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    val event = draft.config["event"] ?: "POSTED"
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.trigger_notification),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (packages.isEmpty()) {
                                        stringResource(R.string.any_app)
                                    } else {
                                        stringResource(R.string.selected_apps_count, packages.size)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onPickApp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Filled.Apps, contentDescription = null)
                            Text(
                                text = stringResource(R.string.choose_apps),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        OutlinedTextField(
                            value = draft.config["contains"] ?: "",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("contains" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.notification_contains)) },
                            placeholder = { Text(text = stringResource(R.string.notification_contains_hint)) },
                            singleLine = true
                        )
                        Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                        OptionChips(
                            options = listOf("POSTED", "REMOVED"),
                            selected = event,
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                        )
                        // Live notification-listener badge (granted / not granted)
                        // refreshed on resume; tapping it explains and opens the
                        // notification access settings screen.
                        SpecialPermissionStatusRow(
                            hintText = stringResource(R.string.notification_access_hint),
                            special = SpecialPermission.NOTIFICATION_ACCESS,
                            context = context,
                            refreshKey = refreshKey,
                            onRequest = { onExplainSpecial(SpecialPermission.NOTIFICATION_ACCESS) }
                        )
                    }
                }
                TriggerType.HEADPHONE -> {
                    Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("CONNECTED", "DISCONNECTED"),
                        labels = mapOf(
                            "CONNECTED" to stringResource(R.string.state_connected),
                            "DISCONNECTED" to stringResource(R.string.state_disconnected)
                        ),
                        selected = draft.config["event"] ?: "CONNECTED",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                    )
                }
                TriggerType.CHARGER -> {
                    Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("CONNECTED", "DISCONNECTED"),
                        labels = mapOf(
                            "CONNECTED" to stringResource(R.string.state_connected),
                            "DISCONNECTED" to stringResource(R.string.state_disconnected)
                        ),
                        selected = draft.config["event"] ?: "CONNECTED",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                    )
                }
                TriggerType.AIRPLANE_MODE -> {
                    Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("ON", "OFF"),
                        labels = mapOf(
                            "ON" to stringResource(R.string.state_on),
                            "OFF" to stringResource(R.string.state_off)
                        ),
                        selected = draft.config["state"] ?: "ON",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                    )
                }
                TriggerType.DARK_MODE -> {
                    Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("ON", "OFF"),
                        labels = mapOf(
                            "ON" to stringResource(R.string.state_on),
                            "OFF" to stringResource(R.string.state_off)
                        ),
                        selected = draft.config["state"] ?: "ON",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                    )
                }
                TriggerType.CALL_STATE -> {
                    Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("INCOMING", "OUTGOING", "ENDED"),
                        labels = mapOf(
                            "INCOMING" to stringResource(R.string.call_incoming),
                            "OUTGOING" to stringResource(R.string.call_outgoing),
                            "ENDED" to stringResource(R.string.call_ended)
                        ),
                        selected = draft.config["event"] ?: "INCOMING",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                    )
                }
                TriggerType.APP_INSTALLED -> {
                    Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("INSTALLED", "REMOVED", "UPDATED"),
                        labels = mapOf(
                            "INSTALLED" to stringResource(R.string.app_installed),
                            "REMOVED" to stringResource(R.string.app_removed),
                            "UPDATED" to stringResource(R.string.app_updated)
                        ),
                        selected = draft.config["event"] ?: "INSTALLED",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                    )
                    OutlinedTextField(
                        value = draft.config["package"] ?: "",
                        onValueChange = { v ->
                            onConfigChange(draft.copy(config = draft.config + ("package" to v)))
                        },
                        label = { Text(text = stringResource(R.string.optional_package)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TriggerType.MEDIA_PLAYING -> {
                    Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("STARTED", "STOPPED"),
                        labels = mapOf(
                            "STARTED" to stringResource(R.string.media_started),
                            "STOPPED" to stringResource(R.string.media_stopped)
                        ),
                        selected = draft.config["event"] ?: "STARTED",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                    )
                }
                TriggerType.VOLUME_CHANGED -> {
                    Text(text = stringResource(R.string.volume_stream), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("MUSIC", "RING", "ALARM", "NOTIFICATION"),
                        labels = mapOf(
                            "MUSIC" to stringResource(R.string.volume_stream_music),
                            "RING" to stringResource(R.string.volume_stream_ring),
                            "ALARM" to stringResource(R.string.volume_stream_alarm),
                            "NOTIFICATION" to stringResource(R.string.volume_stream_notification)
                        ),
                        selected = draft.config["stream"] ?: "MUSIC",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("stream" to it))) }
                    )
                    Text(text = stringResource(R.string.volume_direction), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("ABOVE", "BELOW"),
                        labels = mapOf(
                            "ABOVE" to stringResource(R.string.volume_above),
                            "BELOW" to stringResource(R.string.volume_below)
                        ),
                        selected = draft.config["direction"] ?: "ABOVE",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
                    )
                    OutlinedTextField(
                        value = draft.config["threshold"] ?: "50",
                        onValueChange = { v ->
                            onConfigChange(draft.copy(config = draft.config + ("threshold" to v.filter { it.isDigit() })))
                        },
                        label = { Text(text = stringResource(R.string.volume_threshold)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // State triggers with their own labels (power saver, Wi-Fi, NFC…).
                TriggerType.POWER_SAVER, TriggerType.BLUETOOTH_STATE, TriggerType.AUTO_ROTATE,
                TriggerType.DATA_SAVER_STATE, TriggerType.WIFI_STATE, TriggerType.NFC_STATE -> {
                    val labelRes = ON_OFF_TRIGGER_LABELS[draft.type] ?: R.string.trigger_state_label
                    Text(text = stringResource(labelRes), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("ON", "OFF"),
                        labels = mapOf(
                            "ON" to stringResource(R.string.state_on),
                            "OFF" to stringResource(R.string.state_off)
                        ),
                        selected = draft.config["state"] ?: "ON",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                    )
                }
                TriggerType.BRIGHTNESS_LEVEL -> {
                    Text(text = stringResource(R.string.trigger_brightness_direction), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("ABOVE", "BELOW"),
                        labels = mapOf(
                            "ABOVE" to stringResource(R.string.volume_above),
                            "BELOW" to stringResource(R.string.volume_below)
                        ),
                        selected = draft.config["direction"] ?: "ABOVE",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
                    )
                    OutlinedTextField(
                        value = draft.config["threshold"] ?: "128",
                        onValueChange = { v ->
                            onConfigChange(draft.copy(config = draft.config + ("threshold" to v.filter { it.isDigit() })))
                        },
                        label = { Text(text = stringResource(R.string.trigger_brightness_threshold)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TriggerType.STORAGE_LOW -> {
                    Text(text = stringResource(R.string.trigger_storage_direction), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("BELOW", "ABOVE"),
                        labels = mapOf(
                            "BELOW" to stringResource(R.string.storage_below),
                            "ABOVE" to stringResource(R.string.storage_above)
                        ),
                        selected = draft.config["direction"] ?: "BELOW",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
                    )
                    OutlinedTextField(
                        value = draft.config["threshold"] ?: "1024",
                        onValueChange = { v ->
                            onConfigChange(draft.copy(config = draft.config + ("threshold" to v.filter { it.isDigit() })))
                        },
                        label = { Text(text = stringResource(R.string.trigger_storage_threshold)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TriggerType.DEVICE_LOCKED -> {
                    Text(text = stringResource(R.string.trigger_device_locked_state), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("LOCKED", "UNLOCKED"),
                        labels = mapOf(
                            "LOCKED" to stringResource(R.string.device_locked),
                            "UNLOCKED" to stringResource(R.string.device_unlocked)
                        ),
                        selected = draft.config["state"] ?: "LOCKED",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                    )
                }
                TriggerType.LOCATION_STATE -> {
                    Text(text = stringResource(R.string.trigger_location_mode), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("OFF", "SENSORS", "BATTERY", "HIGH"),
                        labels = mapOf(
                            "OFF" to stringResource(R.string.location_mode_off),
                            "SENSORS" to stringResource(R.string.location_mode_sensors),
                            "BATTERY" to stringResource(R.string.location_mode_battery),
                            "HIGH" to stringResource(R.string.location_mode_high)
                        ),
                        selected = draft.config["mode"] ?: "HIGH",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("mode" to it))) }
                    )
                }
                TriggerType.SCREEN_ROTATION_STATE -> {
                    Text(text = stringResource(R.string.trigger_rotation_state), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("PORTRAIT", "LANDSCAPE"),
                        labels = mapOf(
                            "PORTRAIT" to stringResource(R.string.rotation_portrait),
                            "LANDSCAPE" to stringResource(R.string.rotation_landscape)
                        ),
                        selected = draft.config["state"] ?: "PORTRAIT",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                    )
                }
                TriggerType.WIFI_SIGNAL_STRENGTH -> {
                    Text(text = stringResource(R.string.trigger_signal_threshold), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("1", "2", "3", "4"),
                        selected = draft.config["threshold"] ?: "3",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("threshold" to it))) }
                    )
                    OptionChips(
                        options = listOf("ABOVE", "BELOW"),
                        labels = mapOf(
                            "ABOVE" to stringResource(R.string.volume_above),
                            "BELOW" to stringResource(R.string.volume_below)
                        ),
                        selected = draft.config["direction"] ?: "ABOVE",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
                    )
                }
                TriggerType.CELL_SIGNAL_STRENGTH -> {
                    Text(text = stringResource(R.string.trigger_signal_threshold), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("1", "2", "3", "4"),
                        selected = draft.config["threshold"] ?: "3",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("threshold" to it))) }
                    )
                    OptionChips(
                        options = listOf("ABOVE", "BELOW"),
                        labels = mapOf(
                            "ABOVE" to stringResource(R.string.volume_above),
                            "BELOW" to stringResource(R.string.volume_below)
                        ),
                        selected = draft.config["direction"] ?: "ABOVE",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
                    )
                }
                TriggerType.BATTERY_TEMPERATURE -> {
                    Text(text = stringResource(R.string.trigger_temperature_threshold), style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = draft.config["threshold"] ?: "40",
                        onValueChange = { onConfigChange(draft.copy(config = draft.config + ("threshold" to it))) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.trigger_temperature_threshold)) }
                    )
                    OptionChips(
                        options = listOf("ABOVE", "BELOW"),
                        labels = mapOf(
                            "ABOVE" to stringResource(R.string.volume_above),
                            "BELOW" to stringResource(R.string.volume_below)
                        ),
                        selected = draft.config["direction"] ?: "ABOVE",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
                    )
                }
                // Simple ON/OFF state triggers (generic "State" title).
                TriggerType.USB_CONNECTED, TriggerType.HDMI_CONNECTED,
                TriggerType.ETHERNET_CONNECTED, TriggerType.VPN_CONNECTED,
                TriggerType.DND_STATE, TriggerType.STAY_AWAKE_STATE,
                TriggerType.AUTO_BRIGHTNESS_STATE, TriggerType.DATA_ROAMING_STATE -> {
                    Text(text = stringResource(R.string.trigger_state_label), style = MaterialTheme.typography.titleSmall)
                    OptionChips(
                        options = listOf("ON", "OFF"),
                        labels = mapOf(
                            "ON" to stringResource(R.string.state_on),
                            "OFF" to stringResource(R.string.state_off)
                        ),
                        selected = draft.config["state"] ?: "ON",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                    )
                }
                TriggerType.CLIPBOARD_CHANGED ->
                    Text(text = stringResource(R.string.trigger_desc_clipboard), style = MaterialTheme.typography.bodyMedium)
                TriggerType.SCREEN_TIMEOUT_CHANGED ->
                    Text(text = stringResource(R.string.trigger_desc_screen_timeout), style = MaterialTheme.typography.bodyMedium)
                TriggerType.TIMEZONE_CHANGED ->
                    Text(text = stringResource(R.string.trigger_desc_timezone), style = MaterialTheme.typography.bodyMedium)
                TriggerType.BOOT_COMPLETED ->
                    Text(text = stringResource(R.string.trigger_desc_boot), style = MaterialTheme.typography.bodyMedium)
                TriggerType.NFC_TAG_SCANNED ->
                    Text(text = stringResource(R.string.trigger_desc_nfc_tag), style = MaterialTheme.typography.bodyMedium)
                TriggerType.ALARM_SET_CHANGED ->
                    Text(text = stringResource(R.string.trigger_desc_alarm_set), style = MaterialTheme.typography.bodyMedium)
                // External component identity and approval are managed only by
                // the plugin configuration flow. A persisted trigger stays
                // visible but cannot be changed to an unsafe partial config.
                TriggerType.PLUGIN_EVENT ->
                    Text(text = stringResource(R.string.plugin_no_edit), style = MaterialTheme.typography.bodyMedium)
            }
            }
        }
    }

    if (showTimePicker) {
        val currentTime = when (timePickerTarget) {
            "rangeStart" -> draft.config["rangeStart"] ?: "08:00"
            "rangeEnd" -> draft.config["rangeEnd"] ?: "18:00"
            else -> draft.config["time"] ?: "08:00"
        }
        TimePickerAlert(
            initialTime = currentTime,
            onConfirm = {
                onConfigChange(draft.copy(config = draft.config + (timePickerTarget to it)))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    // The picker state is (re)created fresh on every open because the dialog
    // leaves composition whenever datePickerTarget resets to null on dismiss.
    datePickerTarget?.let { target ->
        val initialMillis = draft.config[target]?.let(::parseDateMillis)
            ?: System.currentTimeMillis()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onConfigChange(
                            draft.copy(config = draft.config + (target to millisToDateString(millis)))
                        )
                    }
                    datePickerTarget = null
                }) {
                    Text(text = stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerTarget = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun parseTimeMinutes(value: String): Int {
    val parts = value.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}
