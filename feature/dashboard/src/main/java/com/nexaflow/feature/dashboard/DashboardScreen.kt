package com.nexaflow.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.rememberPinnedTopBarScrollBehavior
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

    val scrollBehavior = rememberPinnedTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.dashboard_title),
                subtitle = stringResource(R.string.dashboard_subtitle),
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.dashboard_settings)
                        )
                    }
                },
                containerColor = Color.Transparent,
                scrollBehavior = scrollBehavior
            )
        },
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
            // ---- Instant search ----
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = stringResource(R.string.search_hint)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.clear_search)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
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
                    // Google-2026 Keep-style cascade: each card springs in a
                    // beat after the one above it.
                    modifier = Modifier.nexaFlowEntrance(delayMillis = index * 40),
                    onRun = { viewModel.runNow(row.automation) },
                    onToggle = { viewModel.toggleAutomation(row.automation, it) },
                    onClick = { navController.navigate("automation_details/${row.automation.id}") }
                )
            }
        }
    }
}

/** Routine card with a natural-language summary, live switch, and play button. */
@Composable
private fun RoutineCard(
    row: AutomationRow,
    summary: String,
    nextRun: String?,
    isRunning: Boolean,
    onRun: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NexaFlowCard(modifier = modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(
                icon = iconVector(row.automation.icon),
                containerColor = Color(row.automation.backgroundColor),
                contentColor = Color(row.automation.iconColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.automation.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                RoutineMetaLine(
                    nextRun = nextRun,
                    lastRunAt = row.lastRunAt
                )
            }
            if (isRunning) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                IconButton(onClick = onRun) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.run_now),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Switch(checked = row.automation.enabled, onCheckedChange = onToggle)
        }
    }
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
            onRun = {},
            onToggle = {},
            onClick = {},
            modifier = Modifier
        )
    }
}
