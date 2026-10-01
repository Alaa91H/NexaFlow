package com.nexaflow.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.domain.models.SmsActivityEvent
import java.text.DateFormat
import java.util.Date

enum class ActivitySection { EXECUTIONS, BLOCKED_CALLS, SMS }

@Composable
fun ActivityHistoryScreen(navController: NavController) {
    var selected by remember { mutableStateOf(ActivitySection.EXECUTIONS) }
    Column(Modifier.fillMaxSize()) {
        ActivitySectionTabs(selected, onSelect = { selected = it })
        when (selected) {
            ActivitySection.EXECUTIONS -> HistoryScreen(navController)
            ActivitySection.BLOCKED_CALLS -> BlockedCallsScreen(navController)
            ActivitySection.SMS -> SmsActivityContent()
        }
    }
}

@Composable
internal fun ActivitySectionTabs(selected: ActivitySection, onSelect: (ActivitySection) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActivitySection.entries.forEach { section ->
            FilterChip(
                selected = section == selected,
                onClick = { onSelect(section) },
                label = { Text(stringResource(when (section) {
                    ActivitySection.EXECUTIONS -> R.string.activity_executions
                    ActivitySection.BLOCKED_CALLS -> R.string.activity_blocked_calls
                    ActivitySection.SMS -> R.string.activity_sms
                })) },
                modifier = Modifier.testTag("activity_tab_${section.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun SmsActivityContent(viewModel: SmsActivityViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    var showClearConfirmation by remember { mutableStateOf(false) }
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.sms_activity_clear_title)) },
            text = { Text(stringResource(R.string.sms_activity_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = { showClearConfirmation = false; viewModel.clear() }) {
                    Text(stringResource(R.string.sms_activity_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text(stringResource(R.string.sms_activity_cancel)) }
            }
        )
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { showClearConfirmation = true }, enabled = events.isNotEmpty()) {
            Text(stringResource(R.string.sms_activity_clear))
        }
    }
    if (events.isEmpty()) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().testTag("sms_activity_empty")) {
            Text(stringResource(R.string.sms_activity_empty), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            items(events, key = SmsActivityEvent::id) { event ->
                com.nexaflow.core.ui.NexaFlowCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(event.automationName ?: stringResource(R.string.sms_activity_automation), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (event.eventType == "OUTGOING_ACTION") R.string.sms_activity_outgoing else R.string.sms_activity_incoming))
                        Text(event.outcome, style = MaterialTheme.typography.labelMedium)
                        Text(DateFormat.getDateTimeInstance().format(Date(event.occurredAt)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
