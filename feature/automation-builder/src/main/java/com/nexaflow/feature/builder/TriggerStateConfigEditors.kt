package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.CategoryChip

/**
 * Configuration sections for the trigger families that the canonical schema
 * editor does not render (see `TriggerEditorRoutingTest`).
 *
 * Ink rules applied here: a bounded value is a slider with − / + (never a typed
 * number), a fixed choice is a translated chip, and the only text boxes left are
 * genuinely free text (a search phrase, a webhook path).
 */

@Composable
internal fun AppInstalledConfig(
    draft: TriggerDraft,
    onConfigChange: (TriggerDraft) -> Unit,
    onPickApp: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
}

// ── Volume ────────────────────────────────────────────────────────────────────

/** One sound stream and the level that makes it trigger. */
internal data class VolumeStreamSetting(
    val stream: String,
    val enabled: Boolean,
    val threshold: Int,
    val direction: String,
)

internal val volumeStreams: List<String> = listOf("MUSIC", "RING", "ALARM", "NOTIFICATION")

/**
 * Reads the per-stream contract. Tasks saved before per-stream levels existed
 * carry the legacy `stream`/`threshold`/`direction` triple and are read as that
 * single stream, so nothing stored is invalidated.
 */
internal fun volumeStreamSettings(config: Map<String, String>): List<VolumeStreamSetting> {
    val legacyStream = (config["stream"] ?: "MUSIC").takeIf { it in volumeStreams } ?: "MUSIC"
    val declared = config["streams"].orEmpty()
        .split(',')
        .map(String::trim)
        .filter { it in volumeStreams }
        .distinct()
        .ifEmpty { listOf(legacyStream) }
    return volumeStreams.map { stream ->
        VolumeStreamSetting(
            stream = stream,
            enabled = stream in declared,
            threshold = (config["threshold_$stream"] ?: config["threshold"])
                ?.toIntOrNull()
                ?.coerceIn(0, 100)
                ?: 50,
            direction = (config["direction_$stream"] ?: config["direction"] ?: "ABOVE")
                .takeIf { it == "ABOVE" || it == "BELOW" }
                ?: "ABOVE",
        )
    }
}

/**
 * Writes the per-stream contract and mirrors the first enabled stream onto the
 * legacy keys, so older readers of `stream`/`threshold` keep working.
 */
internal fun Map<String, String>.withVolumeStreamSettings(settings: List<VolumeStreamSetting>): Map<String, String> {
    val enabled = settings.filter { it.enabled }.ifEmpty { settings.take(1) }
    val updated = toMutableMap()
    updated["streams"] = enabled.joinToString(",") { it.stream }
    settings.forEach { setting ->
        updated["threshold_${setting.stream}"] = setting.threshold.toString()
        updated["direction_${setting.stream}"] = setting.direction
    }
    val legacy = enabled.first()
    updated["stream"] = legacy.stream
    updated["threshold"] = legacy.threshold.toString()
    updated["direction"] = legacy.direction
    return updated
}

/**
 * Every sound stream in one trigger: a switch to watch it, a − / + level and the
 * above/below direction. One volume trigger now describes "music above 60, ringer
 * below 20" instead of forcing a separate trigger per stream.
 */
@Composable
internal fun VolumeChangedConfig(draft: TriggerDraft, onConfigChange: (TriggerDraft) -> Unit) {
    val settings = volumeStreamSettings(draft.config)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.volume_streams_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        settings.forEachIndexed { index, setting ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(streamLabelRes(setting.stream)),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = setting.enabled,
                    onCheckedChange = { enabled ->
                        onConfigChange(
                            draft.copy(
                                config = draft.config.withVolumeStreamSettings(
                                    settings.toMutableList().also { list ->
                                        list[index] = setting.copy(enabled = enabled)
                                    }
                                )
                            )
                        )
                    }
                )
            }
            if (setting.enabled) {
                OptionChips(
                    options = listOf("ABOVE", "BELOW"),
                    labels = mapOf(
                        "ABOVE" to stringResource(R.string.volume_above),
                        "BELOW" to stringResource(R.string.volume_below)
                    ),
                    selected = setting.direction,
                    onSelect = { direction ->
                        onConfigChange(
                            draft.copy(
                                config = draft.config.withVolumeStreamSettings(
                                    settings.toMutableList().also { list ->
                                        list[index] = setting.copy(direction = direction)
                                    }
                                )
                            )
                        )
                    }
                )
                SteppedValueRow(
                    label = stringResource(streamLabelRes(setting.stream)),
                    valueText = stringResource(R.string.schema_value_percent, setting.threshold),
                    value = setting.threshold.toFloat(),
                    valueRange = 0f..100f,
                    step = 1f,
                    onValueChange = { level ->
                        onConfigChange(
                            draft.copy(
                                config = draft.config.withVolumeStreamSettings(
                                    settings.toMutableList().also { list ->
                                        list[index] = setting.copy(threshold = level.toInt().coerceIn(0, 100))
                                    }
                                )
                            )
                        )
                    }
                )
            }
        }
    }
}

private fun streamLabelRes(stream: String): Int = when (stream) {
    "RING" -> R.string.volume_stream_ring
    "ALARM" -> R.string.volume_stream_alarm
    "NOTIFICATION" -> R.string.volume_stream_notification
    else -> R.string.volume_stream_music
}

// ── Display brightness ────────────────────────────────────────────────────────

/**
 * Brightness is stored on the ROM's 0..255 scale but is meaningless to a user,
 * so the control shows the percentage the notification shade shows.
 */
@Composable
internal fun BrightnessLevelConfig(draft: TriggerDraft, onConfigChange: (TriggerDraft) -> Unit) {
    val threshold = (draft.config["threshold"] ?: "128").toIntOrNull()?.coerceIn(0, 255) ?: 128
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionChips(
            options = listOf("ABOVE", "BELOW"),
            labels = mapOf(
                "ABOVE" to stringResource(R.string.volume_above),
                "BELOW" to stringResource(R.string.volume_below)
            ),
            selected = draft.config["direction"] ?: "ABOVE",
            onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
        )
        SteppedValueRow(
            label = stringResource(R.string.trigger_brightness_threshold),
            valueText = stringResource(
                R.string.schema_value_percent,
                (threshold / 255f * 100f).toInt().coerceIn(0, 100)
            ),
            value = threshold.toFloat(),
            valueRange = 0f..255f,
            step = 5f,
            onValueChange = { value ->
                onConfigChange(
                    draft.copy(config = draft.config + ("threshold" to value.toInt().coerceIn(0, 255).toString()))
                )
            }
        )
    }
}

// ── Storage ───────────────────────────────────────────────────────────────────

/** Storage thresholds are megabytes; the row shows MB below a gigabyte and GB above it. */
@Composable
internal fun StorageLowConfig(draft: TriggerDraft, onConfigChange: (TriggerDraft) -> Unit) {
    val threshold = (draft.config["threshold"] ?: "1024").toIntOrNull()?.coerceIn(0, 8192) ?: 1024
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionChips(
            options = listOf("BELOW", "ABOVE"),
            labels = mapOf(
                "BELOW" to stringResource(R.string.storage_below),
                "ABOVE" to stringResource(R.string.storage_above)
            ),
            selected = draft.config["direction"] ?: "BELOW",
            onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
        )
        SteppedValueRow(
            label = stringResource(R.string.trigger_storage_threshold),
            valueText = if (threshold >= 1024) {
                stringResource(R.string.schema_value_gigabytes, trimNumber(threshold / 1024f))
            } else {
                stringResource(R.string.schema_value_megabytes, threshold.toString())
            },
            value = threshold.toFloat(),
            valueRange = 0f..8192f,
            step = 128f,
            onValueChange = { value ->
                onConfigChange(
                    draft.copy(config = draft.config + ("threshold" to value.toInt().coerceIn(0, 8192).toString()))
                )
            }
        )
    }
}

// ── Battery temperature ───────────────────────────────────────────────────────

@Composable
internal fun BatteryTemperatureConfig(draft: TriggerDraft, onConfigChange: (TriggerDraft) -> Unit) {
    val threshold = (draft.config["threshold"] ?: "40").toFloatOrNull()?.coerceIn(-20f, 80f) ?: 40f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionChips(
            options = listOf("ABOVE", "BELOW"),
            labels = mapOf(
                "ABOVE" to stringResource(R.string.volume_above),
                "BELOW" to stringResource(R.string.volume_below)
            ),
            selected = draft.config["direction"] ?: "ABOVE",
            onSelect = { onConfigChange(draft.copy(config = draft.config + ("direction" to it))) }
        )
        SteppedValueRow(
            label = stringResource(R.string.trigger_temperature_threshold),
            valueText = stringResource(R.string.schema_value_celsius, trimNumber(threshold)),
            value = threshold,
            valueRange = -20f..80f,
            step = 0.5f,
            onValueChange = { value ->
                onConfigChange(
                    draft.copy(config = draft.config + ("threshold" to trimNumber(value.coerceIn(-20f, 80f))))
                )
            }
        )
    }
}

private fun trimNumber(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)

// ── Signal strength ───────────────────────────────────────────────────────────

/** Wi-Fi and cellular signal strength share the same threshold + direction contract. */
@Composable
internal fun SignalStrengthConfig(draft: TriggerDraft, onConfigChange: (TriggerDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.trigger_signal_threshold),
            style = MaterialTheme.typography.titleSmall
        )
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
}

// ── Clipboard / NFC / Wear ────────────────────────────────────────────────────

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
        OptionChips(
            options = listOf("CONNECTED", "DISCONNECTED"),
            labels = mapOf(
                "CONNECTED" to stringResource(R.string.state_connected),
                "DISCONNECTED" to stringResource(R.string.state_disconnected)
            ),
            selected = draft.config["state"] ?: "CONNECTED",
            onSelect = { onConfigChange(draft.copy(config = draft.config + ("state" to it))) }
        )
    }
}

// ── Battery ───────────────────────────────────────────────────────────────────

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
        SteppedValueRow(
            label = if (direction == "ABOVE") {
                stringResource(R.string.minimum_battery, threshold)
            } else {
                stringResource(R.string.maximum_battery, threshold)
            },
            valueText = stringResource(R.string.schema_value_percent, threshold),
            value = threshold.toFloat(),
            valueRange = 5f..100f,
            onValueChange = { value ->
                onConfigChange(
                    draft.copy(config = draft.config + ("above" to value.toInt().toString()))
                )
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // ── Charging state (plug + charger type, separate) ────
        Text(
            text = stringResource(R.string.charging_state_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        OptionChips(
            options = listOf("ANY", "CHARGING", "NOT_CHARGING"),
            labels = mapOf(
                "ANY" to stringResource(R.string.charging_any),
                "CHARGING" to stringResource(R.string.charging_yes),
                "NOT_CHARGING" to stringResource(R.string.charging_no)
            ),
            selected = chargingState,
            onSelect = { onConfigChange(draft.copy(config = draft.config + ("chargingState" to it))) }
        )
        Text(
            text = stringResource(R.string.charger_type_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        OptionChips(
            options = listOf("ANY", "AC", "USB", "WIRELESS"),
            labels = mapOf(
                "ANY" to stringResource(R.string.charger_any),
                "AC" to stringResource(R.string.charger_ac),
                "USB" to stringResource(R.string.charger_usb),
                "WIRELESS" to stringResource(R.string.charger_wireless)
            ),
            selected = chargerType,
            onSelect = { onConfigChange(draft.copy(config = draft.config + ("chargerType" to it))) }
        )
    }
}

// ── Charger ───────────────────────────────────────────────────────────────────

/**
 * Charger trigger: whether the plug went in or out, and which kind of source
 * counts. "USB from a computer" and "wall charger" are the two cases users
 * actually automate differently (developer charging vs. overnight), so the
 * source is part of the condition instead of a separate trigger.
 */
@Composable
internal fun ChargerTriggerConfig(draft: TriggerDraft, onConfigChange: (TriggerDraft) -> Unit) {
    val event = draft.config["event"] ?: "CONNECTED"
    val source = draft.config["chargerType"] ?: "ANY"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionChips(
            options = listOf("CONNECTED", "DISCONNECTED"),
            labels = mapOf(
                "CONNECTED" to stringResource(R.string.charger_event_connected),
                "DISCONNECTED" to stringResource(R.string.charger_event_disconnected)
            ),
            selected = event,
            onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
        )
        Text(
            text = stringResource(R.string.charger_source_label),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(R.string.charger_source_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        OptionChips(
            options = listOf("ANY", "AC", "USB", "WIRELESS"),
            labels = mapOf(
                "ANY" to stringResource(R.string.charger_any),
                "AC" to stringResource(R.string.charger_source_ac),
                "USB" to stringResource(R.string.charger_source_usb),
                "WIRELESS" to stringResource(R.string.charger_wireless)
            ),
            selected = source,
            onSelect = { onConfigChange(draft.copy(config = draft.config + ("chargerType" to it))) }
        )
    }
}

// ── Notifications ─────────────────────────────────────────────────────────────

/**
 * Notification kinds mapped to the platform's notification categories. The
 * monitor compares the incoming notification's category against this list, so
 * "messages only" really means only messaging notifications.
 */
internal val notificationKindCategories: Map<String, List<String>> = linkedMapOf(
    "MESSAGES" to listOf("msg", "email"),
    "CALLS" to listOf("call", "missed_call", "incoming_call"),
    "REMINDERS" to listOf("reminder", "alarm", "event"),
    "PROMOTIONS" to listOf("promo", "recommendation"),
    "ONGOING" to listOf("progress", "service", "sys", "status"),
)

internal fun notificationKindCategories(config: Map<String, String>): Set<String> =
    config["categories"].orEmpty()
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

internal fun Map<String, String>.withNotificationKinds(kinds: Set<String>): Map<String, String> {
    val categories = kinds.filter { it in notificationKindCategories }
        .flatMap { notificationKindCategories.getValue(it) }
        .distinct()
    return if (categories.isEmpty()) this - "categories"
    else this + ("categories" to categories.joinToString(","))
}

internal fun notificationKindsFor(categories: Set<String>): Set<String> =
    notificationKindCategories.filterValues { declared -> declared.any { it in categories } }.keys

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotificationTriggerConfig(
    draft: TriggerDraft,
    onConfigChange: (TriggerDraft) -> Unit,
    onPickApp: () -> Unit,
    onExplainSpecial: (SpecialPermission) -> Unit,
    refreshKey: Int,
) {
    val packages = draft.pickedPackages()
    val categories = notificationKindCategories(draft.config)
    val selectedKinds = notificationKindsFor(categories)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (packages.isEmpty()) {
                    stringResource(R.string.any_app)
                } else {
                    stringResource(R.string.selected_apps_count, packages.size)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onPickApp) {
                Icon(imageVector = Icons.Filled.Apps, contentDescription = null)
                Text(
                    text = stringResource(R.string.choose_apps),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.notification_kind_label),
            style = MaterialTheme.typography.titleSmall
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            notificationKindCategories.keys.forEach { kind ->
                CategoryChip(
                    selected = kind in selectedKinds,
                    onClick = {
                        val updated = if (kind in selectedKinds) selectedKinds - kind else selectedKinds + kind
                        onConfigChange(draft.copy(config = draft.config.withNotificationKinds(updated)))
                    },
                    label = stringResource(notificationKindLabelRes(kind))
                )
            }
        }
        if (selectedKinds.isNotEmpty()) {
            TextButton(
                onClick = { onConfigChange(draft.copy(config = draft.config.withNotificationKinds(emptySet()))) }
            ) {
                Text(stringResource(R.string.notification_kind_any))
            }
        }
        OptionChips(
            options = listOf("POSTED", "REMOVED"),
            labels = mapOf(
                "POSTED" to stringResource(R.string.schema_token_posted),
                "REMOVED" to stringResource(R.string.notification_event_removed)
            ),
            selected = draft.config["event"] ?: "POSTED",
            onSelect = { onConfigChange(draft.copy(config = draft.config + ("event" to it))) }
        )
        OutlinedTextField(
            value = draft.config["contains"] ?: "",
            onValueChange = { onConfigChange(draft.copy(config = draft.config + ("contains" to it))) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.notification_contains)) },
            placeholder = { Text(text = stringResource(R.string.notification_contains_hint)) },
            singleLine = true
        )
        // Live notification-listener badge (granted / not granted) refreshed on
        // resume; tapping it explains and opens the notification access screen.
        SpecialPermissionStatusRow(
            hintText = stringResource(R.string.notification_access_hint),
            special = SpecialPermission.NOTIFICATION_ACCESS,
            context = androidx.compose.ui.platform.LocalContext.current,
            refreshKey = refreshKey,
            onRequest = { onExplainSpecial(SpecialPermission.NOTIFICATION_ACCESS) }
        )
    }
}

internal fun notificationKindLabelRes(kind: String): Int = when (kind) {
    "MESSAGES" -> R.string.notification_kind_messages
    "CALLS" -> R.string.notification_kind_calls
    "REMINDERS" -> R.string.notification_kind_reminders
    "PROMOTIONS" -> R.string.notification_kind_promotions
    else -> R.string.notification_kind_ongoing
}

// ── Screen timeout ────────────────────────────────────────────────────────────

/**
 * Screen timeout: the ROM's own values are the choices. The device's current
 * timeout is read once and shown first, so the list is never a guess and the
 * user never types seconds.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScreenTimeoutChangedConfig(
    draft: TriggerDraft,
    onConfigChange: (TriggerDraft) -> Unit,
    deviceTimeoutSeconds: Long? = null,
) {
    val currentSeconds = draft.config["seconds"]?.toLongOrNull()
    val presets = (listOfNotNull(deviceTimeoutSeconds) + screenTimeoutPresetsSeconds)
        .distinct()
        .sorted()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.trigger_timeout_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            presets.forEach { seconds ->
                CategoryChip(
                    selected = currentSeconds == seconds,
                    onClick = { onConfigChange(draft.copy(config = draft.config + ("seconds" to seconds.toString()))) },
                    label = formatScreenTimeout(
                        seconds = seconds,
                        minutesPattern = stringResource(R.string.schema_duration_minutes),
                        secondsPattern = stringResource(R.string.schema_duration_seconds),
                    )
                )
            }
        }
        if (deviceTimeoutSeconds != null) {
            Text(
                text = stringResource(R.string.trigger_timeout_device_value, deviceTimeoutSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
