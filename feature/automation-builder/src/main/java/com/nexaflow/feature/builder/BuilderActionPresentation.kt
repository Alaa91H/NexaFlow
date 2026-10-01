package com.nexaflow.feature.builder

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nexaflow.core.execution.NotificationActionButton
import com.nexaflow.core.rom.NetworkModePolicy
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowFloatingActionButton
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation

internal fun optionsForActionCategory(
    category: ActionCategory,
    options: List<ActionOption> = actionOptions
): List<ActionOption> = options.filter { it.category == category }

internal val actionCategories: List<ActionCategory> = ActionCategory.entries.toList()

/** Representative icon per action category for the accordion chips. */
internal fun ActionCategory.icon(): ImageVector = when (this) {
    ActionCategory.DISPLAY -> Icons.Filled.BrightnessHigh
    ActionCategory.SOUND -> Icons.AutoMirrored.Filled.VolumeUp
    ActionCategory.CONNECTIVITY -> Icons.Filled.Wifi
    ActionCategory.MEDIA -> Icons.Filled.PlayArrow
    ActionCategory.NOTIFICATIONS -> Icons.Filled.Notifications
    ActionCategory.APPS -> Icons.Filled.Apps
    ActionCategory.SYSTEM -> Icons.Filled.Settings
    ActionCategory.BATTERY -> Icons.Filled.BatteryAlert
    ActionCategory.PLUGINS -> Icons.Filled.Extension
}

/** Action types whose summary is simply On/Off based on `config["enabled"]`. */
private val TOGGLE_SUMMARY_ACTIONS = setOf(
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
    ActionType.SYSTEM_STATUS_BAR_TOGGLE
)

/** Settings-open actions whose summary is a static localized label. */
private val SETTINGS_OPEN_SUMMARY = mapOf(
    ActionType.SYSTEM_OPEN_WIFI_SETTINGS to R.string.settings_wifi,
    ActionType.SYSTEM_OPEN_BLUETOOTH_SETTINGS to R.string.settings_bluetooth,
    ActionType.SYSTEM_OPEN_LOCATION_SETTINGS to R.string.settings_location,
    ActionType.SYSTEM_OPEN_DATA_USAGE_SETTINGS to R.string.settings_data_usage,
    ActionType.SYSTEM_OPEN_BATTERY_SETTINGS to R.string.settings_battery,
    ActionType.SYSTEM_OPEN_DISPLAY_SETTINGS to R.string.settings_display,
    ActionType.SYSTEM_OPEN_SOUND_SETTINGS to R.string.settings_sound,
    ActionType.SYSTEM_OPEN_STORAGE_SETTINGS to R.string.settings_storage,
    ActionType.SYSTEM_OPEN_SECURITY_SETTINGS to R.string.settings_security,
    ActionType.SYSTEM_OPEN_ACCESSIBILITY_SETTINGS to R.string.settings_accessibility,
    ActionType.SYSTEM_OPEN_APP_SETTINGS_LIST to R.string.settings_apps,
    ActionType.SYSTEM_OPEN_ABOUT_PHONE to R.string.settings_about,
    ActionType.SYSTEM_OPEN_NETWORK_SETTINGS to R.string.settings_network,
    ActionType.SYSTEM_OPEN_NFC_SETTINGS to R.string.settings_nfc,
    ActionType.SYSTEM_OPEN_DATA_SAVER_SETTINGS to R.string.settings_data_saver,
    ActionType.SYSTEM_OPEN_DEVELOPER_SETTINGS to R.string.settings_developer,
    ActionType.SYSTEM_OPEN_NOTIFICATION_SETTINGS to R.string.settings_notifications,
    ActionType.SYSTEM_OPEN_PRIVACY_SETTINGS to R.string.settings_privacy,
    ActionType.SYSTEM_OPEN_CAST_SETTINGS to R.string.settings_cast,
    ActionType.SYSTEM_OPEN_INPUT_METHOD_SETTINGS to R.string.settings_input_method,
    ActionType.SYSTEM_OPEN_DEFAULT_APPS_SETTINGS to R.string.settings_default_apps,
    ActionType.SYSTEM_OPEN_VPN_SETTINGS to R.string.settings_vpn,
    ActionType.SYSTEM_OPEN_DATE_SETTINGS to R.string.settings_date,
    ActionType.SYSTEM_OPEN_PRINT_SETTINGS to R.string.settings_print,
    ActionType.SYSTEM_OPEN_DEVICE_ADMIN_SETTINGS to R.string.settings_device_admin,
    ActionType.SYSTEM_OPEN_USAGE_ACCESS_SETTINGS to R.string.settings_usage_access,
    ActionType.SYSTEM_OPEN_AIRPLANE_MODE_SETTINGS to R.string.settings_airplane
)

/** Action types whose summary is the `config["package"]` value. */
private val PACKAGE_SUMMARY_ACTIONS = setOf(
    ActionType.APPLICATION_OPEN_APP_SETTINGS,
    ActionType.SYSTEM_BLOCK_NOTIFICATION,
    ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS,
    ActionType.APPLICATION_CLOSE_APP,
    ActionType.SYSTEM_FORCE_STOP_APP,
    ActionType.SYSTEM_CLEAR_APP_DATA,
    ActionType.SYSTEM_UNINSTALL_APP,
    ActionType.SYSTEM_DISABLE_APP,
    ActionType.SYSTEM_ENABLE_APP
)

/** Actions with no config summary (show only the name). */
private val NO_SUMMARY_ACTIONS = setOf(
    ActionType.SYSTEM_PASTE,
    ActionType.SYSTEM_OPEN_APP_DRAWER,
    ActionType.SYSTEM_TOGGLE_PIP,
    ActionType.SYSTEM_SOFT_RESTART,
    ActionType.SYSTEM_OPEN_CONTACTS,
    ActionType.SYSTEM_BLUETOOTH_SCAN,
    ActionType.SYSTEM_WIFI_SCAN_NOW
)

/**
 * One-line summary of the chosen action values for the collapsed header,
 * mirroring triggerSummary/constraintSummary so every builder row reads
 * "Execution N · <name · chosen values>". Falls back to the action name
 * alone when nothing is configured yet (immediate one-shot actions).
 */
@Composable
internal fun actionSummary(option: ActionOption, config: Map<String, String>): String {
    val name = stringResource(option.titleRes)
    val value: String? = when {
        option.actionType in TOGGLE_SUMMARY_ACTIONS ->
            if (config["enabled"]?.toBoolean() ?: true) stringResource(R.string.builder_state_on)
            else stringResource(R.string.builder_state_off)

        option.actionType in SETTINGS_OPEN_SUMMARY ->
            stringResource(SETTINGS_OPEN_SUMMARY[option.actionType]!!)

        option.actionType in PACKAGE_SUMMARY_ACTIONS ->
            config["package"].orEmpty().trim().ifEmpty { null }

        option.actionType in NO_SUMMARY_ACTIONS -> null

        else -> actionSummaryDetail(option, config)
    }
    return if (value.isNullOrBlank()) name else "$name · $value"
}

/** Type-specific detail for actions that need custom summary logic. */
@Composable
internal fun actionSummaryDetail(option: ActionOption, config: Map<String, String>): String? =
    when (option.actionType) {
        ActionType.SYSTEM_BRIGHTNESS ->
            stringResource(R.string.brightness_label, config["value"]?.toIntOrNull() ?: 128)
        ActionType.SYSTEM_VOLUME ->
            stringResource(R.string.volume_label, config["value"]?.toIntOrNull() ?: 50)
        ActionType.SYSTEM_RING_VOLUME ->
            stringResource(R.string.ring_volume_label, config["value"]?.toIntOrNull() ?: 50)
        ActionType.SYSTEM_STREAM_VOLUME -> {
            val stream = when (config["stream"] ?: "MUSIC") {
                "RING" -> stringResource(R.string.stream_ring)
                "NOTIFICATION" -> stringResource(R.string.stream_notification)
                "ALARM" -> stringResource(R.string.stream_alarm)
                "VOICE_CALL" -> stringResource(R.string.stream_voice_call)
                "SYSTEM" -> stringResource(R.string.stream_system)
                "DTMF" -> stringResource(R.string.stream_dtmf)
                "ACCESSIBILITY" -> stringResource(R.string.stream_accessibility)
                else -> stringResource(R.string.stream_music)
            }
            "$stream · ${config["value"] ?: "50"}"
        }
        ActionType.SYSTEM_NETWORK_MODE -> when (config["mode"] ?: "AUTO") {
            "DYNAMIC" -> config["network_mask"]?.toLongOrNull()
                ?.takeIf {
                    it > 0L && config["network_mask_schema"] ==
                        NetworkModePolicy.NETWORK_MASK_SCHEMA_AOSP_V1
                }
                ?.let(NetworkModePolicy::describe)
                ?: stringResource(R.string.network_mode_unavailable)
            "2G" -> stringResource(R.string.network_mode_2g)
            "3G" -> stringResource(R.string.network_mode_3g)
            "4G" -> stringResource(R.string.network_mode_4g)
            "5G" -> stringResource(R.string.network_mode_5g)
            else -> stringResource(R.string.network_mode_auto)
        }
        ActionType.SYSTEM_SEND_SMS -> {
            val number = config["number"].orEmpty().trim()
            val text = config["text"].orEmpty().trim()
            listOf(number, text).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
        }
        ActionType.SYSTEM_SEND_REMINDER -> {
            val title = config["title"].orEmpty().trim()
            val time = "${config["hour"] ?: "9"}:${(config["minute"] ?: "0").padStart(2, '0')}"
            listOf(title, time).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
        }
        ActionType.SYSTEM_OPEN_SETTINGS -> when (config["page"] ?: "WIFI") {
            "BLUETOOTH" -> stringResource(R.string.settings_bluetooth)
            "LOCATION" -> stringResource(R.string.settings_location)
            "SOUND" -> stringResource(R.string.settings_sound)
            "DISPLAY" -> stringResource(R.string.settings_display)
            "BATTERY" -> stringResource(R.string.settings_battery)
            "NOTIFICATION" -> stringResource(R.string.settings_notification)
            else -> stringResource(R.string.settings_wifi)
        }
        ActionType.SYSTEM_SCREEN_TIMEOUT ->
            stringResource(R.string.timeout_label, config["seconds"]?.toIntOrNull() ?: 60)
        ActionType.SYSTEM_RINGER_MODE -> when (config["mode"] ?: "NORMAL") {
            "VIBRATE" -> stringResource(R.string.ringer_vibrate)
            "SILENT" -> stringResource(R.string.ringer_silent)
            else -> stringResource(R.string.ringer_normal)
        }
        ActionType.SYSTEM_SET_ALARM -> {
            val hour = config["hour"] ?: "7"
            val minute = (config["minute"] ?: "0").padStart(2, '0')
            "$hour:$minute"
        }
        ActionType.SYSTEM_SET_TIMER -> "${config["seconds"] ?: "300"}s"
        ActionType.SYSTEM_MEDIA_PLAY_FROM_SEARCH -> config["query"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_OPEN_APP ->
            (config["packages"] ?: config["package"] ?: "").trim().ifEmpty { null }
        ActionType.SYSTEM_SEND_NOTIFICATION -> {
            val title = config["title"].orEmpty().trim()
            val text = config["text"].orEmpty().trim()
            val content = listOf(title, text).firstOrNull { it.isNotEmpty() }
            val buttons = NotificationActionButton.fromConfig(config["action_buttons"])
            when {
                content != null && buttons.isNotEmpty() ->
                    "$content · ${stringResource(R.string.action_buttons_count, buttons.size)}"
                content != null -> content
                else -> null
            }
        }
        ActionType.SYSTEM_WAIT ->
            stringResource(R.string.wait_counter_label, config["seconds"]?.toIntOrNull() ?: 5)
        ActionType.SYSTEM_SCREEN_ROTATION ->
            if (config["autoRotate"]?.toBoolean() ?: true) stringResource(R.string.auto_rotate)
            else stringResource(R.string.builder_state_off)
        ActionType.SYSTEM_OPEN_URL -> config["url"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_HTTP_REQUEST -> {
            val method = config["method"] ?: "GET"
            val url = config["url"].orEmpty().trim()
            if (url.isEmpty()) null else "$method · $url"
        }
        ActionType.BATTERY_ALERTS ->
            stringResource(R.string.alert_below, config["below"]?.toIntOrNull() ?: 20)
        ActionType.BATTERY_CHARGING_NOTIFICATIONS -> config["sound"] ?: "DEFAULT"
        ActionType.ADVANCED_ROOT,
        ActionType.ADVANCED_SHIZUKU -> config["command"].orEmpty().trim().ifEmpty { null }
        ActionType.PLUGIN_FIRE -> config["blurb"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_VIBRATE -> "${config["seconds"] ?: "1"}s"
        ActionType.SYSTEM_CLIPBOARD_SET -> config["text"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_SET_SETTING -> {
            val key = config["key"].orEmpty().trim()
            if (key.isEmpty()) null else "$key = ${config["value"] ?: ""}"
        }
        ActionType.SYSTEM_SCREENSHOT -> config["filename"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_INPUT_TEXT -> config["text"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_KEY_EVENT -> config["key"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_INPUT_TAP -> "${config["x"] ?: "0"}, ${config["y"] ?: "0"}"
        ActionType.SYSTEM_INPUT_SWIPE ->
            "(${config["x1"] ?: "0"},${config["y1"] ?: "0"}) → (${config["x2"] ?: "0"},${config["y2"] ?: "0"})"
        ActionType.SYSTEM_LOCATION_MODE -> when (config["mode"] ?: "HIGH") {
            "OFF" -> stringResource(R.string.location_mode_off)
            "SENSORS" -> stringResource(R.string.location_mode_sensors)
            "BATTERY" -> stringResource(R.string.location_mode_battery)
            else -> stringResource(R.string.location_mode_high)
        }
        ActionType.SYSTEM_FONT_SCALE -> config["scale"] ?: "1.0"
        ActionType.SYSTEM_DISPLAY_DENSITY -> "${config["dpi"] ?: "440"} dpi"
        ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD -> "${config["percent"] ?: "20"}%"
        ActionType.SYSTEM_CHARGING_LIMIT -> "${config["percent"] ?: "80"}%"
        ActionType.SYSTEM_CHARGING_FEEDBACK -> listOfNotNull(
            if (config["sound"]?.toBoolean() ?: true) stringResource(R.string.charging_sound) else null,
            if (config["vibration"]?.toBoolean() ?: true) stringResource(R.string.charging_vibration) else null
        ).joinToString(" + ")
        ActionType.SYSTEM_PRIVATE_DNS -> when (config["mode"] ?: "AUTOMATIC") {
            "OFF" -> stringResource(R.string.private_dns_off)
            "HOSTNAME" -> config["hostname"].orEmpty().trim().ifEmpty { null }
            else -> stringResource(R.string.private_dns_automatic)
        }
        ActionType.SYSTEM_WIFI_SLEEP_POLICY -> when (config["policy"] ?: "ALWAYS") {
            "PLUGGED" -> stringResource(R.string.wifi_sleep_plugged)
            "NEVER" -> stringResource(R.string.wifi_sleep_never)
            else -> stringResource(R.string.wifi_sleep_always)
        }
        ActionType.SYSTEM_BLUETOOTH_DISCOVERABILITY -> {
            val t = config["timeoutSeconds"]?.toIntOrNull() ?: 300
            if (t == 0) stringResource(R.string.builder_state_off) else "${t}s"
        }
        ActionType.SYSTEM_HAPTIC_INTENSITY -> config["level"] ?: "255"
        ActionType.SYSTEM_DIAL_NUMBER -> config["number"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_TOAST -> config["text"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_ALERT -> {
            val title = config["title"].orEmpty().trim()
            val text = config["text"].orEmpty().trim()
            listOf(title, text).firstOrNull { it.isNotEmpty() }
        }
        ActionType.SYSTEM_VIBRATE_PATTERN -> config["pattern"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_WIFI_CONNECT -> config["ssid"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_WIFI_FORGET -> config["ssid"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_SCREENSAVER_TIMEOUT -> "${config["minutes"] ?: "30"} min"
        ActionType.SYSTEM_POINTER_SPEED -> config["speed"] ?: "1.0"
        ActionType.SYSTEM_INSTALL_APK -> config["path"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_SET_NOTIFICATION_TONE -> config["tone"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_OPEN_MAPS -> {
            val lat = config["lat"].orEmpty().trim()
            val lng = config["lng"].orEmpty().trim()
            if (lat.isEmpty() || lng.isEmpty()) null else "$lat, $lng"
        }
        ActionType.SYSTEM_SEND_EMAIL -> config["to"].orEmpty().trim().ifEmpty { null }
        ActionType.SYSTEM_SET_TIMEZONE -> config["zone"].orEmpty().trim().ifEmpty { null }
        else -> null
    }

/** The sole lower navigation action for the current builder station. */
@Composable
internal fun BuilderBottomPrimaryAction(
    step: Int,
    triggerCount: Int,
    actionCount: Int,
    onAdvance: (Int) -> Unit,
    onSave: () -> Unit
) {
    // One clear lower action per station: When → Do → Review → Save.
    when {
        step == 0 && triggerCount > 0 -> NexaFlowFloatingActionButton(
            onClick = { onAdvance(1) },
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            label = stringResource(R.string.permission_continue)
        )
        step == 1 && actionCount > 0 -> NexaFlowFloatingActionButton(
            onClick = { onAdvance(2) },
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            label = stringResource(R.string.quick_save)
        )
        step == 2 && triggerCount > 0 && actionCount > 0 -> NexaFlowFloatingActionButton(
            onClick = onSave,
            icon = Icons.Filled.Check,
            label = stringResource(R.string.create_task)
        )
    }
}

@Composable
internal fun SelectedActionCard(
    option: ActionOption,
    index: Int,
    total: Int,
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onPickApp: () -> Unit,
    onRequestPermission: (Array<String>) -> Unit = {},
    refreshKey: Int = 0,
    context: Context,
    // Default keeps the pre-explain behavior (open settings directly) so a call
    // site that forgets to wire the explain screen never gets a dead button.
    onExplainSpecial: (SpecialPermission) -> Unit = { PermissionShortcuts.openSpecial(context, it) },
    availableVariables: List<String> = emptyList(),
    // Saved tasks the notification action can attach as interactive buttons.
    automations: List<Automation> = emptyList(),
    // Re-launches the plugin's EDIT_SETTING activity (plugin actions only).
    onPluginConfigure: () -> Unit = {},
    /** Controlled by the builder so one execution card is open at a time. */
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    // Real drag-and-drop: the reorder handle (arrow column) drives these
    // callbacks; the arrow buttons stay as a secondary tap-to-move option.
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    val accent = builderCardAccent(index)
    NexaFlowCard(
        modifier = modifier,
        containerColor = builderCardContainerColor(index),
        contentColor = builderCardContentColor
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // X + reorder handle pinned to the LEFT and the row number
                    // pinned to the RIGHT regardless of the locale direction.
                    // Expand-only: once opened, the card never collapses again,
                    // so the details only ever grow downward.
                    .clickable { onExpandedChange(!expanded) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Execution-task row, strictly left-to-right: remove (X) at the
                // far start, then the reorder handle (up arrow stacked above the
                // down arrow), then the task name.
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.remove_action),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                TaskRowHandle(
                    index = index,
                    total = total,
                    isDragging = isDragging,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onDragStart = onDragStart,
                    onDragDelta = onDragDelta,
                    onDragEnd = onDragEnd
                )
                // Single horizontal line: name · chosen values. The row
                // number lives in a badge pinned to the right end.
                Text(
                    text = actionSummary(option, config),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TaskNumberBadge(
                    number = index + 1,
                    containerColor = accent,
                    contentColor = Color.White
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(if (expanded) R.string.collapse_options else R.string.expand_options),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
            }
            if (expanded) {
                NodeConfiguratorSheet(
                    title = actionSummary(option, config),
                    confirmLabel = stringResource(R.string.save),
                    confirmEnabled = true,
                    onConfirm = { onExpandedChange(false) },
                    onDismiss = { onExpandedChange(false) }
                ) {
                    Text(
                        text = stringResource(option.subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    val canonicalBinding =
                        CanonicalBuilderSchemaBridge.editingBindingForAction(option.actionType)
                    if (canonicalBinding != null) {
                        CanonicalSchemaFieldEditor(
                            binding = canonicalBinding,
                            config = config,
                            onConfigChange = onConfigChange,
                        )
                    } else {
                        ActionConfigEditor(
                            option = option,
                            config = config,
                            onConfigChange = onConfigChange,
                            onPickApp = onPickApp,
                            availableVariables = availableVariables,
                            onPluginConfigure = onPluginConfigure,
                            automations = automations
                        )
                    }
                    PermissionHintForAction(
                        actionType = option.actionType,
                        actionConfig = config,
                        context = context,
                        refreshKey = refreshKey,
                        onRequestPermission = onRequestPermission,
                        onExplainSpecial = onExplainSpecial
                    )
                }
            }
        }
    }
}


