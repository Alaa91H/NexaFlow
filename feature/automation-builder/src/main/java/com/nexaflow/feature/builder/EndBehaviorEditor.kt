package com.nexaflow.feature.builder

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.SelectChip
import com.nexaflow.core.ui.theme.NexaFlowTheme
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndBehaviorCatalog
import com.nexaflow.domain.models.EndMode

/**
 * Samsung-style "when the task ends" editor shown for each action in the
 * unified end-behavior section at the bottom of the builder.
 *
 * Every action offers two base options — leave the change as-is, or run the
 * action again when the task's condition ends — plus type-specific ones:
 *  - toggle actions (Wi-Fi, Bluetooth, NFC, ...) add Turn on / Turn off / Restore original
 *  - value actions (volume, brightness, ringer, ...) add Restore original / Set value
 *    (with a compact value editor for the chosen value)
 *  - ringtone adds Restore original only
 *
 * [showLabel] hides the "When the task ends" header so the editor can be
 * embedded inside the unified end-behavior card, once per action, without
 * repeating the title.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EndBehaviorEditor(
    actionType: ActionType,
    behavior: EndBehavior?,
    onBehaviorChange: (EndBehavior?) -> Unit,
    showLabel: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showLabel) {
            Text(
                text = stringResource(R.string.end_behavior_label),
                style = MaterialTheme.typography.titleSmall
            )
        }
        // Base options shared by every action: leave as-is, or re-run the
        // action when the task's condition ends (e.g. re-send the
        // notification or re-apply the volume).
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SelectChip(
                selected = behavior == null || behavior.mode == EndMode.LEAVE,
                onClick = { onBehaviorChange(null) },
                label = stringResource(R.string.end_leave)
            )
            SelectChip(
                selected = behavior?.mode == EndMode.RERUN,
                onClick = { onBehaviorChange(EndBehavior(EndMode.RERUN)) },
                label = stringResource(R.string.end_rerun)
            )
        }
        when {
            actionType in EndBehaviorCatalog.toggleActions -> {
                val on = behavior?.mode == EndMode.SET_VALUE && behavior.config["enabled"] == "true"
                val off = behavior?.mode == EndMode.SET_VALUE && behavior.config["enabled"] == "false"
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SelectChip(
                        selected = on,
                        onClick = { onBehaviorChange(EndBehavior(EndMode.SET_VALUE, mapOf("enabled" to "true"))) },
                        label = stringResource(R.string.end_turn_on)
                    )
                    SelectChip(
                        selected = off,
                        onClick = { onBehaviorChange(EndBehavior(EndMode.SET_VALUE, mapOf("enabled" to "false"))) },
                        label = stringResource(R.string.end_turn_off)
                    )
                    if (EndBehaviorCatalog.supportsRevert(actionType)) {
                        SelectChip(
                            selected = behavior?.mode == EndMode.REVERT,
                            onClick = { onBehaviorChange(EndBehavior(EndMode.REVERT)) },
                            label = stringResource(R.string.end_revert)
                        )
                    }
                }
            }
            actionType in EndBehaviorCatalog.revertOnlyActions -> {
                // Non-numeric changes (a picked ringtone): the only extra end
                // option is "restore the previous one" — a fixed value makes
                // no sense for a URI.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SelectChip(
                        selected = behavior?.mode == EndMode.REVERT,
                        onClick = { onBehaviorChange(EndBehavior(EndMode.REVERT)) },
                        label = stringResource(R.string.end_revert)
                    )
                }
            }
            actionType in EndBehaviorCatalog.valueActions -> {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (EndBehaviorCatalog.supportsRevert(actionType)) {
                        SelectChip(
                            selected = behavior?.mode == EndMode.REVERT,
                            onClick = { onBehaviorChange(EndBehavior(EndMode.REVERT)) },
                            label = stringResource(R.string.end_revert)
                        )
                    }
                    SelectChip(
                        selected = behavior?.mode == EndMode.SET_VALUE,
                        onClick = { onBehaviorChange(EndBehavior(EndMode.SET_VALUE, defaultEndValue(actionType))) },
                        label = stringResource(R.string.end_set_value)
                    )
                }
                if (behavior?.mode == EndMode.SET_VALUE) {
                    EndValueEditor(
                        actionType = actionType,
                        config = behavior.config,
                        onConfigChange = { updated ->
                            onBehaviorChange(behavior.copy(config = updated))
                        }
                    )
                }
            }
        }
    }
}

private fun defaultEndValue(type: ActionType): Map<String, String> = when (type) {
    ActionType.SYSTEM_BRIGHTNESS -> mapOf("value" to "128")
    ActionType.SYSTEM_VOLUME,
    ActionType.SYSTEM_RING_VOLUME -> mapOf("value" to "50")
    ActionType.SYSTEM_STREAM_VOLUME -> mapOf("stream" to "MUSIC", "value" to "50")
    ActionType.SYSTEM_RINGER_MODE -> mapOf("mode" to "NORMAL")
    ActionType.SYSTEM_SCREEN_TIMEOUT -> mapOf("seconds" to "60")
    ActionType.SYSTEM_SCREEN_ROTATION -> mapOf("autoRotate" to "true")
    ActionType.SYSTEM_NETWORK_MODE -> mapOf("mode" to "AUTO")
    else -> emptyMap()
}

/** Compact value editor shown when a value action is set to end with a value. */
@Composable
private fun EndValueEditor(
    actionType: ActionType,
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit
) {
    when (actionType) {
        ActionType.SYSTEM_BRIGHTNESS -> {
            val value = config["value"]?.toIntOrNull() ?: 128
            SliderRow(
                label = stringResource(R.string.brightness_label, value),
                value = value.toFloat(),
                onValueChange = { onConfigChange(mapOf("value" to it.toInt().toString())) },
                valueRange = 0f..255f
            )
        }
        ActionType.SYSTEM_VOLUME,
        ActionType.SYSTEM_RING_VOLUME -> {
            val value = config["value"]?.toIntOrNull() ?: 50
            SliderRow(
                label = stringResource(
                    if (actionType == ActionType.SYSTEM_RING_VOLUME) R.string.ring_volume_label else R.string.volume_label,
                    value
                ),
                value = value.toFloat(),
                onValueChange = { onConfigChange(mapOf("value" to it.toInt().toString())) },
                valueRange = 0f..100f
            )
        }
        ActionType.SYSTEM_STREAM_VOLUME -> {
            // Same channel list as the action editor: the user picks exactly
            // which stream (media, ringtone, notifications, alarm, ...) gets
            // the end-of-task value, so the end behavior matches the action.
            val streams = STREAM_OPTIONS.map { (key, res) -> key to stringResource(res) }
            val selectedStream = config["stream"] ?: "MUSIC"
            val value = config["value"]?.toIntOrNull() ?: 50
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    streams.forEach { (stream, label) ->
                        SelectChip(
                            selected = selectedStream == stream,
                            onClick = { onConfigChange(config + ("stream" to stream)) },
                            label = label
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
        ActionType.SYSTEM_RINGER_MODE -> {
            val modes = listOf(
                "NORMAL" to stringResource(R.string.ringer_normal),
                "VIBRATE" to stringResource(R.string.ringer_vibrate),
                "SILENT" to stringResource(R.string.ringer_silent)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                modes.forEach { (value, label) ->
                    SelectChip(
                        selected = (config["mode"] ?: "NORMAL") == value,
                        onClick = { onConfigChange(mapOf("mode" to value)) },
                        label = label
                    )
                }
            }
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
        ActionType.SYSTEM_SCREEN_ROTATION -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.auto_rotate),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = config["autoRotate"]?.toBoolean() ?: true,
                    onCheckedChange = { onConfigChange(mapOf("autoRotate" to it.toString())) }
                )
            }
        }
        ActionType.SYSTEM_NETWORK_MODE -> {
            NetworkModeSelector(config = config, onConfigChange = onConfigChange)
        }
        else -> Unit
    }
}

// region Preview

@Preview(name = "EndBehaviorEditor", showBackground = true)
@Composable
private fun EndBehaviorEditorPreview() {
    MaterialTheme {
        EndBehaviorEditor(
            actionType = ActionType.SYSTEM_DND,
            behavior = EndBehavior(),
            onBehaviorChange = {}
        )
    }
}

// endregion
