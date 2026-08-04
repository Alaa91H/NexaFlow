package com.nexaflow.feature.automations

import android.widget.Toast
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
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.ui.EmptyState
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.SettingRow
import com.nexaflow.core.ui.StatusPill
import com.nexaflow.core.ui.ToggleRow
import com.nexaflow.core.ui.iconVector
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationDetailsScreen(navController: NavController) {
    val viewModel: AutomationDetailsViewModel = hiltViewModel()
    val automation by viewModel.automation.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val executionMessage by viewModel.executionMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(executionMessage) {
        executionMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeExecutionMessage()
        }
    }

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.automation_title),
                onBack = { navController.popBackStack() },
                actions = {
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
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = current.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (current.enabled) {
                            StatusPill(
                                text = stringResource(R.string.status_active),
                                background = Color(0xFFE4F4E9),
                                contentColor = Color(0xFF2FA84F)
                            )
                        } else {
                            StatusPill(
                                text = stringResource(R.string.status_off),
                                background = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                NexaFlowCard {
                    ToggleRow(
                        icon = Icons.Filled.Bolt,
                        title = stringResource(R.string.enabled),
                        subtitle = stringResource(R.string.enabled_subtitle),
                        checked = current.enabled,
                        onCheckedChange = { viewModel.toggleEnabled(it) }
                    )
                }
                SectionHeader(text = stringResource(R.string.section_triggers))
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
                                    text = trigger.config.entries.joinToString(", ") { "${it.key}=${it.value}" }.ifEmpty { "default" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            })
                        }
                    }
                }
                SectionHeader(text = stringResource(R.string.section_conditions))
                NexaFlowCard {
                    if (current.conditions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_conditions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        current.conditions.forEach { condition ->
                            SettingRow(
                                icon = Icons.Filled.Bolt,
                                title = condition.type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                                subtitle = condition.config.entries.joinToString(", ") { "${it.key}=${it.value}" }.ifEmpty { stringResource(R.string.always_true) }
                            )
                        }
                    }
                }
                SectionHeader(text = stringResource(R.string.section_actions))
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
                            SettingRow(icon = icon, title = stringResource(titleRes), subtitle = stringResource(subtitleRes))
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

private fun triggerPresentation(type: TriggerType): Triple<Int, Int, ImageVector> = when (type) {
    TriggerType.TIME -> Triple(R.string.trigger_time, R.string.trigger_time_sub, Icons.Filled.Schedule)
    TriggerType.APPLICATION -> Triple(R.string.trigger_app, R.string.trigger_app_sub, Icons.Filled.Add)
    TriggerType.DEVICE -> Triple(R.string.trigger_device, R.string.trigger_device_sub, Icons.Filled.Bolt)
    TriggerType.CONNECTIVITY -> Triple(R.string.trigger_connectivity, R.string.trigger_connectivity_sub, Icons.Filled.Wifi)
    TriggerType.LOCATION -> Triple(R.string.trigger_location, R.string.trigger_location_sub, Icons.Filled.Place)
    TriggerType.SMS -> Triple(R.string.trigger_sms, R.string.trigger_sms_sub, Icons.Filled.NotificationImportant)
}

private fun actionPresentation(type: ActionType): Triple<Int, Int, ImageVector> = when (type) {
    ActionType.SYSTEM_BRIGHTNESS -> Triple(R.string.action_brightness, R.string.action_brightness_sub, Icons.Filled.FlashOn)
    ActionType.SYSTEM_VOLUME -> Triple(R.string.action_volume, R.string.action_volume_sub, Icons.AutoMirrored.Filled.VolumeUp)
    ActionType.SYSTEM_DND -> Triple(R.string.action_dnd, R.string.action_dnd_sub, Icons.Filled.DoNotDisturb)
    ActionType.SYSTEM_SCREEN_ROTATION -> Triple(R.string.action_rotation, R.string.action_rotation_sub, Icons.Filled.ScreenRotation)
    ActionType.SYSTEM_OPEN_APP -> Triple(R.string.action_open_apps, R.string.action_open_apps_sub, Icons.Filled.Apps)
    ActionType.SYSTEM_SEND_NOTIFICATION -> Triple(R.string.action_notification, R.string.action_notification_sub, Icons.Filled.NotificationImportant)
    ActionType.SYSTEM_WIFI -> Triple(R.string.action_wifi, R.string.action_wifi_sub, Icons.Filled.Wifi)
    ActionType.SYSTEM_BLUETOOTH -> Triple(R.string.action_bluetooth, R.string.action_bluetooth_sub, Icons.Filled.Bluetooth)
    ActionType.SYSTEM_FLASHLIGHT -> Triple(R.string.action_flashlight, R.string.action_flashlight_sub, Icons.Filled.FlashOn)
    ActionType.SYSTEM_AIRPLANE_MODE -> Triple(R.string.action_airplane, R.string.action_airplane_sub, Icons.Filled.AirplanemodeActive)
    ActionType.SYSTEM_MEDIA_PLAY_PAUSE -> Triple(R.string.action_media_play, R.string.action_media_play_sub, Icons.Filled.MusicNote)
    ActionType.SYSTEM_MEDIA_NEXT -> Triple(R.string.action_media_next, R.string.action_media_next_sub, Icons.Filled.MusicNote)
    ActionType.SYSTEM_MEDIA_PREVIOUS -> Triple(R.string.action_media_prev, R.string.action_media_prev_sub, Icons.Filled.MusicNote)
    ActionType.SYSTEM_OPEN_URL -> Triple(R.string.action_open_url, R.string.action_open_url_sub, Icons.Filled.Link)
    ActionType.SYSTEM_CLEAR_NOTIFICATIONS -> Triple(R.string.action_clear_notifs, R.string.action_clear_notifs_sub, Icons.Filled.Notifications)
    ActionType.SYSTEM_EXPAND_STATUS_BAR -> Triple(R.string.action_expand_bar, R.string.action_expand_bar_sub, Icons.Filled.NotificationImportant)
    ActionType.SYSTEM_COLLAPSE_STATUS_BAR -> Triple(R.string.action_collapse_bar, R.string.action_collapse_bar_sub, Icons.Filled.NotificationImportant)
    ActionType.SYSTEM_SCREEN_TIMEOUT -> Triple(R.string.action_screen_timeout, R.string.action_screen_timeout_sub, Icons.Filled.ScreenRotation)
    ActionType.SYSTEM_STAY_AWAKE -> Triple(R.string.action_stay_awake, R.string.action_stay_awake_sub, Icons.Filled.FlashOn)
    ActionType.SYSTEM_AUTO_BRIGHTNESS -> Triple(R.string.action_auto_brightness, R.string.action_auto_brightness_sub, Icons.Filled.FlashOn)
    ActionType.SYSTEM_RINGER_MODE -> Triple(R.string.action_ringer, R.string.action_ringer_sub, Icons.AutoMirrored.Filled.VolumeUp)
    ActionType.SYSTEM_MOBILE_DATA -> Triple(R.string.action_mobile_data, R.string.action_mobile_data_sub, Icons.Filled.Wifi)
    ActionType.SYSTEM_HOTSPOT -> Triple(R.string.action_hotspot, R.string.action_hotspot_sub, Icons.Filled.Wifi)
    ActionType.SYSTEM_NFC -> Triple(R.string.action_nfc, R.string.action_nfc_sub, Icons.Filled.Wifi)
    ActionType.SYSTEM_POWER_SAVER -> Triple(R.string.action_power_saver, R.string.action_power_saver_sub, Icons.Filled.BatteryChargingFull)
    ActionType.SYSTEM_ANIMATIONS -> Triple(R.string.action_animations, R.string.action_animations_sub, Icons.Filled.ScreenRotation)
    ActionType.SYSTEM_LOCK_SCREEN -> Triple(R.string.action_lock_screen, R.string.action_lock_screen_sub, Icons.Filled.Lock)
    ActionType.SYSTEM_SET_ALARM -> Triple(R.string.action_set_alarm, R.string.action_set_alarm_sub, Icons.Filled.Schedule)
    ActionType.SYSTEM_DARK_MODE -> Triple(R.string.action_dark_mode, R.string.action_dark_mode_sub, Icons.Filled.DarkMode)
    ActionType.SYSTEM_OPEN_RECENTS -> Triple(R.string.action_open_recents, R.string.action_open_recents_sub, Icons.Filled.Apps)
    ActionType.SYSTEM_GO_HOME -> Triple(R.string.action_go_home, R.string.action_go_home_sub, Icons.Filled.Home)
    ActionType.APPLICATION_OPEN_APP_SETTINGS -> Triple(R.string.action_open_app_settings, R.string.action_open_app_settings_sub, Icons.Filled.Settings)
    ActionType.SYSTEM_RING_VOLUME -> Triple(R.string.action_ring_volume, R.string.action_ring_volume_sub, Icons.AutoMirrored.Filled.VolumeUp)
    ActionType.SYSTEM_LOCATION -> Triple(R.string.action_location, R.string.action_location_sub, Icons.Filled.Place)
    ActionType.SYSTEM_OPEN_PLAY_UPDATES -> Triple(R.string.action_play_updates, R.string.action_play_updates_sub, Icons.Filled.Store)
    ActionType.SYSTEM_OPEN_GALAXY_STORE -> Triple(R.string.action_galaxy_store, R.string.action_galaxy_store_sub, Icons.Filled.Store)
    ActionType.SYSTEM_SEND_SMS -> Triple(R.string.action_send_sms, R.string.action_send_sms_sub, Icons.AutoMirrored.Filled.Message)
    ActionType.SYSTEM_SEND_REMINDER -> Triple(R.string.action_reminder, R.string.action_reminder_sub, Icons.Filled.Schedule)
    ActionType.SYSTEM_OPEN_SETTINGS -> Triple(R.string.action_open_settings, R.string.action_open_settings_sub, Icons.Filled.Settings)
    ActionType.SYSTEM_WAIT -> Triple(R.string.action_wait, R.string.action_wait_sub, Icons.Filled.Schedule)
    ActionType.BATTERY_ALERTS -> Triple(R.string.action_battery_alert, R.string.action_battery_alert_sub, Icons.Filled.BatteryChargingFull)
    ActionType.BATTERY_CHARGING_NOTIFICATIONS -> Triple(R.string.action_charging_alert, R.string.action_charging_alert_sub, Icons.Filled.BatteryChargingFull)
    ActionType.APPLICATION_LAUNCH_APP -> Triple(R.string.action_open_apps, R.string.action_open_apps_sub, Icons.Filled.Add)
    ActionType.APPLICATION_CLOSE_APP -> Triple(R.string.action_close_app, R.string.action_close_app_sub, Icons.Filled.Close)
    ActionType.ADVANCED_SHIZUKU -> Triple(R.string.action_shizuku, R.string.action_shizuku_sub, Icons.Filled.Security)
    ActionType.ADVANCED_ROOT -> Triple(R.string.action_root, R.string.action_root_sub, Icons.Filled.Lock)
}
