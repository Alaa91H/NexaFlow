package com.nexaflow.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.ui.EmptyState
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.iconVector
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Profile
import com.nexaflow.domain.models.TriggerType

@Composable
fun DashboardScreen(navController: NavController) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val automations by viewModel.automations.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()

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

        // ---- Modes (Samsung-style big colorful cards) ----
        item {
            SectionHeader(text = stringResource(R.string.section_modes))
        }
        if (profiles.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Group,
                    title = stringResource(R.string.no_modes_title),
                    subtitle = stringResource(R.string.no_modes_subtitle)
                )
            }
        }
        items(profiles, key = { it.id }) { profile ->
            ModeCard(
                profile = profile,
                routineCount = automations.count { it.id in profile.automationIds },
                onToggle = { viewModel.toggleProfile(profile, it) },
                onClick = { navController.navigate("profiles") }
            )
        }
        if (profiles.isNotEmpty()) {
            item {
                TextButtonLink(
                    text = stringResource(R.string.manage_modes),
                    onClick = { navController.navigate("profiles") }
                )
            }
        }

        // ---- Routines ----
        item {
            SectionHeader(text = stringResource(R.string.section_routines))
        }
        if (automations.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Bolt,
                    title = stringResource(R.string.empty_automations_title),
                    subtitle = stringResource(R.string.empty_automations_sub)
                )
            }
        }
        items(automations, key = { it.id }) { automation ->
            RoutineCard(
                automation = automation,
                summary = automationSummary(automation),
                onToggle = { viewModel.toggleAutomation(automation, it) },
                onClick = { navController.navigate("automation_details/${automation.id}") }
            )
        }
        item {
            Button(
                onClick = { navController.navigate("automation_builder") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Filled.AddCircle, contentDescription = null)
                Text(
                    text = stringResource(R.string.new_routine),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TextButtonLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    )
}

/** Samsung-style big colorful mode card with an instant on/off switch. */
@Composable
private fun ModeCard(
    profile: Profile,
    routineCount: Int,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val accent = Color(profile.color)
    NexaFlowCard(
        modifier = Modifier.clickable(onClick = onClick),
        border = false
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(
                icon = iconVector(profile.icon),
                containerColor = accent,
                size = 52
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (profile.active) {
                        stringResource(R.string.mode_on, routineCount)
                    } else {
                        stringResource(R.string.mode_off, routineCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (profile.active) Color(0xFF2FA84F) else MaterialTheme.colorScheme.secondary
                )
            }
            Switch(checked = profile.active, onCheckedChange = onToggle)
        }
    }
}

/** Routine card with a natural-language "When … → Then …" summary and a live switch. */
@Composable
private fun RoutineCard(
    automation: Automation,
    summary: String,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
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
                Text(
                    text = automation.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(checked = automation.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun automationSummary(automation: Automation): String {
    val triggerText = if (automation.triggers.isEmpty()) {
        stringResource(R.string.summary_any_trigger)
    } else {
        automation.triggers.map { stringResource(triggerLabel(it.type)) }.joinToString(", ")
    }
    val actionText = if (automation.actions.isEmpty()) {
        stringResource(R.string.summary_no_actions)
    } else {
        stringResource(R.string.summary_actions_count, automation.actions.size)
    }
    return stringResource(R.string.summary_template, triggerText, actionText)
}

private fun triggerLabel(type: TriggerType): Int = when (type) {
    TriggerType.TIME -> R.string.trigger_time
    TriggerType.APPLICATION -> R.string.trigger_app
    TriggerType.DEVICE -> R.string.trigger_device
    TriggerType.CONNECTIVITY -> R.string.trigger_connectivity
    TriggerType.LOCATION -> R.string.trigger_location
    TriggerType.SMS -> R.string.trigger_sms
}
