package com.nexaflow.feature.builder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.nexaflow.core.engine.LocationAccess
import com.nexaflow.core.pluginsdk.LocaleContract
import com.nexaflow.core.pluginsdk.PluginConfigParser
import com.nexaflow.core.rom.ElevatedAccessShortcuts
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowIcons
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.iconVector
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.PluginInfo
import com.nexaflow.domain.models.EndBehaviorCatalog
import com.nexaflow.domain.models.EndMode
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
    ADVANCED(R.string.category_advanced),
    PLUGINS(R.string.category_plugins)
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
    ActionOption(R.string.action_set_ringtone, R.string.action_set_ringtone_sub, Icons.Filled.MusicNote, Color(0xFFE8A33D), ActionType.SYSTEM_SET_RINGTONE, ActionCategory.SOUND),
    ActionOption(R.string.action_ringer, R.string.action_ringer_sub, Icons.AutoMirrored.Filled.VolumeUp, Color(0xFF7A5BD1), ActionType.SYSTEM_RINGER_MODE, ActionCategory.SOUND),
    ActionOption(R.string.action_dnd, R.string.action_dnd_sub, Icons.Filled.DoNotDisturb, Color(0xFFE5533D), ActionType.SYSTEM_DND, ActionCategory.SOUND),
    // CONNECTIVITY
    ActionOption(R.string.action_wifi, R.string.action_wifi_sub, Icons.Filled.Wifi, Color(0xFF1B62B7), ActionType.SYSTEM_WIFI, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_bluetooth, R.string.action_bluetooth_sub, Icons.Filled.Bluetooth, Color(0xFF2FA84F), ActionType.SYSTEM_BLUETOOTH, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_mobile_data, R.string.action_mobile_data_sub, Icons.Filled.SignalCellularAlt, Color(0xFF13A5A8), ActionType.SYSTEM_MOBILE_DATA, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_network_mode, R.string.action_network_mode_sub, Icons.Filled.SignalCellularAlt, Color(0xFF13A5A8), ActionType.SYSTEM_NETWORK_MODE, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_hotspot, R.string.action_hotspot_sub, Icons.Filled.Wifi, Color(0xFF2FA84F), ActionType.SYSTEM_HOTSPOT, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_nfc, R.string.action_nfc_sub, Icons.Filled.Nfc, Color(0xFF1B62B7), ActionType.SYSTEM_NFC, ActionCategory.CONNECTIVITY),
    ActionOption(R.string.action_airplane, R.string.action_airplane_sub, Icons.Filled.AirplanemodeActive, Color(0xFF13A5A8), ActionType.SYSTEM_AIRPLANE_MODE, ActionCategory.CONNECTIVITY),
    // MEDIA
    ActionOption(R.string.action_media_play, R.string.action_media_play_sub, Icons.Filled.MusicNote, Color(0xFF7A5BD1), ActionType.SYSTEM_MEDIA_PLAY_PAUSE, ActionCategory.MEDIA),
    ActionOption(R.string.action_media_next, R.string.action_media_next_sub, Icons.Filled.MusicNote, Color(0xFF7A5BD1), ActionType.SYSTEM_MEDIA_NEXT, ActionCategory.MEDIA),
    ActionOption(R.string.action_media_prev, R.string.action_media_prev_sub, Icons.Filled.MusicNote, Color(0xFF7A5BD1), ActionType.SYSTEM_MEDIA_PREVIOUS, ActionCategory.MEDIA),
    // NOTIFICATIONS
    ActionOption(R.string.action_notification, R.string.action_notification_sub, Icons.Filled.NotificationImportant, Color(0xFFE8A33D), ActionType.SYSTEM_SEND_NOTIFICATION, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_block_notification, R.string.action_block_notification_sub, Icons.Filled.NotificationsOff, Color(0xFFE5533D), ActionType.SYSTEM_BLOCK_NOTIFICATION, ActionCategory.NOTIFICATIONS),
    ActionOption(R.string.action_clear_app_notifications, R.string.action_clear_app_notifications_sub, Icons.Filled.Notifications, Color(0xFFE8A33D), ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS, ActionCategory.NOTIFICATIONS),
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
    ActionOption(R.string.action_http_request, R.string.action_http_request_sub, Icons.Filled.Public, Color(0xFF13A5A8), ActionType.SYSTEM_HTTP_REQUEST, ActionCategory.SYSTEM),
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
    ActionOption(R.string.action_root, R.string.action_root_sub, Icons.Filled.Terminal, Color(0xFFE5533D), ActionType.ADVANCED_ROOT, ActionCategory.ADVANCED),
    // PLUGINS
    ActionOption(R.string.action_plugin, R.string.action_plugin_sub, Icons.Filled.Extension, Color(0xFF7A5BD1), ActionType.PLUGIN_FIRE, ActionCategory.PLUGINS)
)

internal val actionCategories: List<ActionCategory> = ActionCategory.entries.toList()


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
    onPluginConfigure: () -> Unit = {}
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
                onPickApp = onPickApp,
                availableVariables = availableVariables,
                onPluginConfigure = onPluginConfigure,
                automations = automations
            )
            PermissionHintForAction(
                actionType = option.actionType,
                context = context,
                refreshKey = refreshKey,
                onRequestPermission = onRequestPermission,
                onExplainSpecial = onExplainSpecial
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationBuilderScreen(
    navController: NavController,
    automationId: String? = null,
    savedStateHandle: SavedStateHandle? = null
) {
    val viewModel: AutomationBuilderViewModel = hiltViewModel()
    val context = LocalContext.current
    val variables by viewModel.variables.collectAsStateWithLifecycle()
    // Saved tasks available for notification action buttons (run from a notification).
    val automations by viewModel.automations.collectAsStateWithLifecycle()
    // %VARIABLE chips offered in text fields: user globals first, then the most
    // useful device-context built-ins (full set still works when typed by hand).
    val availableVariables = remember(variables) {
        variables.map { it.name } + listOf(
            "DATE", "TIME", "DATETIME", "BATTERY", "CHARGING", "WIFI", "BLUETOOTH",
            "RINGER", "SCREEN", "AIRPLANE", "NETWORK", "BRIGHTNESS", "BRAND", "MODEL", "SDK"
        )
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val stringNextNeedsTrigger = stringResource(R.string.next_needs_trigger)
    val stringNextNeedsAction = stringResource(R.string.next_needs_action)
    val stringSavedSuccessfully = stringResource(R.string.saved_successfully)
    val stringLocationFixFailed = stringResource(R.string.location_fix_failed)
    // P2-11: the editable draft survives rotation AND process death via
    // rememberSaveable (custom savers serialize the immutable drafts to Bundle).
    var name by rememberSaveable { mutableStateOf("") }
    val triggers = rememberSaveable(saver = TriggerDraftListSaver) { mutableStateListOf<TriggerDraft>() }
    val constraints = rememberSaveable(saver = ConstraintDraftListSaver) { mutableStateListOf<ConstraintDraft>() }
    var showConstraintPicker by remember { mutableStateOf(false) }
    var selectedIconIndex by rememberSaveable { mutableStateOf(0) }
    var appPickerTarget by remember { mutableStateOf<String?>(null) }
    var bluetoothPickerTarget by remember { mutableStateOf<Int?>(null) }
    var calendarPickerTarget by remember { mutableStateOf<Int?>(null) }
    var showTriggerPicker by remember { mutableStateOf(false) }
    var showActionPicker by remember { mutableStateOf(false) }
    val actionConfigs = rememberSaveable(saver = ActionConfigMapSaver) { mutableStateMapOf<ActionType, Map<String, String>>() }
    val selectedActions = rememberSaveable(saver = ActionOptionListSaver) { mutableStateListOf<ActionOption>() }
    // Per-action end behavior (leave / restore / set value) applied when the task ends.
    val actionEndBehaviors = rememberSaveable(saver = ActionEndBehaviorMapSaver) { mutableStateMapOf<ActionType, EndBehavior?>() }
    val exitActionConfigs = rememberSaveable(saver = ActionConfigMapSaver) { mutableStateMapOf<ActionType, Map<String, String>>() }
    val selectedExitActions = rememberSaveable(saver = ActionOptionListSaver) { mutableStateListOf<ActionOption>() }
    var showExitPicker by remember { mutableStateOf(false) }

    // ── External plugins (Locale protocol) ─────────────────────────
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    var pluginPickerTarget by remember { mutableStateOf(false) }
    var pluginLauncherPackage by remember { mutableStateOf<String?>(null) }
    var pluginLauncherReceiver by remember { mutableStateOf<String?>(null) }
    val pluginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pkg = pluginLauncherPackage
        val receiver = pluginLauncherReceiver
        pluginLauncherPackage = null
        pluginLauncherReceiver = null
        if (result.resultCode == Activity.RESULT_OK && result.data != null && pkg != null && receiver != null) {
            val bundle: Bundle? = result.data?.getBundleExtra(LocaleContract.EXTRA_BUNDLE)
            val blurb = result.data?.getStringExtra(LocaleContract.EXTRA_STRING_BLURB)
                ?: result.data?.getStringExtra(LocaleContract.EXTRA_BLURB).orEmpty()
            // Prefer the JSON convention; fall back to legacy flat extras.
            val configMap = if (bundle != null) {
                PluginConfigParser.fromBundle(bundle).ifEmpty {
                    PluginConfigParser.flattenBundle(bundle)
                }
            } else {
                emptyMap()
            }
            actionConfigs[ActionType.PLUGIN_FIRE] = mapOf(
                "package" to pkg,
                "receiver" to receiver,
                "blurb" to blurb,
                "bundleJson" to PluginConfigParser.toJson(configMap)
            )
        } else {
            // Canceled/failed: drop the plugin action unless it was configured earlier.
            if ((actionConfigs[ActionType.PLUGIN_FIRE] ?: emptyMap()).isEmpty()) {
                selectedActions.removeAll { it.actionType == ActionType.PLUGIN_FIRE }
            }
        }
    }
    val stringPluginNoEdit = stringResource(R.string.plugin_no_edit)
    fun configurePlugin(packageName: String?, receiver: String?, config: Map<String, String>) {
        val pkg = packageName ?: return
        val rec = receiver ?: return
        pluginLauncherPackage = pkg
        pluginLauncherReceiver = rec
        val intent = Intent(LocaleContract.ACTION_EDIT_SETTING).apply {
            `package` = pkg
            putExtra(LocaleContract.EXTRA_STRING_BREADCRUMB, "NexaFlow")
        }
        // Reconfiguring: hand the saved bundle back so the plugin can pre-fill.
        val savedJson = config["bundleJson"]
        if (!savedJson.isNullOrBlank()) {
            runCatching {
                intent.putExtra(
                    LocaleContract.EXTRA_BUNDLE,
                    PluginConfigParser.toBundle(PluginConfigParser.parseJson(savedJson))
                )
            }
        }
        try {
            pluginLauncher.launch(intent)
        } catch (_: Throwable) {
            pluginLauncherPackage = null
            pluginLauncherReceiver = null
            scope.launch { snackbarHostState.showSnackbar(stringPluginNoEdit) }
            // The plugin has no edit screen: never leave a stuck, unconfigured
            // action behind (the user can still add other plugins).
            if ((actionConfigs[ActionType.PLUGIN_FIRE] ?: emptyMap()).isEmpty()) {
                selectedActions.removeAll { it.actionType == ActionType.PLUGIN_FIRE }
            }
        }
    }

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
        constraints.clear()
        loaded.constraints.forEach { constraints.add(ConstraintDraft(it.type, it.config)) }
        selectedActions.clear()
        actionConfigs.clear()
        actionEndBehaviors.clear()
        loaded.actions.forEach { action ->
            actionOptions.find { it.actionType == action.type }?.let { option ->
                selectedActions.add(option)
                actionConfigs[option.actionType] = action.config
                actionEndBehaviors[option.actionType] = action.endBehavior
            }
        }
        // Backward compatibility: the old global revert-on-exit toggle is now
        // expressed per action. Expand it into per-action REVERT behaviors so
        // existing tasks keep restoring exactly what they restored before.
        if (loaded.revertOnExit) {
            loaded.actions.forEach { action ->
                if (action.endBehavior == null && EndBehaviorCatalog.supportsRevert(action.type)) {
                    actionEndBehaviors[action.type] = EndBehavior(EndMode.REVERT)
                }
            }
        }
        selectedExitActions.clear()
        exitActionConfigs.clear()
        loaded.exitActions.forEach { action ->
            actionOptions.find { it.actionType == action.type }?.let { option ->
                selectedExitActions.add(option)
                exitActionConfigs[option.actionType] = action.config
            }
        }
    }
    /**
     * The builder's own savedStateHandle, stable for this destination: the
     * icon picker writes its result here, and the in-app map picker does the
     * same (plus the trigger index it was opened for). Reading it from the
     * navController instead would re-point at the top entry while a picker is
     * open and drop the result. The caller passes the entry's handle; fall
     * back to reading it once from the controller for standalone composition.
     */
    val stableSavedStateHandle = remember {
        savedStateHandle ?: navController.currentBackStackEntry?.savedStateHandle
    }

    /**
     * Opens the in-app map picker for a trigger. External maps apps are a
     * dead end for picking: modern Google Maps no longer handles ACTION_PICK
     * (and ACTION_VIEW never returns a point), so the builder now opens its
     * own OpenStreetMap screen where a crosshair + confirm button return the
     * exact coordinates via the shared savedStateHandle.
     */
    fun launchMapPicker(index: Int) {
        // Remember which trigger the picker was opened for. It lives in the
        // builder's own savedStateHandle (survives the navigation round-trip
        // and process death) so the returned point lands on the right row.
        stableSavedStateHandle?.set("map_picker_target", index)
        navController.navigate("map_picker")
    }

    // ── "Use my current location": silently enable location (privileged),
    // grab a fix, fill the coordinates, then restore the previous mode. When no
    // elevated runtime exists, opens the system location settings and resumes
    // on return — the whole flow needs no permission dialogs beyond the one-time
    // runtime location permission.
    var pendingUseLocationIndex by remember { mutableStateOf<Int?>(null) }
    // Toggled by the permission/settings launchers so their callbacks can resume
    // the locate flow without a forward reference to the local function below.
    var resumeLocationFill by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) resumeLocationFill = true
    }
    val locationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Back from the system location settings: retry the pending fill.
        resumeLocationFill = true
    }
    fun locateAndFill(index: Int) {
        if (index !in triggers.indices) return
        if (!LocationAccess.hasLocationPermission(context)) {
            pendingUseLocationIndex = index
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        scope.launch {
            // Everything below must never crash the builder: location services,
            // elevated shells and the single-shot fix can all throw on odd ROM
            // states, and an uncaught exception here would FC the whole app.
            try {
                val wasEnabled = LocationAccess.isLocationEnabled(context)
                val previousMode = LocationAccess.currentLocationMode(context)
                val enabled = if (wasEnabled) true else LocationAccess.enableLocationSilently(context)
                if (!enabled) {
                    // No privileged path: one tap in the system settings screen.
                    pendingUseLocationIndex = index
                    runCatching {
                        locationSettingsLauncher.launch(
                            Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        )
                    }
                    return@launch
                }
                val fix = LocationAccess.getCurrentLocation(context, 12_000)
                if (fix != null && index in triggers.indices) {
                    triggers[index] = triggers[index].copy(
                        config = triggers[index].config +
                            ("lat" to fix.latitude.toString()) +
                            ("lng" to fix.longitude.toString())
                    )
                } else {
                    scope.launch { snackbarHostState.showSnackbar(stringLocationFixFailed) }
                }
                if (!wasEnabled) LocationAccess.restoreLocationModeIfWeChanged(context, previousMode)
            } catch (t: Throwable) {
                Log.w("LocateFill", "Current-location fill failed", t)
                scope.launch { snackbarHostState.showSnackbar(stringLocationFixFailed) }
            }
        }
    }
    // Resumes a locate flow interrupted by the permission or settings screens.
    LaunchedEffect(resumeLocationFill) {
        if (resumeLocationFill) {
            resumeLocationFill = false
            pendingUseLocationIndex?.let { locateAndFill(it) }
        }
    }
    val scrollState = rememberScrollState()

    // Receive the icon picked in IconPickerScreen. The handle must stay bound
    // to THIS builder entry (see stableSavedStateHandle above).
    DisposableEffect(stableSavedStateHandle) {
        val observer = Observer<Int> { index ->
            selectedIconIndex = index
        }
        stableSavedStateHandle?.getLiveData<Int>("selected_icon")?.observeForever(observer)
        val locationObserver = Observer<String> { value ->
            val coords = value.split(',')
            val lat = coords.getOrNull(0)?.toDoubleOrNull()
            val lng = coords.getOrNull(1)?.toDoubleOrNull()
            val index = stableSavedStateHandle?.get<Int>("map_picker_target") ?: return@Observer
            if (lat != null && lng != null && index in triggers.indices) {
                triggers[index] = triggers[index].copy(
                    config = triggers[index].config + ("lat" to lat.toString()) + ("lng" to lng.toString())
                )
                stableSavedStateHandle?.set("map_picker_target", null)
            }
        }
        stableSavedStateHandle?.getLiveData<String>("picked_location")?.observeForever(locationObserver)
        onDispose {
            stableSavedStateHandle?.getLiveData<Int>("selected_icon")?.removeObserver(observer)
            stableSavedStateHandle?.getLiveData<String>("picked_location")?.removeObserver(locationObserver)
        }
    }

    // Live elevated-permission status (root/Shizuku): re-probe the action
    // cards every time the screen resumes — e.g. after returning from the
    // Magisk/KernelSU grant dialog or the Shizuku grant screen — so the
    // colour-coded badge reflects the freshly granted state without leaving
    // and re-opening the task.
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefreshTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshTick++
                // Coming back from the picker: the handle always holds the
                // latest pick even if the LiveData observer missed the event,
                // so re-apply it to keep the badge in sync.
                stableSavedStateHandle?.get<Int>("selected_icon")?.let {
                    if (it in NexaFlowIcons.all.indices) selectedIconIndex = it
                }
                // Same for a location picked on the in-app map.
                stableSavedStateHandle?.get<String>("picked_location")?.let { value ->
                    val coords = value.split(',')
                    val lat = coords.getOrNull(0)?.toDoubleOrNull()
                    val lng = coords.getOrNull(1)?.toDoubleOrNull()
                    val index = stableSavedStateHandle?.get<Int>("map_picker_target")
                    if (lat != null && lng != null && index != null && index in triggers.indices) {
                        triggers[index] = triggers[index].copy(
                            config = triggers[index].config + ("lat" to lat.toString()) + ("lng" to lng.toString())
                        )
                        stableSavedStateHandle?.set("map_picker_target", null)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isEditing = automationId != null

    val stringPermissionDenied = stringResource(R.string.permission_denied_hint)
    // Permission request currently waiting for the user to confirm the Samsung-style
    // explain screen. The system dialog only opens after the user taps Continue.
    var pendingPermissions by remember { mutableStateOf<Array<String>?>(null) }
    // Special permission (settings-screen) request awaiting the explain screen.
    var pendingSpecialPermission by remember { mutableStateOf<SpecialPermission?>(null) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Surface denied permissions so the user knows why the feature may not work.
        if (grants.values.any { !it }) {
            scope.launch {
                snackbarHostState.showSnackbar(stringPermissionDenied)
            }
        }
    }

    fun requestPermissions(permissions: Array<String>) {
        // Already-granted permissions need neither the explain screen nor the
        // system dialog, so skip straight past them on repeat visits.
        val allGranted = permissions.all {
            context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) return
        // Explain why the permission is needed BEFORE opening the system dialog.
        pendingPermissions = permissions
    }

    fun explainSpecialPermission(special: SpecialPermission) {
        // Explain why the permission is needed BEFORE opening its settings screen.
        pendingSpecialPermission = special
    }


    /** First SMS trigger, if any — its "reply" config is edited in the exit section. */
    val smsTriggerIndex = triggers.indexOfFirst { it.type == TriggerType.SMS }
    // Looked up defensively inside the handler so removing/retargeting the SMS
    // trigger mid-session can never hit a stale index.
    fun updateSmsReply(reply: String) {
        val idx = triggers.indexOfFirst { it.type == TriggerType.SMS }
        if (idx in triggers.indices) {
            triggers[idx] = triggers[idx].copy(
                config = triggers[idx].config + ("reply" to reply)
            )
        }
    }

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
            showSnackbar(stringNextNeedsTrigger)
            return
        }
        if (selectedActions.isEmpty()) {
            showSnackbar(stringNextNeedsAction)
            return
        }
        // The picker writes the selection into this entry's savedStateHandle.
        // Re-read it here as the source of truth: the LiveData observer may
        // miss the event under device recomposition timing, but the handle
        // itself always holds the latest pick.
        stableSavedStateHandle?.get<Int>("selected_icon")?.let {
            if (it in NexaFlowIcons.all.indices) selectedIconIndex = it
        }
        val builtTriggers = triggers.map { draft ->
            Trigger(draft.type, draft.config)
        }
        val actions = selectedActions.map {
            Action(
                type = it.actionType,
                config = actionConfigs[it.actionType] ?: emptyMap(),
                endBehavior = actionEndBehaviors[it.actionType]
            )
        }
        val builtConstraints = constraints.map { Constraint(it.type, it.config) }
        val exitActions = selectedExitActions.map { Action(it.actionType, exitActionConfigs[it.actionType] ?: emptyMap()) }
        viewModel.saveAutomation(
            name = name,
            icon = NexaFlowIcons.all[selectedIconIndex].first,
            triggers = builtTriggers,
            actions = actions,
            constraints = builtConstraints,
            exitActions = exitActions,
            // Unified end behavior: each action carries its own end behavior
            // (leave / restore / set value), so the global toggle stays off.
            // Runs are always immediate: the cooldown UI was removed entirely
            // and the engine gate is pinned to zero so every trigger fires at
            // once, no matter how often the event repeats.
            revertOnExit = false,
            cooldownSeconds = 0
        )
        // Aggressive permission flow: right after saving, request any missing
        // runtime permission through the system dialog immediately, and explain
        // the first missing special (settings-screen) permission — no detour.
        val missingRuntime = PermissionCatalog.allRuntimePermissions(builtTriggers, actions, exitActions)
            .filter {
                context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        if (missingRuntime.isNotEmpty()) {
            requestPermissions(missingRuntime.toTypedArray())
        } else {
            val missingSpecial = PermissionCatalog.allSpecialPermissions(builtTriggers, actions, exitActions)
                .firstOrNull { !PermissionShortcuts.isGranted(context, it) }
            if (missingSpecial != null) {
                explainSpecialPermission(missingSpecial)
            } else {
                // All runtime/special permissions satisfied: keep the monitoring
                // service alive in the background by requesting the battery
                // optimization exemption right away (system dialog, one tap).
                ElevatedAccessShortcuts.requestBatteryOptimizationExemption(context)
            }
        }
        if (closeAfterSave) {
            navController.popBackStack()
        } else {
            showSnackbar(stringSavedSuccessfully)
        }
    }

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = if (isEditing) stringResource(R.string.edit_task_title) else stringResource(R.string.builder_title),
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { navController.navigate("variables") }) {
                        Icon(imageVector = Icons.Filled.Functions, contentDescription = stringResource(R.string.variables_title))
                    }
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
                    onPickCalendar = { calendarPickerTarget = index },
                    onRequestPermission = { requestPermissions(it) },
                    onExplainSpecial = { explainSpecialPermission(it) },
                    refreshKey = permissionRefreshTick,
                    onPickFromMap = { launchMapPicker(index) },
                    onUseCurrentLocation = { locateAndFill(index) }
                )
            }

            // ── IF (constraints) ────────────────────────────────────
            SectionHeader(
                text = stringResource(R.string.section_constraints),
                trailing = {
                    IconButton(onClick = { showConstraintPicker = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_constraint)
                        )
                    }
                }
            )
            if (constraints.isEmpty()) {
                Text(
                    text = stringResource(R.string.constraints_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                constraints.forEachIndexed { index, draft ->
                    ConstraintEditorCard(
                        draft = draft,
                        index = index,
                        onConfigChange = { constraints[index] = it },
                        onRemove = { constraints.removeAt(index) }
                    )
                }
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
                        actionEndBehaviors.remove(option.actionType)
                    },
                    onPickApp = { appPickerTarget = "action:${option.actionType.name}" },
                    onRequestPermission = { requestPermissions(it) },
                    onExplainSpecial = { explainSpecialPermission(it) },
                    refreshKey = permissionRefreshTick,
                    context = context,
                    availableVariables = availableVariables,
                    automations = automations,
                    onPluginConfigure = {
                        configurePlugin(
                            actionConfigs[ActionType.PLUGIN_FIRE]?.get("package"),
                            actionConfigs[ActionType.PLUGIN_FIRE]?.get("receiver"),
                            actionConfigs[ActionType.PLUGIN_FIRE] ?: emptyMap()
                        )
                    }
                )
            }

            // ── When the task ends (one unified place, part of the task) ──
            // Single home for ALL end behavior: per-action end options (what
            // each action does when the condition stops being true) plus any
            // extra exit actions, the run cooldown and the SMS auto-reply.
            SectionHeader(
                text = stringResource(R.string.section_exit_behavior),
                trailing = {
                    IconButton(onClick = { showExitPicker = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_exit_action)
                        )
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // Per-action end options: every selected action decides what
                    // happens to it when the task's condition stops being true.
                    val endCapableActions = selectedActions.filter {
                        EndBehaviorCatalog.supportsEndBehavior(it.actionType)
                    }
                    if (endCapableActions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.exit_nothing_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.exit_per_action_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        endCapableActions.forEach { option ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconBadge(
                                    icon = option.icon,
                                    containerColor = option.color.copy(alpha = 0.15f),
                                    contentColor = option.color
                                )
                                Text(
                                    text = stringResource(option.titleRes),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            EndBehaviorEditor(
                                actionType = option.actionType,
                                behavior = actionEndBehaviors[option.actionType],
                                onBehaviorChange = { actionEndBehaviors[option.actionType] = it },
                                showLabel = false
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (selectedExitActions.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = stringResource(R.string.exit_extra_actions_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
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
                                onPickApp = { appPickerTarget = "exit:${option.actionType.name}" },
                                availableVariables = availableVariables,
                                automations = automations
                            )
                        }
                    }
                    // Auto-reply belongs with the exit behaviour: it is not an action,
                    // so it lives here instead of inside the SMS trigger editor.
                    // Note: the reply is still sent by SmsReceiver when the message
                    // arrives (not when the task ends) — this field only configures it.
                    if (smsTriggerIndex in triggers.indices && triggers[smsTriggerIndex].type == TriggerType.SMS) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = stringResource(R.string.exit_auto_reply_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = triggers[smsTriggerIndex].config["reply"] ?: "",
                            onValueChange = { updateSmsReply(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.sms_reply)) },
                            placeholder = { Text(text = stringResource(R.string.sms_reply_hint)) },
                            singleLine = true
                        )
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

    if (showConstraintPicker) {
        ConstraintTypePickerDialog(
            onPick = { type ->
                constraints.add(ConstraintDraft(type, defaultConstraintConfig(type)))
                showConstraintPicker = false
            },
            onDismiss = { showConstraintPicker = false }
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
                // A picked plugin action immediately asks which plugin to use.
                if (picked.any { it.actionType == ActionType.PLUGIN_FIRE }) {
                    pluginPickerTarget = true
                }
            },
            onDismiss = { showActionPicker = false }
        )
    }

    if (pluginPickerTarget) {
        PluginPickerDialog(
            plugins = plugins,
            onRefresh = { viewModel.refreshPlugins() },
            onPick = { plugin ->
                pluginPickerTarget = false
                configurePlugin(plugin.packageName, plugin.receiverClass, emptyMap())
            },
            onDismiss = {
                pluginPickerTarget = false
                // Dropping the picker without configuring removes the stub action.
                if ((actionConfigs[ActionType.PLUGIN_FIRE] ?: emptyMap()).isEmpty()) {
                    selectedActions.removeAll { it.actionType == ActionType.PLUGIN_FIRE }
                }
            }
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

    calendarPickerTarget?.let { index ->
        if (index in triggers.indices) {
            CalendarPickerDialog(
                onPick = { calendar ->
                    triggers[index] = triggers[index].copy(
                        config = mapOf(
                            "calendar" to calendar.name,
                            "contains" to (triggers[index].config["contains"] ?: ""),
                            "event" to (triggers[index].config["event"] ?: "EVENT_START"),
                            "beforeMinutes" to (triggers[index].config["beforeMinutes"] ?: "0")
                        )
                    )
                    calendarPickerTarget = null
                },
                onDismiss = { calendarPickerTarget = null }
            )
        } else {
            calendarPickerTarget = null
        }
    }

    appPickerTarget?.let { target ->
        val exitActionName = target.removePrefix("exit:")
        if (exitActionName != target) {
            val exitType = ActionType.entries.firstOrNull { it.name == exitActionName }
            if (exitType == ActionType.SYSTEM_OPEN_APP) {
                val existing = exitActionConfigs[ActionType.SYSTEM_OPEN_APP]
                val pre = (existing?.get("packages") ?: existing?.get("package") ?: "")
                    .split(',').map { it.trim() }.filter { it.isNotEmpty() }
                AppPickerDialog(
                    onPickSingle = { app ->
                        val current = pre + app.packageName
                        val merged = current.distinct()
                        exitActionConfigs[ActionType.SYSTEM_OPEN_APP] = mapOf("packages" to merged.joinToString(","))
                        appPickerTarget = null
                    },
                    onPickMultiple = { packages ->
                        exitActionConfigs[ActionType.SYSTEM_OPEN_APP] = mapOf("packages" to packages.joinToString(",") { it.packageName })
                        appPickerTarget = null
                    },
                    multiSelect = true,
                    preSelectedPackages = pre,
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
            val triggerPackages = (triggers[triggerIndex].config["packages"] ?: triggers[triggerIndex].config["package"] ?: "")
                .split(',').map { it.trim() }.filter { it.isNotEmpty() }
            AppPickerDialog(
                onPickSingle = { app ->
                    val merged = (triggerPackages + app.packageName).distinct()
                    val current = triggers[triggerIndex]
                    triggers[triggerIndex] = current.copy(
                        config = mapOf("packages" to merged.joinToString(","))
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
                preSelectedPackages = triggerPackages,
                onDismiss = { appPickerTarget = null }
            )
        } else {
            val actionTypeName = target.removePrefix("action:")
            val isOpenApp = actionTypeName == ActionType.SYSTEM_OPEN_APP.name
            val singlePickType = when (actionTypeName) {
                ActionType.APPLICATION_CLOSE_APP.name -> ActionType.APPLICATION_CLOSE_APP
                ActionType.APPLICATION_OPEN_APP_SETTINGS.name -> ActionType.APPLICATION_OPEN_APP_SETTINGS
                ActionType.SYSTEM_BLOCK_NOTIFICATION.name -> ActionType.SYSTEM_BLOCK_NOTIFICATION
                ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS.name -> ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS
                else -> null
            }
            when {
                isOpenApp -> {
                    val existing = actionConfigs[ActionType.SYSTEM_OPEN_APP]
                    val pre = (existing?.get("packages") ?: existing?.get("package") ?: "")
                        .split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    AppPickerDialog(
                        onPickSingle = { app ->
                            val merged = (pre + app.packageName).distinct()
                            actionConfigs[ActionType.SYSTEM_OPEN_APP] = mapOf("packages" to merged.joinToString(","))
                            appPickerTarget = null
                        },
                        onPickMultiple = { packages ->
                            actionConfigs[ActionType.SYSTEM_OPEN_APP] = mapOf("packages" to packages.joinToString(",") { it.packageName })
                            appPickerTarget = null
                        },
                        multiSelect = true,
                        preSelectedPackages = pre,
                        onDismiss = { appPickerTarget = null }
                    )
                }
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

    // Samsung-style explain screens shown before granting a permission.
    pendingPermissions?.let { permissions ->
        PermissionExplainDialog(
            info = remember(permissions) { permissionExplainInfo(permissions) },
            onContinue = {
                pendingPermissions = null
                permissionLauncher.launch(permissions)
            },
            onDismiss = { pendingPermissions = null }
        )
    }
    pendingSpecialPermission?.let { special ->
        PermissionExplainDialog(
            info = specialPermissionExplainInfo(special),
            onContinue = {
                pendingSpecialPermission = null
                PermissionShortcuts.openSpecial(context, special)
            },
            onDismiss = { pendingSpecialPermission = null }
        )
    }
}
