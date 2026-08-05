package com.nexaflow.feature.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.iconVector
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Profile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeDetailsScreen(navController: NavController) {
    val viewModel: ModeDetailsViewModel = hiltViewModel()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val memberRoutines by viewModel.memberRoutines.collectAsStateWithLifecycle()
    val allAutomations by viewModel.allAutomations.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf(false) }

    val current = profile
    if (current == null) {
        // Mode was deleted or is still loading: leave.
        return
    }

    val accent = Color(current.color)

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.mode_details_title),
                onBack = { navController.popBackStack() },
                containerColor = accent,
                contentColor = Color.White,
                actions = {
                    IconButton(onClick = { deleteTarget = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ModeHeroCard(
                    profile = current,
                    memberCount = memberRoutines.size,
                    onToggle = { viewModel.toggleMode(it) }
                )
            }
            item {
                SectionHeader(
                    text = stringResource(R.string.routines_in_mode),
                    trailing = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.add_routines)
                            )
                        }
                    }
                )
            }
            if (memberRoutines.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Bolt,
                        title = stringResource(R.string.no_routines_in_mode),
                        subtitle = stringResource(R.string.add_routines)
                    )
                }
            }
            items(memberRoutines, key = { it.id }) { automation ->
                MemberRoutineCard(
                    automation = automation,
                    onToggle = { viewModel.toggleRoutine(automation, it) },
                    onClick = { navController.navigate("automation_details/${automation.id}") }
                )
            }
        }
    }

    if (showAddDialog) {
        AddRoutinesDialog(
            profile = current,
            automations = allAutomations,
            onDismiss = { showAddDialog = false },
            onSave = { ids ->
                viewModel.setMemberRoutines(ids)
                showAddDialog = false
            }
        )
    }

    if (deleteTarget) {
        AlertDialog(
            onDismissRequest = { deleteTarget = false },
            title = { Text(stringResource(R.string.delete_profile_title)) },
            text = { Text(stringResource(R.string.delete_profile_text, current.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMode()
                    deleteTarget = false
                    navController.popBackStack()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/** Samsung-style hero: the mode's color fills the card, white icon + text + master switch. */
@Composable
private fun ModeHeroCard(
    profile: Profile,
    memberCount: Int,
    onToggle: (Boolean) -> Unit
) {
    val accent = Color(profile.color)
    NexaFlowCard(
        border = false,
        containerColor = accent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(color = Color.White.copy(alpha = 0.22f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector(profile.icon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = profile.description.ifBlank { stringResource(R.string.no_description) },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.active_count, memberCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(if (profile.active) R.string.mode_on else R.string.mode_off),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Switch(
                    checked = profile.active,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.White.copy(alpha = 0.45f),
                        checkedBorderColor = Color.White.copy(alpha = 0.6f),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.85f),
                        uncheckedTrackColor = Color.Transparent,
                        uncheckedBorderColor = Color.White.copy(alpha = 0.6f)
                    )
                )
            }
        }
    }
}

/** A routine that belongs to this mode, with a direct on/off switch. */
@Composable
private fun MemberRoutineCard(
    automation: Automation,
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
                    text = automation.description.ifBlank { stringResource(R.string.no_description) },
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

/** Samsung-style add-routines picker: checkbox list of all routines. */
@Composable
private fun AddRoutinesDialog(
    profile: Profile,
    automations: List<Automation>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var selectedIds by remember { mutableStateOf(profile.automationIds.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.automations_in, profile.name)) },
        text = {
            if (automations.isEmpty()) {
                Text(stringResource(R.string.no_automations_yet))
            } else {
                Column {
                    LazyColumn(modifier = Modifier.size(320.dp)) {
                        items(automations, key = { it.id }) { automation ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = automation.id in selectedIds,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) selectedIds + automation.id
                                        else selectedIds - automation.id
                                    }
                                )
                                Text(
                                    text = automation.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedIds.toList()) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
