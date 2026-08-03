package com.nexaflow.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.StatCard
import com.nexaflow.core.ui.StatusPill
import com.nexaflow.domain.models.Automation

private val demoAutomations = listOf(
    Automation(
        id = "1",
        name = "Morning Routine",
        description = "Brightness 60% + DND off at 6:30 AM",
        icon = "bolt",
        iconColor = 0xFF1B62B7,
        backgroundColor = 0xFFE3EEFA,
        category = "Routine",
        priority = 1,
        enabled = true,
        triggers = emptyList(),
        conditions = emptyList(),
        actions = emptyList(),
        createdAt = 0L,
        updatedAt = 0L
    ),
    Automation(
        id = "2",
        name = "Low Battery Saver",
        description = "When battery < 20%, enable power saving",
        icon = "battery",
        iconColor = 0xFF2FA84F,
        backgroundColor = 0xFFE4F4E9,
        category = "Battery",
        priority = 2,
        enabled = true,
        triggers = emptyList(),
        conditions = emptyList(),
        actions = emptyList(),
        createdAt = 0L,
        updatedAt = 0L
    ),
    Automation(
        id = "3",
        name = "Silent at Work",
        description = "DND from 9:00 to 17:00 on weekdays",
        icon = "dnd",
        iconColor = 0xFFE5533D,
        backgroundColor = 0xFFFBEAE7,
        category = "DND",
        priority = 3,
        enabled = false,
        triggers = emptyList(),
        conditions = emptyList(),
        actions = emptyList(),
        createdAt = 0L,
        updatedAt = 0L
    )
)

@Composable
fun DashboardScreen(navController: NavController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "NexaFlow",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Automate your device, your way",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(onClick = { navController.navigate("settings") }) {
                    Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    value = "3",
                    label = "Active",
                    icon = Icons.Filled.Bolt,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = "2",
                    label = "Scheduled",
                    icon = Icons.Filled.Schedule,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = "18",
                    label = "Runs today",
                    icon = Icons.Filled.History,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            SectionHeader(text = "QUICK ACTIONS")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(
                    title = "New Automation",
                    subtitle = "Create a flow",
                    icon = Icons.Filled.AddCircle,
                    containerColor = MaterialTheme.colorScheme.primary,
                    onClick = { navController.navigate("automation_builder") },
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    title = "Capability Center",
                    subtitle = "ROM features",
                    icon = Icons.Filled.Home,
                    containerColor = Color(0xFF2FA84F),
                    onClick = { navController.navigate("capability_center") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            SectionHeader(text = "RECENT AUTOMATIONS")
        }
        items(demoAutomations) { automation ->
            AutomationCard(
                automation = automation,
                onClick = { navController.navigate("automation_details/${automation.id}") }
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NexaFlowCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(icon = icon, containerColor = containerColor)
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun AutomationCard(automation: Automation, onClick: () -> Unit) {
    NexaFlowCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(
                icon = Icons.Filled.Bolt,
                containerColor = Color(automation.backgroundColor),
                contentColor = Color(automation.iconColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = automation.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = automation.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1
                )
            }
            if (automation.enabled) {
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
}
