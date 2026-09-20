package com.nexaflow.wear.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Chip
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.ToggleButton
import com.nexaflow.wear.R
import com.nexaflow.wear.data.WearAutomationDto
import java.text.DateFormat
import java.util.Date

/**
 * Detail screen for a single automation: shows full name, last execution
 * status, an enable/disable toggle, and a prominent Run Now button.
 *
 * Navigated to by tapping on the automation name in the list screen.
 */
@Composable
fun AutomationDetailScreen(
    automation: WearAutomationDto,
    isRunning: Boolean,
    onRunNow: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScalingLazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = automation.name,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Last run status chip
        item {
            automation.lastRunAt?.let { runAt ->
                LastRunChip(
                    runAt = runAt,
                    success = automation.lastRunSuccess ?: true,
                    message = automation.lastRunMessage,
                )
            } ?: run {
                Text(
                    text = stringResource(R.string.wear_never_run),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Enable / disable toggle
        item {
            ToggleButton(
                checked = automation.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (automation.enabled) {
                        stringResource(R.string.wear_enabled)
                    } else {
                        stringResource(R.string.wear_disabled)
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        // Run now button
        item {
            Button(
                onClick = onRunNow,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.wear_run_now),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun LastRunChip(
    runAt: Long,
    success: Boolean,
    message: String?,
    modifier: Modifier = Modifier,
) {
    val label = if (success) {
        stringResource(R.string.wear_last_run_success)
    } else {
        stringResource(R.string.wear_last_run_failed)
    }
    val timeString = DateFormat.getTimeInstance(DateFormat.SHORT)
        .format(Date(runAt))
    Chip(
        onClick = {},
        label = {
            Column {
                Text(text = label, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.bodyExtraSmall,
                    maxLines = 1,
                )
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyExtraSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}
