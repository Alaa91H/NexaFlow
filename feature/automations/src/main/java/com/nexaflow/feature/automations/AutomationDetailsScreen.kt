package com.nexaflow.feature.automations

import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Storage

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.ui.EmptyState
import kotlinx.coroutines.launch
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.SettingRow
import com.nexaflow.core.ui.iconVector
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationDetailsScreen(navController: NavController) {
    val viewModel: AutomationDetailsViewModel = hiltViewModel()
    val automation by viewModel.automation.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val executionMessage by viewModel.executionMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var triggersExpanded by remember { mutableStateOf(false) }
    var constraintsExpanded by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var exitBehaviorExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(executionMessage) {
        executionMessage?.let { message ->
            coroutineScope.launch { snackbarHostState.showSnackbar(message) }
            viewModel.consumeExecutionMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.automation_title),
                onBack = { navController.popBackStack() },
                actions = {
                    // P2-5: pin this task as a home-screen shortcut that runs it
                    // through the nexaflow://run-task/{id} deep link.
                    IconButton(
                        onClick = {
                            automation?.let { createTaskShortcut(context, it) }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.AddToHomeScreen,
                            contentDescription = stringResource(R.string.add_shortcut)
                        )
                    }
                    IconButton(
                        onClick = {
                            automation?.let {
                                navController.navigate("automation_builder?automationId=${it.id}")
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(onClick = { viewModel.delete { navController.popBackStack() } }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            )
        }
    ) { padding ->
        val current = automation
        if (current == null) {
            EmptyState(
                icon = Icons.Filled.Bolt,
                title = stringResource(R.string.not_found_title),
                subtitle = stringResource(R.string.not_found_subtitle)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NexaFlowCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(
                            icon = iconVector(current.icon),
                            containerColor = Color(current.backgroundColor),
                            contentColor = Color(current.iconColor)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = current.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = current.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.enabled),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Switch(
                                checked = current.enabled,
                                onCheckedChange = { viewModel.toggleEnabled(it) }
                            )
                        }
                    }
                }
                SectionHeader(
                    text = stringResource(R.string.section_triggers),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { triggersExpanded = !triggersExpanded },
                    trailing = {
                        Icon(
                            imageVector = if (triggersExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                if (triggersExpanded) {
                    NexaFlowCard {
                        if (current.triggers.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_triggers),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        } else {
                            current.triggers.forEach { trigger ->
                                val (titleRes, subtitleRes, icon) = triggerPresentation(trigger.type)
                                SettingRow(icon = icon, title = stringResource(titleRes), subtitle = stringResource(subtitleRes), trailing = {
                                    Text(
                                        text = triggerDetail(trigger.config),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                })
                            }
                        }
                    }
                }
                SectionHeader(
                    text = stringResource(R.string.section_constraints),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { constraintsExpanded = !constraintsExpanded },
                    trailing = {
                        Icon(
                            imageVector = if (constraintsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                if (constraintsExpanded) {
                    NexaFlowCard {
                        if (current.constraints.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_constraints),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        } else {
                            current.constraints.forEach { constraint ->
                                val (titleRes, icon) = constraintPresentation(constraint.type)
                                SettingRow(
                                    icon = icon,
                                    title = stringResource(titleRes),
                                    subtitle = stringResource(R.string.constraint_subtitle),
                                    trailing = {
                                        Text(
                                            text = constraintDetail(constraint.config),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                SectionHeader(
                    text = stringResource(R.string.section_actions),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { actionsExpanded = !actionsExpanded },
                    trailing = {
                        Icon(
                            imageVector = if (actionsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                if (actionsExpanded) {
                    NexaFlowCard {
                        if (current.actions.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_actions),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        } else {
                            current.actions.forEach { action ->
                                val (titleRes, subtitleRes, icon) = actionPresentation(action.type)
                                SettingRow(icon = icon, title = stringResource(titleRes), subtitle = stringResource(subtitleRes), trailing = {
                                    val detailText = actionDetail(action.config)
                                    val endText = endBehaviorText(action)
                                    if (detailText.isNotEmpty() || endText != null) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            if (detailText.isNotEmpty()) {
                                                Text(
                                                    text = detailText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                            if (endText != null) {
                                                Text(
                                                    text = endText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                })
                            }
                        }
                    }
                }
                SectionHeader(
                    text = stringResource(R.string.section_exit_behavior),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exitBehaviorExpanded = !exitBehaviorExpanded },
                    trailing = {
                        Icon(
                            imageVector = if (exitBehaviorExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                if (exitBehaviorExpanded) {
                    NexaFlowCard {
                        if (current.revertOnExit) {
                            SettingRow(
                                icon = Icons.Filled.Security,
                                title = stringResource(R.string.exit_revert_label),
                                subtitle = stringResource(R.string.exit_revert_sub)
                            )
                        } else if (current.exitActions.isEmpty()) {
                            Text(
                                text = stringResource(R.string.exit_nothing_sub),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        } else {
                            current.exitActions.forEach { action ->
                                val (titleRes, subtitleRes, icon) = actionPresentation(action.type)
                                SettingRow(icon = icon, title = stringResource(titleRes), subtitle = stringResource(subtitleRes))
                            }
                        }
                    }
                }
                Button(
                    onClick = { viewModel.runNow() },
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                    Text(
                        text = if (running) stringResource(R.string.running) else stringResource(R.string.run_now),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

private fun constraintPresentation(type: ConstraintType): Pair<Int, ImageVector> = when (type) {
    ConstraintType.WIFI -> R.string.constraint_type_wifi to Icons.Filled.Wifi
    ConstraintType.BATTERY -> R.string.constraint_type_battery to Icons.Filled.BatteryFull
    ConstraintType.SCREEN_LOCKED -> R.string.constraint_type_screen_locked to Icons.Filled.Lock
    ConstraintType.HEADSET -> R.string.constraint_type_headset to Icons.Filled.Headphones
    ConstraintType.BLUETOOTH -> R.string.constraint_type_bluetooth to Icons.Filled.Bluetooth
    ConstraintType.DND -> R.string.constraint_type_dnd to Icons.Filled.DoNotDisturbOn
    ConstraintType.AIRPLANE -> R.string.constraint_type_airplane to Icons.Filled.AirplanemodeActive
    ConstraintType.CHARGING -> R.string.constraint_type_charging to Icons.Filled.BatteryChargingFull
    ConstraintType.LOCATION -> R.string.constraint_type_location to Icons.Filled.MyLocation
}

/** Human-readable constraint detail (e.g. battery "< 20%"). */
private fun constraintDetail(config: Map<String, String>): String {
    val direction = config["direction"]
    val level = config["level"]
    val state = config["state"]
    return when {
        direction != null && level != null ->
            if (direction == "ABOVE") "> $level%" else "< $level%"
        state != null -> state
        else -> "✓"
    }
}

/** Human-readable action detail (e.g. brightness value, package name). */
private fun actionDetail(config: Map<String, String>): String = when {
    config["value"] != null -> config["value"]!!
    config["title"]?.isNotBlank() == true -> config["title"]!!
    config["url"]?.isNotBlank() == true -> config["url"]!!
    config["package"]?.isNotBlank() == true -> config["package"]!!
    config["number"]?.isNotBlank() == true -> config["number"]!!
    config["text"]?.isNotBlank() == true -> config["text"]!!.take(40)
    config["command"]?.isNotBlank() == true -> config["command"]!!.take(40)
    config["key"]?.isNotBlank() == true -> "${config["key"]} = ${config["value"] ?: ""}"
    config["mode"]?.isNotBlank() == true -> config["mode"]!!
    config["scale"]?.isNotBlank() == true -> "×${config["scale"]}"
    config["dpi"]?.isNotBlank() == true -> "${config["dpi"]} dpi"
    config["percent"]?.isNotBlank() == true -> "${config["percent"]}%"
    config["seconds"]?.isNotBlank() == true -> "${config["seconds"]}s"
    config["minutes"]?.isNotBlank() == true -> "${config["minutes"]} min"
    config["blurb"]?.isNotBlank() == true -> config["blurb"]!!.take(40)
    config["lat"]?.isNotBlank() == true -> "${config["lat"]}, ${config["lng"]}"
    config["method"]?.isNotBlank() == true -> "${config["method"]} · ${config["url"] ?: ""}"
    else -> ""
}

/**
 * P2-5: publishes a dynamic shortcut for [automation] (app-long-press menu) and
 * requests a home-screen pin (system dialog) that runs the task via the
 * `nexaflow://run-task/{id}` deep link handled by MainActivity.
 */
private fun createTaskShortcut(context: android.content.Context, automation: Automation) {
    val uri = Uri.parse("nexaflow://run-task/${automation.id}")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    val shortcut = ShortcutInfoCompat.Builder(context, "task_" + automation.id)
        .setShortLabel(automation.name.take(10))
        .setLongLabel(automation.name)
        .setIcon(
            IconCompat.createWithResource(
                context,
                com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow
            )
        )
        .setIntent(intent)
        .build()
    ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    // Best effort — some launchers reject the pin request.
    val supported = ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    Toast.makeText(
        context,
        context.getString(
            if (supported) R.string.shortcut_added else R.string.shortcut_in_app_shortcuts
        ),
        Toast.LENGTH_LONG
    ).show()
}

private fun triggerPresentation(type: TriggerType): Triple<Int, Int, ImageVector> = when (type) {
    TriggerType.TIME -> Triple(R.string.trigger_time, R.string.trigger_time_sub, Icons.Filled.Schedule)
    TriggerType.BATTERY -> Triple(R.string.trigger_battery, R.string.trigger_battery_sub, Icons.Filled.BatteryChargingFull)
    TriggerType.APPLICATION -> Triple(R.string.trigger_app, R.string.trigger_app_sub, Icons.Filled.Add)
    TriggerType.DEVICE -> Triple(R.string.trigger_device, R.string.trigger_device_sub, Icons.Filled.Bolt)
    TriggerType.CONNECTIVITY -> Triple(R.string.trigger_connectivity, R.string.trigger_connectivity_sub, Icons.Filled.Wifi)
    TriggerType.LOCATION -> Triple(R.string.trigger_location, R.string.trigger_location_sub, Icons.Filled.Place)
    TriggerType.SMS -> Triple(R.string.trigger_sms, R.string.trigger_sms_sub, Icons.Filled.NotificationImportant)
    TriggerType.BLUETOOTH_DEVICE -> Triple(R.string.trigger_bluetooth, R.string.trigger_bluetooth_sub, Icons.Filled.Bluetooth)
    TriggerType.RINGER_MODE -> Triple(R.string.trigger_ringer, R.string.trigger_ringer_sub, Icons.Filled.NotificationsActive)
    TriggerType.NOTIFICATION -> Triple(R.string.trigger_notification, R.string.trigger_notification_sub, Icons.Filled.NotificationsActive)
    TriggerType.CALENDAR -> Triple(R.string.trigger_calendar, R.string.trigger_calendar_sub, Icons.Filled.DateRange)
    TriggerType.SENSOR -> Triple(R.string.trigger_sensor, R.string.trigger_sensor_sub, Icons.Filled.Sensors)
    TriggerType.NETWORK_MODE -> Triple(R.string.trigger_type_network_mode, R.string.trigger_type_network_mode_sub, Icons.Filled.SignalCellularAlt)
    TriggerType.WEBHOOK -> Triple(R.string.trigger_webhook, R.string.trigger_webhook_sub, Icons.Filled.Web)
    TriggerType.ROM_SETTING -> Triple(R.string.trigger_rom_setting, R.string.trigger_rom_setting_sub, Icons.Filled.Bolt)
    TriggerType.HEADPHONE -> Triple(R.string.trigger_headphone, R.string.trigger_headphone, Icons.Filled.Headphones)
    TriggerType.CHARGER -> Triple(R.string.trigger_charger, R.string.trigger_charger, Icons.Filled.BatteryChargingFull)
    TriggerType.AIRPLANE_MODE -> Triple(R.string.trigger_airplane, R.string.trigger_airplane, Icons.Filled.AirplanemodeActive)
    TriggerType.DARK_MODE -> Triple(R.string.trigger_dark_mode, R.string.trigger_dark_mode, Icons.Filled.DarkMode)
    TriggerType.CALL_STATE -> Triple(R.string.trigger_call_state, R.string.trigger_call_state, Icons.Filled.PhoneAndroid)
    TriggerType.APP_INSTALLED -> Triple(R.string.trigger_app_installed, R.string.trigger_app_installed, Icons.Filled.Download)
    TriggerType.MEDIA_PLAYING -> Triple(R.string.trigger_media_playing, R.string.trigger_media_playing, Icons.Filled.MusicNote)
    TriggerType.VOLUME_CHANGED -> Triple(R.string.trigger_volume_changed, R.string.trigger_volume_changed, Icons.AutoMirrored.Filled.VolumeUp)
    TriggerType.POWER_SAVER -> Triple(R.string.trigger_power_saver, R.string.trigger_power_saver, Icons.Filled.BatteryChargingFull)
    TriggerType.BLUETOOTH_STATE -> Triple(R.string.trigger_bluetooth_state, R.string.trigger_bluetooth_state, Icons.Filled.Bluetooth)
    TriggerType.BRIGHTNESS_LEVEL -> Triple(R.string.trigger_brightness_level, R.string.trigger_brightness_level, Icons.Filled.BrightnessHigh)
    TriggerType.STORAGE_LOW -> Triple(R.string.trigger_storage_low, R.string.trigger_storage_low, Icons.Filled.Storage)
    TriggerType.AUTO_ROTATE -> Triple(R.string.trigger_auto_rotate, R.string.trigger_auto_rotate, Icons.Filled.ScreenRotation)
    TriggerType.DATA_SAVER_STATE -> Triple(R.string.trigger_data_saver_state, R.string.trigger_data_saver_state, Icons.Filled.DataUsage)
    TriggerType.DEVICE_LOCKED -> Triple(R.string.trigger_device_locked, R.string.trigger_device_locked, Icons.Filled.Lock)
    TriggerType.WIFI_STATE -> Triple(R.string.trigger_wifi_state, R.string.trigger_wifi_state, Icons.Filled.Wifi)
    TriggerType.NFC_STATE -> Triple(R.string.trigger_nfc_state, R.string.trigger_nfc_state, Icons.Filled.Nfc)
    TriggerType.LOCATION_STATE -> Triple(R.string.trigger_location_state, R.string.trigger_location_state, Icons.Filled.LocationOn)
    TriggerType.SCREEN_ROTATION_STATE -> Triple(R.string.trigger_screen_rotation_state, R.string.trigger_screen_rotation_state, Icons.Filled.ScreenRotation)
    TriggerType.WIFI_SIGNAL_STRENGTH -> Triple(R.string.trigger_wifi_signal_strength, R.string.trigger_wifi_signal_strength, Icons.Filled.Wifi)
    TriggerType.CELL_SIGNAL_STRENGTH -> Triple(R.string.trigger_cell_signal_strength, R.string.trigger_cell_signal_strength, Icons.Filled.SignalCellularAlt)
    TriggerType.BATTERY_TEMPERATURE -> Triple(R.string.trigger_battery_temperature, R.string.trigger_battery_temperature, Icons.Filled.DeviceThermostat)
    TriggerType.USB_CONNECTED -> Triple(R.string.trigger_usb_connected, R.string.trigger_usb_connected, Icons.Filled.Usb)
    TriggerType.HDMI_CONNECTED -> Triple(R.string.trigger_hdmi_connected, R.string.trigger_hdmi_connected, Icons.Filled.Monitor)
    TriggerType.ETHERNET_CONNECTED -> Triple(R.string.trigger_ethernet_connected, R.string.trigger_ethernet_connected, Icons.Filled.Router)
    TriggerType.VPN_CONNECTED -> Triple(R.string.trigger_vpn_connected, R.string.trigger_vpn_connected, Icons.Filled.Lock)
    TriggerType.CLIPBOARD_CHANGED -> Triple(R.string.trigger_clipboard_changed, R.string.trigger_clipboard_changed, Icons.Filled.ContentPaste)
    TriggerType.DND_STATE -> Triple(R.string.trigger_dnd_state, R.string.trigger_dnd_state, Icons.Filled.DoNotDisturbOn)
    TriggerType.STAY_AWAKE_STATE -> Triple(R.string.trigger_stay_awake_state, R.string.trigger_stay_awake_state, Icons.Filled.Bedtime)
    TriggerType.AUTO_BRIGHTNESS_STATE -> Triple(R.string.trigger_auto_brightness_state, R.string.trigger_auto_brightness_state, Icons.Filled.BrightnessAuto)
    TriggerType.SCREEN_TIMEOUT_CHANGED -> Triple(R.string.trigger_screen_timeout_changed, R.string.trigger_screen_timeout_changed, Icons.Filled.Timer)
    TriggerType.DATA_ROAMING_STATE -> Triple(R.string.trigger_data_roaming_state, R.string.trigger_data_roaming_state, Icons.Filled.DataUsage)
    TriggerType.TIMEZONE_CHANGED -> Triple(R.string.trigger_timezone_changed, R.string.trigger_timezone_changed, Icons.Filled.AccessTime)
    TriggerType.BOOT_COMPLETED -> Triple(R.string.trigger_boot_completed, R.string.trigger_boot_completed, Icons.Filled.PowerSettingsNew)
    TriggerType.NFC_TAG_SCANNED -> Triple(R.string.trigger_nfc_tag_scanned, R.string.trigger_nfc_tag_scanned, Icons.Filled.Nfc)
    TriggerType.ALARM_SET_CHANGED -> Triple(R.string.trigger_alarm_set_changed, R.string.trigger_alarm_set_changed, Icons.Filled.Alarm)
}

/** Human-readable trigger detail (e.g. battery direction, time range, repeat mode). */
@Composable
private fun triggerDetail(config: Map<String, String>): String {
    val parts = buildList {
        if (config["timeMode"] == "RANGE") {
            add("${config["rangeStart"] ?: ""} → ${config["rangeEnd"] ?: ""}")
        } else {
            config["time"]?.let { add(it) }
        }
        config["direction"]?.let { dir ->
            if (dir == "BELOW") add("< ${config["above"] ?: ""}%") else add("> ${config["above"] ?: ""}%")
        }
        config["chargerType"]?.takeIf { it != "ANY" && it.isNotBlank() }?.let { add(chargerTypeText(it)) }
        config["repeat"]?.let { repeat ->
            when (repeat) {
                "DAILY" -> Unit
                "MONTHLY_WEEKDAY" -> add(monthlyWeekdayText(config))
                else -> add(repeat.lowercase())
            }
        }
        config["days"]?.let { add(it) }
        config["date"]?.let { add(it) }
        config["startDate"]?.let { add("$it → ${config["endDate"]}") }
        config["packages"]?.let { add("${it.split(',').size} app(s)") }
        config["network"]?.let { add(it.lowercase()) }
        config["event"]?.let { add(it.lowercase()) }
        config["from"]?.takeIf { it.isNotBlank() }?.let { add("from $it") }
        config["deviceName"]?.takeIf { it.isNotBlank() }?.let { add(it) }
        config["stream"]?.let { add(it.lowercase()) }
        config["mode"]?.let { add(it.lowercase()) }
        config["packages"]?.takeIf { it.isNotBlank() }?.let { add("${it.split(',').size} app(s)") }
        config["contains"]?.takeIf { it.isNotBlank() }?.let { add("\"$it\"") }
        config["calendar"]?.takeIf { it.isNotBlank() }?.let { add(it) }
        config["beforeMinutes"]?.takeIf { it != "0" && it.isNotBlank() }?.let { add("-$it min") }
    }
    return parts.joinToString(", ").ifEmpty { "default" }
}

/**
 * Google-Tasks-style summary for a MONTHLY_WEEKDAY trigger, e.g.
 * "1st Mon" or "Last Fri", composed from the localized occurrence and day.
 */
@Composable
private fun monthlyWeekdayText(config: Map<String, String>): String {
    val dayRes = when (config["weekday"]?.toIntOrNull()) {
        1 -> R.string.day_mon
        2 -> R.string.day_tue
        3 -> R.string.day_wed
        4 -> R.string.day_thu
        5 -> R.string.day_fri
        6 -> R.string.day_sat
        7 -> R.string.day_sun
        else -> null
    }
    val occurrenceRes = when (config["weekOfMonth"] ?: "1") {
        "1" -> R.string.occurrence_first
        "2" -> R.string.occurrence_second
        "3" -> R.string.occurrence_third
        "4" -> R.string.occurrence_fourth
        "LAST" -> R.string.occurrence_last
        else -> null
    }
    if (dayRes == null || occurrenceRes == null) return ""
    return stringResource(
        R.string.monthly_weekday_summary,
        stringResource(occurrenceRes),
        stringResource(dayRes)
    )
}

/** Localized per-action end-behavior label, or null when the action stays as is. */
@Composable
private fun endBehaviorText(action: Action): String? {
    val behavior = action.endBehavior ?: return null
    val label = when (behavior.mode) {
        com.nexaflow.domain.models.EndMode.LEAVE -> return null
        com.nexaflow.domain.models.EndMode.REVERT -> stringResource(R.string.end_revert)
        com.nexaflow.domain.models.EndMode.RERUN -> stringResource(R.string.end_rerun)
        com.nexaflow.domain.models.EndMode.SET_VALUE -> {
            if (action.type in com.nexaflow.domain.models.EndBehaviorCatalog.toggleActions) {
                if (behavior.config["enabled"] == "true") stringResource(R.string.end_turn_on)
                else stringResource(R.string.end_turn_off)
            } else {
                stringResource(R.string.end_set_value)
            }
        }
    }
    return stringResource(R.string.end_summary, label)
}

/** Localized charger-type label shown in the battery trigger detail. */
@Composable
private fun chargerTypeText(value: String): String = when (value) {
    "AC" -> stringResource(R.string.charger_ac)
    "USB" -> stringResource(R.string.charger_usb)
    "WIRELESS" -> stringResource(R.string.charger_wireless)
    else -> stringResource(R.string.charger_any)
}

/** Design-time preview of the automation header + trigger/action cards. */
@Preview(name = "Automation details", showBackground = true, widthDp = 400)
@Preview(name = "Automation details (dark)", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationDetailsPreview() {
    MaterialTheme {
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            val sample = Automation(
                id = "preview-1",
                name = "Morning Mode",
                description = "Start the day right",
                icon = "sunny",
                iconColor = 0xFFFFFFFF,
                backgroundColor = 0xFF8F4C00,
                category = "general",
                priority = 1,
                enabled = true,
                triggers = listOf(Trigger(TriggerType.TIME, mapOf("time" to "08:00", "repeat" to "DAILY"))),
                actions = listOf(Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("level" to "60"))),
                createdAt = 0,
                updatedAt = 0
            )
            NexaFlowCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(
                        icon = iconVector(sample.icon),
                        containerColor = Color(sample.backgroundColor),
                        contentColor = Color(sample.iconColor)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = sample.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = sample.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            SectionHeader(text = stringResource(R.string.section_triggers))
            NexaFlowCard {
                val (titleRes, subtitleRes, icon) = triggerPresentation(TriggerType.TIME)
                SettingRow(icon = icon, title = stringResource(titleRes), subtitle = stringResource(subtitleRes))
            }
            SectionHeader(text = stringResource(R.string.section_actions))
            NexaFlowCard {
                val (titleRes, subtitleRes, icon) = actionPresentation(ActionType.SYSTEM_BRIGHTNESS)
                SettingRow(icon = icon, title = stringResource(titleRes), subtitle = stringResource(subtitleRes))
            }
        }
    }
}
