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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.ui.EmptyState
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.SettingRow
import com.nexaflow.core.ui.StatusPill
import com.nexaflow.core.ui.ToggleRow
import com.nexaflow.core.ui.iconVector
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
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
                title = "Automation",
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { viewModel.delete { navController.popBackStack() } }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        val current = automation
        if (current == null) {
            EmptyState(
                icon = Icons.Filled.Bolt,
                title = "Automation not found",
                subtitle = "It may have been deleted."
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
                        if (current.enabled) {
                            StatusPill(
                                text = "Active",
                                background = Color(0xFFE4F4E9),
                                contentColor = Color(0xFF2FA84F)
                            )
                        } else {
                            StatusPill(
                                text = "Off",
                                background = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                NexaFlowCard {
                    ToggleRow(
                        icon = Icons.Filled.Bolt,
                        title = "Enabled",
                        subtitle = "Run this automation",
                        checked = current.enabled,
                        onCheckedChange = { viewModel.toggleEnabled(it) }
                    )
                }
                SectionHeader(text = "TRIGGERS")
                NexaFlowCard {
                    if (current.triggers.isEmpty()) {
                        Text(
                            text = "No triggers configured.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        current.triggers.forEach { trigger ->
                            val (title, subtitle, icon) = triggerPresentation(trigger.type)
                            SettingRow(icon = icon, title = title, subtitle = subtitle, trailing = {
                                Text(
                                    text = trigger.config.entries.joinToString(", ") { "${it.key}=${it.value}" }.ifEmpty { "default" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            })
                        }
                    }
                }
                SectionHeader(text = "CONDITIONS")
                NexaFlowCard {
                    if (current.conditions.isEmpty()) {
                        Text(
                            text = "No conditions configured.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        current.conditions.forEach { condition ->
                            SettingRow(
                                icon = Icons.Filled.Bolt,
                                title = condition.type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                                subtitle = condition.config.entries.joinToString(", ") { "${it.key}=${it.value}" }.ifEmpty { "Always true" }
                            )
                        }
                    }
                }
                SectionHeader(text = "ACTIONS")
                NexaFlowCard {
                    if (current.actions.isEmpty()) {
                        Text(
                            text = "No actions configured.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        current.actions.forEach { action ->
                            val (title, subtitle, icon) = actionPresentation(action.type)
                            SettingRow(icon = icon, title = title, subtitle = subtitle)
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
                        text = if (running) "Running..." else "Run Now",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

private fun triggerPresentation(type: TriggerType): Triple<String, String, ImageVector> = when (type) {
    TriggerType.TIME -> Triple("Time", "Trigger at a scheduled time", Icons.Filled.Schedule)
    TriggerType.APPLICATION -> Triple("App", "Trigger on app event", Icons.Filled.Add)
    TriggerType.DEVICE -> Triple("Device", "Trigger on device state", Icons.Filled.Bolt)
    TriggerType.CONNECTIVITY -> Triple("Connectivity", "Trigger on network change", Icons.Filled.Wifi)
    TriggerType.LOCATION -> Triple("Location", "Trigger on location", Icons.Filled.Place)
}

private fun actionPresentation(type: ActionType): Triple<String, String, ImageVector> = when (type) {
    ActionType.SYSTEM_BRIGHTNESS -> Triple("Brightness", "Set screen brightness", Icons.Filled.FlashOn)
    ActionType.SYSTEM_VOLUME -> Triple("Volume", "Change media volume", Icons.Filled.VolumeUp)
    ActionType.SYSTEM_DND -> Triple("Do Not Disturb", "Toggle DND mode", Icons.Filled.DoNotDisturb)
    ActionType.SYSTEM_SCREEN_ROTATION -> Triple("Screen Rotation", "Toggle rotation", Icons.Filled.ScreenRotation)
    ActionType.SYSTEM_OPEN_APP -> Triple("Open App", "Open an application", Icons.Filled.Add)
    ActionType.SYSTEM_SEND_NOTIFICATION -> Triple("Notification", "Send a notification", Icons.Filled.NotificationImportant)
    ActionType.BATTERY_ALERTS -> Triple("Battery Alert", "Alert at battery level", Icons.Filled.BatteryChargingFull)
    ActionType.BATTERY_CHARGING_NOTIFICATIONS -> Triple("Charging Alert", "Notify on charging", Icons.Filled.BatteryChargingFull)
    ActionType.APPLICATION_LAUNCH_APP -> Triple("Launch App", "Launch an application", Icons.Filled.Add)
    ActionType.APPLICATION_CLOSE_APP -> Triple("Close App", "Close an application", Icons.Filled.Close)
    ActionType.ADVANCED_SHIZUKU -> Triple("Shizuku", "Run via Shizuku", Icons.Filled.Security)
    ActionType.ADVANCED_ROOT -> Triple("Root", "Run via root", Icons.Filled.Lock)
}
