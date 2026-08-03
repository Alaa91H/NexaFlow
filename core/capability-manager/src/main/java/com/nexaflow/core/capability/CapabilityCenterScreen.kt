package com.nexaflow.core.capability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexaflow.core.rom.RomIntegrationManager

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
            Text(text = "ROM Integration", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Detected ROM: ${buildInfo.family.displayName}",
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
                    Text(
                        text = "Integration level: ${integrationLevel.displayName}",
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
                text = "Available capabilities (${capabilities.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }
        items(capabilities) { capability ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = capability.displayName,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = capability.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
