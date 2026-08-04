package com.nexaflow.feature.builder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowIcons
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.ToggleRow
import com.nexaflow.core.ui.iconVector
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Condition
import com.nexaflow.domain.models.ConditionType
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType

data class ActionOption(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    val color: Color,
    val actionType: ActionType
)

private val actionOptions = listOf(
    ActionOption(R.string.action_brightness, R.string.action_brightness_sub, Icons.Filled.FlashOn, Color(0xFF1B62B7), ActionType.SYSTEM_BRIGHTNESS),
    ActionOption(R.string.action_volume, R.string.action_volume_sub, Icons.Filled.VolumeUp, Color(0xFF7A5BD1), ActionType.SYSTEM_VOLUME),
    ActionOption(R.string.action_dnd, R.string.action_dnd_sub, Icons.Filled.DoNotDisturb, Color(0xFFE5533D), ActionType.SYSTEM_DND),
    ActionOption(R.string.action_open_apps, R.string.action_open_apps_sub, Icons.Filled.Apps, Color(0xFF2FA84F), ActionType.SYSTEM_OPEN_APP),
    ActionOption(R.string.action_close_app, R.string.action_close_app_sub, Icons.Filled.Close, Color(0xFFE5533D), ActionType.APPLICATION_CLOSE_APP),
    ActionOption(R.string.action_notification, R.string.action_notification_sub, Icons.Filled.NotificationImportant, Color(0xFFE8A33D), ActionType.SYSTEM_SEND_NOTIFICATION),
    ActionOption(R.string.action_wifi, R.string.action_wifi_sub, Icons.Filled.Wifi, Color(0xFF1B62B7), ActionType.SYSTEM_WIFI),
    ActionOption(R.string.action_bluetooth, R.string.action_bluetooth_sub, Icons.Filled.Bluetooth, Color(0xFF2FA84F), ActionType.SYSTEM_BLUETOOTH),
    ActionOption(R.string.action_flashlight, R.string.action_flashlight_sub, Icons.Filled.FlashOn, Color(0xFFE8A33D), ActionType.SYSTEM_FLASHLIGHT),
    ActionOption(R.string.action_airplane, R.string.action_airplane_sub, Icons.Filled.AirplanemodeActive, Color(0xFF13A5A8), ActionType.SYSTEM_AIRPLANE_MODE),
    ActionOption(R.string.action_media_play, R.string.action_media_play_sub, Icons.Filled.MusicNote, Color(0xFF7A5BD1), ActionType.SYSTEM_MEDIA_PLAY_PAUSE),
    ActionOption(R.string.action_media_next, R.string.action_media_next_sub, Icons.Filled.MusicNote, Color(0xFF7A5BD1), ActionType.SYSTEM_MEDIA_NEXT),
    ActionOption(R.string.action_media_prev, R.string.action_media_prev_sub, Icons.Filled.MusicNote, Color(0xFF7A5BD1), ActionType.SYSTEM_MEDIA_PREVIOUS),
    ActionOption(R.string.action_open_url, R.string.action_open_url_sub, Icons.Filled.Link, Color(0xFF1B62B7), ActionType.SYSTEM_OPEN_URL),
    ActionOption(R.string.action_clear_notifs, R.string.action_clear_notifs_sub, Icons.Filled.Notifications, Color(0xFFE8A33D), ActionType.SYSTEM_CLEAR_NOTIFICATIONS),
    ActionOption(R.string.action_expand_bar, R.string.action_expand_bar_sub, Icons.Filled.NotificationImportant, Color(0xFF13A5A8), ActionType.SYSTEM_EXPAND_STATUS_BAR),
    ActionOption(R.string.action_collapse_bar, R.string.action_collapse_bar_sub, Icons.Filled.NotificationImportant, Color(0xFF13A5A8), ActionType.SYSTEM_COLLAPSE_STATUS_BAR),
    ActionOption(R.string.action_rotation, R.string.action_rotation_sub, Icons.Filled.ScreenRotation, Color(0xFF13A5A8), ActionType.SYSTEM_SCREEN_ROTATION),
    ActionOption(R.string.action_battery_alert, R.string.action_battery_alert_sub, Icons.Filled.BatteryAlert, Color(0xFFE5533D), ActionType.BATTERY_ALERTS),
    ActionOption(R.string.action_charging_alert, R.string.action_charging_alert_sub, Icons.Filled.BatteryAlert, Color(0xFF2FA84F), ActionType.BATTERY_CHARGING_NOTIFICATIONS),
    ActionOption(R.string.action_shizuku, R.string.action_shizuku_sub, Icons.Filled.Terminal, Color(0xFF1B62B7), ActionType.ADVANCED_SHIZUKU),
    ActionOption(R.string.action_root, R.string.action_root_sub, Icons.Filled.Terminal, Color(0xFFE5533D), ActionType.ADVANCED_ROOT)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationBuilderScreen(navController: NavController) {
    val viewModel: AutomationBuilderViewModel = hiltViewModel()
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    val triggers = remember { mutableStateListOf<TriggerDraft>() }
    var batteryCondition by remember { mutableStateOf(false) }
    var timeRangeCondition by remember { mutableStateOf(false) }
    var selectedIconIndex by remember { mutableStateOf(0) }
    var batteryThreshold by remember { mutableStateOf(20) }
    var rangeStart by remember { mutableStateOf("22:00") }
    var rangeEnd by remember { mutableStateOf("07:00") }
    var rangePickerTarget by remember { mutableStateOf<String?>(null) }
    var appPickerTarget by remember { mutableStateOf<String?>(null) }
    val actionConfigs = remember { mutableStateMapOf<ActionType, Map<String, String>>() }
    val selectedActions = remember { mutableStateListOf<ActionOption>() }

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    DisposableEffect(savedStateHandle) {
        val observer = Observer<Int> { index ->
            selectedIconIndex = index
        }
        savedStateHandle?.getLiveData<Int>("selected_icon")?.observeForever(observer)
        onDispose {
            savedStateHandle?.getLiveData<Int>("selected_icon")?.removeObserver(observer)
        }
    }

    fun save() {
        val builtTriggers = triggers.map { draft ->
            Trigger(draft.type, draft.config)
        }
        val conditions = buildList {
            if (batteryCondition) {
                add(Condition(ConditionType.BATTERY_PERCENTAGE, mapOf("above" to batteryThreshold.toString())))
            }
            if (timeRangeCondition) {
                add(Condition(ConditionType.TIME_RANGE, mapOf("start" to rangeStart, "end" to rangeEnd)))
            }
        }
        val actions = selectedActions.map { Action(it.actionType, actionConfigs[it.actionType] ?: emptyMap()) }
        viewModel.saveAutomation(
            name = name,
            icon = NexaFlowIcons.all[selectedIconIndex].first,
            triggers = builtTriggers,
            conditions = conditions,
            actions = actions
        )
        navController.popBackStack()
    }

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.builder_title),
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { save() }) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NexaFlowCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(text = stringResource(R.string.name_hint)) },
                        singleLine = true
                    )
                }
            }
            NexaFlowCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("icon_picker") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconBadge(
                        icon = iconVector(NexaFlowIcons.all[selectedIconIndex].first),
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.icon), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = stringResource(R.string.tap_to_choose_icon),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
            SectionHeader(text = stringResource(R.string.section_when))
            triggers.forEachIndexed { index, draft ->
                TriggerEditorCard(
                    draft = draft,
                    index = index,
                    onConfigChange = { updated ->
                        triggers[index] = updated
                    },
                    onRemove = { triggers.removeAt(index) },
                    onPickApp = { appPickerTarget = "trigger:$index" }
                )
            }
            Button(
                onClick = {
                    triggers.add(TriggerDraft(TriggerType.TIME, mapOf("time" to "08:00")))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text(text = stringResource(R.string.add_another_trigger), modifier = Modifier.padding(start = 8.dp))
            }
            SectionHeader(text = stringResource(R.string.section_conditions))
            NexaFlowCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleRow(
                        icon = Icons.Filled.Bolt,
                        title = stringResource(R.string.battery_above),
                        subtitle = stringResource(R.string.battery_above_sub),
                        checked = batteryCondition,
                        onCheckedChange = { batteryCondition = it }
                    )
                    if (batteryCondition) {
                        SliderRow(
                            label = stringResource(R.string.minimum_battery, batteryThreshold),
                            value = batteryThreshold.toFloat(),
                            onValueChange = { batteryThreshold = it.toInt() },
                            valueRange = 5f..100f
                        )
                    }
                    ToggleRow(
                        icon = Icons.Filled.ScreenRotation,
                        title = stringResource(R.string.within_time_range),
                        subtitle = stringResource(R.string.within_time_range_sub),
                        checked = timeRangeCondition,
                        onCheckedChange = { timeRangeCondition = it }
                    )
                    if (timeRangeCondition) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.from), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { rangePickerTarget = "start" }) {
                                    Text(text = rangeStart)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.to), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { rangePickerTarget = "end" }) {
                                    Text(text = rangeEnd)
                                }
                            }
                        }
                    }
                }
            }
            SectionHeader(text = stringResource(R.string.section_actions))
            NexaFlowCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    actionOptions.forEach { option ->
                        val checked = option in selectedActions
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedActions.remove(option) else selectedActions.add(option)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconBadge(icon = option.icon, containerColor = option.color, size = 36)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(option.titleRes), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = stringResource(option.subtitleRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Icon(
                                imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Add,
                                contentDescription = null,
                                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                        if (checked) {
                            val config = actionConfigs[option.actionType] ?: emptyMap()
                            ActionConfigEditor(
                                option = option,
                                config = config,
                                onConfigChange = { actionConfigs[option.actionType] = it },
                                onPickApp = { appPickerTarget = "action" }
                            )
                            when (option.actionType) {
                                ActionType.SYSTEM_BRIGHTNESS,
                                ActionType.SYSTEM_SCREEN_ROTATION -> PermissionHint(
                                    text = stringResource(R.string.write_settings_hint),
                                    buttonLabel = stringResource(R.string.grant),
                                    onClick = { PermissionShortcuts.openWriteSettings(context) }
                                )
                                ActionType.SYSTEM_DND -> PermissionHint(
                                    text = stringResource(R.string.dnd_hint),
                                    buttonLabel = stringResource(R.string.grant),
                                    onClick = { PermissionShortcuts.openNotificationPolicy(context) }
                                )
                                ActionType.SYSTEM_FLASHLIGHT -> PermissionHint(
                                    text = stringResource(R.string.flashlight_hint),
                                    buttonLabel = stringResource(R.string.grant),
                                    onClick = { PermissionShortcuts.openAppSettings(context) }
                                )
                                ActionType.ADVANCED_SHIZUKU -> PermissionHint(
                                    text = stringResource(R.string.shizuku_hint),
                                    buttonLabel = stringResource(R.string.info),
                                    onClick = { PermissionShortcuts.openShizukuManager(context) }
                                )
                                ActionType.ADVANCED_ROOT -> PermissionHint(
                                    text = stringResource(R.string.root_hint),
                                    buttonLabel = stringResource(R.string.info),
                                    onClick = { PermissionShortcuts.openShizukuManager(context) }
                                )
                                else -> Unit
                            }
                            Spacer(modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.save_automation))
            }
        }
    }

    rangePickerTarget?.let { target ->
        val initial = if (target == "start") rangeStart else rangeEnd
        TimePickerAlert(
            initialTime = initial,
            onConfirm = {
                if (target == "start") rangeStart = it else rangeEnd = it
                rangePickerTarget = null
            },
            onDismiss = { rangePickerTarget = null }
        )
    }

    appPickerTarget?.let { target ->
        val triggerIndex = target.removePrefix("trigger:").toIntOrNull()
        if (triggerIndex != null) {
            AppPickerDialog(
                onPickSingle = { app ->
                    val current = triggers[triggerIndex]
                    triggers[triggerIndex] = current.copy(config = mapOf("package" to app.packageName))
                    appPickerTarget = null
                },
                onDismiss = { appPickerTarget = null }
            )
        } else {
            AppPickerDialog(
                onPickSingle = { app ->
                    val existing = actionConfigs[ActionType.SYSTEM_OPEN_APP]
                    val current = (existing?.get("packages") ?: existing?.get("package") ?: "")
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    val merged = (current + app.packageName).distinct()
                    actionConfigs[ActionType.SYSTEM_OPEN_APP] = mapOf("packages" to merged.joinToString(","))
                    appPickerTarget = null
                },
                onPickMultiple = { packages ->
                    actionConfigs[ActionType.SYSTEM_OPEN_APP] = mapOf("packages" to packages.joinToString(",") { it.packageName })
                    appPickerTarget = null
                },
                multiSelect = true,
                onDismiss = { appPickerTarget = null }
            )
        }
    }
}
