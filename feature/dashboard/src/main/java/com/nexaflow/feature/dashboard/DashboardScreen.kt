package com.nexaflow.feature.dashboard

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.ui.EmptyState
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.StatCard
import com.nexaflow.core.ui.StatusPill
import com.nexaflow.core.ui.iconVector
import com.nexaflow.domain.models.Automation

@Composable
fun DashboardScreen(navController: NavController) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val automations by viewModel.automations.collectAsStateWithLifecycle()
    val activeCount by viewModel.activeCount.collectAsStateWithLifecycle()
    val scheduledCount by viewModel.scheduledCount.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()

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
                        text = stringResource(R.string.dashboard_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.dashboard_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(onClick = { navController.navigate("settings") }) {
                    Icon(imageVector = Icons.Filled.Settings, contentDescription = stringResource(R.string.dashboard_settings))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    value = activeCount.toString(),
                    label = stringResource(R.string.stat_active),
                    icon = Icons.Filled.Bolt,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = scheduledCount.toString(),
                    label = stringResource(R.string.stat_scheduled),
                    icon = Icons.Filled.Schedule,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = totalCount.toString(),
                    label = stringResource(R.string.stat_total),
                    icon = Icons.Filled.Dashboard,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            SectionHeader(text = stringResource(R.string.section_quick_actions))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(
                    title = stringResource(R.string.quick_new_automation),
                    subtitle = stringResource(R.string.quick_new_automation_sub),
                    icon = Icons.Filled.AddCircle,
                    containerColor = MaterialTheme.colorScheme.primary,
                    onClick = { navController.navigate("automation_builder") },
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    title = stringResource(R.string.quick_capability_center),
                    subtitle = stringResource(R.string.quick_capability_center_sub),
                    icon = Icons.Filled.Bolt,
                    containerColor = Color(0xFF2FA84F),
                    onClick = { navController.navigate("capability_center") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            SectionHeader(text = stringResource(R.string.section_your_automations))
        }
        if (automations.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Dashboard,
                    title = stringResource(R.string.empty_automations_title),
                    subtitle = stringResource(R.string.empty_automations_sub)
                )
            }
        }
        items(automations) { automation ->
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
                icon = iconVector(automation.icon),
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
                    text = stringResource(R.string.status_active),
                    background = Color(0xFFE4F4E9),
                    contentColor = Color(0xFF2FA84F)
                )
            } else {
                StatusPill(
                    text = stringResource(R.string.status_off),
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
