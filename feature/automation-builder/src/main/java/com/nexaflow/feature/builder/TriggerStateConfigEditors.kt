package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.CategoryChip

/**
 * Configuration sections for the trigger families that the canonical schema
 * editor does not render yet (see `TriggerEditorRoutingTest`).
 *
 * These were split out of [TriggerEditorCard] so the card stays a dispatcher
 * instead of carrying every family's field layout. Each function is a pure
 * projection of the draft onto the chips/text fields the card rendered inline,
 * so behaviour and strings are unchanged.
 */

@Composable
internal fun AppInstalledConfig(
    draft: TriggerDraft,
    onConfigChange: (TriggerDraft) -> Unit,
    onPickApp: () -> Unit
) {
    Text(text = stringResource(R.string.event), style = MaterialTheme.typography.titleSmall)
    OptionChips(
        options = listOf("INSTALLED", "REMOVED", "UPDATED"),
        labels = mapOf(
            "INSTALLED" to stringResource(R.string.app_installed),
            "REMOVED" to stringResource(R.string.app_removed),
            "UPDATED" to stringResource(R.string.app_updated)
        ),
        selected = draft.config["event"] ?: "INSTALLED",
        onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
    )
    val packageName = draft.config["package"]?.trim().orEmpty()
    Text(
        text = packageName.ifBlank { stringResource(R.string.no_apps_selected) },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary
    )
    OutlinedButton(onClick = onPickApp, modifier = Modifier.fillMaxWidth()) {
        Icon(imageVector = Icons.Filled.Apps, contentDescription = null)
        Text(
            text = stringResource(R.string.choose_app),
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
internal fun VolumeChangedConfig(draft: TriggerDraft, onConfigChange: (TriggerDraft) -> Unit) {
    Text(text = stringResource(R.string.volume_stream), style = MaterialTheme.typography.titleSmall)
    OptionChips(
        options = listOf("MUSIC", "RING", "ALARM", "NOTIFICATION"),
        labels = mapOf(
            "MUSIC" to stringResource(R.string.volume_stream_music),
            "RING" to stringResource(R.string.volume_stream_ring),
            "ALARM" to stringResource(R.string.volume_stream_alarm),
            "NOTIFICATION" to stringResource(R.string.volume_stream_notification)
        ),
        selected = draft.config["stream"] ?: "MUSIC",
        onSelect = { onConfigChange(draft.copy(config = draft.config + ("stream" to it))) }
    )
    Text(text = stringResource(R.string.volume_direction), style = MaterialTheme.typography.titleSmall)
    OptionChips(
        options = listOf("ABOVE", "BELOW"),
        labels = mapOf(
            "ABOVE" to stringResource(R.string.volume_above),
            "BELOW" to stringResource(R.string.volume_below)
        ),
        selected = draft.config["direction"] ?: "ABOVE",
        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
    )
    OutlinedTextField(
        value = draft.config["threshold"] ?: "50",
        onValueChange = { v ->
            onConfigChange(draft.copy(config = draft.config + ("threshold" to v.filter { it.isDigit() })))
        },
        label = { Text(text = stringResource(R.string.volume_threshold)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun BrightnessLevelConfig(draft: TriggerDraft, onConfigChange: (TriggerDraft) -> Unit) {
    Text(text = stringResource(R.string.trigger_brightness_direction), style = MaterialTheme.typography.titleSmall)
    OptionChips(
        options = listOf("ABOVE", "BELOW"),
        labels = mapOf(
            "ABOVE" to stringResource(R.string.volume_above),
            "BELOW" to stringResource(R.string.volume_below)
        ),
        selected = draft.config["direction"] ?: "ABOVE",
        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
    )
    OutlinedTextField(
        value = draft.config["threshold"] ?: "128",
        onValueChange = { v ->
            onConfigChange(draft.copy(config = draft.config + ("threshold" to v.filter { it.isDigit() })))
        },
        label = { Text(text = stringResource(R.string.trigger_brightness_threshold)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun StorageLowConfig(draft: TriggerDraft, onConfigChange: (TriggerDraft) -> Unit) {
    Text(text = stringResource(R.string.trigger_storage_direction), style = MaterialTheme.typography.titleSmall)
    OptionChips(
        options = listOf("BELOW", "ABOVE"),
        labels = mapOf(
            "BELOW" to stringResource(R.string.storage_below),
            "ABOVE" to stringResource(R.string.storage_above)
        ),
        selected = draft.config["direction"] ?: "BELOW",
        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
    )
    OutlinedTextField(
        value = draft.config["threshold"] ?: "1024",
        onValueChange = { v ->
            onConfigChange(draft.copy(config = draft.config + ("threshold" to v.filter { it.isDigit() })))
        },
        label = { Text(text = stringResource(R.string.trigger_storage_threshold)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Wi-Fi and cellular signal strength share the same threshold + direction contract. */
@Composable
internal fun SignalStrengthConfig(draft: TriggerDraft, onConfigChange: (TriggerDraft) -> Unit) {
    Text(text = stringResource(R.string.trigger_signal_threshold), style = MaterialTheme.typography.titleSmall)
    OptionChips(
        options = listOf("1", "2", "3", "4"),
        selected = draft.config["threshold"] ?: "3",
        onSelect = { onConfigChange(draft.copy(config = draft.config + ("threshold" to it))) }
    )
    OptionChips(
        options = listOf("ABOVE", "BELOW"),
        labels = mapOf(
            "ABOVE" to stringResource(R.string.volume_above),
            "BELOW" to stringResource(R.string.volume_below)
        ),
        selected = draft.config["direction"] ?: "ABOVE",
        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
    )
}

@Composable
internal fun BatteryTemperatureConfig(draft: TriggerDraft, onConfigChange: (TriggerDraft) -> Unit) {
    Text(text = stringResource(R.string.trigger_temperature_threshold), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = draft.config["threshold"] ?: "40",
        onValueChange = { input ->
            // Temperature is a decimal number (°C): keep digits
            // and a single decimal separator only.
            val cleaned = buildString {
                input.forEachIndexed { index, char ->
                    when {
                        char.isDigit() -> append(char)
                        char == '-' && index == 0 && isEmpty() -> append(char)
                        char == '.' && '.' !in this -> append(char)
                    }
                }
            }
            onConfigChange(draft.copy(config = draft.config + ("threshold" to cleaned)))
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.trigger_temperature_threshold)) },
        supportingText = { Text(stringResource(R.string.battery_temp_hint)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    OptionChips(
        options = listOf("ABOVE", "BELOW"),
        labels = mapOf(
            "ABOVE" to stringResource(R.string.volume_above),
            "BELOW" to stringResource(R.string.volume_below)
        ),
        selected = draft.config["direction"] ?: "ABOVE",
        onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
    )
}

@Composable
internal fun ClipboardChangedConfig(draft: TriggerDraft, onConfigChange: (TriggerDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.trigger_desc_clipboard), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = draft.config["contains"].orEmpty(),
            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("contains" to it))) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.notification_contains)) },
            placeholder = { Text(stringResource(R.string.sms_contains_hint)) },
            singleLine = true
        )
    }
}

@Composable
internal fun ScreenTimeoutChangedConfig(draft: TriggerDraft, onConfigChange: (TriggerDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.trigger_desc_screen_timeout), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = draft.config["seconds"].orEmpty(),
            onValueChange = { value ->
                onConfigChange(
                    draft.copy(config = draft.config + ("seconds" to value.filter { it.isDigit() }))
                )
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.action_screen_timeout)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
internal fun NfcTagScannedConfig(draft: TriggerDraft, onConfigChange: (TriggerDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.trigger_desc_nfc_tag), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = draft.config["contains"].orEmpty(),
            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("contains" to it))) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.notification_contains)) },
            placeholder = { Text(stringResource(R.string.sms_contains_hint)) },
            singleLine = true
        )
    }
}

@Composable
internal fun WearEventConfig(draft: TriggerDraft, onConfigChange: (TriggerDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.trigger_wear_event_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        OutlinedTextField(
            value = draft.config["watchInstallId"].orEmpty(),
            onValueChange = {
                onConfigChange(
                    draft.copy(
                        config = draft.config + ("watchInstallId" to it.trim())
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.trigger_wear_event)) },
            placeholder = { Text(stringResource(R.string.any_device)) },
            singleLine = true
        )
        Text(
            text = stringResource(R.string.state),
            style = MaterialTheme.typography.titleSmall
        )
        OptionChips(
            options = listOf("CONNECTED", "DISCONNECTED"),
            labels = mapOf(
                "CONNECTED" to stringResource(R.string.state_connected),
                "DISCONNECTED" to stringResource(R.string.state_disconnected)
            ),
            selected = draft.config["state"] ?: "CONNECTED",
            onSelect = {
                onConfigChange(
                    draft.copy(config = draft.config + ("state" to it))
                )
            }
        )
    }
}

/**
 * Battery level (with an independent plug/charger filter) lives in its own
 * section because it is the only trigger with two orthogonal conditions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BatteryTriggerConfig(draft: TriggerDraft, onConfigChange: (TriggerDraft) -> Unit) {
    val direction = draft.config["direction"] ?: "ABOVE"
    val chargerType = draft.config["chargerType"] ?: "ANY"
    val chargingState = draft.config["chargingState"] ?: "ANY"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val threshold = (draft.config["above"] ?: "80").toIntOrNull() ?: 80
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.BatteryChargingFull,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = stringResource(R.string.battery_trigger_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = if (direction == "ABOVE") {
                        stringResource(R.string.battery_above_sub)
                    } else {
                        stringResource(R.string.battery_below_sub)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        // ── Battery level (independent of charging) ──────────
        Text(
            text = stringResource(R.string.battery_level_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip(
                selected = direction == "ABOVE",
                onClick = {
                    onConfigChange(draft.copy(config = draft.config + ("direction" to "ABOVE")))
                },
                label = stringResource(R.string.battery_above)
            )
            CategoryChip(
                selected = direction == "BELOW",
                onClick = {
                    onConfigChange(draft.copy(config = draft.config + ("direction" to "BELOW")))
                },
                label = stringResource(R.string.battery_below)
            )
        }
        SliderRow(
            label = if (direction == "ABOVE") {
                stringResource(R.string.minimum_battery, threshold)
            } else {
                stringResource(R.string.maximum_battery, threshold)
            },
            value = threshold.toFloat(),
            onValueChange = { value ->
                onConfigChange(
                    draft.copy(config = draft.config + ("above" to value.toInt().toString()))
                )
            },
            valueRange = 5f..100f
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // ── Charging state (plug + charger type, separate) ────
        Text(
            text = stringResource(R.string.charging_state_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        val chargingOptions = listOf(
            "ANY" to R.string.charging_any,
            "CHARGING" to R.string.charging_yes,
            "NOT_CHARGING" to R.string.charging_no
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            chargingOptions.forEach { (value, labelRes) ->
                CategoryChip(
                    selected = chargingState == value,
                    onClick = {
                        onConfigChange(draft.copy(config = draft.config + ("chargingState" to value)))
                    },
                    label = stringResource(labelRes)
                )
            }
        }
        Text(
            text = stringResource(R.string.charger_type_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        val chargerOptions = listOf(
            "ANY" to R.string.charger_any,
            "AC" to R.string.charger_ac,
            "USB" to R.string.charger_usb,
            "WIRELESS" to R.string.charger_wireless
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            chargerOptions.forEach { (value, labelRes) ->
                CategoryChip(
                    selected = chargerType == value,
                    onClick = {
                        onConfigChange(draft.copy(config = draft.config + ("chargerType" to value)))
                    },
                    label = stringResource(labelRes)
                )
            }
        }
    }
}
