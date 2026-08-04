package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexaflow.domain.models.ActionType

@Composable
fun ActionConfigEditor(
    option: ActionOption,
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
    onPickApp: () -> Unit
) {
    when (option.actionType) {
        ActionType.SYSTEM_BRIGHTNESS -> {
            val value = config["value"]?.toIntOrNull() ?: 128
            SliderRow(
                label = stringResource(R.string.brightness_label, value),
                value = value.toFloat(),
                onValueChange = { onConfigChange(mapOf("value" to it.toInt().toString())) },
                valueRange = 0f..255f
            )
        }
        ActionType.SYSTEM_VOLUME -> {
            val value = config["value"]?.toIntOrNull() ?: 50
            SliderRow(
                label = stringResource(R.string.volume_label, value),
                value = value.toFloat(),
                onValueChange = { onConfigChange(mapOf("value" to it.toInt().toString())) },
                valueRange = 0f..100f
            )
        }
        ActionType.SYSTEM_DND -> {
            ToggleConfigRow(
                label = stringResource(R.string.turn_on),
                checked = config["enabled"]?.toBoolean() ?: true,
                onCheckedChange = { onConfigChange(mapOf("enabled" to it.toString())) }
            )
        }
        ActionType.SYSTEM_WIFI,
        ActionType.SYSTEM_BLUETOOTH,
        ActionType.SYSTEM_FLASHLIGHT,
        ActionType.SYSTEM_AIRPLANE_MODE -> {
            ToggleConfigRow(
                label = stringResource(R.string.turn_on),
                checked = config["enabled"]?.toBoolean() ?: true,
                onCheckedChange = { onConfigChange(mapOf("enabled" to it.toString())) }
            )
        }
        ActionType.SYSTEM_OPEN_APP -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val packages = (config["packages"] ?: config["package"] ?: "")
                OutlinedTextField(
                    value = packages,
                    onValueChange = { onConfigChange(mapOf("packages" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.apps_comma)) },
                    singleLine = true
                )
                TextButton(onClick = onPickApp) {
                    Text(text = stringResource(R.string.choose_from_installed))
                }
            }
        }
        ActionType.SYSTEM_SEND_NOTIFICATION -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["title"] ?: "",
                    onValueChange = { onConfigChange(config + ("title" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.title)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = config["text"] ?: "",
                    onValueChange = { onConfigChange(config + ("text" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.text)) },
                    singleLine = true
                )
            }
        }
        ActionType.SYSTEM_SCREEN_ROTATION -> {
            ToggleConfigRow(
                label = stringResource(R.string.auto_rotate),
                checked = config["autoRotate"]?.toBoolean() ?: true,
                onCheckedChange = { onConfigChange(mapOf("autoRotate" to it.toString())) }
            )
        }
        ActionType.SYSTEM_OPEN_URL -> {
            OutlinedTextField(
                value = config["url"] ?: "",
                onValueChange = { onConfigChange(mapOf("url" to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.url)) },
                placeholder = { Text(text = stringResource(R.string.url_hint)) },
                singleLine = true
            )
        }
        ActionType.BATTERY_ALERTS,
        ActionType.BATTERY_CHARGING_NOTIFICATIONS -> {
            val below = config["below"]?.toIntOrNull() ?: 20
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SliderRow(
                    label = stringResource(R.string.alert_below, below),
                    value = below.toFloat(),
                    onValueChange = { onConfigChange(config + ("below" to it.toInt().toString())) },
                    valueRange = 5f..100f
                )
                OutlinedTextField(
                    value = config["message"] ?: "",
                    onValueChange = { onConfigChange(config + ("message" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.message_optional)) },
                    singleLine = true
                )
            }
        }
        ActionType.ADVANCED_SHIZUKU,
        ActionType.ADVANCED_ROOT -> {
            OutlinedTextField(
                value = config["command"] ?: "",
                onValueChange = { onConfigChange(mapOf("command" to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.shell_command)) },
                placeholder = { Text(text = stringResource(R.string.shell_hint)) }
            )
        }
        ActionType.APPLICATION_CLOSE_APP -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = config["package"] ?: "",
                    onValueChange = { onConfigChange(mapOf("package" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.package_name)) },
                    singleLine = true
                )
                TextButton(onClick = onPickApp) {
                    Text(text = stringResource(R.string.choose_from_installed))
                }
            }
        }
        ActionType.SYSTEM_MEDIA_PLAY_PAUSE,
        ActionType.SYSTEM_MEDIA_NEXT,
        ActionType.SYSTEM_MEDIA_PREVIOUS,
        ActionType.SYSTEM_CLEAR_NOTIFICATIONS,
        ActionType.SYSTEM_EXPAND_STATUS_BAR,
        ActionType.SYSTEM_COLLAPSE_STATUS_BAR,
        ActionType.APPLICATION_LAUNCH_APP -> {
            Text(
                text = stringResource(R.string.runs_immediately),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun ToggleConfigRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
