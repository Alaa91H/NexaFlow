package com.nexaflow.feature.builder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.nexaflow.core.ui.ToggleRow

private data class ActionOption(val title: String, val subtitle: String, val icon: ImageVector, val color: Color)

private val actionOptions = listOf(
    ActionOption("Brightness", "Set screen brightness", Icons.Filled.FlashOn, Color(0xFF1B62B7)),
    ActionOption("Volume", "Change media volume", Icons.Filled.VolumeUp, Color(0xFF7A5BD1)),
    ActionOption("Do Not Disturb", "Toggle DND mode", Icons.Filled.DoNotDisturb, Color(0xFFE5533D)),
    ActionOption("Open app", "Launch an application", Icons.Filled.Add, Color(0xFF2FA84F)),
    ActionOption("Notification", "Send a notification", Icons.Filled.NotificationImportant, Color(0xFFE8A33D)),
    ActionOption("Wi-Fi", "Control Wi-Fi state", Icons.Filled.Wifi, Color(0xFF13A5A8))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationBuilderScreen(navController: NavController) {
    var name by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("Time") }
    var batteryCondition by remember { mutableStateOf(false) }
    var timeRangeCondition by remember { mutableStateOf(false) }
    val selectedActions = remember { mutableSetOf<ActionOption>() }

    val triggerOptions = listOf("Time", "App", "Device", "Connectivity")

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = "New Automation",
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = "Save")
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Name", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(text = "e.g. Morning Routine") },
                        singleLine = true
                    )
                }
            }
            NexaFlowCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconBadge(icon = Icons.Filled.Palette, containerColor = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Icon", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Choose an icon for this automation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(onClick = { navController.navigate("icon_picker") }) {
                        Icon(imageVector = Icons.Filled.Bolt, contentDescription = "Pick icon")
                    }
                }
            }
            SectionHeader(text = "WHEN")
            NexaFlowCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    triggerOptions.forEach { option ->
                        FilterChip(
                            selected = trigger == option,
                            onClick = { trigger = option },
                            label = { Text(text = option) }
                        )
                    }
                }
            }
            SectionHeader(text = "CONDITIONS")
            NexaFlowCard {
                ToggleRow(
                    icon = Icons.Filled.Bolt,
                    title = "Battery above 20%",
                    subtitle = "Only run when battery is above 20%",
                    checked = batteryCondition,
                    onCheckedChange = { batteryCondition = it }
                )
                ToggleRow(
                    icon = Icons.Filled.Schedule,
                    title = "Within time range",
                    subtitle = "Only run between the configured hours",
                    checked = timeRangeCondition,
                    onCheckedChange = { timeRangeCondition = it }
                )
            }
            SectionHeader(text = "THEN")
            NexaFlowCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    actionOptions.forEach { option ->
                        val checked = option in selectedActions
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedActions.remove(option) else selectedActions.add(option)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconBadge(icon = option.icon, containerColor = option.color, size = 36)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = option.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = option.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Icon(
                                imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Add,
                                contentDescription = null,
                                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Save Automation")
            }
        }
    }
}
