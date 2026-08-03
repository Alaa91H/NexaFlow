package com.nexaflow.feature.automations

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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.SettingRow
import com.nexaflow.core.ui.StatusPill
import com.nexaflow.core.ui.ToggleRow

private data class TriggerRow(val title: String, val subtitle: String, val icon: ImageVector, val color: Color)
private data class ActionRow(val title: String, val subtitle: String, val icon: ImageVector, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationDetailsScreen(navController: NavController, automationId: String) {
    var enabled by remember { mutableStateOf(true) }

    val triggers = listOf(
        TriggerRow("Time", "Every day at 06:30", Icons.Filled.FlashOn, Color(0xFF1B62B7))
    )
    val conditions = listOf(
        TriggerRow("Battery", "Above 20%", Icons.Filled.BatteryChargingFull, Color(0xFF2FA84F))
    )
    val actions = listOf(
        ActionRow("Brightness", "Set to 60%", Icons.Filled.FlashOn, Color(0xFF1B62B7)),
        ActionRow("Do Not Disturb", "Turn off", Icons.Filled.DoNotDisturb, Color(0xFFE5533D)),
        ActionRow("Volume", "Set media to 80%", Icons.Filled.VolumeUp, Color(0xFF7A5BD1))
    )

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = "Automation",
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
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
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    IconBadge(icon = Icons.Filled.Bolt, containerColor = Color(0xFFE3EEFA), contentColor = Color(0xFF1B62B7))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Morning Routine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Automation #$automationId",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (enabled) {
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
                    checked = enabled,
                    onCheckedChange = { enabled = it }
                )
            }
            SectionHeader(text = "TRIGGERS")
            NexaFlowCard {
                triggers.forEach { trigger ->
                    SettingRow(
                        icon = trigger.icon,
                        title = trigger.title,
                        subtitle = trigger.subtitle
                    )
                }
            }
            SectionHeader(text = "CONDITIONS")
            NexaFlowCard {
                conditions.forEach { condition ->
                    SettingRow(
                        icon = condition.icon,
                        title = condition.title,
                        subtitle = condition.subtitle
                    )
                }
            }
            SectionHeader(text = "ACTIONS")
            NexaFlowCard {
                actions.forEach { action ->
                    SettingRow(
                        icon = action.icon,
                        title = action.title,
                        subtitle = action.subtitle
                    )
                }
            }
            Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                Text(text = "Run Now", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
