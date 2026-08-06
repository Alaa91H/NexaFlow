package com.nexaflow.feature.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.domain.models.TriggerType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

val triggerTypeOptions = listOf(
    TriggerType.TIME,
    TriggerType.BATTERY,
    TriggerType.APPLICATION,
    TriggerType.DEVICE,
    TriggerType.CONNECTIVITY,
    TriggerType.LOCATION,
    TriggerType.SMS,
    TriggerType.BLUETOOTH_DEVICE,
    TriggerType.RINGER_MODE,
    TriggerType.NOTIFICATION
)

private val repeatOptions = listOf(
    "ONCE" to R.string.repeat_once,
    "DAILY" to R.string.repeat_daily,
    "WEEKDAYS" to R.string.repeat_weekdays,
    "WEEKENDS" to R.string.repeat_weekends,
    "SPECIFIC_DAYS" to R.string.repeat_specific_days,
    "MONTHLY" to R.string.repeat_monthly,
    "SPECIFIC_DATE" to R.string.repeat_specific_date,
    "DATE_RANGE" to R.string.repeat_date_range
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
    TriggerType.BATTERY -> mapOf("direction" to "ABOVE", "above" to "80")
    TriggerType.APPLICATION -> mapOf("packages" to "")
    TriggerType.DEVICE -> mapOf("event" to "SCREEN_ON")
    TriggerType.CONNECTIVITY -> mapOf("network" to "WIFI", "state" to "CONNECTED")
    TriggerType.LOCATION -> mapOf("lat" to "", "lng" to "", "radius" to "100", "event" to "ENTER")
    TriggerType.SMS -> mapOf("from" to "", "contains" to "", "reply" to "")
    TriggerType.BLUETOOTH_DEVICE -> mapOf("deviceName" to "", "deviceAddress" to "", "event" to "CONNECTED")
    TriggerType.RINGER_MODE -> mapOf("mode" to "NORMAL")
    TriggerType.NOTIFICATION -> mapOf("packages" to "", "contains" to "", "event" to "POSTED")
}

internal fun TriggerType.labelRes(): Int = when (this) {
    TriggerType.TIME -> R.string.trigger_type_time
    TriggerType.BATTERY -> R.string.trigger_type_battery
    TriggerType.APPLICATION -> R.string.trigger_type_app
    TriggerType.DEVICE -> R.string.trigger_type_device
    TriggerType.CONNECTIVITY -> R.string.trigger_type_connectivity
    TriggerType.LOCATION -> R.string.trigger_type_location
    TriggerType.SMS -> R.string.trigger_type_sms
    TriggerType.BLUETOOTH_DEVICE -> R.string.trigger_type_bluetooth
    TriggerType.RINGER_MODE -> R.string.trigger_type_ringer
    TriggerType.NOTIFICATION -> R.string.trigger_type_notification
}

internal fun TriggerType.icon(): ImageVector = when (this) {
    TriggerType.TIME -> Icons.Filled.Schedule
    TriggerType.BATTERY -> Icons.Filled.BatteryChargingFull
    TriggerType.APPLICATION -> Icons.Filled.Apps
    TriggerType.DEVICE -> Icons.Filled.Bolt
    TriggerType.CONNECTIVITY -> Icons.Filled.Wifi
    TriggerType.LOCATION -> Icons.Filled.Place
    TriggerType.SMS -> Icons.AutoMirrored.Filled.Message
    TriggerType.BLUETOOTH_DEVICE -> Icons.Filled.Bluetooth
    TriggerType.RINGER_MODE -> Icons.Filled.NotificationsActive
    TriggerType.NOTIFICATION -> Icons.Filled.NotificationsActive
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
                FilterChip(
                    selected = (draft.config["repeat"] ?: "DAILY") == value,
                    onClick = {
                        onConfigChange(draft.copy(config = draft.config + ("repeat" to value)))
                    },
                    label = { Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
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
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = {
                                val updated = if (day in selectedDays) selectedDays - day else selectedDays + day
                                onConfigChange(draft.copy(config = draft.config + ("days" to updated.sorted().joinToString(","))))
                            },
                            label = { Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelMedium) }
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
            "SPECIFIC_DATE" -> {
                DateField(
                    label = stringResource(R.string.specific_date),
                    value = draft.config["date"] ?: "",
                    onClick = { onPickDate("date") }
                )
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TriggerEditorCard(
    draft: TriggerDraft,
    index: Int,
    onConfigChange: (TriggerDraft) -> Unit,
    onRemove: () -> Unit,
    onPickApp: () -> Unit,
    onPickFromMap: () -> Unit = {},
    onPickBluetooth: () -> Unit = {}
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    var timePickerTarget by remember { mutableStateOf("time") } // "time" | "rangeStart" | "rangeEnd"
    var datePickerTarget by remember { mutableStateOf<String?>(null) } // "date" | "startDate" | "endDate"

    NexaFlowCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.trigger_n, index + 1),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
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
                    FilterChip(
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
                        leadingIcon = {
                            Icon(
                                imageVector = option.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (draft.type == option) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                            )
                        },
                        label = { Text(text = stringResource(option.labelRes())) }
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
                            FilterChip(
                                selected = !rangeMode,
                                onClick = {
                                    onConfigChange(
                                        draft.copy(
                                            config = draft.config + ("timeMode" to "SINGLE")
                                        )
                                    )
                                },
                                label = { Text(text = stringResource(R.string.time_single)) }
                            )
                            FilterChip(
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
                                label = { Text(text = stringResource(R.string.time_range)) }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = direction == "ABOVE",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("direction" to "ABOVE")))
                                },
                                label = { Text(text = stringResource(R.string.battery_above)) }
                            )
                            FilterChip(
                                selected = direction == "BELOW",
                                onClick = {
                                    onConfigChange(draft.copy(config = draft.config + ("direction" to "BELOW")))
                                },
                                label = { Text(text = stringResource(R.string.battery_below)) }
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
                        PermissionHint(
                            text = stringResource(R.string.app_detection_hint),
                            buttonLabel = stringResource(R.string.enable),
                            onClick = { PermissionShortcuts.openAccessibilitySettings(context) }
                        )
                    }
                }
                TriggerType.DEVICE -> {
                    OptionChips(
                        options = listOf(
                            "SCREEN_ON",
                            "SCREEN_OFF",
                            "POWER_CONNECTED",
                            "POWER_DISCONNECTED",
                            "HEADSET_CONNECTED",
                            "HEADSET_DISCONNECTED"
                        ),
                        selected = draft.config["event"] ?: "SCREEN_ON",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                    )
                }
                TriggerType.CONNECTIVITY -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.network), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        OptionChips(
                            options = listOf("WIFI", "MOBILE"),
                            selected = draft.config["network"] ?: "WIFI",
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("network" to it))) }
                        )
                        Text(text = stringResource(R.string.state), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        OptionChips(
                            options = listOf("CONNECTED", "DISCONNECTED"),
                            selected = draft.config["state"] ?: "CONNECTED",
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                        )
                    }
                }
                TriggerType.LOCATION -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onPickFromMap) {
                            Icon(imageVector = Icons.Filled.Map, contentDescription = null)
                            Text(text = stringResource(R.string.pick_on_map), modifier = Modifier.padding(start = 4.dp))
                        }
                        OutlinedTextField(
                            value = draft.config["lat"] ?: "",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("lat" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.latitude)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = draft.config["lng"] ?: "",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("lng" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.longitude)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = draft.config["radius"] ?: "100",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("radius" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.radius_meters)) },
                            singleLine = true
                        )
                        Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        OptionChips(
                            options = listOf("ENTER", "EXIT"),
                            selected = draft.config["event"] ?: "ENTER",
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                        )
                        PermissionHint(
                            text = stringResource(R.string.location_hint),
                            buttonLabel = stringResource(R.string.grant),
                            onClick = { PermissionShortcuts.openAppSettings(context) }
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
                        OutlinedTextField(
                            value = draft.config["reply"] ?: "",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("reply" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.sms_reply)) },
                            placeholder = { Text(text = stringResource(R.string.sms_reply_hint)) },
                            singleLine = true
                        )
                        PermissionHint(
                            text = stringResource(R.string.sms_permission_hint),
                            buttonLabel = stringResource(R.string.grant),
                            onClick = { PermissionShortcuts.openAppSettings(context) }
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
                                FilterChip(
                                    selected = mode == value,
                                    onClick = { onConfigChange(draft.copy(config = draft.config + ("mode" to value))) },
                                    label = { Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelMedium) }
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
                        PermissionHint(
                            text = stringResource(R.string.bluetooth_permission_hint),
                            buttonLabel = stringResource(R.string.enable),
                            onClick = { PermissionShortcuts.openBluetoothSettings(context) }
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
                        PermissionHint(
                            text = stringResource(R.string.notification_access_hint),
                            buttonLabel = stringResource(R.string.enable),
                            onClick = { PermissionShortcuts.openNotificationAccessSettings(context) }
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
