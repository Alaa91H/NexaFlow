package com.nexaflow.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.nexaflow.core.agentruntime.AgentDefinition
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar

object ManagedAgentDestination {
    const val ROUTE = "managed_ai_agents"
    const val RUN_ROUTE = "managed_ai_agent_run/{agentId}"
    fun runRoute(agentId: String): String = "managed_ai_agent_run/${android.net.Uri.encode(agentId)}"
}

@Composable
fun ManagedAgentScreen(
    navController: NavController,
    viewModel: ManagedAgentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val pendingDelete = state.definitions.firstOrNull { it.id == pendingDeleteId }
    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.managed_agent_title),
                subtitle = stringResource(R.string.managed_agent_subtitle),
                onBack = { navController.popBackStack() },
                actions = {
                    if (state.draft == null) {
                        TextButton(onClick = viewModel::createNew) {
                            Text(stringResource(R.string.managed_agent_create))
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.errorCode?.let { code ->
                item(key = "managed_agent_error") {
                    NexaFlowCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            text = stringResource(
                                if (code == "invalid_name" || code == "invalid_budget_or_policy") {
                                    R.string.managed_agent_invalid
                                } else {
                                    R.string.agent_operation_failed
                                }
                            ),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            state.draft?.let { draft ->
                item(key = "managed_agent_editor") {
                    ManagedAgentEditor(
                        state = state,
                        viewModel = viewModel,
                        onCancel = viewModel::cancelEdit,
                        onSave = viewModel::save,
                        onDelete = { pendingDeleteId = draft.id }
                    )
                }
            } ?: run {
                if (state.definitions.isEmpty()) {
                    item(key = "managed_agent_empty") {
                        NexaFlowCard {
                            Text(stringResource(R.string.managed_agent_empty))
                            Button(onClick = viewModel::createNew) {
                                Text(stringResource(R.string.managed_agent_create))
                            }
                        }
                    }
                } else {
                    items(state.definitions, key = AgentDefinition::id) { definition ->
                        ManagedAgentCard(
                            definition = definition,
                            onEdit = { viewModel.edit(definition) },
                            onRun = {
                                navController.navigate(ManagedAgentDestination.runRoute(definition.id))
                            },
                            onDelete = { pendingDeleteId = definition.id }
                        )
                    }
                }
            }
        }
    }
    pendingDelete?.let { definition ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.managed_agent_delete_title)) },
            text = { Text(stringResource(R.string.managed_agent_delete_message, definition.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(definition)
                    if (state.draft?.id == definition.id) viewModel.cancelEdit()
                    pendingDeleteId = null
                }) { Text(stringResource(R.string.managed_agent_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.managed_agent_cancel))
                }
            }
        )
    }
}

@Composable
private fun ManagedAgentCard(
    definition: AgentDefinition,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit
) {
    NexaFlowCard {
        Text(definition.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (definition.description.isNotBlank()) {
            Text(definition.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = stringResource(
                if (definition.enabled) R.string.managed_agent_enabled
                else R.string.managed_agent_disabled
            ),
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = stringResource(
                R.string.managed_agent_tool_summary,
                definition.policy.allowedToolNames.size,
                definition.policy.approvalRequiredToolNames.size
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRun, enabled = definition.enabled) {
                Text(stringResource(R.string.managed_agent_run))
            }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.managed_agent_edit)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.managed_agent_delete)) }
        }
    }
}

@Composable
private fun ManagedAgentEditor(
    state: ManagedAgentUiState,
    viewModel: ManagedAgentViewModel,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    val draft = state.draft ?: return
    val update = viewModel::updateDraft
    NexaFlowCard {
        Text(
            stringResource(
                if (draft.id == null) R.string.managed_agent_create
                else R.string.managed_agent_edit
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
            value = draft.name,
            onValueChange = { value -> update { it.copy(name = value.take(AgentDefinition.MAX_NAME_CHARACTERS)) } },
            label = { Text(stringResource(R.string.agent_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.description,
            onValueChange = { value -> update { it.copy(description = value.take(AgentDefinition.MAX_DESCRIPTION_CHARACTERS)) } },
            label = { Text(stringResource(R.string.managed_agent_description)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.systemInstructions,
            onValueChange = { value -> update { it.copy(systemInstructions = value.take(AgentDefinition.MAX_INSTRUCTION_CHARACTERS)) } },
            label = { Text(stringResource(R.string.managed_agent_instructions)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Text(stringResource(R.string.managed_agent_provider), style = MaterialTheme.typography.titleSmall)
        FlowChips {
            FilterChip(
                selected = draft.providerProfileId == null,
                onClick = { update { it.copy(providerProfileId = null, modelId = "") } },
                label = { Text(stringResource(R.string.managed_agent_provider_default)) }
            )
            state.providerProfiles.forEach { profile ->
                FilterChip(
                    selected = draft.providerProfileId == profile.id,
                    onClick = { update { it.copy(providerProfileId = profile.id, modelId = profile.modelId) } },
                    label = { Text(profile.displayName) }
                )
            }
        }
        OutlinedTextField(
            value = draft.modelId,
            onValueChange = {},
            label = { Text(stringResource(R.string.managed_agent_model)) },
            singleLine = true,
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(stringResource(R.string.managed_agent_tools), style = MaterialTheme.typography.titleSmall)
        if (state.tools.isEmpty()) {
            Text(stringResource(R.string.managed_agent_no_tools), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowChips {
            state.tools.forEach { tool ->
                FilterChip(
                    selected = tool.name in draft.allowedToolNames,
                    onClick = {
                        update { value ->
                            val names = value.allowedToolNames.toggle(tool.name)
                            value.copy(
                                allowedToolNames = names,
                                approvalRequiredToolNames = value.approvalRequiredToolNames intersect names
                            )
                        }
                    },
                    label = { Text(tool.name) }
                )
            }
        }
        Text(stringResource(R.string.managed_agent_approval_required), style = MaterialTheme.typography.titleSmall)
        FlowChips {
            draft.allowedToolNames.forEach { name ->
                FilterChip(
                    selected = name in draft.approvalRequiredToolNames,
                    onClick = {
                        update { value -> value.copy(approvalRequiredToolNames = value.approvalRequiredToolNames.toggle(name)) }
                    },
                    label = { Text(name) }
                )
            }
        }
        Text(stringResource(R.string.managed_agent_budget), style = MaterialTheme.typography.titleSmall)
        BudgetField(draft.maxTurns, R.string.managed_agent_turn_limit) { value -> update { it.copy(maxTurns = value) } }
        BudgetField(draft.maxToolCalls, R.string.managed_agent_tool_limit) { value -> update { it.copy(maxToolCalls = value) } }
        BudgetField(draft.maxDurationSeconds, R.string.managed_agent_duration_seconds) { value -> update { it.copy(maxDurationSeconds = value) } }
        BudgetField(draft.maxOutputCharacters, R.string.managed_agent_output_limit) { value -> update { it.copy(maxOutputCharacters = value) } }
        BudgetField(draft.maxCostMicros, R.string.managed_agent_cost_micros) { value -> update { it.copy(maxCostMicros = value) } }
        Text(
            stringResource(R.string.managed_agent_cost_unknown_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.managed_agent_cloud_data), modifier = Modifier.weight(1f))
            Switch(checked = draft.allowCloudData, onCheckedChange = { checked -> update { it.copy(allowCloudData = checked) } })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.managed_agent_enabled), modifier = Modifier.weight(1f))
            Switch(checked = draft.enabled, onCheckedChange = { checked -> update { it.copy(enabled = checked) } })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, enabled = !state.operationInProgress) {
                Text(stringResource(R.string.managed_agent_save))
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.managed_agent_cancel)) }
            if (draft.id != null) {
                TextButton(onClick = onDelete, enabled = !state.operationInProgress) {
                    Text(stringResource(R.string.managed_agent_delete))
                }
            }
        }
    }
}

@Composable
private fun FlowChips(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) { content() }
}

@Composable
private fun BudgetField(value: String, label: Int, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate -> onValueChange(candidate.filter(Char::isDigit).take(12)) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value
