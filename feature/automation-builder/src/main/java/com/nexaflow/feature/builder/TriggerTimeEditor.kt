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

internal fun parseDateMillis(value: String): Long? {
    return runCatching {
        LocalDate.parse(value)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

internal fun millisToDateString(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()
}

/** Guided tappable field that opens a Material3 date picker. */
@Composable
internal fun DateField(
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

/** Guided tappable row that opens the time picker. */
@Composable
internal fun TimeField(
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
internal fun TimeRepeatSection(
    draft: TriggerDraft,
    onConfigChange: (TriggerDraft) -> Unit,
    onPickDate: (String) -> Unit,
    onPickExcludedDate: () -> Unit
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
            val excludedDates = intervalConfig["excludedDates"].orEmpty().split(',')
                .map(String::trim).filter(String::isNotBlank).distinct().sorted()
            Text(text = stringResource(R.string.time_exclusions_title), style = MaterialTheme.typography.titleSmall)
            Text(text = stringResource(R.string.time_exclusions_hint), style = MaterialTheme.typography.bodySmall)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                excludedDates.forEach { date ->
                    SelectChip(
                        selected = false,
                        onClick = {
                            val updated = excludedDates.filterNot { it == date }
                            val config = if (updated.isEmpty()) intervalConfig - "excludedDates"
                            else intervalConfig + ("excludedDates" to updated.joinToString(","))
                            onConfigChange(draft.copy(config = config))
                        },
                        label = "$date ×"
                    )
                }
            }
            OutlinedButton(
                onClick = onPickExcludedDate,
                enabled = excludedDates.size < 64,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.time_exclusions_add))
            }
        }
        RepeatSummary(draft = draft)
    }
}

internal fun intervalConfig(config: Map<String, String>): Map<String, String> {
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
internal fun repeatLabel(draft: TriggerDraft): String {
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
internal fun RepeatSummary(draft: TriggerDraft) {
    Text(
        text = stringResource(R.string.repeat_selected, repeatLabel(draft)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium
    )
}

