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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexaflow.core.rom.RomIntegrationManager
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.ui.StatusPill

@Composable
fun CapabilityCenterScreen() {
    val context = LocalContext.current
    val buildInfo = remember { RomIntegrationManager.buildInfo(context) }
    val integrationLevel = remember { RomIntegrationManager.integrationLevel(context) }
    val capabilities = remember { RomIntegrationManager.availableCapabilities(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = stringResource(R.string.capability_title), style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.detected_rom, buildInfo.family.displayName),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${buildInfo.brand} \u00b7 ${buildInfo.model} (${buildInfo.device})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Android ${buildInfo.androidVersion} \u00b7 Build ${buildInfo.buildDisplay}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Security patch: ${buildInfo.securityPatch}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.integration_level, integrationLevel.displayName),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = integrationLevel.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.capabilities_count, capabilities.size),
                style = MaterialTheme.typography.titleMedium
            )
        }
        items(capabilities) { capability ->
            CapabilityRow(
                capability = capability,
                available = RomIntegrationManager.isCapabilityAvailable(context, capability),
                context = context
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.about_privileged),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.about_privileged_text),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityRow(
    capability: RomCapability,
    available: Boolean,
    context: android.content.Context
) {
    val grantIntent = remember(capability) {
        CapabilityGrantHelper.grantIntent(context, capability)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = capability.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = capability.description,
                style = MaterialTheme.typography.bodyMedium
            )
            if (grantIntent != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { CapabilityGrantHelper.launch(context, grantIntent) }
                ) {
                    Text(if (available) stringResource(R.string.settings) else stringResource(R.string.grant))
                }
            }
        }
    }
}
