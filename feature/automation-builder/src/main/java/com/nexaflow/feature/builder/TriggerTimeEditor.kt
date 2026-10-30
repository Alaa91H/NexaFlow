package com.nexaflow.feature.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.nexaflow.core.ui.SelectChip
import com.nexaflow.domain.schedule.TimeTriggerCalculator
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

/**
 * The TIME trigger body: single time or overnight range, exact-alarm status,
 * recurrence, and the explicit time zone the schedule is computed in.
 *
 * Extracted from `TriggerEditorCard` so the card stays a dispatcher and the
 * zone contract lives next to the other schedule editing surfaces.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimeTriggerConfig(
    draft: TriggerDraft,
    onConfigChange: (TriggerDraft) -> Unit,
    onPickTime: (String) -> Unit,
    onPickDate: (String) -> Unit,
    refreshKey: Int = 0,
    onExplainSpecial: (SpecialPermission) -> Unit = {}
) {
    val context = LocalContext.current
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
                    onConfigChange(draft.copy(config = draft.config + ("timeMode" to "SINGLE")))
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
                onClick = { onPickTime("rangeStart") }
            )
            TimeField(
                label = stringResource(R.string.time_range_end),
                value = end,
                onClick = { onPickTime("rangeEnd") }
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
                    .clickable { onPickTime("time") }
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
                TextButton(onClick = { onPickTime("time") }) {
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
            onPickDate = onPickDate,
            onPickExcludedDate = { onPickDate("excludedDate") }
        )
        TimeZoneSection(draft = draft, onConfigChange = onConfigChange)
    }
}

/**
 * Explicit time-zone control for the schedule.
 *
 * The schema and the calculator always supported a fixed IANA zone, but no
 * builder surface wrote `zonePolicy`/`zoneId`, so every task scheduled in the
 * device zone. A fixed zone is written together with its canonical `timezone`
 * mirror and only when it parses: an invalid zone would make the calculator
 * fail closed and the task would never fire.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimeZoneSection(
    draft: TriggerDraft,
    onConfigChange: (TriggerDraft) -> Unit
) {
    val storedZone = draft.config["zoneId"] ?: draft.config["timezone"] ?: ""
    val storedFixed = (draft.config["zonePolicy"] ?: if (storedZone.isNotBlank()) {
        TimeTriggerCalculator.ZONE_POLICY_FIXED_IANA
    } else {
        TimeTriggerCalculator.ZONE_POLICY_DEVICE_LOCAL
    }) == TimeTriggerCalculator.ZONE_POLICY_FIXED_IANA
    // Which editor is shown is local UI state: clearing the field to type a new
    // zone must not collapse the row. The persisted policy stays device-local
    // while the text does not parse, so an unfinished zone can never become a
    // fixed zone that the scheduler fails closed on.
    var fixed by remember { mutableStateOf(storedFixed) }
    val valid = storedZone.isNotBlank() && runCatching { ZoneId.of(storedZone.trim()) }.isSuccess

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.time_zone_section), style = MaterialTheme.typography.titleSmall)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SelectChip(
                selected = !fixed,
                onClick = {
                    fixed = false
                    onConfigChange(
                        draft.copy(
                            config = draft.config +
                                (TimeTriggerCalculator.ZONE_POLICY_KEY to TimeTriggerCalculator.ZONE_POLICY_DEVICE_LOCAL) +
                                (TimeTriggerCalculator.ZONE_ID_KEY to "") +
                                (TimeTriggerCalculator.CANONICAL_ZONE_ID_KEY to "")
                        )
                    )
                },
                label = stringResource(R.string.time_zone_device)
            )
            SelectChip(
                selected = fixed,
                onClick = {
                    fixed = true
                    val zone = storedZone.trim().takeIf { runCatching { ZoneId.of(it) }.isSuccess }
                        ?: ZoneId.systemDefault().id
                    onConfigChange(draft.copy(config = draft.config + fixedZoneConfig(zone)))
                },
                label = stringResource(R.string.time_zone_fixed)
            )
        }
        if (fixed) {
            OutlinedTextField(
                value = storedZone,
                onValueChange = { raw ->
                    val zone = raw.trim()
                    onConfigChange(
                        draft.copy(
                            config = if (runCatching { ZoneId.of(zone) }.isSuccess) {
                                draft.config + fixedZoneConfig(zone)
                            } else {
                                draft.config +
                                    (TimeTriggerCalculator.ZONE_POLICY_KEY to TimeTriggerCalculator.ZONE_POLICY_DEVICE_LOCAL) +
                                    (TimeTriggerCalculator.ZONE_ID_KEY to raw) +
                                    (TimeTriggerCalculator.CANONICAL_ZONE_ID_KEY to raw)
                            }
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.time_zone_id_label)) },
                singleLine = true,
                isError = !valid
            )
            if (!valid) {
                Text(
                    text = stringResource(R.string.time_zone_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                COMMON_TIME_ZONES.forEach { zone ->
                    SelectChip(
                        selected = storedZone.trim() == zone,
                        onClick = { onConfigChange(draft.copy(config = draft.config + fixedZoneConfig(zone))) },
                        label = zone
                    )
                }
            }
        }
    }
}

/** Writes the fixed-zone keys together so no reader sees a half-updated zone. */
private fun fixedZoneConfig(zone: String): Map<String, String> = mapOf(
    TimeTriggerCalculator.ZONE_POLICY_KEY to TimeTriggerCalculator.ZONE_POLICY_FIXED_IANA,
    TimeTriggerCalculator.ZONE_ID_KEY to zone,
    TimeTriggerCalculator.CANONICAL_ZONE_ID_KEY to zone
)

/** Quick picks only; the field accepts every IANA zone id. */
private val COMMON_TIME_ZONES = listOf(
    "UTC",
    "Europe/London",
    "Europe/Berlin",
    "America/New_York",
    "Asia/Dubai",
    "Asia/Tokyo"
)

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

private fun parseTimeMinutes(value: String): Int {
    val parts = value.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}
