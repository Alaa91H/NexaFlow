package com.nexaflow.feature.builder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexaflow.core.execution.NotificationActionButton
import com.nexaflow.core.rom.NetworkModeCapabilities
import com.nexaflow.core.rom.NetworkModePolicy
import com.nexaflow.core.rom.NetworkModeSnapshot
import com.nexaflow.core.rom.RootPermissionGranter
import com.nexaflow.core.ui.SelectChip
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.variables.VariableResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Simple toggle actions that use "turn_on" as their label. */
private val TURN_ON_TOGGLE_ACTIONS = setOf(
    ActionType.SYSTEM_LOCATION,
    ActionType.SYSTEM_DND,
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
    ActionType.SYSTEM_DARK_MODE,
    ActionType.SYSTEM_DATA_ROAMING,
    ActionType.SYSTEM_CALL_VIBRATION,
    ActionType.SYSTEM_STATUS_BAR_TOGGLE
)

/**
 * Network-mode editor backed by a live per-subscription telephony snapshot.
 * It never promotes a generic generation list to a device capability: when
 * Android/OEM policy blocks the read, the card says so and preserves existing
 * task data rather than inventing choices that the device may reject.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NetworkModeSelector(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<NetworkModeSnapshot?>(null) }
    var elevatedPermissionGrantAvailable by remember { mutableStateOf(false) }
    var permissionRevision by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Always re-read: a denial remains explicit, while a grant can expose
        // the live per-SIM capabilities without requiring the user to reopen.
        permissionRevision += 1
    }
    LaunchedEffect(context, permissionRevision) {
        val state = withContext(Dispatchers.IO) {
            NetworkModeCapabilities(context.applicationContext).read() to
                RootPermissionGranter.canAutoGrant()
        }
        snapshot = state.first
        elevatedPermissionGrantAvailable = state.second
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.network_mode_label), style = MaterialTheme.typography.titleSmall)
        when (val state = snapshot) {
            null -> Text(
                text = stringResource(R.string.network_mode_reading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            else -> when (state.status) {
                NetworkModeSnapshot.Status.AVAILABLE -> {
                    val subscriptions = state.subscriptions
                    val selectedSubscriptionId = config["network_subscription_id"]?.toIntOrNull()
                        ?.takeIf { id -> subscriptions.any { it.subscriptionId == id } }
                        ?: subscriptions.first().subscriptionId
                    val selectedSubscription = subscriptions.first { it.subscriptionId == selectedSubscriptionId }
                    if (subscriptions.size > 1) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            subscriptions.forEach { subscription ->
                                SelectChip(
                                    selected = subscription.subscriptionId == selectedSubscriptionId,
                                    onClick = {
                                        onConfigChange(
                                            config + ("network_subscription_id" to subscription.subscriptionId.toString()) -
                                                "network_mask"
                                        )
                                    },
                                    label = stringResource(
                                        R.string.network_mode_sim,
                                        subscription.slotIndex + 1
                                    )
                                )
                            }
                        }
                    }
                    selectedSubscription.currentUserMask?.let { current ->
                        Text(
                            text = stringResource(
                                R.string.network_mode_current,
                                NetworkModePolicy.describe(current)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    val savedMask = config["network_mask"]?.toLongOrNull()
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        selectedSubscription.options.forEach { option ->
                            val selected = savedMask == option.allowedNetworkTypes ||
                                (savedMask == null && option.isAutomatic &&
                                    (config["mode"] ?: "AUTO") == "AUTO")
                            SelectChip(
                                selected = selected,
                                onClick = {
                                    onConfigChange(
                                        config + mapOf(
                                            "mode" to if (option.isAutomatic) "AUTO" else "DYNAMIC",
                                            "network_mask" to option.allowedNetworkTypes.toString(),
                                            "network_subscription_id" to selectedSubscriptionId.toString()
                                        )
                                    )
                                },
                                label = if (option.isAutomatic) {
                                    "${stringResource(R.string.network_mode_auto)}: ${option.label}"
                                } else {
                                    option.label
                                }
                            )
                        }
                    }
                }
                NetworkModeSnapshot.Status.NO_TELEPHONY,
                NetworkModeSnapshot.Status.NO_ACTIVE_SUBSCRIPTION,
                NetworkModeSnapshot.Status.UNREADABLE -> {
                    Text(
                        text = stringResource(R.string.network_mode_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    val phoneStateGranted = context.checkSelfPermission(
                        Manifest.permission.READ_PHONE_STATE
                    ) == PackageManager.PERMISSION_GRANTED
                    if (state.status == NetworkModeSnapshot.Status.UNREADABLE && !phoneStateGranted) {
                        TextButton(
                            onClick = {
                                if (elevatedPermissionGrantAvailable) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            RootPermissionGranter.grantRuntimePermissions(
                                                context.applicationContext,
                                                listOf(Manifest.permission.READ_PHONE_STATE)
                                            )
                                        }
                                        // The result is validated inside the
                                        // grant helper; always re-read live
                                        // SIM capabilities after it finishes.
                                        permissionRevision += 1
                                    }
                                } else {
                                    phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.network_mode_grant_phone_permission))
                        }
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.network_mode_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackagePickerField(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
    onPickApp: () -> Unit,
    multiPackage: Boolean = false,
    label: Int = R.string.package_name
) {
    val key = if (multiPackage) "packages" else "package"
    val value = if (multiPackage) {
        (config["packages"] ?: config["package"] ?: "")
    } else {
        config["package"] ?: ""
    }
    val fieldLabel = if (multiPackage) R.string.apps_comma else label
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { onConfigChange(mapOf(key to it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(fieldLabel)) },
            singleLine = true
        )
        TextButton(onClick = onPickApp) {
            Text(text = stringResource(R.string.choose_from_installed))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    availableVariables: List<String>,
    placeholder: String? = null,
    singleLine: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(label)) },
            placeholder = placeholder?.let { { Text(text = it) } },
            singleLine = singleLine
        )
        VariableInsertChips(
            availableVariables = availableVariables,
            currentValue = value,
            onValueChange = onValueChange
        )
    }
}

@Composable
internal fun RunsImmediatelyHint() {
    Text(
        text = stringResource(R.string.runs_immediately),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary
    )
}


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
            val streams = STREAM_OPTIONS.map { (key, res) -> key to stringResource(res) }
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
        ActionType.SYSTEM_NETWORK_MODE -> {
            NetworkModeSelector(config = config, onConfigChange = onConfigChange)
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
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                data.getParcelableExtra(
                                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                                    Uri::class.java
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                data.getParcelableExtra<Uri>(
                                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI
                                )
                            }
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
        ActionType.SYSTEM_LOCATION,
        ActionType.SYSTEM_DND,
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
            val label = if (option.actionType in TURN_ON_TOGGLE_ACTIONS) {
                R.string.turn_on
            } else {
                option.titleRes
            }
            ToggleConfigRow(
                label = stringResource(label),
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                pages.forEach { (value, label) ->
                    SelectChip(
                        selected = (config["page"] ?: "WIFI") == value,
                        onClick = { onConfigChange(mapOf("page" to value)) },
                        label = label
                    )
                }
            }
        }
        ActionType.SYSTEM_VIBRATE -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.vibrate_duration),
                    style = MaterialTheme.typography.titleSmall
                )
                val durations = listOf(1, 3, 5, 10, 30)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    durations.forEach { seconds ->
                        SelectChip(
                            selected = (config["seconds"]?.toIntOrNull() ?: 1) == seconds,
                            onClick = { onConfigChange(mapOf("seconds" to seconds.toString())) },
                            label = "${seconds}s"
                        )
                    }
                }
            }
        }
        ActionType.SYSTEM_CLIPBOARD_SET -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["text"] ?: "",
                    onValueChange = { onConfigChange(mapOf("text" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.clipboard_text)) },
                    singleLine = true
                )
                VariableInsertChips(
                    availableVariables = availableVariables,
                    currentValue = config["text"] ?: "",
                    onValueChange = { onConfigChange(mapOf("text" to it)) }
                )
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
        ActionType.SYSTEM_SET_TIMER -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["seconds"] ?: "300",
                    onValueChange = { onConfigChange(config + ("seconds" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.timer_duration_seconds)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = config["message"] ?: "",
                    onValueChange = { onConfigChange(config + ("message" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.message_optional)) },
                    singleLine = true
                )
                ToggleConfigRow(
                    label = stringResource(R.string.timer_skip_ui),
                    checked = config["skipUi"]?.toBoolean() ?: false,
                    onCheckedChange = { onConfigChange(config + ("skipUi" to it.toString())) }
                )
            }
        }
        ActionType.SYSTEM_MEDIA_PLAY_FROM_SEARCH -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["query"] ?: "",
                    onValueChange = { onConfigChange(config + ("query" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.music_search_query)) },
                    singleLine = true
                )
                VariableInsertChips(
                    availableVariables = availableVariables,
                    currentValue = config["query"] ?: "",
                    onValueChange = { onConfigChange(config + ("query" to it)) }
                )
                PackagePickerField(config = config, onConfigChange = onConfigChange, onPickApp = onPickApp)
            }
        }
        ActionType.APPLICATION_OPEN_APP_SETTINGS -> {
            PackagePickerField(config = config, onConfigChange = onConfigChange, onPickApp = onPickApp)
        }
        ActionType.SYSTEM_OPEN_APP -> {
            PackagePickerField(config = config, onConfigChange = onConfigChange, onPickApp = onPickApp, multiPackage = true)
        }
        ActionType.SYSTEM_BLOCK_NOTIFICATION -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PackagePickerField(config = config, onConfigChange = onConfigChange, onPickApp = onPickApp)
                ToggleConfigRow(
                    label = stringResource(R.string.block_label),
                    checked = config["enabled"]?.toBoolean() ?: true,
                    onCheckedChange = { onConfigChange(config + ("enabled" to it.toString())) }
                )
            }
        }
        ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS -> {
            PackagePickerField(config = config, onConfigChange = onConfigChange, onPickApp = onPickApp)
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
                        SelectChip(
                            selected = (config["sound"] ?: "DEFAULT") == value,
                            onClick = { onConfigChange(config + ("sound" to value)) },
                            label = label
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
                val selectedMethod = config["method"] ?: "GET"
                val showBody = selectedMethod !in listOf("GET", "DELETE")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    methods.forEach { method ->
                        SelectChip(
                            selected = selectedMethod == method,
                            onClick = { onConfigChange(config + ("method" to method)) },
                            label = method
                        )
                    }
                }
                if (showBody) {
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
        }
        ActionType.BATTERY_ALERTS -> {
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
                Text(text = stringResource(R.string.sound_label), style = MaterialTheme.typography.titleSmall)
                val alertSounds = listOf(
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
                    alertSounds.forEach { (value, label) ->
                        SelectChip(
                            selected = (config["sound"] ?: "DEFAULT") == value,
                            onClick = { onConfigChange(config + ("sound" to value)) },
                            label = label
                        )
                    }
                }
            }
        }
        ActionType.BATTERY_CHARGING_NOTIFICATIONS -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Text(text = stringResource(R.string.sound_label), style = MaterialTheme.typography.titleSmall)
                val chargingSounds = listOf(
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
                    chargingSounds.forEach { (value, label) ->
                        SelectChip(
                            selected = (config["sound"] ?: "DEFAULT") == value,
                            onClick = { onConfigChange(config + ("sound" to value)) },
                            label = label
                        )
                    }
                }
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
        ActionType.APPLICATION_LAUNCH_APP -> {
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
        ActionType.SYSTEM_LOCK_SCREEN,
        ActionType.SYSTEM_OPEN_RECENTS,
        ActionType.SYSTEM_GO_HOME,
        ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS,
        ActionType.SYSTEM_OPEN_PLAY_UPDATES,
        ActionType.SYSTEM_OPEN_GALAXY_STORE,
        ActionType.SYSTEM_MEDIA_STOP,
        ActionType.SYSTEM_OPEN_NOTIFICATIONS,
        ActionType.SYSTEM_OPEN_QUICK_SETTINGS,
        ActionType.SYSTEM_WAKE_SCREEN,
        ActionType.SYSTEM_MEDIA_FAST_FORWARD,
        ActionType.SYSTEM_MEDIA_REWIND,
        ActionType.SYSTEM_OPEN_CAMERA,
        ActionType.SYSTEM_OPEN_PLAY_STORE_APP,
        ActionType.SYSTEM_OPEN_SYSTEM_UPDATE_SETTINGS,
        ActionType.SYSTEM_OPEN_WIFI_SETTINGS,
        ActionType.SYSTEM_OPEN_BLUETOOTH_SETTINGS,
        ActionType.SYSTEM_OPEN_LOCATION_SETTINGS,
        ActionType.SYSTEM_OPEN_DATA_USAGE_SETTINGS,
        ActionType.SYSTEM_OPEN_BATTERY_SETTINGS,
        ActionType.SYSTEM_OPEN_DISPLAY_SETTINGS,
        ActionType.SYSTEM_OPEN_SOUND_SETTINGS,
        ActionType.SYSTEM_OPEN_STORAGE_SETTINGS,
        ActionType.SYSTEM_OPEN_SECURITY_SETTINGS,
        ActionType.SYSTEM_OPEN_ACCESSIBILITY_SETTINGS,
        ActionType.SYSTEM_OPEN_APP_SETTINGS_LIST,
        ActionType.SYSTEM_OPEN_ABOUT_PHONE,
        ActionType.SYSTEM_REBOOT,
        ActionType.SYSTEM_SHUTDOWN,
        ActionType.SYSTEM_RESTART_SYSTEM_UI,
        ActionType.SYSTEM_PASTE,
        ActionType.SYSTEM_OPEN_APP_DRAWER,
        ActionType.SYSTEM_TOGGLE_PIP,
        ActionType.SYSTEM_SOFT_RESTART,
        ActionType.SYSTEM_OPEN_CONTACTS,
        ActionType.SYSTEM_BLUETOOTH_SCAN,
        ActionType.SYSTEM_WIFI_SCAN_NOW,
        ActionType.SYSTEM_OPEN_NETWORK_SETTINGS,
        ActionType.SYSTEM_OPEN_NFC_SETTINGS,
        ActionType.SYSTEM_OPEN_DATA_SAVER_SETTINGS,
        ActionType.SYSTEM_OPEN_DEVELOPER_SETTINGS,
        ActionType.SYSTEM_OPEN_NOTIFICATION_SETTINGS,
        ActionType.SYSTEM_OPEN_PRIVACY_SETTINGS,
        ActionType.SYSTEM_OPEN_CAST_SETTINGS,
        ActionType.SYSTEM_OPEN_INPUT_METHOD_SETTINGS,
        ActionType.SYSTEM_OPEN_DEFAULT_APPS_SETTINGS,
        ActionType.SYSTEM_OPEN_VPN_SETTINGS,
        ActionType.SYSTEM_OPEN_DATE_SETTINGS,
        ActionType.SYSTEM_OPEN_PRINT_SETTINGS,
        ActionType.SYSTEM_OPEN_DEVICE_ADMIN_SETTINGS,
        ActionType.SYSTEM_OPEN_USAGE_ACCESS_SETTINGS,
        ActionType.SYSTEM_OPEN_AIRPLANE_MODE_SETTINGS -> {
            RunsImmediatelyHint()
        }
        ActionType.SYSTEM_SET_SETTING -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.setting_namespace), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("SYSTEM", "SECURE", "GLOBAL").forEach { ns ->
                        SelectChip(
                            selected = (config["namespace"] ?: "GLOBAL") == ns,
                            onClick = { onConfigChange(config + ("namespace" to ns)) },
                            label = ns
                        )
                    }
                }
                OutlinedTextField(
                    value = config["key"] ?: "",
                    onValueChange = { onConfigChange(config + ("key" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.setting_key)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = config["value"] ?: "",
                    onValueChange = { onConfigChange(config + ("value" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.setting_value)) },
                    singleLine = true
                )
            }
        }
        ActionType.SYSTEM_SCREENSHOT -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["filename"] ?: "",
                    onValueChange = { onConfigChange(mapOf("filename" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.screenshot_filename)) },
                    singleLine = true
                )
                Text(
                    text = stringResource(R.string.screenshot_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        ActionType.SYSTEM_INPUT_TEXT -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["text"] ?: "",
                    onValueChange = { onConfigChange(mapOf("text" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.input_text_label)) },
                    singleLine = true
                )
                VariableInsertChips(
                    availableVariables = availableVariables,
                    currentValue = config["text"] ?: "",
                    onValueChange = { onConfigChange(mapOf("text" to it)) }
                )
            }
        }
        ActionType.SYSTEM_KEY_EVENT -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["key"] ?: "",
                    onValueChange = { onConfigChange(mapOf("key" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.key_event_label)) },
                    singleLine = true
                )
                Text(
                    text = stringResource(R.string.key_event_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        ActionType.SYSTEM_INPUT_TAP -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["x"] ?: "",
                    onValueChange = { onConfigChange(mapOf("x" to it.filter { ch -> ch.isDigit() })) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.tap_x)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = config["y"] ?: "",
                    onValueChange = { onConfigChange(mapOf("y" to it.filter { ch -> ch.isDigit() })) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.tap_y)) },
                    singleLine = true
                )
            }
        }
        ActionType.SYSTEM_INPUT_SWIPE -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["x1"] ?: "",
                    onValueChange = { onConfigChange(config + ("x1" to it.filter { ch -> ch.isDigit() })) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.swipe_from_x)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = config["y1"] ?: "",
                    onValueChange = { onConfigChange(config + ("y1" to it.filter { ch -> ch.isDigit() })) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.swipe_from_y)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = config["x2"] ?: "",
                    onValueChange = { onConfigChange(config + ("x2" to it.filter { ch -> ch.isDigit() })) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.swipe_to_x)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = config["y2"] ?: "",
                    onValueChange = { onConfigChange(config + ("y2" to it.filter { ch -> ch.isDigit() })) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.swipe_to_y)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = config["durationMs"] ?: "300",
                    onValueChange = { onConfigChange(config + ("durationMs" to it.filter { ch -> ch.isDigit() })) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.swipe_duration)) },
                    singleLine = true
                )
            }
        }
        ActionType.SYSTEM_COLOR_INVERSION,
        ActionType.SYSTEM_GRAYSCALE,
        ActionType.SYSTEM_EXTRA_DIM,
        ActionType.SYSTEM_NIGHT_LIGHT,
        ActionType.SYSTEM_HAPTIC_FEEDBACK,
        ActionType.SYSTEM_SOUND_EFFECTS,
        ActionType.SYSTEM_DATA_SAVER,
        ActionType.SYSTEM_SCREENSAVER,
        ActionType.SYSTEM_ALWAYS_ON_DISPLAY,
        ActionType.SYSTEM_SHOW_TAPS,
        ActionType.SYSTEM_POINTER_LOCATION,
        ActionType.SYSTEM_ADAPTIVE_BATTERY,
        ActionType.SYSTEM_AUTO_TIME,
        ActionType.SYSTEM_AUTO_TIMEZONE,
        ActionType.SYSTEM_CAMERA_SHUTTER_SOUND,
        ActionType.SYSTEM_WIFI_SCANNING,
        ActionType.SYSTEM_DATA_ROAMING,
        ActionType.SYSTEM_CALL_VIBRATION,
        ActionType.SYSTEM_STATUS_BAR_TOGGLE -> {
            val label = when (option.actionType) {
                ActionType.SYSTEM_COLOR_INVERSION -> R.string.color_inversion
                ActionType.SYSTEM_GRAYSCALE -> R.string.grayscale
                ActionType.SYSTEM_EXTRA_DIM -> R.string.extra_dim
                ActionType.SYSTEM_NIGHT_LIGHT -> R.string.night_light
                ActionType.SYSTEM_HAPTIC_FEEDBACK -> R.string.haptic_feedback
                ActionType.SYSTEM_SOUND_EFFECTS -> R.string.sound_effects
                ActionType.SYSTEM_DATA_SAVER -> R.string.data_saver
                ActionType.SYSTEM_SCREENSAVER -> R.string.screensaver
                ActionType.SYSTEM_ALWAYS_ON_DISPLAY -> R.string.always_on_display
                ActionType.SYSTEM_SHOW_TAPS -> R.string.show_taps
                ActionType.SYSTEM_POINTER_LOCATION -> R.string.pointer_location
                ActionType.SYSTEM_ADAPTIVE_BATTERY -> R.string.adaptive_battery
                ActionType.SYSTEM_AUTO_TIME -> R.string.auto_time
                ActionType.SYSTEM_AUTO_TIMEZONE -> R.string.auto_timezone
                ActionType.SYSTEM_CAMERA_SHUTTER_SOUND -> R.string.camera_shutter_sound
                ActionType.SYSTEM_WIFI_SCANNING -> R.string.wifi_scanning
                else -> option.titleRes
            }
            ToggleConfigRow(
                label = stringResource(label),
                checked = config["enabled"]?.toBoolean() ?: true,
                onCheckedChange = { onConfigChange(mapOf("enabled" to it.toString())) }
            )
        }
        ActionType.SYSTEM_FORCE_STOP_APP,
        ActionType.SYSTEM_CLEAR_APP_DATA -> {
            PackagePickerField(config = config, onConfigChange = onConfigChange, onPickApp = onPickApp)
        }
        ActionType.SYSTEM_PRIVATE_DNS -> {
            val mode = config["mode"] ?: "AUTOMATIC"
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.private_dns_mode), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("OFF", "AUTOMATIC", "HOSTNAME").forEach { value ->
                        val label = when (value) {
                            "OFF" -> stringResource(R.string.private_dns_off)
                            "HOSTNAME" -> stringResource(R.string.private_dns_hostname_mode)
                            else -> stringResource(R.string.private_dns_automatic)
                        }
                        SelectChip(
                            selected = mode == value,
                            onClick = {
                                onConfigChange(
                                    config + ("mode" to value) +
                                        ("hostname" to if (value == "HOSTNAME") config["hostname"].orEmpty() else "")
                                )
                            },
                            label = label
                        )
                    }
                }
                if (mode == "HOSTNAME") {
                    OutlinedTextField(
                        value = config["hostname"].orEmpty(),
                        onValueChange = { onConfigChange(config + ("hostname" to it.trim())) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = stringResource(R.string.private_dns_hostname)) },
                        singleLine = true
                    )
                }
                Text(
                    text = stringResource(R.string.private_dns_elevated_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ActionType.SYSTEM_LOCATION_MODE -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.location_mode), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("OFF", "SENSORS", "BATTERY", "HIGH").forEach { mode ->
                        val label = when (mode) {
                            "OFF" -> stringResource(R.string.location_mode_off)
                            "SENSORS" -> stringResource(R.string.location_mode_sensors)
                            "BATTERY" -> stringResource(R.string.location_mode_battery)
                            else -> stringResource(R.string.location_mode_high)
                        }
                        SelectChip(
                            selected = (config["mode"] ?: "HIGH") == mode,
                            onClick = { onConfigChange(config + ("mode" to mode)) },
                            label = label
                        )
                    }
                }
            }
        }
        ActionType.SYSTEM_FONT_SCALE -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.font_scale), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("0.85", "1.0", "1.15", "1.3").forEach { scale ->
                        SelectChip(
                            selected = (config["scale"] ?: "1.0") == scale,
                            onClick = { onConfigChange(config + ("scale" to scale)) },
                            label = scale
                        )
                    }
                }
            }
        }
        ActionType.SYSTEM_DISPLAY_DENSITY -> {
            OutlinedTextField(
                value = config["dpi"] ?: "440",
                onValueChange = { v -> onConfigChange(mapOf("dpi" to v.filter { it.isDigit() })) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.display_density)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
        }
        ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD -> {
            val value = config["percent"]?.toIntOrNull() ?: 20
            SliderRow(
                label = stringResource(R.string.battery_saver_threshold_label, value),
                value = value.toFloat(),
                onValueChange = { onConfigChange(mapOf("percent" to it.toInt().toString())) },
                valueRange = 0f..100f
            )
        }
        ActionType.SYSTEM_CHARGING_LIMIT -> {
            val value = (config["percent"]?.toIntOrNull() ?: 80).coerceIn(50, 100)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SliderRow(
                    label = stringResource(R.string.charging_limit_value, value),
                    value = value.toFloat(),
                    onValueChange = { onConfigChange(mapOf("percent" to it.toInt().toString())) },
                    valueRange = 50f..100f
                )
                Text(
                    text = stringResource(R.string.charging_limit_root_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ActionType.SYSTEM_CHARGING_FEEDBACK -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleConfigRow(
                    label = stringResource(R.string.charging_sound),
                    checked = config["sound"]?.toBoolean() ?: true,
                    onCheckedChange = { onConfigChange(config + ("sound" to it.toString())) }
                )
                ToggleConfigRow(
                    label = stringResource(R.string.charging_vibration),
                    checked = config["vibration"]?.toBoolean() ?: true,
                    onCheckedChange = { onConfigChange(config + ("vibration" to it.toString())) }
                )
                Text(
                    text = stringResource(R.string.charging_feedback_elevated_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ActionType.SYSTEM_WIFI_SLEEP_POLICY -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.wifi_sleep_policy), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("ALWAYS", "PLUGGED", "NEVER").forEach { policy ->
                        val label = when (policy) {
                            "PLUGGED" -> stringResource(R.string.wifi_sleep_plugged)
                            "NEVER" -> stringResource(R.string.wifi_sleep_never)
                            else -> stringResource(R.string.wifi_sleep_always)
                        }
                        SelectChip(
                            selected = (config["policy"] ?: "ALWAYS") == policy,
                            onClick = { onConfigChange(config + ("policy" to policy)) },
                            label = label
                        )
                    }
                }
            }
        }
        ActionType.SYSTEM_BLUETOOTH_DISCOVERABILITY -> {
            OutlinedTextField(
                value = config["timeoutSeconds"] ?: "300",
                onValueChange = { v -> onConfigChange(mapOf("timeoutSeconds" to v.filter { it.isDigit() })) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.bluetooth_discoverable_timeout)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
        }
        ActionType.SYSTEM_HAPTIC_INTENSITY -> {
            val value = config["level"]?.toIntOrNull() ?: 255
            SliderRow(
                label = stringResource(R.string.haptic_intensity_label, value),
                value = value.toFloat(),
                onValueChange = { onConfigChange(mapOf("level" to it.toInt().toString())) },
                valueRange = 0f..255f
            )
        }
        ActionType.SYSTEM_DIAL_NUMBER -> {
            OutlinedTextField(
                value = config["number"] ?: "",
                onValueChange = { onConfigChange(mapOf("number" to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.phone_number)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
            )
        }
        ActionType.SYSTEM_TOAST -> {
            OutlinedTextField(
                value = config["text"] ?: "",
                onValueChange = { onConfigChange(config + ("text" to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.toast_text)) },
                singleLine = true
            )
            VariableInsertChips(
                availableVariables = availableVariables,
                currentValue = config["text"] ?: "",
                onValueChange = { onConfigChange(config + ("text" to it)) }
            )
        }
        ActionType.SYSTEM_ALERT -> {
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
            }
        }
        ActionType.SYSTEM_VIBRATE_PATTERN -> {
            OutlinedTextField(
                value = config["pattern"] ?: "0,200,100,200",
                onValueChange = { onConfigChange(config + ("pattern" to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.vibrate_pattern_label)) },
                singleLine = true
            )
        }
        ActionType.SYSTEM_WIFI_CONNECT -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["ssid"] ?: "",
                    onValueChange = { onConfigChange(config + ("ssid" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.wifi_ssid)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = config["password"] ?: "",
                    onValueChange = { onConfigChange(config + ("password" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.wifi_password)) },
                    singleLine = true
                )
            }
        }
        ActionType.SYSTEM_WIFI_FORGET -> {
            OutlinedTextField(
                value = config["ssid"] ?: "",
                onValueChange = { onConfigChange(config + ("ssid" to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.wifi_ssid)) },
                singleLine = true
            )
        }
        ActionType.SYSTEM_SCREENSAVER_TIMEOUT -> {
            OutlinedTextField(
                value = config["minutes"] ?: "30",
                onValueChange = { onConfigChange(config + ("minutes" to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.screensaver_timeout_label)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
        }
        ActionType.SYSTEM_POINTER_SPEED -> {
            OutlinedTextField(
                value = config["speed"] ?: "1.0",
                onValueChange = { onConfigChange(config + ("speed" to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.pointer_speed_label)) },
                singleLine = true
            )
        }
        ActionType.SYSTEM_INSTALL_APK -> {
            OutlinedTextField(
                value = config["path"] ?: "",
                onValueChange = { onConfigChange(config + ("path" to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.install_apk_path)) },
                singleLine = true
            )
        }
        ActionType.SYSTEM_UNINSTALL_APP,
        ActionType.SYSTEM_DISABLE_APP,
        ActionType.SYSTEM_ENABLE_APP -> {
            OutlinedTextField(
                value = config["package"] ?: "",
                onValueChange = { onConfigChange(config + ("package" to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.package_name)) },
                singleLine = true
            )
        }
        ActionType.SYSTEM_SET_NOTIFICATION_TONE -> {
            OutlinedTextField(
                value = config["tone"] ?: "",
                onValueChange = { onConfigChange(config + ("tone" to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.notification_tone_label)) },
                singleLine = true
            )
        }
        ActionType.SYSTEM_OPEN_MAPS -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["lat"] ?: "",
                    onValueChange = { onConfigChange(config + ("lat" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.latitude)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = config["lng"] ?: "",
                    onValueChange = { onConfigChange(config + ("lng" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.longitude)) },
                    singleLine = true
                )
            }
        }
        ActionType.SYSTEM_SEND_EMAIL -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config["to"] ?: "",
                    onValueChange = { onConfigChange(config + ("to" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.email_to)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = config["subject"] ?: "",
                    onValueChange = { onConfigChange(config + ("subject" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.email_subject)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = config["body"] ?: "",
                    onValueChange = { onConfigChange(config + ("body" to it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.email_body)) },
                    singleLine = true
                )
            }
        }
        ActionType.SYSTEM_SET_TIMEZONE -> {
            OutlinedTextField(
                value = config["zone"] ?: "GMT",
                onValueChange = { onConfigChange(config + ("zone" to it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.timezone_label)) },
                singleLine = true
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
                SelectChip(
                    selected = name.lowercase() in used,
                    onClick = {
                        val placeholder = "%$name"
                        onValueChange(
                            if (currentValue.isBlank()) placeholder
                            else "$currentValue $placeholder"
                        )
                    },
                    label = "%$name"
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
            SelectChip(
                selected = currentValue.contains("%CTX.", ignoreCase = true),
                onClick = {
                    val placeholder = "%CTX.$"
                    onValueChange(
                        if (currentValue.isBlank()) placeholder
                        else "$currentValue $placeholder"
                    )
                },
                label = "%CTX.$"
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
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            )
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
