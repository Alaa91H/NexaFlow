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

/** One-line summary of the chosen trigger values for the collapsed card header. */
@Composable
internal fun triggerSummary(draft: TriggerDraft): String {
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
        TriggerType.WIFI_CONNECTED, TriggerType.MOBILE_DATA_CONNECTED, TriggerType.CONNECTIVITY -> {
            val network = c["network"] ?: when (draft.type) {
                TriggerType.MOBILE_DATA_CONNECTED -> "MOBILE"
                else -> "WIFI"
            }
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
                network == "HOTSPOT" -> if (state == "ON") stringResource(R.string.builder_state_on) else stringResource(R.string.builder_state_off)
                state == "CONNECTED" -> stringResource(R.string.state_connected)
                else -> stringResource(R.string.state_disconnected)
            }
            val wifiFilters = if (draft.type == TriggerType.WIFI_CONNECTED && state == "CONNECTED") {
                listOf(
                    "validated" to stringResource(R.string.network_validated),
                    "captivePortal" to stringResource(R.string.network_captive_portal),
                    "metered" to stringResource(R.string.network_metered)
                ).mapNotNull { (key, label) ->
                    val filter = c[key]?.uppercase()?.takeIf { it == "YES" || it == "NO" }
                        ?: return@mapNotNull null
                    val value = if (filter == "YES") {
                        stringResource(R.string.builder_state_on)
                    } else {
                        stringResource(R.string.builder_state_off)
                    }
                    "$label: $value"
                } + listOfNotNull(
                    c["ssid"]?.trim()?.takeIf(String::isNotEmpty)?.let { "${stringResource(R.string.network_ssid)}: $it" },
                    c["bssid"]?.trim()?.takeIf(String::isNotEmpty)?.let { "${stringResource(R.string.network_bssid)}: $it" }
                )
            } else {
                emptyList()
            }
            (listOf("$networkLabel · $stateLabel") + wifiFilters).joinToString(" · ")
        }
        TriggerType.HOTSPOT -> {
            val state = if ((c["state"] ?: "ON") == "ON") {
                stringResource(R.string.builder_state_on)
            } else {
                stringResource(R.string.builder_state_off)
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
            val mode = (c["matchMode"] ?: "CONTAINS").trim().uppercase()
            when {
                from.isNotEmpty() -> "${stringResource(R.string.sms_from)}: $from"
                contains.isNotEmpty() && mode == "EXACT" ->
                    "${stringResource(R.string.sms_match_exact)}: $contains"
                contains.isNotEmpty() -> "${stringResource(R.string.sms_contains)}: $contains"
                else -> stringResource(R.string.trigger_type_sms)
            }
        }
        TriggerType.INCOMING_CALL -> {
            val from = (c["from"] ?: "").trim()
            val category = (c["category"] ?: "ANY").trim().uppercase()
            when {
                from.isNotEmpty() -> "${stringResource(R.string.sms_from)}: $from"
                category == "UNKNOWN" -> stringResource(R.string.call_category_unknown)
                category == "PRIVATE" -> stringResource(R.string.call_category_private)
                category == "CONTACT" -> stringResource(R.string.call_category_contact)
                else -> stringResource(R.string.trigger_type_incoming_call)
            }
        }
        TriggerType.RINGER_MODE -> when (c["mode"] ?: "NORMAL") {
            "VIBRATE" -> stringResource(R.string.ringer_vibrate)
            "SILENT" -> stringResource(R.string.ringer_silent)
            else -> stringResource(R.string.ringer_normal)
        }
        TriggerType.BLUETOOTH_DEVICE -> {
            val rawName = c["deviceName"] ?: ""
            val name = when {
                rawName.isBlank() || rawName == "__ANY__" || rawName == "*" || rawName.equals("ANY", ignoreCase = true) -> stringResource(R.string.any_bluetooth_device)
                else -> rawName
            }
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
            if ((c["state"] ?: "ON") == "ON") stringResource(R.string.builder_state_on)
            else stringResource(R.string.builder_state_off)
        TriggerType.DARK_MODE ->
            if ((c["state"] ?: "ON") == "ON") stringResource(R.string.builder_state_on)
            else stringResource(R.string.builder_state_off)
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
            if ((c["state"] ?: "ON") == "ON") stringResource(R.string.builder_state_on)
            else stringResource(R.string.builder_state_off)
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
        TriggerType.LOCATION_STATE -> when ((c["mode"] ?: "ON").uppercase()) {
            "OFF" -> stringResource(R.string.builder_state_off)
            "ON", "HIGH", "SENSORS", "BATTERY" -> stringResource(R.string.builder_state_on)
            else -> stringResource(R.string.builder_state_on)
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
        TriggerType.CLIPBOARD_CHANGED ->
            c["contains"]?.takeIf { it.isNotBlank() } ?: stringResource(R.string.trigger_events_on_change)
        TriggerType.SCREEN_TIMEOUT_CHANGED ->
            c["seconds"]?.toIntOrNull()?.let { stringResource(R.string.timeout_label, it) }
                ?: stringResource(R.string.trigger_events_on_change)
        TriggerType.TIMEZONE_CHANGED ->
            c["zone"]?.takeIf { it.isNotBlank() } ?: stringResource(R.string.trigger_events_on_change)
        TriggerType.BOOT_COMPLETED -> stringResource(R.string.trigger_events_on_change)
        TriggerType.NFC_TAG_SCANNED ->
            c["contains"]?.takeIf { it.isNotBlank() } ?: stringResource(R.string.trigger_events_on_change)
        TriggerType.ALARM_SET_CHANGED ->
            c["event"]?.takeIf { it.isNotBlank() } ?: stringResource(R.string.trigger_events_on_change)
        TriggerType.WEAR_EVENT -> {
            val state = if ((c["state"] ?: "CONNECTED") == "CONNECTED") {
                stringResource(R.string.state_connected)
            } else {
                stringResource(R.string.state_disconnected)
            }
            c["watchInstallId"]?.takeIf { it.isNotBlank() }?.let { "$state · $it" } ?: state
        }
        TriggerType.PLUGIN_EVENT -> stringResource(R.string.action_plugin)
    }
}

