package com.nexaflow.feature.builder

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowIcons
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.iconVector
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.launch

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

internal val actionOptions = listOf(
    // DISPLAY
    ActionOption(R.string.action_brightness, R.string.action_brightness_sub, Icons.Filled.FlashOn, Color(0xFF1B62B7), ActionType.SYSTEM_BRIGHTNESS, ActionCategory.DISPLAY),
    ActionOption(R.string.action_rotation, R.string.action_rotation_sub, Icons.Filled.ScreenRotation, Color(0xFF13A5A8), ActionType.SYSTEM_SCREEN_ROTATION, ActionCategory.DISPLAY),
    ActionOption(R.string.action_screen_timeout, R.string.action_screen_timeout_sub, Icons.Filled.ScreenRotation, Color(0xFF1B62B7), ActionType.SYSTEM_SCREEN_TIMEOUT, ActionCategory.DISPLAY),
    ActionOption(R.string.action_stay_awake, R.string.action_stay_awake_sub, Icons.Filled.WbSunny, Color(0xFFE8A33D), ActionType.SYSTEM_STAY_AWAKE, ActionCategory.DISPLAY),
    ActionOption(R.string.action_auto_brightness, R.string.action_auto_brightness_sub, Icons.Filled.FlashOn, Color(0xFF1B62B7), ActionType.SYSTEM_AUTO_BRIGHTNESS, ActionCategory.DISPLAY),
    ActionOption(R.string.action_dark_mode, R.string.action_dark_mode_sub, Icons.Filled.DarkMode, Color(0xFF7A5BD1), ActionType.SYSTEM_DARK_MODE, ActionCategory.DISPLAY),
    // SOUND
    ActionOption(R.string.action_volume, R.string.action_volume_sub, Icons.AutoMirrored.Filled.VolumeUp, Color(0xFF7A5BD1), ActionType.SYSTEM_VOLUME, ActionCategory.SOUND),
    ActionOption(R.string.action_stream_volume, R.string.action_stream_volume_sub, Icons.AutoMirrored.Filled.VolumeUp, Color(0xFF7A5BD1), ActionType.SYSTEM_STREAM_VOLUME, ActionCategory.SOUND),
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

internal val actionCategories: List<ActionCategory> = ActionCategory.entries.toList()

private fun TriggerType.summaryLabelRes(): Int = when (this) {
    TriggerType.TIME -> R.string.trigger_type_time
    TriggerType.BATTERY -> R.string.trigger_type_battery
    TriggerType.APPLICATION -> R.string.trigger_type_app
    TriggerType.DEVICE -> R.string.trigger_type_device
    TriggerType.CONNECTIVITY -> R.string.trigger_type_connectivity
    TriggerType.LOCATION -> R.string.trigger_type_location
    TriggerType.SMS -> R.string.trigger_type_sms
    TriggerType.BLUETOOTH_DEVICE -> R.string.trigger_type_bluetooth
    TriggerType.RINGER_MODE -> R.string.trigger_type_ringer
}

/** Samsung-style live "IF … THEN …" summary shown while building a task. */
@Composable
private fun BuilderSummaryCard(
    triggers: List<TriggerDraft>,
    actions: List<ActionOption>
) {
    NexaFlowCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryLine(
                label = stringResource(R.string.summary_if),
                text = if (triggers.isEmpty()) {
                    stringResource(R.string.summary_no_triggers)
                } else {
                    triggers.map { stringResource(it.type.summaryLabelRes()) }.joinToString(" + ")
                },
                accent = Color(0xFF1B62B7)
            )
            SummaryLine(
                label = stringResource(R.string.summary_then),
                text = if (actions.isEmpty()) {
                    stringResource(R.string.summary_no_actions)
                } else {
                    actions.map { stringResource(it.titleRes) }.joinToString(" + ")
                },
                accent = Color(0xFF2FA84F)
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, text: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (text.isBlank()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Bottom save bar for the single-page task editor. */
@Composable
private fun BuilderSaveBar(
    onSave: () -> Unit
) {
    Surface(shadowElevation = 8.dp) {
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
            Text(
                text = stringResource(R.string.save_automation),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/** One selected action, in order, with reorder buttons, config editor and permission hint. */
@Composable
private fun SelectedActionCard(
    option: ActionOption,
    index: Int,
    total: Int,
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onPickApp: () -> Unit,
    context: Context
) {
    NexaFlowCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(20.dp)
                )
                IconBadge(
                    icon = option.icon,
                    containerColor = option.color.copy(alpha = 0.15f),
                    contentColor = option.color
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(option.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(option.subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(onClick = onMoveUp, enabled = index > 0) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.move_up)
                    )
                }
                IconButton(onClick = onMoveDown, enabled = index < total - 1) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.move_down)
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.remove_action),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActionConfigEditor(
                option = option,
                config = config,
                onConfigChange = onConfigChange,
                onPickApp = onPickApp
            )
            PermissionHintForAction(
                actionType = option.actionType,
                context = context
            )
        }
    }
}

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
fun AutomationBuilderScreen(navController: NavController, automationId: String? = null) {
    val viewModel: AutomationBuilderViewModel = hiltViewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var name by remember { mutableStateOf("") }
    val triggers = remember { mutableStateListOf<TriggerDraft>() }
    var selectedIconIndex by remember { mutableStateOf(0) }
    var appPickerTarget by remember { mutableStateOf<String?>(null) }
    var mapPickerTarget by remember { mutableStateOf<Int?>(null) }
    var bluetoothPickerTarget by remember { mutableStateOf<Int?>(null) }
    var showTriggerPicker by remember { mutableStateOf(false) }
    var showActionPicker by remember { mutableStateOf(false) }
    val actionConfigs = remember { mutableStateMapOf<ActionType, Map<String, String>>() }
    val selectedActions = remember { mutableStateListOf<ActionOption>() }
    var revertOnExit by remember { mutableStateOf(false) }
    val exitActionConfigs = remember { mutableStateMapOf<ActionType, Map<String, String>>() }
    val selectedExitActions = remember { mutableStateListOf<ActionOption>() }
    var showExitPicker by remember { mutableStateOf(false) }

    // Edit mode: load the existing automation once and pre-fill the drafts.
    LaunchedEffect(automationId) {
        automationId?.let { viewModel.loadAutomation(it) }
    }
    val loadedAutomation by viewModel.loaded.collectAsStateWithLifecycle()
    LaunchedEffect(loadedAutomation) {
        val loaded = loadedAutomation ?: return@LaunchedEffect
        if (loaded.id != automationId) return@LaunchedEffect
        name = loaded.name
        selectedIconIndex = NexaFlowIcons.all.indexOfFirst { it.first == loaded.icon }.coerceAtLeast(0)
        triggers.clear()
        loaded.triggers.forEach { triggers.add(TriggerDraft(it.type, it.config)) }
        selectedActions.clear()
        actionConfigs.clear()
        loaded.actions.forEach { action ->
            actionOptions.find { it.actionType == action.type }?.let { option ->
                selectedActions.add(option)
                actionConfigs[option.actionType] = action.config
            }
        }
        revertOnExit = loaded.revertOnExit
        selectedExitActions.clear()
        exitActionConfigs.clear()
        loaded.exitActions.forEach { action ->
            actionOptions.find { it.actionType == action.type }?.let { option ->
                selectedExitActions.add(option)
                exitActionConfigs[option.actionType] = action.config
            }
        }
    }
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
    val scrollState = rememberScrollState()

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

    val isEditing = automationId != null

    fun moveAction(from: Int, to: Int) {
        if (from !in selectedActions.indices || to !in selectedActions.indices) return
        if (from == to) return
        val item = selectedActions.removeAt(from)
        selectedActions.add(to, item)
    }

    fun showSnackbar(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun save(closeAfterSave: Boolean = true) {
        if (triggers.isEmpty()) {
            showSnackbar(context.getString(R.string.next_needs_trigger))
            return
        }
        if (selectedActions.isEmpty()) {
            showSnackbar(context.getString(R.string.next_needs_action))
            return
        }
        val builtTriggers = triggers.map { draft ->
            Trigger(draft.type, draft.config)
        }
        val actions = selectedActions.map { Action(it.actionType, actionConfigs[it.actionType] ?: emptyMap()) }
        val exitActions = selectedExitActions.map { Action(it.actionType, exitActionConfigs[it.actionType] ?: emptyMap()) }
        viewModel.saveAutomation(
            name = name,
            icon = NexaFlowIcons.all[selectedIconIndex].first,
            triggers = builtTriggers,
            actions = actions,
            exitActions = exitActions,
            revertOnExit = revertOnExit
        )
        if (closeAfterSave) {
            navController.popBackStack()
        } else {
            showSnackbar(context.getString(R.string.saved_successfully))
        }
    }

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = if (isEditing) stringResource(R.string.edit_task_title) else stringResource(R.string.builder_title),
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { save(closeAfterSave = false) }) {
                        Icon(imageVector = Icons.Filled.Save, contentDescription = stringResource(R.string.quick_save))
                    }
                    IconButton(onClick = { save() }) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                    }
                }
            )
        },
        bottomBar = {
            BuilderSaveBar(onSave = { save() })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Name + icon (one card) ────────────────────────────────
            NexaFlowCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = stringResource(R.string.name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(text = stringResource(R.string.name_hint)) },
                        singleLine = true
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            }

            // ── Live summary ─────────────────────────────────────────
            BuilderSummaryCard(
                triggers = triggers,
                actions = selectedActions
            )

            // ── WHEN (triggers) ──────────────────────────────────────
            SectionHeader(
                text = stringResource(R.string.section_when),
                trailing = {
                    IconButton(onClick = { showTriggerPicker = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_another_trigger)
                        )
                    }
                }
            )
            if (triggers.isEmpty()) {
                NexaFlowCard {
                    Text(
                        text = stringResource(R.string.summary_no_triggers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
            triggers.forEachIndexed { index, draft ->
                TriggerEditorCard(
                    draft = draft,
                    index = index,
                    onConfigChange = { updated ->
                        triggers[index] = updated
                    },
                    onRemove = { triggers.removeAt(index) },
                    onPickApp = { appPickerTarget = "trigger:$index" },
                    onPickBluetooth = { bluetoothPickerTarget = index },
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

            // ── THEN (actions) ───────────────────────────────────────
            SectionHeader(
                text = stringResource(R.string.section_actions),
                trailing = {
                    IconButton(onClick = { showActionPicker = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_action)
                        )
                    }
                }
            )
            if (selectedActions.isEmpty()) {
                NexaFlowCard {
                    Text(
                        text = stringResource(R.string.summary_no_actions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
            selectedActions.forEachIndexed { index, option ->
                SelectedActionCard(
                    option = option,
                    index = index,
                    total = selectedActions.size,
                    config = actionConfigs[option.actionType] ?: emptyMap(),
                    onConfigChange = { actionConfigs[option.actionType] = it },
                    onMoveUp = { moveAction(index, index - 1) },
                    onMoveDown = { moveAction(index, index + 1) },
                    onRemove = {
                        selectedActions.remove(option)
                        actionConfigs.remove(option.actionType)
                    },
                    onPickApp = { appPickerTarget = "action:${option.actionType.name}" },
                    context = context
                )
            }

            // ── When the task ends (part of the task itself) ─────────
            SectionHeader(
                text = stringResource(R.string.section_exit_behavior),
                trailing = {
                    if (!revertOnExit) {
                        IconButton(onClick = { showExitPicker = true }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.add_exit_action)
                            )
                        }
                    }
                }
            )
            NexaFlowCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.exit_behavior_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.exit_revert_label),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = revertOnExit,
                            onCheckedChange = { enabled ->
                                revertOnExit = enabled
                                // Restoring the original state and custom exit actions are mutually
                                // exclusive: enabling revert discards custom exit actions.
                                if (enabled) {
                                    selectedExitActions.clear()
                                    exitActionConfigs.clear()
                                }
                            }
                        )
                    }
                    when {
                        revertOnExit -> {
                            Text(
                                text = stringResource(R.string.exit_revert_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        selectedExitActions.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.exit_nothing_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        else -> {
                            selectedExitActions.forEach { option ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(option.titleRes),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    IconButton(onClick = {
                                        selectedExitActions.remove(option)
                                        exitActionConfigs.remove(option.actionType)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.remove_action),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                val config = exitActionConfigs[option.actionType] ?: emptyMap()
                                ActionConfigEditor(
                                    option = option,
                                    config = config,
                                    onConfigChange = { exitActionConfigs[option.actionType] = it },
                                    onPickApp = { appPickerTarget = "exit:${option.actionType.name}" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTriggerPicker) {
        TriggerTypePickerDialog(
            onPick = { type ->
                triggers.add(TriggerDraft(type, defaultTriggerConfig(type)))
                showTriggerPicker = false
            },
            onDismiss = { showTriggerPicker = false }
        )
    }

    if (showActionPicker) {
        ActionPickerDialog(
            alreadySelected = selectedActions.toList(),
            onConfirm = { picked ->
                picked.forEach { option ->
                    if (option !in selectedActions) selectedActions.add(option)
                }
                showActionPicker = false
            },
            onDismiss = { showActionPicker = false }
        )
    }

    if (showExitPicker) {
        ExitActionPickerDialog(
            alreadySelected = selectedExitActions.toList(),
            onPick = { option ->
                if (option !in selectedExitActions) {
                    selectedExitActions.add(option)
                }
                showExitPicker = false
            },
            onDismiss = { showExitPicker = false }
        )
    }

    bluetoothPickerTarget?.let { index ->
        if (index in triggers.indices) {
            BluetoothDevicePickerDialog(
                onPick = { device ->
                    triggers[index] = triggers[index].copy(
                        config = mapOf(
                            "deviceName" to device.name,
                            "deviceAddress" to device.address,
                            "event" to (triggers[index].config["event"] ?: "CONNECTED")
                        )
                    )
                    bluetoothPickerTarget = null
                },
                onDismiss = { bluetoothPickerTarget = null }
            )
        } else {
            bluetoothPickerTarget = null
        }
    }

    appPickerTarget?.let { target ->
        val exitActionName = target.removePrefix("exit:")
        if (exitActionName != target) {
            val exitType = ActionType.entries.firstOrNull { it.name == exitActionName }
            if (exitType == ActionType.SYSTEM_OPEN_APP) {
                AppPickerDialog(
                    onPickSingle = { app ->
                        val existing = exitActionConfigs[ActionType.SYSTEM_OPEN_APP]
                        val current = (existing?.get("packages") ?: existing?.get("package") ?: "")
                            .split(',')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        val merged = (current + app.packageName).distinct()
                        exitActionConfigs[ActionType.SYSTEM_OPEN_APP] = mapOf("packages" to merged.joinToString(","))
                        appPickerTarget = null
                    },
                    onPickMultiple = { packages ->
                        exitActionConfigs[ActionType.SYSTEM_OPEN_APP] = mapOf("packages" to packages.joinToString(",") { it.packageName })
                        appPickerTarget = null
                    },
                    multiSelect = true,
                    onDismiss = { appPickerTarget = null }
                )
            } else if (exitType != null) {
                AppPickerDialog(
                    onPickSingle = { app ->
                        exitActionConfigs[exitType] = mapOf("package" to app.packageName)
                        appPickerTarget = null
                    },
                    onDismiss = { appPickerTarget = null }
                )
            } else {
                appPickerTarget = null
            }
            return@let
        }
        val triggerIndex = target.removePrefix("trigger:").toIntOrNull()
        if (triggerIndex != null) {
            AppPickerDialog(
                onPickSingle = { app ->
                    val current = triggers[triggerIndex]
                    triggers[triggerIndex] = current.copy(
                        config = mapOf("packages" to app.packageName)
                    )
                    appPickerTarget = null
                },
                onPickMultiple = { apps ->
                    val current = triggers[triggerIndex]
                    triggers[triggerIndex] = current.copy(
                        config = mapOf("packages" to apps.joinToString(",") { it.packageName })
                    )
                    appPickerTarget = null
                },
                multiSelect = true,
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
