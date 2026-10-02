package com.nexaflow.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.nexaflow.core.agentapi.AgentApiAuditEventV1
import com.nexaflow.core.agentsecurity.AgentGrantRecord
import com.nexaflow.core.airuntime.AiRoutingMode
import com.nexaflow.core.airuntime.AiProviderDefinitionRegistry as Registry
import com.nexaflow.core.airuntime.AiProviderProtocol
import com.nexaflow.core.airuntime.AiReasoningLevel
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.domain.security.HttpAccessPolicy
import java.net.URI
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
    var providerName by rememberSaveable { mutableStateOf("") }
    var providerUrl by rememberSaveable { mutableStateOf("") }
    var providerModel by rememberSaveable { mutableStateOf("") }
    var providerLocal by rememberSaveable { mutableStateOf(true) }
    var providerApiKey by remember { mutableStateOf("") }
    var profilePresetId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var profileProtocol by rememberSaveable {
        mutableStateOf(AiProviderProtocol.OPENAI_CHAT_COMPLETIONS)
    }
    var routingMode by rememberSaveable {
        mutableStateOf(AiRoutingMode.AUTOMATIC)
    }
    var selectedProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    var allowCloudFallback by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(AiSettingsTab.AGENTS) }
    var reasoningLevel by rememberSaveable { mutableStateOf(AiReasoningLevel.BALANCED) }
    var showModelPicker by rememberSaveable { mutableStateOf(false) }
    var showClearActivityDialog by rememberSaveable { mutableStateOf(false) }
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }
    val agentLanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setLanAccessEnabled(true)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    LaunchedEffect(state.providerSettings) {
        providerName = state.providerSettings.displayName
        providerUrl = state.providerSettings.baseUrl
        providerModel = state.providerSettings.modelId
        providerLocal = state.providerSettings.local
        providerApiKey = ""
        routingMode = runCatching {
            AiRoutingMode.valueOf(state.providerSettings.routingMode)
        }.getOrDefault(AiRoutingMode.AUTOMATIC)
        selectedProviderId = state.providerSettings.selectedProviderId
        allowCloudFallback = state.providerSettings.allowCloudFallback
    }
    LaunchedEffect(
        profilePresetId,
        profileProtocol,
        providerApiKey,
        providerUrl,
        providerModel,
        providerLocal,
        editingProfileId
    ) {
        viewModel.invalidateProviderDraft()
    }

    if (showModelPicker) {
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text(stringResource(R.string.ai_provider_models_title)) },
            text = {
                LazyColumn {
                    items(state.profileModelChoices, key = { it }) { model ->
                        TextButton(
                            onClick = {
                                providerModel = model
                                showModelPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(model) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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

    if (showClearActivityDialog) {
        AlertDialog(
            onDismissRequest = { showClearActivityDialog = false },
            title = { Text(stringResource(R.string.agent_activity_clear_title)) },
            text = { Text(stringResource(R.string.agent_activity_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearActivityDialog = false
                    viewModel.clearActivity()
                }) { Text(stringResource(R.string.agent_activity_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearActivityDialog = false }) {
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
            item(key = "ai_settings_tabs") {
                AiSettingsTabRow(
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
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

            if (selectedTab == AiSettingsTab.AGENTS) item(key = "agent_access") {
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

            if (selectedTab == AiSettingsTab.AGENTS) item(key = "agent_gateway") {
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.agent_lan_access_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(
                                    if (state.lanAccessEnabled) {
                                        R.string.agent_lan_access_on
                                    } else {
                                        R.string.agent_lan_access_off
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.agent_lan_access_description),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.lanAccessEnabled,
                            enabled = state.accessEnabled,
                            onCheckedChange = { enabled ->
                                when {
                                    !enabled -> viewModel.setLanAccessEnabled(false)
                                    Build.VERSION.SDK_INT >= 37 &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            HttpAccessPolicy.LOCAL_NETWORK_PERMISSION
                                        ) != PackageManager.PERMISSION_GRANTED -> {
                                        agentLanPermissionLauncher.launch(
                                            HttpAccessPolicy.LOCAL_NETWORK_PERMISSION
                                        )
                                    }
                                    else -> viewModel.setLanAccessEnabled(true)
                                }
                            }
                        )
                    }
                }
            }

            if (selectedTab != AiSettingsTab.LOGS) item(key = "ai_model_provider") {
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
                    if (selectedTab == AiSettingsTab.AGENTS) {
                    Text(
                        text = stringResource(R.string.ai_provider_profiles),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    state.providerProfiles.forEach { profile ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.providerSettings.selectedProviderId == profile.id,
                                enabled = profile.enabled,
                                onClick = { viewModel.selectProviderProfile(profile.id) }
                            )
                            Column(Modifier.weight(1f)) {
                                TextButton(
                                    onClick = {
                                        editingProfileId = profile.id
                                        profilePresetId = profile.presetId?.takeIf {
                                            Registry.preset(it) != null
                                        }
                                        profileProtocol = runCatching {
                                            AiProviderProtocol.valueOf(profile.protocol)
                                        }.getOrDefault(AiProviderProtocol.OPENAI_CHAT_COMPLETIONS)
                                        providerName = profile.displayName
                                        providerUrl = profile.baseUrl
                                        providerModel = profile.modelId
                                        providerLocal = profile.local
                                        reasoningLevel = AiReasoningLevel.fromStoredValue(profile.reasoningEffort)
                                    }
                                ) {
                                    Text(profile.displayName)
                                }
                                Text(
                                    profile.modelId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { viewModel.verifyProviderProfile(profile.id) }) {
                                Text(stringResource(R.string.ai_provider_verify))
                            }
                            TextButton(onClick = { viewModel.removeProviderProfile(profile.id) }) {
                                Text(stringResource(R.string.ai_provider_remove))
                            }
                        }
                    }
                    }
                    if (selectedTab == AiSettingsTab.ADD_AGENT) {
                    Text(
                        text = stringResource(R.string.ai_provider_add_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Registry.presets.forEach { preset ->
                        TextButton(
                            onClick = {
                                editingProfileId = null
                                profilePresetId = preset.id
                                profileProtocol = preset.protocol
                                providerName = preset.displayName
                                providerUrl = preset.baseUrl
                                providerModel = preset.defaultModelId
                                providerLocal = preset.local
                                reasoningLevel = AiReasoningLevel.BALANCED
                                viewModel.clearProfileModelChoices()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${preset.displayName} — ${preset.defaultModelId}")
                        }
                    }
                    TextButton(
                        onClick = {
                            editingProfileId = null
                            profilePresetId = null
                            profileProtocol = AiProviderProtocol.OPENAI_CHAT_COMPLETIONS
                            providerName = ""
                            providerUrl = ""
                            providerModel = ""
                            providerLocal = false
                            reasoningLevel = AiReasoningLevel.BALANCED
                            viewModel.clearProfileModelChoices()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.ai_provider_manual))
                    }
                    if (profilePresetId == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = profileProtocol ==
                                    AiProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                                onClick = {
                                    profileProtocol = AiProviderProtocol.OPENAI_CHAT_COMPLETIONS
                                },
                                label = { Text(stringResource(R.string.ai_provider_protocol_compatible)) }
                            )
                            FilterChip(
                                selected = profileProtocol == AiProviderProtocol.ANTHROPIC_MESSAGES,
                                onClick = {
                                    profileProtocol = AiProviderProtocol.ANTHROPIC_MESSAGES
                                    providerUrl = Registry.preset("claude")?.baseUrl.orEmpty()
                                    providerModel = Registry.preset("claude")?.defaultModelId.orEmpty()
                                },
                                label = { Text(stringResource(R.string.ai_provider_protocol_anthropic)) }
                            )
                        }
                    }
                    if (profilePresetId == null) {
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
                    } else {
                        TextButton(
                            onClick = { showModelPicker = true },
                            enabled = state.profileModelChoices.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (providerModel.isBlank()) {
                                    stringResource(R.string.ai_provider_choose_model)
                                } else {
                                    "$providerName · $providerModel"
                                }
                            )
                        }
                        OutlinedTextField(
                            value = providerModel,
                            onValueChange = { if (it.length <= 256) providerModel = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.ai_provider_model)) },
                            placeholder = { Text(stringResource(R.string.ai_provider_model_hint)) },
                            singleLine = true
                        )
                    }
                    TextButton(
                        onClick = {
                            viewModel.discoverProfileModels(
                                profileId = editingProfileId,
                                presetId = profilePresetId,
                                protocol = profileProtocol,
                                displayName = providerName,
                                baseUrl = providerUrl,
                                modelId = providerModel,
                                local = providerLocal,
                                apiKey = providerApiKey
                            )
                        },
                        enabled = AiProviderSetupPolicy.canDiscoverModels(state.providerProbeState)
                    ) { Text(stringResource(R.string.ai_provider_discover_models)) }
                    if (profilePresetId == null && state.profileModelChoices.isNotEmpty()) {
                        TextButton(
                            onClick = { showModelPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.ai_provider_choose_model))
                        }
                    }
                    when (state.modelDiscoveryState) {
                        AiModelDiscoveryState.LOADING -> Text(stringResource(R.string.ai_provider_models_loading))
                        AiModelDiscoveryState.SUCCESS -> Text(stringResource(R.string.ai_provider_models_ready))
                        AiModelDiscoveryState.UNSUPPORTED -> Text(stringResource(R.string.ai_provider_models_unsupported))
                        AiModelDiscoveryState.FAILED -> Text(stringResource(R.string.ai_provider_models_failed))
                        AiModelDiscoveryState.IDLE -> Unit
                    }
                    when (state.providerProbeState) {
                        AiProviderProbeState.TESTING -> Text(stringResource(R.string.ai_provider_testing))
                        AiProviderProbeState.SUCCESS -> Text(
                            buildString {
                                append(stringResource(R.string.ai_provider_test_success))
                                state.providerLastVerifiedAtMillis?.let {
                                    append(" · ")
                                    append(dateFormat.format(Date(it)))
                                }
                            }
                        )
                        AiProviderProbeState.FAILED -> Text(
                            stringResource(providerProbeFailureMessageRes(state.providerProbeStatusCode))
                        )
                        AiProviderProbeState.IDLE -> Unit
                    }
                    if (profilePresetId == "openai" || profilePresetId == "claude") {
                        Text(
                            text = stringResource(R.string.ai_provider_level),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AiReasoningLevel.entries.forEach { level ->
                                FilterChip(
                                    selected = reasoningLevel == level,
                                    onClick = { reasoningLevel = level },
                                    label = {
                                        Text(
                                            stringResource(
                                                when (level) {
                                                    AiReasoningLevel.FAST -> R.string.ai_provider_level_fast
                                                    AiReasoningLevel.BALANCED -> R.string.ai_provider_level_balanced
                                                    AiReasoningLevel.DEEP -> R.string.ai_provider_level_deep
                                                }
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                    if (profilePresetId == null &&
                        profileProtocol == AiProviderProtocol.OPENAI_CHAT_COMPLETIONS
                    ) Row(
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
                    if (editingProfileId != null) {
                        Text(
                            text = stringResource(R.string.ai_provider_api_key_saved),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (
                            state.providerProfiles.any {
                                it.id == editingProfileId && it.credentialRef != null
                            }
                        ) {
                            TextButton(
                                onClick = {
                                    editingProfileId?.let(viewModel::clearProviderProfileApiKey)
                                    providerApiKey = ""
                                }
                            ) {
                                Text(
                                    stringResource(R.string.ai_provider_remove) + " " +
                                        stringResource(R.string.ai_provider_api_key)
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            viewModel.verifyProviderDraft(
                                profileId = editingProfileId,
                                presetId = profilePresetId,
                                protocol = profileProtocol,
                                displayName = providerName,
                                baseUrl = providerUrl,
                                modelId = providerModel,
                                local = providerLocal,
                                apiKey = providerApiKey
                            )
                        },
                        enabled = providerName.isNotBlank() && providerUrl.isNotBlank() &&
                            providerModel.isNotBlank() && (
                                providerApiKey.isNotBlank() ||
                                    state.providerProfiles.any { it.id == editingProfileId } ||
                                    (
                                        profilePresetId == null &&
                                            providerLocal &&
                                            profileProtocol ==
                                            AiProviderProtocol.OPENAI_CHAT_COMPLETIONS
                                    )
                                )
                    ) {
                        Text(stringResource(R.string.ai_provider_verify))
                    }
                    Button(
                        onClick = {
                            viewModel.saveProviderProfile(
                                profileId = editingProfileId,
                                presetId = profilePresetId,
                                protocol = profileProtocol,
                                displayName = providerName,
                                baseUrl = providerUrl,
                                modelId = providerModel,
                                local = providerLocal,
                                apiKey = providerApiKey,
                                reasoningEffort = reasoningLevel.apiValue,
                                enableRequested = AiProviderSetupPolicy.enableOnSave(
                                    state.providerProbeState
                                ),
                                onComplete = { saved ->
                                    if (saved) {
                                        providerApiKey = ""
                                        selectedTab = AiSettingsTab.AGENTS
                                    }
                                }
                            )
                        },
                        enabled = providerName.isNotBlank() && providerUrl.isNotBlank() &&
                            providerModel.isNotBlank() && (providerApiKey.isNotBlank() ||
                            state.providerProfiles.any { it.id == editingProfileId } ||
                            (profilePresetId == null && providerLocal && profileProtocol ==
                                AiProviderProtocol.OPENAI_CHAT_COMPLETIONS))
                    ) {
                        Text(
                            stringResource(
                                if (
                                    state.providerProbeState == AiProviderProbeState.SUCCESS &&
                                    editingProfileId == null
                                ) {
                                    R.string.ai_provider_add
                                } else {
                                    R.string.ai_provider_save
                                }
                            )
                        )
                    }
                    }
                    if (selectedTab == AiSettingsTab.AGENTS) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.ai_routing_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.ai_routing_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AiRoutingMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = routingMode == mode,
                                onClick = {
                                    routingMode = mode
                                    if (
                                        mode == AiRoutingMode.SELECTED_PROVIDER &&
                                        selectedProviderId == null
                                    ) {
                                        selectedProviderId =
                                            state.providerDescriptors.firstOrNull()?.id
                                    }
                                }
                            )
                            Text(
                                text = stringResource(
                                    when (mode) {
                                        AiRoutingMode.AUTOMATIC ->
                                            R.string.ai_routing_automatic
                                        AiRoutingMode.LOCAL_ONLY ->
                                            R.string.ai_routing_local_only
                                        AiRoutingMode.CLOUD_ONLY ->
                                            R.string.ai_routing_cloud_only
                                        AiRoutingMode.SELECTED_PROVIDER ->
                                            R.string.ai_routing_selected
                                    }
                                )
                            )
                        }
                    }
                    if (routingMode == AiRoutingMode.SELECTED_PROVIDER) {
                        state.providerDescriptors.forEach { descriptor ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedProviderId == descriptor.id,
                                    onClick = { selectedProviderId = descriptor.id }
                                )
                                Text(
                                    text = descriptor.displayName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    if (routingMode == AiRoutingMode.AUTOMATIC) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(stringResource(R.string.ai_routing_cloud_fallback))
                                Text(
                                    text = stringResource(
                                        R.string.ai_routing_cloud_fallback_subtitle
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = allowCloudFallback,
                                onCheckedChange = { allowCloudFallback = it }
                            )
                        }
                    }
                    when (state.providerProbeState) {
                        AiProviderProbeState.TESTING -> Text(
                            text = stringResource(R.string.ai_provider_testing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    }
                }
            }

            if (selectedTab == AiSettingsTab.AGENTS) item(key = "agent_pairing") {
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

            if (selectedTab == AiSettingsTab.AGENTS) item(key = "trusted_agents_header") {
                Text(
                    text = stringResource(R.string.agent_trusted_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (selectedTab == AiSettingsTab.AGENTS && state.agents.isEmpty()) {
                item(key = "no_trusted_agents") {
                    NexaFlowCard {
                        Text(
                            text = stringResource(R.string.agent_none),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (selectedTab == AiSettingsTab.AGENTS) {
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

            if (selectedTab == AiSettingsTab.LOGS) item(key = "agent_activity_header") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.agent_activity_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = { showClearActivityDialog = true }) {
                        Text(stringResource(R.string.agent_activity_clear))
                    }
                }
            }

            if (selectedTab == AiSettingsTab.LOGS && state.activity.isEmpty()) {
                item(key = "no_agent_activity") {
                    NexaFlowCard {
                        Text(
                            text = stringResource(R.string.agent_activity_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (selectedTab == AiSettingsTab.LOGS) {
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
