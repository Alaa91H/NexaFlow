package com.nexaflow.feature.builder

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import com.nexaflow.core.execution.NotificationActionButton
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.variables.VariableResolver

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionConfigEditor(
    option: ActionOption,
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
    onPickApp: () -> Unit,
    availableVariables: List<String> = emptyList(),
    // Re-launches the plugin's EDIT_SETTING activity (plugin actions only).
    onPluginConfigure: (() -> Unit)? = null,
    // Saved tasks the notification action can attach as interactive buttons.
    automations: List<Automation> = emptyList()
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
                Text(text = stringResource(R.string.stream_label), style = MaterialTheme.typography.titleSmall)
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
        ActionType.SYSTEM_NETWORK_MODE -> {
            val modes = listOf(
                "AUTO" to stringResource(R.string.network_mode_auto),
                "2G" to stringResource(R.string.network_mode_2g),
                "3G" to stringResource(R.string.network_mode_3g),
                "4G" to stringResource(R.string.network_mode_4g),
                "5G" to stringResource(R.string.network_mode_5g)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.network_mode_label),
                    style = MaterialTheme.typography.titleSmall
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    modes.forEach { (value, label) ->
                        FilterChip(
                            selected = (config["mode"] ?: "AUTO") == value,
                            onClick = { onConfigChange(mapOf("mode" to value)) },
                            label = { Text(text = label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.network_mode_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        ActionType.SYSTEM_SET_RINGTONE -> {
            val context = LocalContext.current
            // Ringtone picker returns the chosen URI in the result intent; the
            // URI is stored so execution (and revert) can apply it later.
            val ringtoneLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val uri = result.data?.getStringExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                    ?: result.data?.let { data ->
                        runCatching {
                            data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                        }.getOrNull()?.toString()
                    }
                if (!uri.isNullOrBlank()) {
                    onConfigChange(config + ("uri" to uri))
                }
            }
            val ringtoneTitle = stringResource(R.string.choose_ringtone)
            val buildRingtoneIntent = {
                Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, ringtoneTitle)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    config["uri"]?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.ringtone_label),
                    style = MaterialTheme.typography.titleSmall
                )
                val currentUri = config["uri"]
                val ringtoneName = currentUri?.let { uri ->
                    runCatching {
                        RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context)
                    }.getOrNull()
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        val intent = buildRingtoneIntent()
                        if (intent.resolveActivity(context.packageManager) != null) {
                            ringtoneLauncher.launch(intent)
                        } else {
                            // No ringtone picker activity available: fall back to
                            // the default ringtone silently rather than crashing.
                            onConfigChange(
                                config + ("uri" to RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE).toString())
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Filled.MusicNote, contentDescription = null)
                    Text(
                        text = if (!ringtoneName.isNullOrBlank()) {
                            ringtoneName
                        } else {
                            stringResource(R.string.choose_ringtone)
                        },
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
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
                VariableInsertChips(
                    availableVariables = availableVariables,
                    currentValue = config["text"] ?: "",
                    onValueChange = { onConfigChange(config + ("text" to it)) }
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
                VariableInsertChips(
                    availableVariables = availableVariables,
                    currentValue = config["text"] ?: "",
                    onValueChange = { onConfigChange(config + ("text" to it)) }
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
                VariableInsertChips(
                    availableVariables = availableVariables,
                    currentValue = config["text"] ?: "",
                    onValueChange = { onConfigChange(config + ("text" to it)) }
                )
                Text(text = stringResource(R.string.sound_label), style = MaterialTheme.typography.titleSmall)
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // Interactive action buttons: each runs another saved task
                // straight from the notification (PendingIntent → receiver).
                NotificationButtonsEditor(
                    buttons = NotificationActionButton.fromConfig(config["action_buttons"]),
                    automations = automations,
                    onButtonsChange = { buttons ->
                        onConfigChange(
                            if (buttons.isEmpty()) config - "action_buttons"
                            else config + ("action_buttons" to NotificationActionButton.toConfig(buttons))
                        )
                    }
                )
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["url"] ?: "",
                    onValueChange = { onConfigChange(mapOf("url" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.url)) },
                    placeholder = { Text(text = stringResource(R.string.url_hint)) },
                    singleLine = true
                )
                VariableInsertChips(
                    availableVariables = availableVariables,
                    currentValue = config["url"] ?: "",
                    onValueChange = { onConfigChange(mapOf("url" to it)) }
                )
            }
        }
        ActionType.SYSTEM_HTTP_REQUEST -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["url"] ?: "",
                    onValueChange = { onConfigChange(config + ("url" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.url)) },
                    placeholder = { Text(text = "https://api.example.com/data") },
                    singleLine = true
                )
                VariableInsertChips(
                    availableVariables = availableVariables,
                    currentValue = config["url"] ?: "",
                    onValueChange = { onConfigChange(config + ("url" to it)) }
                )
                ContextPathInsertChips(
                    currentValue = config["url"] ?: "",
                    onValueChange = { onConfigChange(config + ("url" to it)) }
                )
                Text(text = stringResource(R.string.http_method), style = MaterialTheme.typography.titleSmall)
                val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    methods.forEach { method ->
                        FilterChip(
                            selected = (config["method"] ?: "GET") == method,
                            onClick = { onConfigChange(config + ("method" to method)) },
                            label = { Text(text = method, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
                OutlinedTextField(
                    value = config["body"] ?: "",
                    onValueChange = { onConfigChange(config + ("body" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.http_body)) },
                    placeholder = { Text(text = stringResource(R.string.http_body_hint)) },
                    minLines = 2,
                    maxLines = 4
                )
                VariableInsertChips(
                    availableVariables = availableVariables,
                    currentValue = config["body"] ?: "",
                    onValueChange = { onConfigChange(config + ("body" to it)) }
                )
                ContextPathInsertChips(
                    currentValue = config["body"] ?: "",
                    onValueChange = { onConfigChange(config + ("body" to it)) }
                )
            }
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
                VariableInsertChips(
                    availableVariables = availableVariables,
                    currentValue = config["message"] ?: "",
                    onValueChange = { onConfigChange(config + ("message" to it)) }
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
        ActionType.PLUGIN_FIRE -> {
            val blurb = config["blurb"].orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (blurb.isBlank()) {
                    Text(
                        text = stringResource(R.string.plugin_not_configured),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    Text(
                        text = blurb,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = config["package"].orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (onPluginConfigure != null) {
                    TextButton(onClick = onPluginConfigure) {
                        Text(text = stringResource(R.string.plugin_reconfigure))
                    }
                }
            }
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

/**
 * Samsung-style insert row: tapping a chip appends its `%NAME` placeholder to
 * the field's current text so users never type the syntax by hand. Built-in
 * and user-global variables share the row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariableInsertChips(
    availableVariables: List<String>,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    if (availableVariables.isEmpty()) return
    // Variables already referenced in the field's text are shown as selected
    // (live feedback that the insertion is in effect).
    val used = remember(currentValue) {
        VariableResolver.referencedPlaceholders(currentValue).map { it.lowercase() }.toSet()
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.insert_variable_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            availableVariables.forEach { name ->
                FilterChip(
                    selected = name.lowercase() in used,
                    onClick = {
                        val placeholder = "%$name"
                        onValueChange(
                            if (currentValue.isBlank()) placeholder
                            else "$currentValue $placeholder"
                        )
                    },
                    label = {
                        Text(
                            text = "%$name",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }
    }
}

/**
 * Step-5 insert row: tapping the chip appends a `%CTX.$` reference placeholder
 * to the field so the user can feed the output of an earlier node (published
 * via its `outputPath`) into this one. The JSONPath is typed after the `$`.
 */
@Composable
private fun ContextPathInsertChips(
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.insert_context_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = currentValue.contains("%CTX.", ignoreCase = true),
                onClick = {
                    val placeholder = "%CTX.$"
                    onValueChange(
                        if (currentValue.isBlank()) placeholder
                        else "$currentValue $placeholder"
                    )
                },
                label = { Text(text = "%CTX.$", style = MaterialTheme.typography.labelSmall) }
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

/**
 * Samsung-style editor for notification action buttons: attach up to three
 * tasks that run straight from the notification when it is shown. Tapping a
 * row opens a picker of the other saved tasks; each picked task becomes a
 * button labelled with the task name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationButtonsEditor(
    buttons: List<NotificationActionButton>,
    automations: List<Automation>,
    onButtonsChange: (List<NotificationActionButton>) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var replyEditorButton by remember { mutableStateOf<NotificationActionButton?>(null) }
    var replyEditorVariable by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.notification_buttons_title),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(R.string.notification_buttons_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        if (buttons.isEmpty()) {
            Text(
                text = stringResource(R.string.notification_buttons_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            buttons.forEach { button ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = button.label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            // Subtitle: the reply variable the button writes to
                            // (P2-1), so the user sees the target at a glance.
                            if (!button.replyVariable.isNullOrBlank()) {
                                Text(
                                    text = stringResource(
                                        R.string.notification_button_reply_sub,
                                        "%" + button.replyVariable
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        // Reply toggle: turns this button into a text-input
                        // action that stores the typed reply into a %variable.
                        IconButton(onClick = {
                            replyEditorVariable = button.replyVariable.orEmpty()
                            replyEditorButton = button
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = stringResource(R.string.notification_button_reply),
                                tint = if (button.replyVariable.isNullOrBlank()) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        IconButton(onClick = {
                            onButtonsChange(buttons.filterNot { it.automationId == button.automationId })
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.notification_button_remove),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
        // Android caps a notification at three action buttons.
        if (buttons.size < 3) {
            TextButton(
                onClick = { showPicker = true },
                enabled = automations.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(text = stringResource(R.string.notification_buttons_add))
            }
        }
    }

    if (showPicker) {
        // Google 2026: selection tasks open as a full-height modal bottom sheet.
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Text(
                text = stringResource(R.string.notification_buttons_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                if (automations.isEmpty()) {
                    Text(
                        text = stringResource(R.string.notification_buttons_picker_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    // Never offer a task twice — already-attached buttons are
                    // hidden from the picker so duplicate entries can't stack.
                    val selectedIds = buttons.map { it.automationId }.toSet()
                    automations.filterNot { it.id in selectedIds }.forEach { automation ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onButtonsChange(buttons + NotificationActionButton(automation.name, automation.id))
                                    showPicker = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = automation.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
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
                TextButton(onClick = { showPicker = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        }
    }

    // Reply-variable editor: a small dialog setting the %variable that receives
    // the text typed into this button's RemoteInput field. Blank clears it.
    replyEditorButton?.let { button ->
        AlertDialog(
            onDismissRequest = {
                replyEditorButton = null
                replyEditorVariable = ""
            },
            title = { Text(text = stringResource(R.string.notification_button_reply_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.notification_button_reply_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    OutlinedTextField(
                        value = replyEditorVariable,
                        onValueChange = { replyEditorVariable = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = stringResource(R.string.notification_button_reply_label)) },
                        placeholder = { Text(text = "MyReply") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val updated = buttons.map {
                        if (it.automationId == button.automationId) {
                            it.copy(replyVariable = replyEditorVariable.takeIf { v -> v.isNotBlank() })
                        } else it
                    }
                    onButtonsChange(updated)
                    replyEditorButton = null
                    replyEditorVariable = ""
                }) {
                    Text(text = stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    replyEditorButton = null
                    replyEditorVariable = ""
                }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}
