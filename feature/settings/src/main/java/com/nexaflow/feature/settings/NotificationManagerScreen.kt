package com.nexaflow.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SettingRow

private data class NotificationCategory(
    val icon: ImageVector,
    val titleRes: Int,
    val subtitleRes: Int,
    val enabled: Boolean,
    val onToggle: (Boolean) -> Unit,
    val locked: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationManagerScreen(navController: NavController) {
    val viewModel: NotificationManagerViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val categories = listOf(
        NotificationCategory(
            icon = Icons.Filled.Bolt,
            titleRes = R.string.notification_execution,
            subtitleRes = R.string.notification_execution_sub,
            enabled = settings.executionEnabled,
            onToggle = viewModel::setExecutionEnabled,
            locked = !settings.enabled
        ),
        NotificationCategory(
            icon = Icons.Filled.Schedule,
            titleRes = R.string.notification_reminders,
            subtitleRes = R.string.notification_reminders_sub,
            enabled = settings.remindersEnabled,
            onToggle = viewModel::setRemindersEnabled,
            locked = !settings.enabled
        ),
        NotificationCategory(
            icon = Icons.Filled.Visibility,
            titleRes = R.string.notification_monitoring,
            subtitleRes = R.string.notification_monitoring_sub,
            enabled = settings.monitoringEnabled,
            onToggle = viewModel::setMonitoringEnabled,
            locked = !settings.enabled
        )
    )

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.notification_manager),
                onBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.notification_manager_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item {
                NexaFlowCard {
                    SettingRow(
                        icon = if (settings.enabled) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                        title = stringResource(R.string.notification_master),
                        subtitle = stringResource(R.string.notification_master_sub),
                        trailing = {
                            Switch(
                                checked = settings.enabled,
                                onCheckedChange = viewModel::setEnabled
                            )
                        }
                    )
                }
            }
            item {
                NexaFlowCard(modifier = Modifier.padding(top = 8.dp)) {
                    categories.forEach { category ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingRow(
                                icon = category.icon,
                                title = stringResource(category.titleRes),
                                subtitle = stringResource(category.subtitleRes),
                                trailing = {
                                    Switch(
                                        checked = category.enabled,
                                        onCheckedChange = category.onToggle,
                                        enabled = !category.locked
                                    )
                                }
                            )
                        }
                    }
                }
            }
            if (!settings.enabled) {
                item {
                    Text(
                        text = stringResource(R.string.notifications_off_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, start = 4.dp)
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.notifications_global_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 16.dp, start = 4.dp)
                )
            }
        }
    }
}
