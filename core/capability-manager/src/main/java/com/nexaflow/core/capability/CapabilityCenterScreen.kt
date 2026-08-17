package com.nexaflow.core.capability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nexaflow.core.rom.EvolutionXSettingsBridge
import com.nexaflow.core.rom.RomIntegrationManager
import com.nexaflow.core.rom.SystemAppInstaller
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.theme.NexaFlowTheme
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.StatusPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CapabilityCenterScreen() {
    val context = LocalContext.current
    val buildInfo = remember { RomIntegrationManager.buildInfo(context) }
    val integrationLevel = remember { RomIntegrationManager.integrationLevel(context) }
    val capabilities = remember { RomIntegrationManager.availableCapabilities(context) }
    val availableCount = remember(capabilities) {
        capabilities.count { RomIntegrationManager.isCapabilityAvailable(context, it) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.capability_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.capability_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        item {
            SectionHeader(text = stringResource(R.string.section_device))
        }
        item {
            NexaFlowCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconBadge(
                        icon = Icons.Filled.Smartphone,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.detected_rom, buildInfo.family.displayName),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "${buildInfo.brand} · ${buildInfo.model} (${buildInfo.device})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Android ${buildInfo.androidVersion} · ${buildInfo.buildDisplay}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = stringResource(R.string.security_patch, buildInfo.securityPatch),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconBadge(
                        icon = Icons.Filled.Tune,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.integration_level, integrationLevel.displayName),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = integrationLevel.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        if (EvolutionXSettingsBridge.isEvolutionX(context)) {
            item {
                SectionHeader(text = stringResource(R.string.section_evolution))
            }
            item {
                EvolutionXCard(buildInfo = buildInfo, integrationLevel = integrationLevel)
            }
        }

        item {
            SectionHeader(text = stringResource(R.string.section_sms))
        }
        item {
            SmsAwarenessCard()
        }

        item {
            SectionHeader(
                text = stringResource(R.string.section_capabilities),
                trailing = {
                    StatusPill(
                        text = stringResource(R.string.capabilities_count, availableCount),
                        background = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                }
            )
        }
        items(capabilities) { capability ->
            CapabilityCard(
                capability = capability,
                available = RomIntegrationManager.isCapabilityAvailable(context, capability),
                context = context
            )
        }

        item {
            SectionHeader(text = stringResource(R.string.section_about))
        }
        item {
            NexaFlowCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconBadge(
                        icon = Icons.Filled.Info,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.about_privileged),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.about_privileged_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Android 17 (API 37) SMS awareness card: explains the 3-hour OTP block for
 * targetSdk 37 apps and lets the user opt into the instant SMS User Consent
 * path (no READ_SMS permission needed).
 */
@Composable
private fun SmsAwarenessCard(viewModel: SmsCapabilityViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val consentEnabled by viewModel.consentEnabled.collectAsState()
    val armed by viewModel.armed.collectAsState()
    val playServicesOk = remember { viewModel.isConsentAvailable() }

    NexaFlowCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(
                icon = Icons.AutoMirrored.Filled.Message,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.sms_android17_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.sms_android17_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (playServicesOk) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.sms_consent_label),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.sms_consent_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Switch(
                            checked = consentEnabled,
                            onCheckedChange = { viewModel.setConsentEnabled(it) }
                        )
                    }
                    if (consentEnabled) {
                        TextButton(onClick = { viewModel.armNow() }) {
                            Text(text = stringResource(R.string.sms_consent_arm))
                        }
                        if (armed) {
                            StatusPill(
                                text = stringResource(R.string.sms_consent_listening),
                                background = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.sms_consent_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Evolution X deep-integration card: shows the detected ROM version + build
 * type, offers "install as a system app" (Magisk module with the privileged
 * whitelist) when not yet a system app, and a browse/edit dialog for the
 * ROM's custom Evolver settings keys.
 */
@Composable
private fun EvolutionXCard(
    buildInfo: com.nexaflow.core.rom.model.RomBuildInfo,
    integrationLevel: IntegrationLevel
) {
    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(false) }
    var installMessage by remember { mutableStateOf<String?>(null) }
    val notElevatedMsg = stringResource(R.string.evolution_not_elevated)
    val installConfirmMsg = stringResource(R.string.evolution_install_system_app_confirm)
    val installDoneMsg = stringResource(R.string.evolution_install_done)
    val installFailedMsg = stringResource(R.string.evolution_install_failed)
    val isSystemApp = integrationLevel == IntegrationLevel.SYSTEM_APP ||
        integrationLevel == IntegrationLevel.PRIVILEGED_SYSTEM_APP ||
        integrationLevel == IntegrationLevel.PLATFORM_SIGNED_SYSTEM_APP

    NexaFlowCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(
                icon = Icons.Filled.Smartphone,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
            Column(modifier = Modifier.weight(1f)) {
                if (buildInfo.evolutionVersion.isNotBlank()) {
                    Text(
                        text = stringResource(
                            R.string.evolution_rom_version,
                            buildInfo.evolutionVersion
                        ),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                if (buildInfo.evolutionBuildType.isNotBlank()) {
                    Text(
                        text = stringResource(
                            R.string.evolution_build_type,
                            buildInfo.evolutionBuildType
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = stringResource(R.string.integration_level, integrationLevel.displayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (isSystemApp) {
                StatusPill(
                    text = stringResource(R.string.evolution_already_system_app),
                    background = NexaFlowTheme.colors.success,
                    contentColor = NexaFlowTheme.colors.onSuccess
                )
            }
        }

        if (!isSystemApp) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.evolution_system_app_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val elevated = integrationLevel == IntegrationLevel.ROOT ||
                        integrationLevel == IntegrationLevel.SHIZUKU
                    if (!elevated) {
                        installMessage = notElevatedMsg
                        return@Button
                    }
                    installMessage = installConfirmMsg
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.evolution_install_system_app))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = { showSettingsDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.evolution_custom_settings))
        }
    }

    if (showSettingsDialog) {
        EvolutionSettingsDialog(onDismiss = { showSettingsDialog = false })
    }

    installMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { installMessage = null },
            confirmButton = {
                TextButton(onClick = {
                    installMessage = null
                    val result = SystemAppInstaller.install(context)
                    installMessage = if (result.success) installDoneMsg else installFailedMsg
                }) {
                    Text(text = stringResource(R.string.evolution_install_system_app))
                }
            },
            dismissButton = {
                TextButton(onClick = { installMessage = null }) {
                    Text(text = stringResource(R.string.evolution_cancel))
                }
            },
            title = { Text(text = stringResource(R.string.evolution_install_system_app)) },
            text = { Text(text = message) }
        )
    }
}

/**
 * Browse / edit dialog for the ROM's custom settings keys. Lists the keys
 * actually present on the device (via `settings list`), lets the user edit any
 * value and writes it back through the elevated runtime.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvolutionSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<EvolutionXSettingsBridge.SettingEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<EvolutionXSettingsBridge.SettingEntry?>(null) }
    var editValue by remember { mutableStateOf("") }
    var writeError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            EvolutionXSettingsBridge.listCustomKeys(context)
        }
        entries = loaded
        loading = false
    }

    // Google 2026: browsing content opens as a full-height modal bottom sheet.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        Text(
            text = stringResource(R.string.evolution_custom_settings),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (loading) {
                Text(
                    text = stringResource(R.string.evolution_no_custom_settings),
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.evolution_no_custom_settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.key,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(
                                    R.string.evolution_setting_value,
                                    entry.value
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        TextButton(onClick = {
                            editing = entry
                            editValue = entry.value
                        }) {
                            Text(text = stringResource(R.string.evolution_edit_setting))
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ok))
            }
        }
    }

    editing?.let { entry ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(text = stringResource(R.string.evolution_edit_setting)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${entry.namespace.shellName} · ${entry.key}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        label = { Text(text = stringResource(R.string.evolution_setting_value, "")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                    writeError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            EvolutionXSettingsBridge.write(
                                context,
                                entry.namespace,
                                entry.key,
                                editValue
                            )
                        }
                        if (result.success) {
                            editing = null
                            writeError = null
                            // Reload so the list reflects the new value.
                            entries = withContext(Dispatchers.IO) {
                                EvolutionXSettingsBridge.listCustomKeys()
                            }
                        } else {
                            writeError = result.message
                        }
                    }
                }) {
                    Text(text = stringResource(R.string.evolution_setting_write))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CapabilityCard(
    capability: RomCapability,
    available: Boolean,
    context: android.content.Context
) {
    val grantIntent = remember(capability) {
        CapabilityGrantHelper.grantIntent(context, capability)
    }
    NexaFlowCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(
                icon = Icons.Filled.Security,
                containerColor = if (available) {
                    NexaFlowTheme.colors.success
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (available) NexaFlowTheme.colors.onSuccess else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = capability.displayName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = capability.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (available) {
                StatusPill(
                    text = stringResource(R.string.available),
                    background = NexaFlowTheme.colors.success,
                    contentColor = NexaFlowTheme.colors.onSuccess
                )
            } else {
                StatusPill(
                    text = stringResource(R.string.not_available),
                    background = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            }
        }
        if (grantIntent != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { CapabilityGrantHelper.launch(context, grantIntent) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (available) stringResource(R.string.settings) else stringResource(R.string.grant))
            }
        }
    }
}
