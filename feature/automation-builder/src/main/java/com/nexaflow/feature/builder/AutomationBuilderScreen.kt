package com.nexaflow.feature.builder

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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

enum class ActionCategory(val headerRes: Int) {
    DISPLAY(R.string.category_display),
    SOUND(R.string.category_sound),
    CONNECTIVITY(R.string.category_connectivity),
    MEDIA(R.string.category_media),
    NOTIFICATIONS(R.string.category_notifications),
    APPS(R.string.category_apps),
    SYSTEM(R.string.category_system),
    BATTERY(R.string.category_battery),
    ADVANCED(R.string.category_advanced)
}

data class ActionOption(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    val color: Color,
    val actionType: ActionType,
    val category: ActionCategory
)

private val actionOptions = listOf(
    // DISPLAY
    ActionOption(R.string.action_brightness, R.string.action_brightness_sub, Icons.Filled.FlashOn, Color(0xFF1B62B7), ActionType.SYSTEM_BRIGHTNESS, ActionCategory.DISPLAY),
    ActionOption(R.string.action_rotation, R.string.action_rotation_sub, Icons.Filled.ScreenRotation, Color(0xFF13A5A8), ActionType.SYSTEM_SCREEN_ROTATION, ActionCategory.DISPLAY),
    ActionOption(R.string.action_screen_timeout, R.string.action_screen_timeout_sub, Icons.Filled.ScreenRotation, Color(0xFF1B62B7), ActionType.SYSTEM_SCREEN_TIMEOUT, ActionCategory.DISPLAY),
    ActionOption(R.string.action_stay_awake, R.string.action_stay_awake_sub, Icons.Filled.WbSunny, Color(0xFFE8A33D), ActionType.SYSTEM_STAY_AWAKE, ActionCategory.DISPLAY),
    ActionOption(R.string.action_auto_brightness, R.string.action_auto_brightness_sub, Icons.Filled.FlashOn, Color(0xFF1B62B7), ActionType.SYSTEM_AUTO_BRIGHTNESS, ActionCategory.DISPLAY),
    ActionOption(R.string.action_dark_mode, R.string.action_dark_mode_sub, Icons.Filled.DarkMode, Color(0xFF7A5BD1), ActionType.SYSTEM_DARK_MODE, ActionCategory.DISPLAY),
    // SOUND
    ActionOption(R.string.action_volume, R.string.action_volume_sub, Icons.AutoMirrored.Filled.VolumeUp, Color(0xFF7A5BD1), ActionType.SYSTEM_VOLUME, ActionCategory.SOUND),
    ActionOption(R.string.action_ringer, R.string.action_ringer_sub, Icons.AutoMirrored.Filled.VolumeUp, Color(0xFF7A5BD1), ActionType.SYSTEM_RINGER_MODE, ActionCategory.SOUND),
    ActionOption(R.string.action_dnd, R.string.action_dnd_sub, Icons.Filled.DoNotDisturb, Color(0xFFE5533D), ActionType.SYSTEM_DND, ActionCategory.SOUND),
    // CONNECTIVITY
    ActionOption(R.string.action_wifi, R.string.action_wifi_sub, Icons.Filled.Wifi, Color(0xFF1B62B7), ActionType.SYSTEM_WIFI, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_bluetooth, R.string.action_bluetooth_sub, Icons.Filled.Bluetooth, Color(0xFF2FA84F), ActionType.SYSTEM_BLUETOOTH, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_mobile_data, R.string.action_mobile_data_sub, Icons.Filled.SignalCellularAlt, Color(0xFF13A5A8), ActionType.SYSTEM_MOBILE_DATA, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_hotspot, R.string.action_hotspot_sub, Icons.Filled.Wifi, Color(0xFF2FA84F), ActionType.SYSTEM_HOTSPOT, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_nfc, R.string.action_nfc_sub, Icons.Filled.Nfc, Color(0xFF1B62B7), ActionType.SYSTEM_NFC, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_airplane, R.string.action_airplane_sub, Icons.Filled.AirplanemodeActive, Color(0xFF13A5A8), ActionType.SYSTEM_AIRPLANE_MODE, ActionCategory.CONNECTIVITY),
    // MEDIA
    ActionOption(R.string.action_media_play, R.string.action_media_play_sub, Icons.Filled.MusicNote, Color(0xFF7A5BD1), ActionType.SYSTEM_MEDIA_PLAY_PAUSE, ActionCategory.MEDIA),
    ActionOption(R.string.action_media_next, R.string.action_media_next_sub, Icons.Filled.MusicNote, Color(0xFF7A5BD1), ActionType.SYSTEM_MEDIA_NEXT, ActionCategory.MEDIA),
    ActionOption(R.string.action_media_prev, R.string.action_media_prev_sub, Icons.Filled.MusicNote, Color(0xFF7A5BD1), ActionType.SYSTEM_MEDIA_PREVIOUS, ActionCategory.MEDIA),
    // NOTIFICATIONS
    ActionOption(R.string.action_notification, R.string.action_notification_sub, Icons.Filled.NotificationImportant, Color(0xFFE8A33D), ActionType.SYSTEM_SEND_NOTIFICATION, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_clear_notifs, R.string.action_clear_notifs_sub, Icons.Filled.Notifications, Color(0xFFE8A33D), ActionType.SYSTEM_CLEAR_NOTIFICATIONS, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_expand_bar, R.string.action_expand_bar_sub, Icons.Filled.NotificationImportant, Color(0xFF13A5A8), ActionType.SYSTEM_EXPAND_STATUS_BAR, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_collapse_bar, R.string.action_collapse_bar_sub, Icons.Filled.NotificationImportant, Color(0xFF13A5A8), ActionType.SYSTEM_COLLAPSE_STATUS_BAR, ActionCategory.NOTIFICATIONS),
    // APPS
    ActionOption(R.string.action_open_apps, R.string.action_open_apps_sub, Icons.Filled.Apps, Color(0xFF2FA84F), ActionType.SYSTEM_OPEN_APP, ActionCategory.APPS),
    ActionOption(R.string.action_close_app, R.string.action_close_app_sub, Icons.Filled.Close, Color(0xFFE5533D), ActionType.APPLICATION_CLOSE_APP, ActionCategory.APPS),
    ActionOption(R.string.action_open_app_settings, R.string.action_open_app_settings_sub, Icons.Filled.Settings, Color(0xFF1B62B7), ActionType.APPLICATION_OPEN_APP_SETTINGS, ActionCategory.APPS),
    // SYSTEM
    ActionOption(R.string.action_flashlight, R.string.action_flashlight_sub, Icons.Filled.FlashOn, Color(0xFFE8A33D), ActionType.SYSTEM_FLASHLIGHT, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_url, R.string.action_open_url_sub, Icons.Filled.Link, Color(0xFF1B62B7), ActionType.SYSTEM_OPEN_URL, ActionCategory.SYSTEM),
    ActionOption(R.string.action_power_saver, R.string.action_power_saver_sub, Icons.Filled.BatteryAlert, Color(0xFF2FA84F), ActionType.SYSTEM_POWER_SAVER, ActionCategory.SYSTEM),
    ActionOption(R.string.action_animations, R.string.action_animations_sub, Icons.Filled.Palette, Color(0xFF7A5BD1), ActionType.SYSTEM_ANIMATIONS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_lock_screen, R.string.action_lock_screen_sub, Icons.Filled.Lock, Color(0xFFE5533D), ActionType.SYSTEM_LOCK_SCREEN, ActionCategory.SYSTEM),
    ActionOption(R.string.action_set_alarm, R.string.action_set_alarm_sub, Icons.Filled.Schedule, Color(0xFF13A5A8), ActionType.SYSTEM_SET_ALARM, ActionCategory.SYSTEM),
    ActionOption(R.string.action_open_recents, R.string.action_open_recents_sub, Icons.Filled.Apps, Color(0xFF1B62B7), ActionType.SYSTEM_OPEN_RECENTS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_go_home, R.string.action_go_home_sub, Icons.Filled.Home, Color(0xFF2FA84F), ActionType.SYSTEM_GO_HOME, ActionCategory.SYSTEM),
    ActionOption(R.string.action_ring_volume, R.string.action_ring_volume_sub, Icons.Filled.PhoneAndroid, Color(0xFF7A5BD1), ActionType.SYSTEM_RING_VOLUME, ActionCategory.SOUND),
    ActionOption(R.string.action_location, R.string.action_location_sub, Icons.Filled.LocationOn, Color(0xFF2FA84F), ActionType.SYSTEM_LOCATION, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_play_updates, R.string.action_play_updates_sub, Icons.Filled.Storefront, Color(0xFF13A5A8), ActionType.SYSTEM_OPEN_PLAY_UPDATES, ActionCategory.APPS),
    ActionOption(R.string.action_galaxy_store, R.string.action_galaxy_store_sub, Icons.Filled.Storefront, Color(0xFF1B62B7), ActionType.SYSTEM_OPEN_GALAXY_STORE, ActionCategory.APPS),
    ActionOption(R.string.action_send_sms, R.string.action_send_sms_sub, Icons.Filled.NotificationImportant, Color(0xFF2FA84F), ActionType.SYSTEM_SEND_SMS, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_reminder, R.string.action_reminder_sub, Icons.Filled.NotificationsActive, Color(0xFFE8A33D), ActionType.SYSTEM_SEND_REMINDER, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_open_settings, R.string.action_open_settings_sub, Icons.Filled.Settings, Color(0xFF1B62B7), ActionType.SYSTEM_OPEN_SETTINGS, ActionCategory.SYSTEM),
    ActionOption(R.string.action_wait, R.string.action_wait_sub, Icons.Filled.Schedule, Color(0xFF7A5BD1), ActionType.SYSTEM_WAIT, ActionCategory.SYSTEM),
    // BATTERY
    ActionOption(R.string.action_battery_alert, R.string.action_battery_alert_sub, Icons.Filled.BatteryAlert, Color(0xFFE5533D), ActionType.BATTERY_ALERTS, ActionCategory.BATTERY),
    ActionOption(R.string.action_charging_alert, R.string.action_charging_alert_sub, Icons.Filled.BatteryAlert, Color(0xFF2FA84F), ActionType.BATTERY_CHARGING_NOTIFICATIONS, ActionCategory.BATTERY),
    // ADVANCED
    ActionOption(R.string.action_shizuku, R.string.action_shizuku_sub, Icons.Filled.Terminal, Color(0xFF1B62B7), ActionType.ADVANCED_SHIZUKU, ActionCategory.ADVANCED),
    ActionOption(R.string.action_root, R.string.action_root_sub, Icons.Filled.Terminal, Color(0xFFE5533D), ActionType.ADVANCED_ROOT, ActionCategory.ADVANCED)
)

private val actionCategories: List<ActionCategory> = ActionCategory.entries.toList()

/** Parses "geo:lat,lng..." (and geo:0,0?q=lat,lng) into a (lat, lng) pair. */
private fun parseGeoUri(uri: String): Pair<Double, Double>? {
    if (!uri.startsWith("geo:")) return null
    val coordsPart = uri.removePrefix("geo:").substringBefore("?")
    val parts = coordsPart.split(',')
    if (parts.size < 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lng = parts[1].toDoubleOrNull() ?: return null
    if (lat == 0.0 && lng == 0.0) {
        // Some map apps return geo:0,0?q=<address> instead of coordinates.
        return null
    }
    return lat to lng
}

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
    var mapPickerTarget by remember { mutableStateOf<Int?>(null) }
    val mapLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val index = mapPickerTarget ?: return@rememberLauncherForActivityResult
        mapPickerTarget = null
        val data = result.data?.data?.toString().orEmpty()
        val coords = parseGeoUri(data)
        if (coords != null && index in triggers.indices) {
            val current = triggers[index]
            triggers[index] = current.copy(
                config = current.config + ("lat" to coords.first.toString()) + ("lng" to coords.second.toString())
            )
        }
    }
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

    fun moveAction(from: Int, to: Int) {
        if (from !in selectedActions.indices || to !in selectedActions.indices) return
        if (from == to) return
        val item = selectedActions.removeAt(from)
        selectedActions.add(to, item)
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
                    onPickApp = { appPickerTarget = "trigger:$index" },
                    onPickFromMap = {
                        mapPickerTarget = index
                        try {
                            mapLauncher.launch(
                                Intent(Intent.ACTION_PICK, Uri.parse("geo:0,0?z=15")).apply {
                                    `package` = "com.google.android.apps.maps"
                                }
                            )
                        } catch (_: Throwable) {
                            mapLauncher.launch(Intent(Intent.ACTION_PICK, Uri.parse("geo:0,0?z=15")))
                        }
                    }
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
            actionCategories.forEach { category ->
                val categoryOptions = actionOptions.filter { it.category == category }
                if (categoryOptions.isNotEmpty()) {
                    itemHeader(text = stringResource(category.headerRes))
                    NexaFlowCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            categoryOptions.forEach { option ->
                                ActionOptionRow(
                                    option = option,
                                    checked = option in selectedActions,
                                    onToggle = {
                                        if (option in selectedActions) selectedActions.remove(option)
                                        else selectedActions.add(option)
                                    }
                                )
                                if (option in selectedActions) {
                                    val config = actionConfigs[option.actionType] ?: emptyMap()
                                    ActionConfigEditor(
                                        option = option,
                                        config = config,
                                        onConfigChange = { actionConfigs[option.actionType] = it },
                                        onPickApp = { appPickerTarget = "action:${option.actionType.name}" }
                                    )
                                    PermissionHintForAction(
                                        actionType = option.actionType,
                                        context = context
                                    )
                                    Spacer(modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (selectedActions.isNotEmpty()) {
                SectionHeader(text = stringResource(R.string.section_execution_order))
                NexaFlowCard {
                    ActionOrderSection(
                        actions = selectedActions.toList(),
                        onMove = { from, to -> moveAction(from, to) },
                        onRemove = { option -> selectedActions.remove(option) }
                    )
                }
                Text(
                    text = stringResource(R.string.execution_order_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
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
            val actionTypeName = target.removePrefix("action:")
            val isOpenApp = actionTypeName == ActionType.SYSTEM_OPEN_APP.name
            val singlePickType = when (actionTypeName) {
                ActionType.APPLICATION_CLOSE_APP.name -> ActionType.APPLICATION_CLOSE_APP
                ActionType.APPLICATION_OPEN_APP_SETTINGS.name -> ActionType.APPLICATION_OPEN_APP_SETTINGS
                else -> null
            }
            when {
                isOpenApp -> AppPickerDialog(
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
                singlePickType != null -> AppPickerDialog(
                    onPickSingle = { app ->
                        actionConfigs[singlePickType] = mapOf("package" to app.packageName)
                        appPickerTarget = null
                    },
                    onDismiss = { appPickerTarget = null }
                )
                else -> appPickerTarget = null
            }
        }
    }
}
