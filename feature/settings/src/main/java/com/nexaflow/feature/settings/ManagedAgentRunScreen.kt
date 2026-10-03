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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.nexaflow.core.agentruntime.AgentRun
import com.nexaflow.core.agentruntime.AgentRunStatus
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar

@Composable
fun ManagedAgentRunScreen(
    navController: NavController,
    viewModel: ManagedAgentRunViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val agent = state.agent
    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = agent?.name ?: stringResource(R.string.managed_agent_run_title),
                subtitle = stringResource(R.string.managed_agent_run_subtitle),
                onBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "run_composer") {
                NexaFlowCard {
                    OutlinedTextField(
                        value = state.local.prompt,
                        onValueChange = viewModel::editPrompt,
                        label = { Text(stringResource(R.string.managed_agent_run_prompt)) },
                        minLines = 3,
                        maxLines = 8,
                        enabled = !state.local.active,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::run,
                            enabled = agent?.enabled == true && !state.local.active && state.local.prompt.isNotBlank()
                        ) { Text(stringResource(R.string.managed_agent_run_start)) }
                        if (state.local.active) {
                            TextButton(onClick = viewModel::cancel) {
                                Text(stringResource(R.string.managed_agent_run_stop))
                            }
                        }
                    }
                }
            }
            state.local.errorCode?.let { error ->
                item(key = "run_error") {
                    NexaFlowCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            text = stringResource(
                                when (error) {
                                    "provider_unavailable" -> R.string.managed_agent_run_provider_unavailable
                                    "cost_unknown" -> R.string.managed_agent_run_cost_unknown
                                    "deadline_exceeded" -> R.string.managed_agent_run_deadline
                                    "approval_invalidated" -> R.string.managed_agent_run_approval_invalid
                                    else -> R.string.managed_agent_run_failed
                                }
                            ),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            if (state.local.active) {
                item(key = "run_active") { Text(stringResource(R.string.managed_agent_run_active)) }
            }
            if (state.local.output.isNotBlank()) {
                item(key = "run_output") {
                    NexaFlowCard {
                        Text(stringResource(R.string.managed_agent_run_result), fontWeight = FontWeight.SemiBold)
                        Text(state.local.output)
                    }
                }
            }
            item(key = "run_history_header") {
                Text(stringResource(R.string.managed_agent_run_history), style = MaterialTheme.typography.titleMedium)
            }
            if (state.runs.isEmpty()) {
                item(key = "run_history_empty") {
                    Text(stringResource(R.string.managed_agent_run_empty_history), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(state.runs.take(25), key = AgentRun::id) { run -> AgentRunHistoryRow(run) }
            }
        }
    }
    state.local.pendingApproval?.let { approval ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.managed_agent_run_approval_title)) },
            text = {
                Text(stringResource(
                    R.string.managed_agent_run_approval_body,
                    approval.toolName,
                    approval.redactedArguments
                ))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.resolveApproval(approved = true) }) {
                    Text(stringResource(R.string.managed_agent_run_approve))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveApproval(approved = false) }) {
                    Text(stringResource(R.string.managed_agent_run_deny))
                }
            }
        )
    }
}

@Composable
private fun AgentRunHistoryRow(run: AgentRun) {
    val statusLabel = when (run.status) {
        AgentRunStatus.QUEUED -> R.string.managed_agent_run_queued
        AgentRunStatus.RUNNING -> R.string.managed_agent_run_active
        AgentRunStatus.WAITING_FOR_APPROVAL -> R.string.managed_agent_run_waiting
        AgentRunStatus.COMPLETED -> R.string.managed_agent_run_completed
        AgentRunStatus.FAILED -> R.string.managed_agent_run_failed
        AgentRunStatus.CANCELLED -> R.string.managed_agent_run_cancelled
        AgentRunStatus.INTERRUPTED -> R.string.managed_agent_run_interrupted
    }
    NexaFlowCard {
        Text(stringResource(statusLabel), style = MaterialTheme.typography.titleSmall)
        Text(
            text = run.outcomeCode ?: run.id.take(8),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
