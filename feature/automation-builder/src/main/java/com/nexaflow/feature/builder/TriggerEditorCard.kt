package com.nexaflow.feature.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.rememberCoroutineScope
import com.nexaflow.core.engine.currentCellularGeneration
import com.nexaflow.core.rom.EvolutionXSettingsBridge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val LOCATION_RADIUS_MIN_M = 50
private const val LOCATION_RADIUS_MAX_M = 2000
// (max - min) / step - 1: 50 m granularity between the endpoints.
private const val LOCATION_RADIUS_STEPS = (LOCATION_RADIUS_MAX_M - LOCATION_RADIUS_MIN_M) / 50 - 1

val triggerTypeOptions = listOf(
    TriggerType.TIME,
    TriggerType.BATTERY,
    TriggerType.APPLICATION,
    TriggerType.DEVICE,
    TriggerType.CONNECTIVITY,
    TriggerType.NETWORK_MODE,
    TriggerType.LOCATION,
    TriggerType.SMS,
    TriggerType.RINGER_MODE,
    TriggerType.NOTIFICATION,
    TriggerType.CALENDAR,
    TriggerType.SENSOR,
    TriggerType.WEBHOOK,
    TriggerType.ROM_SETTING
)

private val repeatOptions = listOf(
    "ONCE" to R.string.repeat_once,
    "DAILY" to R.string.repeat_daily,
    "SPECIFIC_DAYS" to R.string.repeat_specific_days,
    "MONTHLY" to R.string.repeat_monthly,
    "MONTHLY_WEEKDAY" to R.string.repeat_monthly_weekday,
    "DATE_RANGE" to R.string.repeat_date_range
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
    TriggerType.NETWORK_MODE -> mapOf("state" to "4G")
    TriggerType.LOCATION -> mapOf("lat" to "", "lng" to "", "radius" to "100", "event" to "ENTER")
    TriggerType.SMS -> mapOf("from" to "", "contains" to "", "reply" to "")
    TriggerType.BLUETOOTH_DEVICE -> mapOf("deviceName" to "", "deviceAddress" to "", "event" to "CONNECTED")
    TriggerType.RINGER_MODE -> mapOf("mode" to "NORMAL")
    TriggerType.NOTIFICATION -> mapOf("packages" to "", "contains" to "", "event" to "POSTED")
    TriggerType.CALENDAR -> mapOf("calendar" to "", "contains" to "", "event" to "EVENT_START", "beforeMinutes" to "0")
    TriggerType.SENSOR -> mapOf("sensor" to "PROXIMITY", "event" to "COVERED", "threshold" to "200", "sensitivity" to "14")
    TriggerType.WEBHOOK -> mapOf("path" to "/nexaflow", "method" to "POST", "token" to "")
    TriggerType.ROM_SETTING -> mapOf("namespace" to "SYSTEM", "key" to "", "operator" to "EQUALS", "value" to "")
}

internal fun TriggerType.labelRes(): Int = when (this) {
    TriggerType.TIME -> R.string.trigger_type_time
    TriggerType.BATTERY -> R.string.trigger_type_battery
    TriggerType.APPLICATION -> R.string.trigger_type_app
    TriggerType.DEVICE -> R.string.trigger_type_device
    TriggerType.CONNECTIVITY -> R.string.trigger_type_connectivity
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
}

internal fun TriggerType.icon(): ImageVector = when (this) {
    TriggerType.TIME -> Icons.Filled.Schedule
    TriggerType.BATTERY -> Icons.Filled.BatteryChargingFull
    TriggerType.APPLICATION -> Icons.Filled.Apps
    TriggerType.DEVICE -> Icons.Filled.Bolt
    TriggerType.CONNECTIVITY -> Icons.Filled.Wifi
    TriggerType.NETWORK_MODE -> Icons.Filled.SignalCellularAlt
    TriggerType.LOCATION -> Icons.Filled.Place
    TriggerType.SMS -> Icons.AutoMirrored.Filled.Message
    TriggerType.BLUETOOTH_DEVICE -> Icons.Filled.Bluetooth
    TriggerType.RINGER_MODE -> Icons.Filled.NotificationsActive
    TriggerType.NOTIFICATION -> Icons.Filled.NotificationsActive
    TriggerType.CALENDAR -> Icons.Filled.DateRange
    TriggerType.SENSOR -> Icons.Filled.Sensors
    TriggerType.WEBHOOK -> Icons.Filled.Web
    TriggerType.ROM_SETTING -> Icons.Filled.Bolt
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
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
 * Shared repeat schedule (once / daily / specific days / specific date / date range).
 * Used by both the single-time and time-range modes so every time trigger can
 * choose exactly how often it runs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeRepeatSection(
    draft: TriggerDraft,
    onConfigChange: (TriggerDraft) -> Unit,
    onPickDate: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.repeat_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeatOptions.forEach { (value, labelRes) ->
                SelectChip(
                    selected = (draft.config["repeat"] ?: "DAILY") == value,
                    onClick = {
                        onConfigChange(draft.copy(config = draft.config + ("repeat" to value)))
                    },
                    label = stringResource(labelRes)
                )
            }
        }
        RepeatSummary(draft = draft)
        when (draft.config["repeat"] ?: "DAILY") {
            "SPECIFIC_DAYS" -> {
                Text(
                    text = stringResource(R.string.select_days),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    weekdayOptions.forEach { (day, labelRes) ->
                        val selectedDays = draft.config["days"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
                        SelectChip(
                            selected = day in selectedDays,
                            onClick = {
                                val updated = if (day in selectedDays) selectedDays - day else selectedDays + day
                                onConfigChange(draft.copy(config = draft.config + ("days" to updated.sorted().joinToString(","))))
                            },
                            label = stringResource(labelRes),
                            showCheck = false
                        )
                    }
                }
            }
            "MONTHLY" -> {
                val monthDay = (draft.config["monthDay"] ?: "1").toIntOrNull() ?: 1
                SliderRow(
                    label = stringResource(R.string.month_day_label, monthDay),
                    value = monthDay.toFloat(),
                    onValueChange = { value ->
                        onConfigChange(draft.copy(config = draft.config + ("monthDay" to value.toInt().toString())))
                    },
                    valueRange = 1f..28f
                )
            }
            "MONTHLY_WEEKDAY" -> {
                val weekday = (draft.config["weekday"] ?: "1").toIntOrNull() ?: 1
                val occurrence = draft.config["weekOfMonth"] ?: "1"
                Text(
                    text = stringResource(R.string.select_days),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    weekdayOptions.forEach { (day, labelRes) ->
                        SelectChip(
                            selected = weekday == day,
                            onClick = {
                                onConfigChange(draft.copy(config = draft.config + ("weekday" to day.toString())))
                            },
                            label = stringResource(labelRes),
                            showCheck = false
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.week_of_month_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    occurrenceOptions.forEach { (value, labelRes) ->
                        SelectChip(
                            selected = occurrence == value,
                            onClick = {
                                onConfigChange(draft.copy(config = draft.config + ("weekOfMonth" to value)))
                            },
                            label = stringResource(labelRes)
                        )
                    }
                }
            }
            "DATE_RANGE" -> {
                DateField(
                    label = stringResource(R.string.start_date),
                    value = draft.config["startDate"] ?: "",
                    onClick = { onPickDate("startDate") }
                )
                DateField(
                    label = stringResource(R.string.end_date),
                    value = draft.config["endDate"] ?: "",
                    onClick = { onPickDate("endDate") }
                )
                val start = draft.config["startDate"]?.let(::parseDateMillis)
                val end = draft.config["endDate"]?.let(::parseDateMillis)
                if (start != null && end != null && start > end) {
                    Text(
                        text = stringResource(R.string.date_range_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** Live "Selected: …" line that makes the current repeat choice unambiguous. */
@Composable
private fun RepeatSummary(draft: TriggerDraft) {
    val config = draft.config
    val text = when (config["repeat"] ?: "DAILY") {
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
        "DATE_RANGE" -> {
            val start = config["startDate"] ?: ""
            val end = config["endDate"] ?: ""
            if (start.isEmpty() && end.isEmpty()) stringResource(R.string.repeat_date_range)
            else "$start → $end"
        }
        else -> stringResource(R.string.repeat_daily)
    }
    Text(
        text = stringResource(R.string.repeat_selected, text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TriggerEditorCard(
    draft: TriggerDraft,
    index: Int,
    onConfigChange: (TriggerDraft) -> Unit,
    onRemove: () -> Unit,
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
    // Always expanded: the card shows the trigger type plus the type picker
    // and the ordered options for that type. The task card never collapses.
    NexaFlowCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = draft.type.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.trigger_n, index + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = stringResource(draft.type.labelRes()),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.remove_trigger),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                triggerTypeOptions.forEach { option ->
                    SelectChip(
                        selected = draft.type == option,
                        onClick = {
                            // Keep values that already exist for this type (e.g. the chosen
                            // time) while filling in defaults for the newly selected type.
                            val defaults = defaultTriggerConfig(option)
                            onConfigChange(
                                TriggerDraft(
                                    type = option,
                                    config = defaults + draft.config.filterKeys { it in defaults.keys }
                                )
                            )
                        },
                        label = stringResource(option.labelRes()),
                        leadingIcon = option.icon()
                    )
                }
            }
            when (draft.type) {
                TriggerType.TIME -> {
                    val rangeMode = draft.config["timeMode"] == "RANGE"
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
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
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val threshold = (draft.config["above"] ?: "80").toIntOrNull() ?: 80
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BatteryChargingFull,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.battery_trigger_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
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
                            fontWeight = FontWeight.SemiBold,
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
                            fontWeight = FontWeight.SemiBold,
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.trigger_app),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
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
                        Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bluetooth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.trigger_bluetooth),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
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
                            Text(text = stringResource(R.string.state), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                TriggerType.CONNECTIVITY -> {
                    val network = draft.config["network"] ?: "WIFI"
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.network), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        // WIFI / MOBILE / hotspot ON-OFF / cellular network mode
                        // (2G/3G/4G/5G) — each with its own state vocabulary.
                        OptionChips(
                            options = listOf("WIFI", "MOBILE", "HOTSPOT", "NETWORK_MODE"),
                            labels = mapOf(
                                "WIFI" to stringResource(R.string.network_wifi),
                                "MOBILE" to stringResource(R.string.network_mobile),
                                "HOTSPOT" to stringResource(R.string.network_hotspot),
                                "NETWORK_MODE" to stringResource(R.string.network_mode)
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
                        Text(text = stringResource(R.string.state), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                    var currentGen by remember { mutableStateOf(currentCellularGeneration(context)) }
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
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = currentGen ?: stringResource(R.string.network_mode_unknown),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { currentGen = currentCellularGeneration(context) }) {
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
                        Text(
                            text = stringResource(R.string.network_mode_fire_when),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
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
                        // Two ways to set the location: current position or map.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onUseCurrentLocation,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Filled.MyLocation, contentDescription = null)
                                Text(
                                    text = stringResource(R.string.use_current_location),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = onPickFromMap,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Filled.Map, contentDescription = null)
                                Text(
                                    text = stringResource(R.string.pick_on_map),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                        // Read-only summary of the chosen point (replaces the
                        // old manual latitude/longitude text fields).
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
                        // Radius slider.
                        val radius = (draft.config["radius"]?.toIntOrNull() ?: 100)
                            .coerceIn(LOCATION_RADIUS_MIN_M, LOCATION_RADIUS_MAX_M)
                        Text(
                            text = stringResource(R.string.radius_meters_format, radius),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
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
                        // Inside / outside the defined location.
                        val event = draft.config["event"] ?: "ENTER"
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = event == "ENTER",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("event" to "ENTER")))
                                },
                                label = { Text(text = stringResource(R.string.location_inside)) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = event == "EXIT",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("event" to "EXIT")))
                                },
                                label = { Text(text = stringResource(R.string.location_outside)) },
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.trigger_ringer),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.trigger_ringer_sub),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Text(text = stringResource(R.string.ringer_mode_label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bluetooth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.trigger_bluetooth),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
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
                        Text(text = stringResource(R.string.state), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.trigger_calendar),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
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
                        Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
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
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
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
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
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
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
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
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
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
                                        EvolutionXSettingsBridge.listCustomKeys()
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
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
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
                        AlertDialog(
                            onDismissRequest = { showKeyPicker = false },
                            title = { Text(text = stringResource(R.string.rom_setting_pick_title)) },
                            text = {
                                if (liveKeys.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.rom_setting_pick_empty),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.heightIn(max = 360.dp)
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
                                                    .padding(vertical = 10.dp, horizontal = 4.dp),
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
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showKeyPicker = false }) {
                                    Text(text = stringResource(R.string.cancel))
                                }
                            }
                        )
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.trigger_notification),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
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
                        Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
