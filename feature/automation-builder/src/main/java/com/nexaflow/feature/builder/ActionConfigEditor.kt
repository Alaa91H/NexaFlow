package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexaflow.domain.models.ActionType

@OptIn(ExperimentalLayoutApi::class)
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
        ActionType.SYSTEM_RING_VOLUME -> {
            val value = config["value"]?.toIntOrNull() ?: 50
            SliderRow(
                label = stringResource(R.string.ring_volume_label, value),
                value = value.toFloat(),
                onValueChange = { onConfigChange(mapOf("value" to it.toInt().toString())) },
                valueRange = 0f..100f
            )
        }
        ActionType.SYSTEM_STREAM_VOLUME -> {
            val streams = listOf(
                "MUSIC" to stringResource(R.string.stream_music),
                "RING" to stringResource(R.string.stream_ring),
                "NOTIFICATION" to stringResource(R.string.stream_notification),
                "ALARM" to stringResource(R.string.stream_alarm),
                "VOICE_CALL" to stringResource(R.string.stream_voice_call),
                "SYSTEM" to stringResource(R.string.stream_system),
                "DTMF" to stringResource(R.string.stream_dtmf),
                "ACCESSIBILITY" to stringResource(R.string.stream_accessibility)
            )
            val selectedStream = config["stream"] ?: "MUSIC"
            val value = config["value"]?.toIntOrNull() ?: 50
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.stream_label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    streams.forEach { (stream, label) ->
                        FilterChip(
                            selected = selectedStream == stream,
                            onClick = { onConfigChange(config + ("stream" to stream)) },
                            label = { Text(text = label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
                SliderRow(
                    label = stringResource(R.string.stream_volume_label, value),
                    value = value.toFloat(),
                    onValueChange = { onConfigChange(config + ("value" to it.toInt().toString())) },
                    valueRange = 0f..100f
                )
            }
        }
        ActionType.SYSTEM_LOCATION -> {
            ToggleConfigRow(
                label = stringResource(R.string.turn_on),
                checked = config["enabled"]?.toBoolean() ?: true,
                onCheckedChange = { onConfigChange(mapOf("enabled" to it.toString())) }
            )
        }
        ActionType.SYSTEM_SEND_SMS -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["number"] ?: "",
                    onValueChange = { onConfigChange(config + ("number" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.phone_number)) },
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
        ActionType.SYSTEM_SEND_REMINDER -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["title"] ?: "",
                    onValueChange = { onConfigChange(config + ("title" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.reminder_title)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = config["text"] ?: "",
                    onValueChange = { onConfigChange(config + ("text" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.reminder_text)) },
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = config["hour"] ?: "9",
                        onValueChange = { onConfigChange(config + ("hour" to it)) },
                        modifier = Modifier.weight(1f),
                        label = { Text(text = stringResource(R.string.hour)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = config["minute"] ?: "0",
                        onValueChange = { onConfigChange(config + ("minute" to it)) },
                        modifier = Modifier.weight(1f),
                        label = { Text(text = stringResource(R.string.minute)) },
                        singleLine = true
                    )
                }
            }
        }
        ActionType.SYSTEM_OPEN_SETTINGS -> {
            val pages = listOf(
                "WIFI" to stringResource(R.string.settings_wifi),
                "BLUETOOTH" to stringResource(R.string.settings_bluetooth),
                "LOCATION" to stringResource(R.string.settings_location),
                "SOUND" to stringResource(R.string.settings_sound),
                "DISPLAY" to stringResource(R.string.settings_display),
                "BATTERY" to stringResource(R.string.settings_battery),
                "NOTIFICATION" to stringResource(R.string.settings_notification)
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.forEach { (value, label) ->
                    FilterChip(
                        selected = (config["page"] ?: "WIFI") == value,
                        onClick = { onConfigChange(mapOf("page" to value)) },
                        label = { Text(text = label) }
                    )
                }
            }
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
        ActionType.SYSTEM_AIRPLANE_MODE,
        ActionType.SYSTEM_STAY_AWAKE,
        ActionType.SYSTEM_AUTO_BRIGHTNESS,
        ActionType.SYSTEM_MOBILE_DATA,
        ActionType.SYSTEM_HOTSPOT,
        ActionType.SYSTEM_NFC,
        ActionType.SYSTEM_POWER_SAVER,
        ActionType.SYSTEM_ANIMATIONS,
        ActionType.SYSTEM_DARK_MODE -> {
            ToggleConfigRow(
                label = stringResource(R.string.turn_on),
                checked = config["enabled"]?.toBoolean() ?: true,
                onCheckedChange = { onConfigChange(mapOf("enabled" to it.toString())) }
            )
        }
        ActionType.SYSTEM_SCREEN_TIMEOUT -> {
            val seconds = config["seconds"]?.toIntOrNull() ?: 60
            SliderRow(
                label = stringResource(R.string.timeout_label, seconds),
                value = seconds.toFloat(),
                onValueChange = { onConfigChange(mapOf("seconds" to it.toInt().toString())) },
                valueRange = 10f..1800f
            )
        }
        ActionType.SYSTEM_RINGER_MODE -> {
            val modes = listOf(
                "NORMAL" to stringResource(R.string.ringer_normal),
                "VIBRATE" to stringResource(R.string.ringer_vibrate),
                "SILENT" to stringResource(R.string.ringer_silent)
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                modes.forEach { (value, label) ->
                    FilterChip(
                        selected = (config["mode"] ?: "NORMAL") == value,
                        onClick = { onConfigChange(mapOf("mode" to value)) },
                        label = { Text(text = label) }
                    )
                }
            }
        }
        ActionType.SYSTEM_SET_ALARM -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["hour"] ?: "7",
                    onValueChange = { onConfigChange(config + ("hour" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.hour)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = config["minute"] ?: "0",
                    onValueChange = { onConfigChange(config + ("minute" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.minute)) },
                    singleLine = true
                )
            }
        }
        ActionType.APPLICATION_OPEN_APP_SETTINGS -> {
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
        ActionType.SYSTEM_BLOCK_NOTIFICATION -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                ToggleConfigRow(
                    label = stringResource(R.string.block_label),
                    checked = config["enabled"]?.toBoolean() ?: true,
                    onCheckedChange = { onConfigChange(config + ("enabled" to it.toString())) }
                )
            }
        }
        ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS -> {
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
                Text(text = stringResource(R.string.sound_label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val sounds = listOf(
                    "DEFAULT" to stringResource(R.string.sound_default),
                    "RINGTONE" to stringResource(R.string.sound_ringtone),
                    "NOTIFICATION" to stringResource(R.string.sound_notification),
                    "BEEP" to stringResource(R.string.sound_beep),
                    "SILENT" to stringResource(R.string.sound_silent)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    sounds.forEach { (value, label) ->
                        FilterChip(
                            selected = (config["sound"] ?: "DEFAULT") == value,
                            onClick = { onConfigChange(config + ("sound" to value)) },
                            label = { Text(text = label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }
        }
        ActionType.SYSTEM_WAIT -> {
            val seconds = config["seconds"]?.toIntOrNull() ?: 5
            SliderRow(
                label = stringResource(R.string.wait_counter_label, seconds),
                value = seconds.toFloat(),
                onValueChange = { onConfigChange(mapOf("seconds" to it.toInt().toString())) },
                valueRange = 1f..300f
            )
            Text(
                text = stringResource(R.string.wait_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
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
        ActionType.APPLICATION_LAUNCH_APP,
        ActionType.SYSTEM_LOCK_SCREEN,
        ActionType.SYSTEM_OPEN_RECENTS,
        ActionType.SYSTEM_GO_HOME,
        ActionType.SYSTEM_OPEN_PLAY_UPDATES,
        ActionType.SYSTEM_OPEN_GALAXY_STORE -> {
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
