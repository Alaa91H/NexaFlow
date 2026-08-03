package com.nexaflow.feature.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.nexaflow.core.ui.iconVector
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Profile

private data class AccentOption(val color: Color, val label: String)

private val accentOptions = listOf(
    AccentOption(Color(0xFF1B62B7), "Blue"),
    AccentOption(Color(0xFF2FA84F), "Green"),
    AccentOption(Color(0xFFE5533D), "Red"),
    AccentOption(Color(0xFF7A5BD1), "Purple"),
    AccentOption(Color(0xFFE8A33D), "Amber"),
    AccentOption(Color(0xFF13A5A8), "Teal")
)

private val profileIcons = listOf(
    "home", "bolt", "dnd", "wifi", "star", "settings"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(navController: NavController) {
    val viewModel: ProfilesViewModel = hiltViewModel()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val automations by viewModel.automations.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var managingProfile by remember { mutableStateOf<Profile?>(null) }
    var deleteTarget by remember { mutableStateOf<Profile?>(null) }

    Scaffold(
        topBar = { NexaFlowTopBar(title = "Profiles", onBack = { navController.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New Profile") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(text = "PROFILES")
            }
            if (profiles.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Group,
                        title = "No profiles yet",
                        subtitle = "Profiles group your automations so you can apply them in one tap."
                    )
                }
            }
            items(profiles) { profile ->
                ProfileCard(
                    profile = profile,
                    automationCount = automations.count { it.id in profile.automationIds },
                    onToggle = { viewModel.toggleProfile(profile, it) },
                    onManage = { managingProfile = profile },
                    onDelete = { deleteTarget = profile }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateProfileDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { name, description, icon, color ->
                viewModel.createProfile(name, description, icon, color)
                showCreateDialog = false
            }
        )
    }

    managingProfile?.let { profile ->
        ManageAutomationsDialog(
            profile = profile,
            automations = automations,
            onDismiss = { managingProfile = null },
            onSave = { ids ->
                viewModel.setProfileAutomations(profile.id, ids)
                managingProfile = null
            }
        )
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete profile?") },
            text = { Text("\"${profile.name}\" will be removed. Its automations will be disabled.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(profile)
                    deleteTarget = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    automationCount: Int,
    onToggle: (Boolean) -> Unit,
    onManage: () -> Unit,
    onDelete: () -> Unit
) {
    NexaFlowCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(icon = iconVector(profile.icon), containerColor = Color(profile.color))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = profile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = profile.description.ifBlank { "No description" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2
                )
                Text(
                    text = if (profile.active) "Active · $automationCount automation(s)" else "Inactive · $automationCount automation(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (profile.active) Color(0xFF2FA84F) else MaterialTheme.colorScheme.secondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onManage) {
                        Icon(Icons.Filled.PlaylistAdd, contentDescription = "Manage automations")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Switch(checked = profile.active, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
private fun CreateProfileDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, icon: String, color: Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(profileIcons.first()) }
    var selectedColor by remember { mutableStateOf(accentOptions.first().color) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(profileIcons) { icon ->
                        val selected = selectedIcon == icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { selectedIcon = icon },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconVector(icon),
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                Text("Color", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(accentOptions) { option ->
                        val color = option.color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(color = color, shape = CircleShape)
                                .clickable { selectedColor = color },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == color) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, description, selectedIcon, selectedColor.value.toLong()) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ManageAutomationsDialog(
    profile: Profile,
    automations: List<Automation>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var selectedIds by remember { mutableStateOf(profile.automationIds.toMutableSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Automations in ${profile.name}") },
        text = {
            if (automations.isEmpty()) {
                Text("No automations yet. Create one first.")
            } else {
                Column(modifier = Modifier.heightIn(max = 360.dp)) {
                    LazyColumn {
                        items(automations, key = { it.id }) { automation ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!selectedIds.add(automation.id)) {
                                            selectedIds.remove(automation.id)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = automation.id in selectedIds,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedIds.add(automation.id)
                                        else selectedIds.remove(automation.id)
                                    }
                                )
                                Text(text = automation.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedIds.toList()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
