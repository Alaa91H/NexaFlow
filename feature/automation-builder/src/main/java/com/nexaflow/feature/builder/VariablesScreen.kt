package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.domain.models.GlobalVariable

/**
 * Samsung-style manager for Tasker-like global variables. Every variable is
 * referenced inside action texts as `%NAME` and resolved by the engine when
 * the task runs.
 */
@Composable
fun VariablesScreen(navController: NavController) {
    val viewModel: VariablesViewModel = hiltViewModel()
    val variables by viewModel.variables.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GlobalVariable?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.variables_title),
                onBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                NexaFlowCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconBadge(
                            icon = Icons.Filled.Functions,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.variables_help_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.variables_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
            if (variables.isEmpty()) {
                item {
                    NexaFlowCard {
                        Text(
                            text = stringResource(R.string.variables_empty),
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            } else {
                items(variables, key = { it.id }) { variable ->
                    NexaFlowCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "%${variable.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (variable.sensitive) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = stringResource(R.string.variable_sensitive),
                                            modifier = Modifier.padding(start = 6.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                Text(
                                    text = variable.value.ifBlank { stringResource(R.string.variables_empty_value) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (variable.value.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = { editing = variable }) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.edit),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            IconButton(onClick = { viewModel.delete(variable.id) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.delete_variable),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Text(
                        text = stringResource(R.string.add_variable),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }

    if (showAdd) {
        VariableEditDialog(
            initialName = "",
            initialValue = "",
            initialSensitive = false,
            onSave = { name, value, sensitive ->
                viewModel.add(name, value, sensitive)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    editing?.let { variable ->
        VariableEditDialog(
            initialName = variable.name,
            initialValue = variable.value,
            initialSensitive = variable.sensitive,
            onSave = { name, value, sensitive ->
                viewModel.update(variable.id, name, value, sensitive)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun VariableEditDialog(
    initialName: String,
    initialValue: String,
    initialSensitive: Boolean,
    onSave: (String, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    // Keyed on the initial values so editing a different variable never shows
    // the previous one's stale state (the dialog stays in the same slot).
    var name by remember(initialName) { mutableStateOf(initialName) }
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    var sensitive by remember(initialSensitive) { mutableStateOf(initialSensitive) }
    val nameValid = name.trim().matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialName.isEmpty()) stringResource(R.string.add_variable)
                else stringResource(R.string.edit)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.variable_name)) },
                    placeholder = { Text(text = "HomeAddress") },
                    singleLine = true,
                    isError = name.isNotEmpty() && !nameValid,
                    supportingText = if (name.isNotEmpty() && !nameValid) {
                        { Text(text = stringResource(R.string.variable_name_error)) }
                    } else {
                        null
                    }
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.variable_value)) },
                    placeholder = { Text(text = stringResource(R.string.variables_value_hint)) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.variable_sensitive),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.variable_sensitive_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Switch(checked = sensitive, onCheckedChange = { sensitive = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, value, sensitive) },
                enabled = nameValid
            ) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}
