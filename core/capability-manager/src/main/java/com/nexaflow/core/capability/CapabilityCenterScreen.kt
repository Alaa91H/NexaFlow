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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexaflow.core.rom.RomIntegrationManager
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.StatusPill

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
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
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
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconBadge(
                        icon = Icons.Filled.Tune,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.integration_level, integrationLevel.displayName),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
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
                        background = MaterialTheme.colorScheme.surfaceVariant,
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
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.about_privileged),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
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
                containerColor = Color(0xFFF59E0B).copy(alpha = 0.15f),
                contentColor = Color(0xFFB45309)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.sms_android17_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
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
                                background = Color(0xFFE4F4E9),
                                contentColor = Color(0xFF2FA84F)
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
                    Color(0xFF2FA84F)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (available) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = capability.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
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
                    background = Color(0xFF2FA84F),
                    contentColor = Color.White
                )
            } else {
                StatusPill(
                    text = stringResource(R.string.not_available),
                    background = Color(0xFFB3261E),
                    contentColor = Color.White
                )
            }
        }
        if (grantIntent != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { CapabilityGrantHelper.launch(context, grantIntent) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (available) stringResource(R.string.settings) else stringResource(R.string.grant))
            }
        }
    }
}
