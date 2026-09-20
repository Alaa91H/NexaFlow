package com.nexaflow.wear.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.ToggleButton
import com.nexaflow.wear.R
import com.nexaflow.wear.data.WearAutomationDto

/**
 * Main list screen: shows all automations received from the phone in a
 * [ScalingLazyColumn] suitable for a round or square watch face.
 */
@Composable
fun AutomationListScreen(
    state: WearUiState,
    onRunNow: (WearAutomationDto) -> Unit,
    onToggle: (WearAutomationDto, Boolean) -> Unit,
    onOpenDetail: (WearAutomationDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is WearUiState.Connecting -> ConnectingPlaceholder(modifier)
        is WearUiState.Empty -> EmptyPlaceholder(modifier)
        is WearUiState.Loaded -> AutomationList(
            automations = state.automations,
            runningId = null,
            onRunNow = onRunNow,
            onToggle = onToggle,
            onOpenDetail = onOpenDetail,
            modifier = modifier,
        )
        is WearUiState.Running -> AutomationList(
            automations = state.automations,
            runningId = state.runningId,
            onRunNow = onRunNow,
            onToggle = onToggle,
            onOpenDetail = onOpenDetail,
            modifier = modifier,
        )
    }
}

@Composable
private fun AutomationList(
    automations: List<WearAutomationDto>,
    runningId: String?,
    onRunNow: (WearAutomationDto) -> Unit,
    onToggle: (WearAutomationDto, Boolean) -> Unit,
    onOpenDetail: (WearAutomationDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScalingLazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.wear_automations_title),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(automations, key = { it.id }) { automation ->
            AutomationCard(
                automation = automation,
                isRunning = runningId == automation.id,
                onRunNow = { onRunNow(automation) },
                onToggle = { onToggle(automation, it) },
                onOpenDetail = { onOpenDetail(automation) },
            )
        }
    }
}

@Composable
private fun AutomationCard(
    automation: WearAutomationDto,
    isRunning: Boolean,
    onRunNow: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Automation name
        Text(
            text = automation.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        // Enable / disable toggle
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
                style = MaterialTheme.typography.labelSmall,
            )
        }

        // Run now button (or progress indicator while running)
        if (isRunning) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else {
            Button(
                onClick = onRunNow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.wear_run_now),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ConnectingPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.wear_connecting),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.wear_no_automations),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
