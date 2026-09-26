package com.nexaflow.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.nexaflow.core.agentapi.AgentApiAuditEventV1
import com.nexaflow.core.agentsecurity.AgentGrantRecord
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import java.text.DateFormat
import java.util.Date

@Composable
fun AgentSettingsScreen(
    navController: NavController,
    viewModel: AgentSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var agentName by rememberSaveable { mutableStateOf("") }
    var showRevokeAll by rememberSaveable { mutableStateOf(false) }
    var copiedPayload by rememberSaveable { mutableStateOf(false) }
    var providerEnabled by rememberSaveable { mutableStateOf(false) }
    var providerName by rememberSaveable { mutableStateOf("") }
    var providerUrl by rememberSaveable { mutableStateOf("") }
    var providerModel by rememberSaveable { mutableStateOf("") }
    var providerLocal by rememberSaveable { mutableStateOf(true) }
    var providerApiKey by rememberSaveable { mutableStateOf("") }
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    LaunchedEffect(state.providerSettings) {
        providerEnabled = state.providerSettings.enabled
        providerName = state.providerSettings.displayName
        providerUrl = state.providerSettings.baseUrl
        providerModel = state.providerSettings.modelId
        providerLocal = state.providerSettings.local
        providerApiKey = ""
    }

    if (showRevokeAll) {
        AlertDialog(
            onDismissRequest = { showRevokeAll = false },
            title = { Text(stringResource(R.string.agent_revoke_all_title)) },
            text = { Text(stringResource(R.string.agent_revoke_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevokeAll = false
                        viewModel.revokeAllAgents()
                    }
                ) {
                    Text(stringResource(R.string.agent_revoke_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeAll = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.ai_agents_title),
                subtitle = stringResource(R.string.ai_agents_subtitle),
                onBack = { navController.popBackStack() },
                actions = {
                    TextButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.agent_refresh))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.operationFailed) {
                item(key = "agent_error") {
                    NexaFlowCard(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = stringResource(R.string.agent_operation_failed),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = viewModel::clearOperationError) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                }
            }

            item(key = "agent_access") {
                NexaFlowCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.agent_access_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(
                                    if (state.accessEnabled) {
                                        R.string.agent_access_on
                                    } else {
                                        R.string.agent_access_off
                                    }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(
                                    R.string.agent_active_sessions,
                                    state.activeSessionCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.accessEnabled,
                            onCheckedChange = viewModel::setAccessEnabled
                        )
                    }
                }
            }

            item(key = "agent_gateway") {
                NexaFlowCard {
                    Text(
                        text = stringResource(R.string.agent_gateway_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (state.serverPort > 0) {
                            stringResource(
                                R.string.agent_gateway_running,
                                state.serverPort
                            )
                        } else {
                            stringResource(R.string.agent_gateway_stopped)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.agent_gateway_rest),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = stringResource(R.string.agent_gateway_mcp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            item(key = "ai_model_provider") {
                NexaFlowCard {
                    Text(
                        text = stringResource(R.string.ai_provider_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.ai_provider_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.ai_provider_enabled),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = providerEnabled,
                            onCheckedChange = { providerEnabled = it }
                        )
                    }
                    OutlinedTextField(
                        value = providerName,
                        onValueChange = { if (it.length <= 128) providerName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.ai_provider_name)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = providerUrl,
                        onValueChange = { if (it.length <= 2048) providerUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.ai_provider_endpoint)) },
                        placeholder = {
                            Text(stringResource(R.string.ai_provider_endpoint_hint))
                        },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = providerModel,
                        onValueChange = { if (it.length <= 256) providerModel = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.ai_provider_model)) },
                        placeholder = {
                            Text(stringResource(R.string.ai_provider_model_hint))
                        },
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(stringResource(R.string.ai_provider_local))
                            Text(
                                text = stringResource(R.string.ai_provider_local_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = providerLocal,
                            onCheckedChange = { providerLocal = it }
                        )
                    }
                    OutlinedTextField(
                        value = providerApiKey,
                        onValueChange = { if (it.length <= 16_384) providerApiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.ai_provider_api_key)) },
                        placeholder = {
                            Text(stringResource(R.string.ai_provider_api_key_hint))
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    if (state.providerApiKeyConfigured) {
                        Text(
                            text = stringResource(R.string.ai_provider_api_key_saved),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = stringResource(R.string.ai_provider_compatibility),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.saveProvider(
                                    enabled = providerEnabled,
                                    displayName = providerName,
                                    baseUrl = providerUrl,
                                    modelId = providerModel,
                                    local = providerLocal,
                                    apiKey = providerApiKey
                                )
                                providerApiKey = ""
                            }
                        ) {
                            Text(stringResource(R.string.ai_provider_save))
                        }
                        TextButton(
                            onClick = viewModel::testProvider,
                            enabled = state.providerSettings.enabled &&
                                state.providerProbeState != AiProviderProbeState.TESTING
                        ) {
                            Text(
                                stringResource(
                                    if (state.providerProbeState == AiProviderProbeState.TESTING) {
                                        R.string.ai_provider_testing
                                    } else {
                                        R.string.ai_provider_test
                                    }
                                )
                            )
                        }
                    }
                    when (state.providerProbeState) {
                        AiProviderProbeState.SUCCESS -> Text(
                            text = stringResource(R.string.ai_provider_test_success),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        AiProviderProbeState.FAILED -> Text(
                            text = stringResource(R.string.ai_provider_test_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> Unit
                    }
                    if (state.providerApiKeyConfigured) {
                        TextButton(onClick = viewModel::clearProviderApiKey) {
                            Text(stringResource(R.string.ai_provider_clear_key))
                        }
                    }
                }
            }

            item(key = "agent_pairing") {
                NexaFlowCard {
                    Text(
                        text = stringResource(R.string.agent_add_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedTextField(
                        value = agentName,
                        onValueChange = {
                            if (it.length <= 128) agentName = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.agent_name_label)) },
                        placeholder = { Text(stringResource(R.string.agent_name_hint)) },
                        singleLine = true,
                        enabled = state.accessEnabled
                    )
                    Button(
                        onClick = {
                            copiedPayload = false
                            viewModel.createPairing(agentName)
                        },
                        enabled = state.accessEnabled && agentName.isNotBlank()
                    ) {
                        Text(stringResource(R.string.agent_generate_pairing))
                    }
                    if (state.pendingPairingCount > 0) {
                        Text(
                            text = stringResource(
                                R.string.agent_pending_pairings,
                                state.pendingPairingCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    state.pairing?.let { pairing ->
                        HorizontalDivider()
                        Text(
                            text = stringResource(R.string.agent_pairing_payload),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.agent_pairing_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SelectionContainer {
                            Text(
                                text = pairing.payload,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.agent_expires_at,
                                dateFormat.format(Date(pairing.expiresAt))
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    context.getSystemService(ClipboardManager::class.java)
                                        ?.setPrimaryClip(
                                            ClipData.newPlainText(
                                                "NexaFlow pairing",
                                                pairing.payload
                                            )
                                        )
                                    copiedPayload = true
                                }
                            ) {
                                Text(stringResource(R.string.agent_copy_payload))
                            }
                            TextButton(
                                onClick = {
                                    copiedPayload = false
                                    viewModel.clearPairing()
                                }
                            ) {
                                Text(stringResource(R.string.dismiss))
                            }
                        }
                        if (copiedPayload) {
                            Text(
                                text = stringResource(R.string.agent_payload_copied),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item(key = "trusted_agents_header") {
                Text(
                    text = stringResource(R.string.agent_trusted_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (state.agents.isEmpty()) {
                item(key = "no_trusted_agents") {
                    NexaFlowCard {
                        Text(
                            text = stringResource(R.string.agent_none),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(
                    items = state.agents,
                    key = AgentGrantRecord::agentId
                ) { agent ->
                    TrustedAgentCard(
                        agent = agent,
                        dateFormat = dateFormat,
                        onRevoke = { viewModel.revokeAgent(agent.agentId) }
                    )
                }
                item(key = "revoke_all_agents") {
                    TextButton(onClick = { showRevokeAll = true }) {
                        Text(stringResource(R.string.agent_revoke_all))
                    }
                }
            }

            item(key = "agent_activity_header") {
                Text(
                    text = stringResource(R.string.agent_activity_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (state.activity.isEmpty()) {
                item(key = "no_agent_activity") {
                    NexaFlowCard {
                        Text(
                            text = stringResource(R.string.agent_activity_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item(key = "agent_activity") {
                    NexaFlowCard {
                        state.activity.forEachIndexed { index, event ->
                            AgentActivityRow(event, dateFormat)
                            if (index != state.activity.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrustedAgentCard(
    agent: AgentGrantRecord,
    dateFormat: DateFormat,
    onRevoke: () -> Unit
) {
    NexaFlowCard {
        Text(
            text = agent.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = agent.agentId,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.agent_full_access),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        agent.lastUsedAt?.let {
            Text(
                text = dateFormat.format(Date(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onRevoke) {
            Text(stringResource(R.string.agent_revoke))
        }
    }
}

@Composable
private fun AgentActivityRow(
    event: AgentApiAuditEventV1,
    dateFormat: DateFormat
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = event.eventType,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = event.outcome,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        val subject = listOfNotNull(event.agentId, event.automationId)
            .joinToString(" · ")
        if (subject.isNotBlank()) {
            Text(
                text = subject,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = dateFormat.format(Date(event.createdAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
