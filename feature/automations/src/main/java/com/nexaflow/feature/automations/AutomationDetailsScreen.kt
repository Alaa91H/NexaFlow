package com.nexaflow.feature.automations

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.ui.EmptyState
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.SettingRow
import com.nexaflow.core.ui.iconVector
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationDetailsScreen(navController: NavController) {
    val viewModel: AutomationDetailsViewModel = hiltViewModel()
    val automation by viewModel.automation.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val executionMessage by viewModel.executionMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(executionMessage) {
        executionMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeExecutionMessage()
        }
    }

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.automation_title),
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(
                        onClick = {
                            automation?.let {
                                navController.navigate("automation_builder?automationId=${it.id}")
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(onClick = { viewModel.delete { navController.popBackStack() } }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            )
        }
    ) { padding ->
        val current = automation
        if (current == null) {
            EmptyState(
                icon = Icons.Filled.Bolt,
                title = stringResource(R.string.not_found_title),
                subtitle = stringResource(R.string.not_found_subtitle)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NexaFlowCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(
                            icon = iconVector(current.icon),
                            containerColor = Color(current.backgroundColor),
                            contentColor = Color(current.iconColor)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = current.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = current.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.enabled),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Switch(
                                checked = current.enabled,
                                onCheckedChange = { viewModel.toggleEnabled(it) }
                            )
                        }
                    }
                }
                SectionHeader(text = stringResource(R.string.section_triggers))
                NexaFlowCard {
                    if (current.triggers.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_triggers),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        current.triggers.forEach { trigger ->
                            val (titleRes, subtitleRes, icon) = triggerPresentation(trigger.type)
                            SettingRow(icon = icon, title = stringResource(titleRes), subtitle = stringResource(subtitleRes), trailing = {
                                Text(
                                    text = triggerDetail(trigger.config),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            })
                        }
                    }
                }
                SectionHeader(text = stringResource(R.string.section_constraints))
                NexaFlowCard {
                    if (current.constraints.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_constraints),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        current.constraints.forEach { constraint ->
                            val (titleRes, icon) = constraintPresentation(constraint.type)
                            SettingRow(
                                icon = icon,
                                title = stringResource(titleRes),
                                subtitle = stringResource(R.string.constraint_subtitle),
                                trailing = {
                                    Text(
                                        text = constraintDetail(constraint.config),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            )
                        }
                    }
                }
                SectionHeader(text = stringResource(R.string.section_actions))
                NexaFlowCard {
                    if (current.actions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_actions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        current.actions.forEach { action ->
                            val (titleRes, subtitleRes, icon) = actionPresentation(action.type)
                            SettingRow(icon = icon, title = stringResource(titleRes), subtitle = stringResource(subtitleRes), trailing = {
                                // Per-action adaptive end behavior, e.g. "On end: Restore original".
                                val endText = endBehaviorText(action)
                                if (endText != null) {
                                    Text(
                                        text = endText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            })
                        }
                    }
                }
                SectionHeader(text = stringResource(R.string.section_exit_behavior))
                NexaFlowCard {
                    if (current.revertOnExit) {
                        SettingRow(
                            icon = Icons.Filled.Security,
                            title = stringResource(R.string.exit_revert_label),
                            subtitle = stringResource(R.string.exit_revert_sub)
                        )
                    } else if (current.exitActions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.exit_nothing_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        current.exitActions.forEach { action ->
                            val (titleRes, subtitleRes, icon) = actionPresentation(action.type)
                            SettingRow(icon = icon, title = stringResource(titleRes), subtitle = stringResource(subtitleRes))
                        }
                    }
                }
                Button(
                    onClick = { viewModel.runNow() },
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                    Text(
                        text = if (running) stringResource(R.string.running) else stringResource(R.string.run_now),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

private fun constraintPresentation(type: ConstraintType): Pair<Int, ImageVector> = when (type) {
    ConstraintType.WIFI -> R.string.constraint_type_wifi to Icons.Filled.Wifi
    ConstraintType.BATTERY -> R.string.constraint_type_battery to Icons.Filled.BatteryChargingFull
    ConstraintType.SCREEN_LOCKED -> R.string.constraint_type_screen_locked to Icons.Filled.Lock
    ConstraintType.HEADSET -> R.string.constraint_type_headset to Icons.Filled.Headphones
}

/** Human-readable constraint detail (e.g. battery "< 20%"). */
private fun constraintDetail(config: Map<String, String>): String {
    val direction = config["direction"]
    val level = config["level"]
    return when {
        direction != null && level != null ->
            if (direction == "ABOVE") "> $level%" else "< $level%"
        else -> "✓"
    }
}

private fun triggerPresentation(type: TriggerType): Triple<Int, Int, ImageVector> = when (type) {
    TriggerType.TIME -> Triple(R.string.trigger_time, R.string.trigger_time_sub, Icons.Filled.Schedule)
    TriggerType.BATTERY -> Triple(R.string.trigger_battery, R.string.trigger_battery_sub, Icons.Filled.BatteryChargingFull)
    TriggerType.APPLICATION -> Triple(R.string.trigger_app, R.string.trigger_app_sub, Icons.Filled.Add)
    TriggerType.DEVICE -> Triple(R.string.trigger_device, R.string.trigger_device_sub, Icons.Filled.Bolt)
    TriggerType.CONNECTIVITY -> Triple(R.string.trigger_connectivity, R.string.trigger_connectivity_sub, Icons.Filled.Wifi)
    TriggerType.LOCATION -> Triple(R.string.trigger_location, R.string.trigger_location_sub, Icons.Filled.Place)
    TriggerType.SMS -> Triple(R.string.trigger_sms, R.string.trigger_sms_sub, Icons.Filled.NotificationImportant)
    TriggerType.BLUETOOTH_DEVICE -> Triple(R.string.trigger_bluetooth, R.string.trigger_bluetooth_sub, Icons.Filled.Bluetooth)
    TriggerType.RINGER_MODE -> Triple(R.string.trigger_ringer, R.string.trigger_ringer_sub, Icons.Filled.NotificationsActive)
    TriggerType.NOTIFICATION -> Triple(R.string.trigger_notification, R.string.trigger_notification_sub, Icons.Filled.NotificationsActive)
    TriggerType.CALENDAR -> Triple(R.string.trigger_calendar, R.string.trigger_calendar_sub, Icons.Filled.DateRange)
}

/** Human-readable trigger detail (e.g. battery direction, time range, repeat mode). */
@Composable
private fun triggerDetail(config: Map<String, String>): String {
    val parts = buildList {
        if (config["timeMode"] == "RANGE") {
            add("${config["rangeStart"] ?: ""} → ${config["rangeEnd"] ?: ""}")
        } else {
            config["time"]?.let { add(it) }
        }
        config["direction"]?.let { dir ->
            if (dir == "BELOW") add("< ${config["above"] ?: ""}%") else add("> ${config["above"] ?: ""}%")
        }
        config["chargerType"]?.takeIf { it != "ANY" && it.isNotBlank() }?.let { add(chargerTypeText(it)) }
        config["repeat"]?.let { repeat ->
            when (repeat) {
                "DAILY" -> Unit
                "MONTHLY_WEEKDAY" -> add(monthlyWeekdayText(config))
                else -> add(repeat.lowercase())
            }
        }
        config["days"]?.let { add(it) }
        config["date"]?.let { add(it) }
        config["startDate"]?.let { add("$it → ${config["endDate"]}") }
        config["packages"]?.let { add("${it.split(',').size} app(s)") }
        config["network"]?.let { add(it.lowercase()) }
        config["event"]?.let { add(it.lowercase()) }
        config["from"]?.takeIf { it.isNotBlank() }?.let { add("from $it") }
        config["deviceName"]?.takeIf { it.isNotBlank() }?.let { add(it) }
        config["stream"]?.let { add(it.lowercase()) }
        config["mode"]?.let { add(it.lowercase()) }
        config["packages"]?.takeIf { it.isNotBlank() }?.let { add("${it.split(',').size} app(s)") }
        config["contains"]?.takeIf { it.isNotBlank() }?.let { add("\"$it\"") }
        config["calendar"]?.takeIf { it.isNotBlank() }?.let { add(it) }
        config["beforeMinutes"]?.takeIf { it != "0" && it.isNotBlank() }?.let { add("-$it min") }
    }
    return parts.joinToString(", ").ifEmpty { "default" }
}

/**
 * Google-Tasks-style summary for a MONTHLY_WEEKDAY trigger, e.g.
 * "1st Mon" or "Last Fri", composed from the localized occurrence and day.
 */
@Composable
private fun monthlyWeekdayText(config: Map<String, String>): String {
    val dayRes = when (config["weekday"]?.toIntOrNull()) {
        1 -> R.string.day_mon
        2 -> R.string.day_tue
        3 -> R.string.day_wed
        4 -> R.string.day_thu
        5 -> R.string.day_fri
        6 -> R.string.day_sat
        7 -> R.string.day_sun
        else -> null
    }
    val occurrenceRes = when (config["weekOfMonth"] ?: "1") {
        "1" -> R.string.occurrence_first
        "2" -> R.string.occurrence_second
        "3" -> R.string.occurrence_third
        "4" -> R.string.occurrence_fourth
        "LAST" -> R.string.occurrence_last
        else -> null
    }
    if (dayRes == null || occurrenceRes == null) return ""
    return stringResource(
        R.string.monthly_weekday_summary,
        stringResource(occurrenceRes),
        stringResource(dayRes)
    )
}

/** Localized per-action end-behavior label, or null when the action stays as is. */
@Composable
private fun endBehaviorText(action: Action): String? {
    val behavior = action.endBehavior ?: return null
    val label = when (behavior.mode) {
        com.nexaflow.domain.models.EndMode.LEAVE -> return null
        com.nexaflow.domain.models.EndMode.REVERT -> stringResource(R.string.end_revert)
        com.nexaflow.domain.models.EndMode.SET_VALUE -> {
            if (action.type in com.nexaflow.domain.models.EndBehaviorCatalog.toggleActions) {
                if (behavior.config["enabled"] == "true") stringResource(R.string.end_turn_on)
                else stringResource(R.string.end_turn_off)
            } else {
                stringResource(R.string.end_set_value)
            }
        }
    }
    return stringResource(R.string.end_summary, label)
}

/** Localized charger-type label shown in the battery trigger detail. */
@Composable
private fun chargerTypeText(value: String): String = when (value) {
    "AC" -> stringResource(R.string.charger_ac)
    "USB" -> stringResource(R.string.charger_usb)
    "WIRELESS" -> stringResource(R.string.charger_wireless)
    else -> stringResource(R.string.charger_any)
}

/** Design-time preview of the automation header + trigger/action cards. */
@Preview(name = "Automation details", showBackground = true, widthDp = 400)
@Preview(name = "Automation details (dark)", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationDetailsPreview() {
    MaterialTheme {
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            val sample = Automation(
                id = "preview-1",
                name = "Morning Mode",
                description = "Start the day right",
                icon = "sunny",
                iconColor = 0xFFFFFFFF,
                backgroundColor = 0xFFE8A33D,
                category = "general",
                priority = 1,
                enabled = true,
                triggers = listOf(Trigger(TriggerType.TIME, mapOf("time" to "08:00", "repeat" to "DAILY"))),
                actions = listOf(Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("level" to "60"))),
                createdAt = 0,
                updatedAt = 0
            )
            NexaFlowCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(
                        icon = iconVector(sample.icon),
                        containerColor = Color(sample.backgroundColor),
                        contentColor = Color(sample.iconColor)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = sample.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = sample.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            SectionHeader(text = stringResource(R.string.section_triggers))
            NexaFlowCard {
                val (titleRes, subtitleRes, icon) = triggerPresentation(TriggerType.TIME)
                SettingRow(icon = icon, title = stringResource(titleRes), subtitle = stringResource(subtitleRes))
            }
            SectionHeader(text = stringResource(R.string.section_actions))
            NexaFlowCard {
                val (titleRes, subtitleRes, icon) = actionPresentation(ActionType.SYSTEM_BRIGHTNESS)
                SettingRow(icon = icon, title = stringResource(titleRes), subtitle = stringResource(subtitleRes))
            }
        }
    }
}
