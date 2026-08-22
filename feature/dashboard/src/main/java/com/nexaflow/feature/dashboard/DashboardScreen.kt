package com.nexaflow.feature.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.ui.EmptyState
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowFloatingActionButton
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.iconVector
import com.nexaflow.core.ui.nexaFlowEntrance
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.schedule.TimeTriggerCalculator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(navController: NavController) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val rows by viewModel.automations.collectAsStateWithLifecycle()
    val runningIds by viewModel.runningIds.collectAsStateWithLifecycle()
    val executionMessage by viewModel.executionMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }
    var actionMenuTarget by remember { mutableStateOf<Automation?>(null) }
    var deleteTarget by remember { mutableStateOf<Automation?>(null) }
    // Keep one task expanded at a time so the dashboard remains scannable.
    var expandedAutomationId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(executionMessage) {
        executionMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeExecutionMessage()
        }
    }

    val filteredRows = remember(rows, searchQuery) {
        if (searchQuery.isBlank()) rows
        else rows.filter { it.automation.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            NexaFlowFloatingActionButton(
                onClick = { navController.navigate("automation_builder") },
                icon = Icons.Filled.Add,
                label = stringResource(R.string.new_routine)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Instant search (slim pill) + settings gear beside it ----
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 11.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.search_hint),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.clear_search)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(48.dp))
                            }
                        }
                    }
                    // Settings gear: the only entry point to settings now.
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.dashboard_settings)
                        )
                    }
                }
            }

            // ---- Routines ----
            item {
                SectionHeader(text = stringResource(R.string.section_routines))
            }
            if (rows.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Bolt,
                        title = stringResource(R.string.empty_automations_title),
                        subtitle = stringResource(R.string.empty_automations_sub)
                    )
                }
            } else if (filteredRows.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = stringResource(R.string.no_search_results_title),
                        subtitle = stringResource(R.string.no_search_results_sub)
                    )
                }
            }
            itemsIndexed(filteredRows, key = { _, row -> row.automation.id }) { index, row ->
                RoutineCard(
                    row = row,
                    summary = automationSummary(row.automation),
                    nextRun = nextRunText(row.automation),
                    isRunning = row.automation.id in runningIds,
                    containerColor = scheduledTaskCardColor(index),
                    expanded = expandedAutomationId == row.automation.id,
                    menuExpanded = actionMenuTarget?.id == row.automation.id,
                    // Google-2026 Keep-style cascade: each card springs in a
                    // beat after the one above it, capped so a long list still
                    // feels instant (no multi-second tail).
                    modifier = Modifier.nexaFlowEntrance(
                        delayMillis = minOf(index * 40, 400)
                    ),
                    onRun = { viewModel.runNow(row.automation) },
                    onEdit = { navController.navigate("automation_builder?automationId=${row.automation.id}") },
                    onDelete = { deleteTarget = row.automation },
                    onToggle = { viewModel.toggleAutomation(row.automation, it) },
                    onExpandedChange = {
                        expandedAutomationId = nextExpandedAutomationId(
                            currentExpandedId = expandedAutomationId,
                            tappedAutomationId = row.automation.id
                        )
                    },
                    onLongClick = { actionMenuTarget = row.automation },
                    onDismissMenu = { actionMenuTarget = null }
                )
            }
        }
    }

    deleteTarget?.let { automation ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_task_title)) },
            text = { Text(stringResource(R.string.delete_task_message, automation.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAutomation(automation)
                        deleteTarget = null
                    }
                ) {
                    Text(stringResource(R.string.delete_task))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

internal fun nextExpandedAutomationId(
    currentExpandedId: String?,
    tappedAutomationId: String
): String? = if (currentExpandedId == tappedAutomationId) null else tappedAutomationId

/**
 * A progressive-disclosure task card. In its resting state it exposes only the
 * task name; a tap expands exactly one card on the dashboard to reveal the
 * task's persisted definition and live execution metadata.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RoutineCard(
    row: AutomationRow,
    summary: String,
    nextRun: String?,
    isRunning: Boolean,
    containerColor: Color,
    expanded: Boolean,
    menuExpanded: Boolean,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onExpandedChange: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    NexaFlowCard(
        modifier = modifier
            .animateContentSize()
            .combinedClickable(
                onClick = onExpandedChange,
                onLongClick = onLongClick
            ),
        containerColor = containerColor
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = row.automation.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.task_details_collapse else R.string.task_details_expand
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (expanded) {
                    RoutineDetails(
                        row = row,
                        summary = summary,
                        nextRun = nextRun,
                        isRunning = isRunning,
                        onToggle = onToggle
                    )
                }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onDismissMenu
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.run_now)) },
                    onClick = {
                        onDismissMenu()
                        onRun()
                    },
                    enabled = !isRunning
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_task)) },
                    onClick = {
                        onDismissMenu()
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_task)) },
                    onClick = {
                        onDismissMenu()
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun RoutineDetails(
    row: AutomationRow,
    summary: String,
    nextRun: String?,
    isRunning: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val automation = row.automation
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconBadge(
            icon = iconVector(automation.icon),
            containerColor = Color(automation.backgroundColor),
            contentColor = Color(automation.iconColor)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    when {
                        isRunning -> R.string.task_status_running
                        automation.enabled -> R.string.task_status_enabled
                        else -> R.string.task_status_disabled
                    }
                ),
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    isRunning -> MaterialTheme.colorScheme.primary
                    automation.enabled -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            RoutineMetaLine(nextRun = nextRun, lastRunAt = row.lastRunAt)
        }
        if (isRunning) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        Switch(checked = automation.enabled, onCheckedChange = onToggle)
    }

    if (automation.description.isNotBlank()) {
        DetailBlock(
            title = stringResource(R.string.task_details_description),
            lines = listOf(automation.description)
        )
    }
    DetailBlock(
        title = stringResource(R.string.task_details_summary),
        lines = listOf(summary)
    )
    DetailBlock(
        title = stringResource(R.string.task_details_triggers, automation.triggers.size),
        lines = automation.triggers.map { trigger ->
            listOf(stringResource(triggerLabel(trigger.type)), taskConfigDetail(trigger.config))
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        }.ifEmpty { listOf(stringResource(R.string.task_details_none)) }
    )
    DetailBlock(
        title = stringResource(R.string.task_details_constraints, automation.constraints.size),
        lines = automation.constraints.map { constraint ->
            listOf(constraint.type.name.toDisplayLabel(), taskConfigDetail(constraint.config))
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        }.ifEmpty { listOf(stringResource(R.string.task_details_none)) }
    )
    DetailBlock(
        title = stringResource(R.string.task_details_actions, automation.actions.size),
        lines = automation.actions.map { action ->
            listOf(action.type.name.toDisplayLabel(), taskConfigDetail(action.config))
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        }.ifEmpty { listOf(stringResource(R.string.task_details_none)) }
    )
    DetailBlock(
        title = stringResource(R.string.task_details_exit_actions, automation.exitActions.size),
        lines = when {
            automation.revertOnExit -> listOf(stringResource(R.string.task_details_revert_on_exit))
            automation.exitActions.isEmpty() -> listOf(stringResource(R.string.task_details_none))
            else -> automation.exitActions.map { action ->
                listOf(action.type.name.toDisplayLabel(), taskConfigDetail(action.config))
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            }
        }
    )
}

@Composable
private fun DetailBlock(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun taskConfigDetail(config: Map<String, String>): String =
    config.entries
        .sortedBy { it.key }
        .joinToString(" · ") { (key, value) -> "$key: $value" }

private fun String.toDisplayLabel(): String =
    removePrefix("SYSTEM_")
        .split('_')
        .joinToString(" ") { part -> part.lowercase().replaceFirstChar(Char::uppercase) }

@Composable
private fun scheduledTaskCardColor(index: Int): Color =
    scheduledTaskCardColor(index, isSystemInDarkTheme())

internal fun scheduledTaskCardColor(index: Int, darkTheme: Boolean): Color = when {
    darkTheme && index % 2 == 0 -> Color(0xFF363636)
    darkTheme -> Color(0xFF252525)
    index % 2 == 0 -> Color(0xFFE7E7E7)
    else -> Color(0xFFD3D3D3)
}

/** Samsung-style "Next run · 8:00 PM · Last run · 2 h ago" meta line under the title. */
@Composable
private fun RoutineMetaLine(
    nextRun: String?,
    lastRunAt: Long?
) {
    // Last-run is shown even when the routine is disabled (Samsung does too);
    // only the "Next" preview is gated on the routine being enabled.
    val segments = buildList {
        nextRun?.let { add(it) }
        lastRunAt?.let { add(stringResource(R.string.last_run_prefix, formatRelativeTime(it))) }
    }
    if (segments.isEmpty()) return
    Text(
        text = segments.joinToString("  ·  "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp)
    )
}

/** "Next · today 8:00 PM" for enabled time triggers, null otherwise. */
@Composable
private fun nextRunText(automation: Automation): String? {
    if (!automation.enabled) return null
    val trigger = automation.triggers.firstOrNull { it.type == TriggerType.TIME } ?: return null
    val nowMillis = System.currentTimeMillis()
    val next = TimeTriggerCalculator.nextFireTime(trigger.config, nowMillis) ?: return null
    val zone = ZoneId.systemDefault()
    val nextTime = Instant.ofEpochMilli(next).atZone(zone)
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val context = LocalContext.current
    val timeText = android.text.format.DateFormat.getTimeFormat(context)
        .format(java.util.Date(next))
    val dayPrefix = when (nextTime.toLocalDate()) {
        now.toLocalDate() -> stringResource(R.string.today)
        now.toLocalDate().plusDays(1) -> stringResource(R.string.tomorrow)
        else -> nextTime.format(DateTimeFormatter.ofPattern("MMM d"))
    }
    return stringResource(R.string.next_run_prefix, "$dayPrefix $timeText")
}

/** Human-friendly relative time: "just now", "5 m ago", "2 h ago", "3 d ago". */
@Composable
private fun formatRelativeTime(timestamp: Long): String {
    val minutes = (System.currentTimeMillis() - timestamp) / 60_000L
    return when {
        minutes < 1 -> stringResource(R.string.just_now)
        minutes < 60 -> stringResource(R.string.minutes_ago, minutes)
        minutes < 60 * 24 -> stringResource(R.string.hours_ago, minutes / 60)
        else -> stringResource(R.string.days_ago, minutes / (60 * 24))
    }
}

@Composable
private fun automationSummary(automation: Automation): String {
    val triggerText = if (automation.triggers.isEmpty()) {
        stringResource(R.string.summary_any_trigger)
    } else {
        automation.triggers.map { stringResource(triggerLabel(it.type)) }.joinToString(", ")
    }
    val actionText = if (automation.actions.isEmpty()) {
        stringResource(R.string.summary_no_actions)
    } else {
        stringResource(R.string.summary_actions_count, automation.actions.size)
    }
    return stringResource(R.string.summary_template, triggerText, actionText)
}

private fun triggerLabel(type: TriggerType): Int = when (type) {
    TriggerType.TIME -> R.string.trigger_time
    TriggerType.BATTERY -> R.string.trigger_battery
    TriggerType.APPLICATION -> R.string.trigger_app
    TriggerType.DEVICE -> R.string.trigger_device
    TriggerType.CONNECTIVITY -> R.string.trigger_connectivity
    TriggerType.LOCATION -> R.string.trigger_location
    TriggerType.SMS -> R.string.trigger_sms
    TriggerType.BLUETOOTH_DEVICE -> R.string.trigger_bluetooth
    TriggerType.RINGER_MODE -> R.string.trigger_ringer
    TriggerType.NOTIFICATION -> R.string.trigger_notification
    TriggerType.CALENDAR -> R.string.trigger_calendar
    TriggerType.SENSOR -> R.string.trigger_sensor
    TriggerType.NETWORK_MODE -> R.string.trigger_type_network_mode
    TriggerType.WEBHOOK -> R.string.trigger_webhook
    TriggerType.ROM_SETTING -> R.string.trigger_rom_setting
    TriggerType.HEADPHONE -> R.string.trigger_headphone
    TriggerType.CHARGER -> R.string.trigger_charger
    TriggerType.AIRPLANE_MODE -> R.string.trigger_airplane
    TriggerType.DARK_MODE -> R.string.trigger_dark_mode
    TriggerType.CALL_STATE -> R.string.trigger_call_state
    TriggerType.APP_INSTALLED -> R.string.trigger_app_installed
    TriggerType.MEDIA_PLAYING -> R.string.trigger_media_playing
    TriggerType.VOLUME_CHANGED -> R.string.trigger_volume_changed
    TriggerType.POWER_SAVER -> R.string.trigger_power_saver
    TriggerType.BLUETOOTH_STATE -> R.string.trigger_bluetooth_state
    TriggerType.BRIGHTNESS_LEVEL -> R.string.trigger_brightness_level
    TriggerType.STORAGE_LOW -> R.string.trigger_storage_low
    TriggerType.AUTO_ROTATE -> R.string.trigger_auto_rotate
    TriggerType.DATA_SAVER_STATE -> R.string.trigger_data_saver_state
    TriggerType.DEVICE_LOCKED -> R.string.trigger_device_locked
    TriggerType.WIFI_STATE -> R.string.trigger_wifi_state
    TriggerType.NFC_STATE -> R.string.trigger_nfc_state
    TriggerType.LOCATION_STATE -> R.string.trigger_location_state
    TriggerType.SCREEN_ROTATION_STATE -> R.string.trigger_screen_rotation_state
    TriggerType.WIFI_SIGNAL_STRENGTH -> R.string.trigger_wifi_signal_strength
    TriggerType.CELL_SIGNAL_STRENGTH -> R.string.trigger_cell_signal_strength
    TriggerType.BATTERY_TEMPERATURE -> R.string.trigger_battery_temperature
    TriggerType.USB_CONNECTED -> R.string.trigger_usb_connected
    TriggerType.HDMI_CONNECTED -> R.string.trigger_hdmi_connected
    TriggerType.ETHERNET_CONNECTED -> R.string.trigger_ethernet_connected
    TriggerType.VPN_CONNECTED -> R.string.trigger_vpn_connected
    TriggerType.CLIPBOARD_CHANGED -> R.string.trigger_clipboard_changed
    TriggerType.DND_STATE -> R.string.trigger_dnd_state
    TriggerType.STAY_AWAKE_STATE -> R.string.trigger_stay_awake_state
    TriggerType.AUTO_BRIGHTNESS_STATE -> R.string.trigger_auto_brightness_state
    TriggerType.SCREEN_TIMEOUT_CHANGED -> R.string.trigger_screen_timeout_changed
    TriggerType.DATA_ROAMING_STATE -> R.string.trigger_data_roaming_state
    TriggerType.TIMEZONE_CHANGED -> R.string.trigger_timezone_changed
    TriggerType.BOOT_COMPLETED -> R.string.trigger_boot_completed
    TriggerType.NFC_TAG_SCANNED -> R.string.trigger_nfc_tag_scanned
    TriggerType.ALARM_SET_CHANGED -> R.string.trigger_alarm_set_changed
    TriggerType.PLUGIN_EVENT -> R.string.trigger_plugin_event
}

/** Design-time preview of a Samsung-style routine card. */
@Preview(name = "Routine card", showBackground = true, widthDp = 400)
@Preview(name = "Routine card (dark)", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RoutineCardPreview() {
    MaterialTheme {
        RoutineCard(
            row = AutomationRow(
                automation = Automation(
                    id = "preview-1",
                    name = "Morning Mode",
                    description = "Start the day",
                    icon = "sunny",
                    iconColor = 0xFFFFFFFF,
                    backgroundColor = 0xFF8F4C00,
                    category = "general",
                    priority = 1,
                    enabled = true,
                    triggers = listOf(com.nexaflow.domain.models.Trigger(TriggerType.TIME, mapOf("time" to "08:00"))),
                    actions = listOf(com.nexaflow.domain.models.Action(com.nexaflow.domain.models.ActionType.SYSTEM_BRIGHTNESS, mapOf("level" to "60"))),
                    createdAt = 0,
                    updatedAt = 0
                ),
                lastRunAt = System.currentTimeMillis() - 3_600_000L
            ),
            summary = "Time · 1 action",
            nextRun = "Next · today 8:00 PM",
            isRunning = false,
            containerColor = scheduledTaskCardColor(0),
            expanded = true,
            menuExpanded = false,
            onRun = {},
            onEdit = {},
            onDelete = {},
            onToggle = {},
            onExpandedChange = {},
            onLongClick = {},
            onDismissMenu = {},
            modifier = Modifier
        )
    }
}
