package com.nexaflow.feature.builder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.domain.models.TriggerType

val triggerTypeOptions = listOf(
    TriggerType.TIME,
    TriggerType.APPLICATION,
    TriggerType.DEVICE,
    TriggerType.CONNECTIVITY,
    TriggerType.LOCATION
)

private fun TriggerType.labelRes(): Int = when (this) {
    TriggerType.TIME -> R.string.trigger_type_time
    TriggerType.APPLICATION -> R.string.trigger_type_app
    TriggerType.DEVICE -> R.string.trigger_type_device
    TriggerType.CONNECTIVITY -> R.string.trigger_type_connectivity
    TriggerType.LOCATION -> R.string.trigger_type_location
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerEditorCard(
    draft: TriggerDraft,
    index: Int,
    onConfigChange: (TriggerDraft) -> Unit,
    onRemove: () -> Unit,
    onPickApp: () -> Unit
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    NexaFlowCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.trigger_n, index + 1),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.remove_trigger),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                triggerTypeOptions.forEach { option ->
                    FilterChip(
                        selected = draft.type == option,
                        onClick = {
                            onConfigChange(
                                TriggerDraft(
                                    type = option,
                                    config = when (option) {
                                        TriggerType.TIME -> mapOf("time" to (draft.config["time"] ?: "08:00"))
                                        TriggerType.APPLICATION -> mapOf("package" to (draft.config["package"] ?: ""))
                                        TriggerType.DEVICE -> mapOf("event" to (draft.config["event"] ?: "SCREEN_ON"))
                                        TriggerType.CONNECTIVITY -> mapOf(
                                            "network" to (draft.config["network"] ?: "WIFI"),
                                            "state" to (draft.config["state"] ?: "CONNECTED")
                                        )
                                        TriggerType.LOCATION -> mapOf(
                                            "lat" to (draft.config["lat"] ?: ""),
                                            "lng" to (draft.config["lng"] ?: ""),
                                            "radius" to (draft.config["radius"] ?: "100"),
                                            "event" to (draft.config["event"] ?: "ENTER")
                                        )
                                    }
                                )
                            )
                        },
                        label = { Text(text = stringResource(option.labelRes())) }
                    )
                }
            }
            when (draft.type) {
                TriggerType.TIME -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTimePicker = true }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.trigger_time),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.runs_daily),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        TextButton(onClick = { showTimePicker = true }) {
                            Text(text = draft.config["time"] ?: "08:00")
                        }
                    }
                }
                TriggerType.APPLICATION -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.config["package"] ?: "",
                            onValueChange = {
                                onConfigChange(draft.copy(config = draft.config + ("package" to it)))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.package_name)) },
                            placeholder = { Text(text = stringResource(R.string.package_hint)) },
                            singleLine = true
                        )
                        TextButton(onClick = onPickApp) {
                            Text(text = stringResource(R.string.choose_from_installed))
                        }
                        PermissionHint(
                            text = stringResource(R.string.app_detection_hint),
                            buttonLabel = stringResource(R.string.enable),
                            onClick = { PermissionShortcuts.openAccessibilitySettings(context) }
                        )
                    }
                }
                TriggerType.DEVICE -> {
                    OptionChips(
                        options = listOf(
                            "SCREEN_ON",
                            "SCREEN_OFF",
                            "POWER_CONNECTED",
                            "POWER_DISCONNECTED",
                            "HEADSET_CONNECTED",
                            "HEADSET_DISCONNECTED"
                        ),
                        selected = draft.config["event"] ?: "SCREEN_ON",
                        onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                    )
                }
                TriggerType.CONNECTIVITY -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.network), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        OptionChips(
                            options = listOf("WIFI", "MOBILE"),
                            selected = draft.config["network"] ?: "WIFI",
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("network" to it))) }
                        )
                        Text(text = stringResource(R.string.state), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        OptionChips(
                            options = listOf("CONNECTED", "DISCONNECTED"),
                            selected = draft.config["state"] ?: "CONNECTED",
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
                        )
                    }
                }
                TriggerType.LOCATION -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.config["lat"] ?: "",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("lat" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.latitude)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = draft.config["lng"] ?: "",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("lng" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.longitude)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = draft.config["radius"] ?: "100",
                            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("radius" to it))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.radius_meters)) },
                            singleLine = true
                        )
                        Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        OptionChips(
                            options = listOf("ENTER", "EXIT"),
                            selected = draft.config["event"] ?: "ENTER",
                            onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
                        )
                        PermissionHint(
                            text = stringResource(R.string.location_hint),
                            buttonLabel = stringResource(R.string.grant),
                            onClick = { PermissionShortcuts.openAppSettings(context) }
                        )
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        TimePickerAlert(
            initialTime = draft.config["time"] ?: "08:00",
            onConfirm = {
                onConfigChange(draft.copy(config = draft.config + ("time" to it)))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}
