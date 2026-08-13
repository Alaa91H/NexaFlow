package com.nexaflow.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.ui.EmptyState
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.theme.NexaFlowTheme
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.StatusPill
import com.nexaflow.domain.models.ActionExecutionResult
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.feature.automations.actionPresentation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Samsung-style run-details screen: shows the full execution timeline of one
 * history record — every action with its own outcome, duration and the channel
 * that ran it, plus the run summary (status, channel, total duration).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionDetailsScreen(navController: NavController) {
    val viewModel: ExecutionDetailsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.execution_details_title),
                onBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        val current = uiState.record
        if (uiState.loading) {
            // Keep the previous content frame stable; nothing to render yet.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (current == null) {
            EmptyState(
                icon = Icons.Filled.Bolt,
                title = stringResource(R.string.execution_not_found_title),
                subtitle = stringResource(R.string.execution_not_found_subtitle)
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
                RunSummaryCard(record = current)
                SectionHeader(text = stringResource(R.string.section_timeline))
                NexaFlowCard {
                    if (current.actionResults.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_timeline_detail),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        current.actionResults.forEachIndexed { index, result ->
                            TimelineRow(result = result)
                            if (index < current.actionResults.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Run header: icon, name, result message, status pill, channel and total duration. */
@Composable
private fun RunSummaryCard(record: ExecutionRecord) {
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = remember(locale) { SimpleDateFormat("MMM d, HH:mm:ss", locale) }
    val totalMs = record.actionResults.sumOf { it.durationMs }
    val msLabel = stringResource(R.string.duration_ms)
    val sLabel = stringResource(R.string.duration_s)
    val successColor = NexaFlowTheme.colors.success
    val failColor = MaterialTheme.colorScheme.error
    val successBg = NexaFlowTheme.colors.successContainer
    val failBg = MaterialTheme.colorScheme.errorContainer

    NexaFlowCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconBadge(
                    icon = Icons.Filled.Bolt,
                    containerColor = if (record.success) successBg else failBg,
                    contentColor = if (record.success) successColor else failColor
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.automationName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = record.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 2
                    )
                }
                StatusPill(
                    text = if (record.success) stringResource(R.string.status_success) else stringResource(R.string.status_failed),
                    background = if (record.success) successBg else failBg,
                    contentColor = if (record.success) successColor else failColor
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = timeFormat.format(Date(record.executedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    val channel = record.channel
                    if (channel != null) {
                        Text(
                            text = stringResource(
                                R.string.executed_via,
                                stringResource(channelLabelRes(channel))
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.total_duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = formatDuration(totalMs, msLabel, sLabel),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

/** One executed action: icon, localized name, result message, duration and status. */
@Composable
private fun TimelineRow(result: ActionExecutionResult) {
    val msLabel = stringResource(R.string.duration_ms)
    val sLabel = stringResource(R.string.duration_s)
    val successColor = NexaFlowTheme.colors.success
    val failColor = MaterialTheme.colorScheme.error
    val successBg = NexaFlowTheme.colors.successContainer
    val failBg = MaterialTheme.colorScheme.errorContainer

    val (titleRes, icon) = actionTitleAndIcon(result.actionType)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconBadge(
            icon = icon,
            containerColor = if (result.success) MaterialTheme.colorScheme.primaryContainer else failBg,
            contentColor = if (result.success) MaterialTheme.colorScheme.onPrimaryContainer else failColor,
            size = 40
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 2
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = formatDuration(result.durationMs, msLabel, sLabel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            StatusPill(
                text = if (result.success) stringResource(R.string.status_success) else stringResource(R.string.status_failed),
                background = if (result.success) successBg else failBg,
                contentColor = if (result.success) successColor else failColor
            )
        }
    }
}

/** Localized action title + icon for a stored action-type name. */
@Composable
private fun actionTitleAndIcon(actionType: String): Pair<Int, androidx.compose.ui.graphics.vector.ImageVector> {
    if (actionType == "STATE_RESTORE") {
        return R.string.action_restore_state to Icons.Filled.Security
    }
    val type = runCatching { ActionType.valueOf(actionType) }.getOrNull()
    return if (type != null) {
        val (title, _, icon) = actionPresentation(type)
        title to icon
    } else {
        R.string.action_unknown to Icons.Filled.Schedule
    }
}

/** Formats a duration: "820 ms" below a second, "1.4 s" above. The unit labels
 * are appended by concatenation so translated labels can never break the format. */
fun formatDuration(durationMs: Long, msLabel: String, sLabel: String): String {
    return if (durationMs < 1000) {
        "$durationMs $msLabel"
    } else {
        String.format(Locale.ROOT, "%.1f", durationMs / 1000f) + " $sLabel"
    }
}
