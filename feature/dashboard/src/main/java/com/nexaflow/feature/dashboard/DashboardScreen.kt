package com.nexaflow.feature.dashboard

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.execution.ManualBlockKind
import com.nexaflow.core.execution.ManualBlockReason
import com.nexaflow.core.execution.R as ExecutionR
import com.nexaflow.core.ui.BentoStatCard
import com.nexaflow.core.ui.Dock
import com.nexaflow.core.ui.DockAskPill
import com.nexaflow.core.ui.DockIconButton
import com.nexaflow.core.ui.EmptyState
import com.nexaflow.core.ui.HeaderIconButton
import com.nexaflow.core.ui.IconTile
import com.nexaflow.core.ui.FlowStepRow
import com.nexaflow.core.ui.NexaFlowAnimatedVisibility
import com.nexaflow.core.ui.StatusPill
import com.nexaflow.core.ui.StatusPillVariant
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.ScreenGlowVariant
import com.nexaflow.core.ui.ScreenHeader
import com.nexaflow.core.ui.SearchPill
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.Dimens
import com.nexaflow.core.ui.executionBuckets
import com.nexaflow.core.ui.glassSource
import com.nexaflow.core.ui.iconVector
import com.nexaflow.core.ui.nexaFlowEntrance
import com.nexaflow.core.ui.rememberInstalledAppPresentation
import com.nexaflow.core.ui.screenGlow
import com.nexaflow.core.ui.theme.NexaFlowAlphas
import com.nexaflow.core.ui.theme.NexaFlowCategory
import com.nexaflow.core.ui.theme.NexaFlowTheme
import com.nexaflow.core.ui.theme.forText
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.EndBehaviorCatalog
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.hasExecutableEndBehavior
import com.nexaflow.domain.models.hasUserAuthoredDescription
import com.nexaflow.domain.schedule.TimeTriggerCalculator
import com.nexaflow.feature.automations.actionPresentation
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(navController: NavController) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val rows by viewModel.automations.collectAsStateWithLifecycle()
    val runningIds by viewModel.runningIds.collectAsStateWithLifecycle()
    val executionMessage by viewModel.executionMessage.collectAsStateWithLifecycle()
    val todayHours by viewModel.todayExecutionHours.collectAsStateWithLifecycle()
    // Compact dark toast: message-sized, centered above the Dock.
    var toastText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Single-task share (.nexaflow) localized strings, captured once per
    // composition so the coroutine and intents hold stable values.
    val shareTaskTitle = stringResource(R.string.share_task_title)
    val shareTaskFailure = stringResource(R.string.share_task_failed)
    var searchQuery by remember { mutableStateOf("") }
    var actionMenuTarget by remember { mutableStateOf<Automation?>(null) }
    var deleteTarget by remember { mutableStateOf<Automation?>(null) }
    // Task tapped for Run-now whose gate check failed: hosts the mismatch dialog.
    var runBlockDialogTarget by remember { mutableStateOf<Automation?>(null) }
    var forceRunConfirmTarget by remember { mutableStateOf<Automation?>(null) }
    val legacyConnectivityTasks by viewModel.legacyConnectivityTasks.collectAsStateWithLifecycle()
    // Keep one task expanded at a time so the dashboard remains scannable.
    var expandedAutomationId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(executionMessage) {
        executionMessage?.let { message ->
            toastText = message
            viewModel.consumeExecutionMessage()
        }
    }
    LaunchedEffect(toastText) {
        if (toastText != null) {
            delay(3000)
            toastText = null
        }
    }

    val filteredRows by remember(rows, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) rows
            else rows.filter { it.automation.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dockBottom = Dimens.DockMarginBottom + navigationBottom

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .screenGlow(ScreenGlowVariant.Dashboard)
            .glassSource()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dimens.ScreenHorizontalPadding,
                end = Dimens.ScreenHorizontalPadding,
                top = statusBarTop + Dimens.Space4,
                bottom = Dimens.DockHeight + dockBottom + Dimens.DockMarginBottom
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = stringResource(R.string.dashboard_title),
                    caption = stringResource(
                        R.string.dashboard_caption,
                        rows.size,
                        rows.count { it.automation.enabled }
                    ),
                    // The header block carries 4dp extra inset so the title
                    // starts at 20dp instead of the 16dp gutter.
                    modifier = Modifier.padding(start = Dimens.Space1),
                    actions = {
                        HeaderIconButton(
                            icon = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.dashboard_settings),
                            onClick = { navController.navigate("settings") }
                        )
                    }
                )
            }

            item(key = "search") {
                SearchPill(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = stringResource(R.string.search_hint),
                    modifier = Modifier.padding(top = 14.dp, bottom = 12.dp)
                )
            }

            item(key = "bento") {
                DashboardBento(rows = rows, todayHours = todayHours)
            }

            // ---- Legacy connectivity migration banner ----
            if (legacyConnectivityTasks.isNotEmpty()) {
                item(key = "migrate_banner") {
                    NexaFlowCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.migrate_banner_title),
                                style = NexaFlowTheme.type.cardTitle,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = stringResource(R.string.migrate_banner_message),
                                style = NexaFlowTheme.type.meta,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { viewModel.migrateLegacyConnectivityTriggers() }) {
                                    Text(text = stringResource(R.string.migrate_banner_button))
                                }
                            }
                        }
                    }
                }
            }

            // ---- Routines ----
            item(key = "section") {
                SectionHeader(
                    text = stringResource(R.string.section_routines),
                    modifier = Modifier.padding(top = 6.dp, start = 6.dp)
                )
            }
            if (rows.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.Bolt,
                        title = stringResource(R.string.empty_automations_title),
                        subtitle = stringResource(R.string.empty_automations_sub)
                    )
                }
            } else if (filteredRows.isEmpty()) {
                item(key = "no_results") {
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = stringResource(R.string.no_search_results_title),
                        subtitle = stringResource(R.string.no_search_results_sub)
                    )
                }
            }
            items(
                items = filteredRows,
                key = { row -> row.automation.id },
                contentType = { "routine" }
            ) { row ->
                RoutineCard(
                    row = row,
                    summary = automationSummary(row.automation),
                    isRunning = row.automation.id in runningIds,
                    expanded = expandedAutomationId == row.automation.id,
                    menuExpanded = actionMenuTarget?.id == row.automation.id,
                    // Keep-style cascade: each card springs in a beat after the
                    // one above it, capped so a long list still feels instant.
                    modifier = Modifier.nexaFlowEntrance(
                        delayMillis = minOf(filteredRows.indexOf(row) * 30, 150)
                    ),
                    onRun = {
                        // Ask the gate first: admissible → run; rejected →
                        // show the typed mismatch dialog with a force-run path.
                        scope.launch {
                            val block = viewModel.describeManualBlock(row.automation)
                            if (block == null) {
                                viewModel.runNow(row.automation)
                            } else {
                                runBlockDialogTarget = row.automation
                            }
                        }
                    },
                    onEdit = { navController.navigate("automation_builder?automationId=${row.automation.id}") },
                    onDetails = { navController.navigate("automation_details/${android.net.Uri.encode(row.automation.id)}") },
                    onDelete = { deleteTarget = row.automation },
                    onShare = {
                        scope.launch {
                            val json = viewModel.exportTaskJson(row.automation.id)
                            if (json == null) {
                                toastText = shareTaskFailure
                                return@launch
                            }
                            val file = runCatching {
                                val dir = File(context.cacheDir, "backup").apply { mkdirs() }
                                File(dir, taskFileName(row.automation.name)).apply { writeText(json) }
                            }.getOrNull()
                            val uri = file?.let {
                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
                            }
                            if (uri == null) {
                                toastText = shareTaskFailure
                                return@launch
                            }
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, shareTaskTitle)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(sendIntent, shareTaskTitle))
                            }.onFailure { toastText = shareTaskFailure }
                        }
                    },
                    onToggle = { viewModel.toggleAutomation(row.automation, it) },
                    onToggleToast = { target, show -> viewModel.setShowToastOnToggle(target, show) },
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

        // The one floating element: a glass Dock that never covers the last card.
        Dock(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = Dimens.DockMarginH, vertical = dockBottom),
            leading = {
                DockAskPill(
                    label = stringResource(R.string.ask_nexaflow),
                    icon = Icons.Filled.Bolt,
                    onClick = { navController.navigate("ai_chat") },
                    modifier = Modifier.weight(1f)
                )
            },
            action = {
                DockIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.new_routine),
                    onClick = { navController.navigate("automation_builder") }
                )
            }
        )

        // Compact toast: wraps the message, centered above the Dock so it never
        // hides the primary action.
        toastText?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 32.dp,
                        end = 32.dp,
                        bottom = Dimens.DockHeight + dockBottom + 12.dp
                    )
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    style = NexaFlowTheme.type.meta,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    textAlign = TextAlign.Center
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

    // Typed mismatch dialog: names the trigger/constraint that failed and
    // offers either the honest exit path (OK = run the end behavior) or the
    // explicit force-run override.
    runBlockDialogTarget?.let { automation ->
        val block = produceState<ManualBlockReason?>(
            initialValue = null,
            key1 = automation.id
        ) { value = viewModel.describeManualBlock(automation) }.value
        AlertDialog(
            onDismissRequest = { runBlockDialogTarget = null },
            title = { Text(stringResource(R.string.run_gate_title)) },
            text = {
                Column {
                    Text(text = stringResource(R.string.run_reason_task, automation.name))
                    when (block?.kind) {
                        ManualBlockKind.TRIGGERS_NOT_MET ->
                            block.failedTriggerLabels.forEach { label ->
                                Text(
                                    text = stringResource(R.string.run_reason_trigger, label),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        ManualBlockKind.TRIGGERS_UNKNOWN -> {
                            Text(text = stringResource(R.string.run_reason_unknown))
                            block.failedTriggerLabels.forEach { label ->
                                Text(
                                    text = stringResource(R.string.run_reason_trigger, label),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        ManualBlockKind.CONSTRAINTS_NOT_MET ->
                            block.failedConstraintLabels.forEach { label ->
                                Text(
                                    text = stringResource(R.string.run_reason_constraint, label),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        ManualBlockKind.INVALID_TIME_RANGE ->
                            Text(text = stringResource(R.string.run_reason_no_exit))
                        else -> Unit
                    }
                }
            },
            confirmButton = {
                if (automation.hasExecutableEndBehavior) {
                    TextButton(
                        onClick = {
                            runBlockDialogTarget = null
                            viewModel.runEndBehavior(automation)
                        }
                    ) {
                        Text(stringResource(R.string.run_reason_run_end))
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { runBlockDialogTarget = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            runBlockDialogTarget = null
                            forceRunConfirmTarget = automation
                        }
                    ) {
                        Text(stringResource(R.string.run_force_short))
                    }
                }
            }
        )
    }

    // Separate confirmation step for the force-run override: the bypass must
    // never be one accidental tap away from the mismatch dialog.
    forceRunConfirmTarget?.let { automation ->
        AlertDialog(
            onDismissRequest = { forceRunConfirmTarget = null },
            title = { Text(stringResource(R.string.run_force_title)) },
            text = { Text(stringResource(R.string.run_force_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        forceRunConfirmTarget = null
                        viewModel.forceRun(automation)
                    }
                ) {
                    Text(stringResource(R.string.run_force_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { forceRunConfirmTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * "Next run" and "Today" bento cards. Next run picks the earliest upcoming
 * TIME trigger across enabled routines; Today counts the six 4-hour buckets of
 * the current day from durable history.
 */
@Composable
private fun DashboardBento(rows: List<AutomationRow>, todayHours: List<Int>) {
    val context = LocalContext.current
    val now = remember(rows) { System.currentTimeMillis() }
    val next = remember(rows, now) {
        rows.mapNotNull { row -> nextRunMillis(row.automation, now)?.let { row to it } }
            .minByOrNull { it.second }
    }
    val timeFormat = remember(context) {
        android.text.format.DateFormat.getTimeFormat(context)
    }
    val nextValue = next?.let { timeFormat.format(java.util.Date(it.second)) } ?: "—"
    val minutes = next?.let { ((it.second - now) / 60_000L).coerceAtLeast(0) }
    val nextCaption = when {
        next == null || minutes == null -> stringResource(R.string.bento_no_upcoming)
        minutes < 60 -> stringResource(
            R.string.bento_next_caption,
            next.first.automation.name,
            stringResource(R.string.relative_in_minutes, minutes.toInt())
        )
        minutes < 60 * 24 -> stringResource(
            R.string.bento_next_caption,
            next.first.automation.name,
            stringResource(R.string.relative_in_hours, (minutes / 60).toInt())
        )
        else -> stringResource(
            R.string.bento_next_caption,
            next.first.automation.name,
            stringResource(R.string.relative_in_days, (minutes / (60 * 24)).toInt())
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap)
    ) {
        BentoStatCard(
            eyebrow = stringResource(R.string.bento_next_run),
            value = nextValue,
            caption = nextCaption,
            modifier = Modifier.weight(1f)
        )
        BentoStatCard(
            eyebrow = stringResource(R.string.bento_today),
            value = todayHours.size.toString(),
            caption = stringResource(R.string.bento_runs),
            sparkline = executionBuckets(todayHours),
            sparklineDescription = stringResource(R.string.bento_today_a11y, todayHours.size),
            modifier = Modifier.weight(1f)
        )
    }
}

/** Earliest upcoming fire time of an enabled routine's first TIME trigger. */
internal fun nextRunMillis(automation: Automation, nowMillis: Long): Long? {
    if (!automation.enabled) return null
    val trigger = automation.triggers.firstOrNull { it.type == TriggerType.TIME } ?: return null
    val zone = TimeTriggerCalculator.resolveZone(trigger.config) ?: return null
    return TimeTriggerCalculator.nextFireTime(
        config = trigger.config,
        fromMillis = nowMillis,
        zone = zone
    )
}
internal fun nextExpandedAutomationId(
    currentExpandedId: String?,
    tappedAutomationId: String
): String? = if (currentExpandedId == tappedAutomationId) null else tappedAutomationId

/**
 * File name for a shared single-task (.nexaflow) file: the task name,
 * stripped to filesystem-safe characters and truncated defensively so a
 * pathological task name cannot break the share flow.
 */
internal fun taskFileName(taskName: String): String {
    val sanitized = taskName
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(60)
        .ifBlank { "task" }
    return "$sanitized.nexaflow"
}

/** Stable semantics used by the dashboard Compose contract test. */
/** Stable semantics used by the dashboard Compose contract test. */
internal object RoutineCardTestTags {
    const val HeaderIcon = "routine_card_header_icon"
    const val HeaderToggle = "routine_card_header_toggle"
    const val HeaderCard = "routine_card_header_card"
}

/**
 * A routine card in the Ink language: category-tinted tile, title, one meta
 * line, optional status pill and a trailing switch. The whole card toggles its
 * expansion — there is no chevron. Expanded, it shows the WHEN → THEN logic as
 * structured rows plus the exit footnote, indented to the text column.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun RoutineCard(
    row: AutomationRow,
    summary: String,
    isRunning: Boolean,
    expanded: Boolean,
    menuExpanded: Boolean,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onToggleToast: (Automation, Boolean) -> Unit,
    onExpandedChange: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onDetails: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val automation = row.automation
    val category = automationCategory(automation)
    val colors = MaterialTheme.colorScheme
    val expandLabel = stringResource(
        if (expanded) R.string.task_details_collapse else R.string.task_details_expand
    )
    val stateText = stringResource(
        when {
            isRunning -> R.string.task_status_running
            automation.enabled -> R.string.task_status_enabled
            else -> R.string.task_status_disabled
        }
    )
    val whenChips = automation.triggers.map { triggerChip(it) }
    val thenChips = automation.actions.map { actionDisplayText(it) }
    val footnote = routineFootnote(automation)
    val status = when {
        isRunning -> StatusPillVariant.Running
        row.lastRunSucceeded == false -> StatusPillVariant.Error
        else -> null
    }

    Box {
        NexaFlowCard(
            modifier = modifier
                .testTag(RoutineCardTestTags.HeaderCard)
                .animateContentSize()
                .combinedClickable(
                    onClick = onExpandedChange,
                    onLongClick = onLongClick,
                    onClickLabel = expandLabel
                )
                .semantics { stateDescription = stateText }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.TileTextGap)
            ) {
                IconTile(
                    icon = iconVector(automation.icon),
                    category = category,
                    modifier = Modifier.testTag(RoutineCardTestTags.HeaderIcon)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = automation.name,
                        style = NexaFlowTheme.type.cardTitle.forText(automation.name),
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = summary,
                        style = NexaFlowTheme.type.meta.forText(summary),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (status != null) {
                        StatusPill(
                            text = if (isRunning) stateText else stringResource(R.string.status_error),
                            variant = status,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Switch(
                    checked = automation.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier
                        .testTag(RoutineCardTestTags.HeaderToggle)
                        .width(52.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.primary,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = colors.outline,
                        uncheckedTrackColor = colors.surfaceContainerHighest,
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }

            NexaFlowAnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(
                        start = Dimens.RoutineTile + Dimens.TileTextGap,
                        top = Dimens.TileTextGap
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FlowStepRow(label = stringResource(R.string.flow_when), chips = whenChips)
                    FlowStepRow(label = stringResource(R.string.flow_then), chips = thenChips)
                    if (footnote != null) {
                        Text(
                            text = footnote,
                            style = NexaFlowTheme.type.meta.forText(footnote),
                            color = colors.onSurfaceVariant
                        )
                    }
                    RoutineExpandedDetails(
                        row = row,
                        onToggleToast = { onToggleToast(automation, it) }
                    )
                }
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
                text = { Text(stringResource(R.string.task_access_details)) },
                onClick = { onDismissMenu(); onDetails() }
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
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_task)) },
                onClick = {
                    onDismissMenu()
                    onShare()
                }
            )
        }
    }
}

/**
 * The long-form body of an expanded routine. The WHEN/THEN chips above carry
 * the flow at a glance; these sections keep the per-node diagnostics, live
 * runtime state and health the card has always exposed. Presentation only —
 * every section is shared with the task-details screen.
 */
@Composable
private fun RoutineExpandedDetails(
    row: AutomationRow,
    onToggleToast: (Boolean) -> Unit
) {
    val automation = row.automation
    val conditionSnapshot = rememberRoutineConditionSnapshot(automation)
    val nextRun = nextRunText(automation)

    RoutineMetaLine(nextRun = nextRun, lastRunAt = row.lastRunAt)
    if (row.lastRunSucceeded == false) {
        Text(
            text = stringResource(ExecutionR.string.execution_task_failed),
            style = NexaFlowTheme.type.meta,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 2.dp)
        )
    }

    // Toast on toggle option — shown for every task in expanded view.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.toast_on_toggle_title),
                style = NexaFlowTheme.type.cardTitle
            )
            Text(
                text = stringResource(R.string.toast_on_toggle_sub),
                style = NexaFlowTheme.type.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = automation.showToastOnToggle,
            onCheckedChange = onToggleToast,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }

    if (automation.hasUserAuthoredDescription()) {
        DetailBlock(
            title = stringResource(R.string.task_details_description),
            lines = listOf(automation.description)
        )
    }

    DetailedReadinessSection(automation = automation, snapshot = conditionSnapshot)
    DetailedLifecycleSection(automation)
    DetailedHealthSection(row.healthReport)
    DetailedTriggerSection(automation = automation, states = conditionSnapshot.triggerStates)
    DetailedConstraintSection(automation = automation, states = conditionSnapshot.constraintStates)
    DetailedActionSection(automation = automation, latestExecution = row.latestExecution)
    DetailedEndBehaviorSection(automation)
    RoutineCustomizationSection(automation)
}

/** A trigger chip: localized label plus its most meaningful config value. */
@Composable
private fun triggerChip(trigger: Trigger): String {
    val label = stringResource(triggerLabel(trigger.type))
    val detail = trigger.config["time"]
        ?: trigger.config["level"]
        ?: trigger.config["state"]
        ?: trigger.config["threshold"]
    return if (detail.isNullOrBlank()) label else "$label · $detail"
}

/** The exit footnote: revert behaviour, or the number of custom exit actions. */
@Composable
private fun routineFootnote(automation: Automation): String? = when {
    automation.revertOnExit -> stringResource(R.string.flow_reverts_auto)
    automation.exitActions.isNotEmpty() ->
        stringResource(R.string.flow_exit_actions, automation.exitActions.size)
    else -> null
}

/**
 * Category of a routine: its first trigger, else its first action, else
 * Advanced (§5.2). Expressed once here and reused by the dashboard, details
 * and widgets through [automationCategory].
 */
internal fun automationCategory(automation: Automation): NexaFlowCategory =
    automation.triggers.firstOrNull()?.let { triggerCategory(it.type) }
        ?: automation.actions.firstOrNull()?.let { actionCategory(it.type) }
        ?: NexaFlowCategory.ADVANCED

/** Trigger type → Ink category. Time/schedule/display/sleep all read as TIME. */
internal fun triggerCategory(type: TriggerType): NexaFlowCategory = when (type) {
    TriggerType.TIME,
    TriggerType.CALENDAR,
    TriggerType.DARK_MODE,
    TriggerType.DND_STATE,
    TriggerType.STAY_AWAKE_STATE,
    TriggerType.AUTO_BRIGHTNESS_STATE,
    TriggerType.SCREEN_TIMEOUT_CHANGED,
    TriggerType.BRIGHTNESS_LEVEL,
    TriggerType.SCREEN_ROTATION_STATE,
    TriggerType.AUTO_ROTATE,
    TriggerType.ALARM_SET_CHANGED -> NexaFlowCategory.TIME

    TriggerType.LOCATION,
    TriggerType.LOCATION_STATE,
    TriggerType.DEVICE,
    TriggerType.SENSOR -> NexaFlowCategory.LOCATION

    TriggerType.BATTERY,
    TriggerType.BATTERY_TEMPERATURE,
    TriggerType.POWER_SAVER,
    TriggerType.CHARGER,
    TriggerType.STORAGE_LOW,
    TriggerType.DATA_SAVER_STATE -> NexaFlowCategory.POWER

    TriggerType.CONNECTIVITY,
    TriggerType.WIFI_CONNECTED,
    TriggerType.MOBILE_DATA_CONNECTED,
    TriggerType.HOTSPOT,
    TriggerType.BLUETOOTH_DEVICE,
    TriggerType.BLUETOOTH_STATE,
    TriggerType.NETWORK_MODE,
    TriggerType.WIFI_STATE,
    TriggerType.NFC_STATE,
    TriggerType.NFC_TAG_SCANNED,
    TriggerType.WIFI_SIGNAL_STRENGTH,
    TriggerType.CELL_SIGNAL_STRENGTH,
    TriggerType.USB_CONNECTED,
    TriggerType.HDMI_CONNECTED,
    TriggerType.ETHERNET_CONNECTED,
    TriggerType.VPN_CONNECTED,
    TriggerType.AIRPLANE_MODE,
    TriggerType.DATA_ROAMING_STATE,
    TriggerType.TIMEZONE_CHANGED -> NexaFlowCategory.CONNECTIVITY

    TriggerType.NOTIFICATION,
    TriggerType.APPLICATION,
    TriggerType.APP_INSTALLED,
    TriggerType.SMS,
    TriggerType.PLUGIN_EVENT -> NexaFlowCategory.NOTIFICATIONS

    TriggerType.RINGER_MODE,
    TriggerType.VOLUME_CHANGED,
    TriggerType.MEDIA_PLAYING,
    TriggerType.HEADPHONE,
    TriggerType.CALL_STATE,
    TriggerType.INCOMING_CALL -> NexaFlowCategory.SOUND

    else -> NexaFlowCategory.ADVANCED
}

/**
 * Action type → Ink category, used only as the fallback when a routine has no
 * triggers. The name heuristic keeps this file independent of the builder's
 * action catalogue while staying deterministic and testable.
 */
internal fun actionCategory(type: com.nexaflow.domain.models.ActionType): NexaFlowCategory {
    val name = type.name
    return when {
        name.contains("VOLUME") || name.contains("SOUND") || name.contains("RINGER") ||
            name.contains("MEDIA") || name.contains("DND") -> NexaFlowCategory.SOUND
        name.contains("WIFI") || name.contains("BLUETOOTH") || name.contains("NFC") ||
            name.contains("DATA") || name.contains("NETWORK") || name.contains("AIRPLANE") ||
            name.contains("HOTSPOT") || name.contains("VPN") -> NexaFlowCategory.CONNECTIVITY
        name.contains("BATTERY") || name.contains("POWER") -> NexaFlowCategory.POWER
        name.contains("LOCATION") || name.contains("GPS") -> NexaFlowCategory.LOCATION
        name.contains("NOTIFICATION") || name.contains("SMS") || name.contains("APP") ->
            NexaFlowCategory.NOTIFICATIONS
        name.contains("BRIGHTNESS") || name.contains("DISPLAY") || name.contains("ROTATION") ||
            name.contains("DARK") || name.contains("THEME") || name.contains("SCREEN") ->
            NexaFlowCategory.TIME
        else -> NexaFlowCategory.ADVANCED
    }
}
@Composable
internal fun TriggerApps(trigger: Trigger) {
    val packages = trigger.config.selectedPackages()
    if (packages.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        packages.forEach { packageName ->
            val app = rememberInstalledAppPresentation(packageName)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                app.icon?.let { icon ->
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp).clip(CircleShape)
                    )
                }
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
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

internal fun Automation.exitBehaviorItemCount(): Int =
    (if (revertOnExit) 1 else 0) +
        exitActions.size +
        actions.count { action ->
            action.endBehavior?.mode?.let { it != EndMode.LEAVE } == true
        }

private fun Map<String, String>.selectedPackages(): List<String> =
    (this["packages"] ?: this["package"] ?: "")
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

@Composable
private fun actionDisplayText(action: Action): String {
    val (titleRes, _, _) = actionPresentation(action.type)
    val toggleValue = action.config["enabled"] ?: action.config["autoRotate"]
    val setting = toggleValue?.let { enabled ->
        stringResource(
            if (enabled.equals("true", ignoreCase = true)) {
                R.string.task_details_enabled
            } else {
                R.string.task_details_disabled
            }
        )
    }
    return listOf(stringResource(titleRes), setting)
        .filterNotNull()
        .joinToString(" · ")
}

/** Exact, per-action end behavior shown in task cards; null means leave unchanged. */
@Composable
internal fun taskEndBehaviorDetail(action: Action): String {
    val behavior = action.endBehavior ?: return ""
    val label = when (behavior.mode) {
        EndMode.LEAVE -> return ""
        EndMode.REVERT -> stringResource(R.string.task_details_end_revert)
        EndMode.RERUN -> stringResource(R.string.task_details_end_rerun)
        EndMode.SET_VALUE -> {
            if (action.type in EndBehaviorCatalog.toggleActions) {
                stringResource(
                    if (behavior.config["enabled"] == "true") {
                        R.string.task_details_enabled
                    } else {
                        R.string.task_details_disabled
                    }
                )
            } else {
                stringResource(R.string.task_details_end_set_value)
            }
        }
    }
    return stringResource(R.string.task_details_end_behavior, label)
}

/** "Next run · 8:00 PM · Last run · 2 h ago" meta line under the title. */
@Composable
private fun RoutineMetaLine(
    nextRun: String?,
    lastRunAt: Long?
) {
    // Last-run is shown even when the routine is disabled;
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

/** "Next · today 8:00 PM" for enabled time triggers, null otherwise — cached per automation to avoid per-frame recomputation. */
@Composable
private fun nextRunText(automation: Automation): String? {
    if (!automation.enabled) return null
    val trigger = automation.triggers.firstOrNull { it.type == TriggerType.TIME } ?: return null
    val context = LocalContext.current
    // Resolve localized labels through Compose so configuration changes invalidate this composable.
    val todayLabel = stringResource(R.string.today)
    val tomorrowLabel = stringResource(R.string.tomorrow)
    val nextRunPrefixFormat = stringResource(R.string.next_run_prefix)
    // Cache per trigger config; recomputes only when triggers change, not on every recomposition
    return remember(automation.triggers, automation.enabled, todayLabel, tomorrowLabel, nextRunPrefixFormat) {
        val nowMillis = System.currentTimeMillis()
        val zone = TimeTriggerCalculator.resolveZone(trigger.config) ?: return@remember null
        val next = TimeTriggerCalculator.nextFireTime(
            config = trigger.config,
            fromMillis = nowMillis,
            zone = zone
        ) ?: return@remember null
        val nextTime = Instant.ofEpochMilli(next).atZone(zone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val timeText = android.text.format.DateFormat.getTimeFormat(context).apply {
            timeZone = java.util.TimeZone.getTimeZone(zone)
        }.format(java.util.Date(next))
        val dayPrefix = when (nextTime.toLocalDate()) {
            now.toLocalDate() -> todayLabel
            now.toLocalDate().plusDays(1) -> tomorrowLabel
            else -> nextTime.format(DateTimeFormatter.ofPattern("MMM d"))
        }
        String.format(nextRunPrefixFormat, "$dayPrefix $timeText")
    }
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
    val actionText = when {
        automation.actions.isEmpty() -> stringResource(R.string.summary_no_actions)
        automation.actions.size == 1 -> actionDisplayText(automation.actions.first())
        else -> stringResource(R.string.summary_actions_count, automation.actions.size)
    }
    return stringResource(R.string.summary_template, triggerText, actionText)
}

internal fun triggerLabel(type: TriggerType): Int = when (type) {
    TriggerType.TIME -> R.string.trigger_time
    TriggerType.BATTERY -> R.string.trigger_battery
    TriggerType.APPLICATION -> R.string.trigger_app
    TriggerType.DEVICE -> R.string.trigger_device
    TriggerType.CONNECTIVITY -> R.string.trigger_connectivity
    TriggerType.WIFI_CONNECTED -> R.string.trigger_type_wifi_connected
    TriggerType.MOBILE_DATA_CONNECTED -> R.string.trigger_type_mobile_data
    TriggerType.HOTSPOT -> R.string.trigger_hotspot
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
    TriggerType.INCOMING_CALL -> R.string.trigger_type_incoming_call
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
    TriggerType.WEAR_EVENT -> R.string.trigger_wear_event
    TriggerType.PLUGIN_EVENT -> R.string.trigger_plugin_event
}

/** Design-time preview of a routine card. */
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
            summary = "Time · Brightness",
            isRunning = false,
            expanded = true,
            menuExpanded = false,
            onRun = {},
            onEdit = {},
            onDelete = {},
            onShare = {},
            onToggle = {},
            onToggleToast = { _, _ -> },
            onExpandedChange = {},
            onLongClick = {},
            onDismissMenu = {},
            modifier = Modifier
        )
    }
}
