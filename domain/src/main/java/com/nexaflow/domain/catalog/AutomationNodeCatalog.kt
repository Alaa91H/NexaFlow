package com.nexaflow.domain.catalog

import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType

/**
 * Canonical semantic inventory for every persisted trigger/action enum entry.
 *
 * This is intentionally additive in the first migration:
 * - persistence still uses TriggerType / ActionType;
 * - advanced/dynamic config keys remain accepted;
 * - presentation resources/icons remain in feature modules;
 * - capability execution stays in the existing compatibility/requirement layers.
 *
 * The strict family maps are exhaustive by construction. Adding a new enum
 * entry without classifying it makes catalog initialization fail in tests/CI.
 */
object AutomationNodeCatalog {

    val triggerDefinitions: List<AutomationNodeDefinition> =
        TriggerType.entries.map(::buildTriggerDefinition)

    val actionDefinitions: List<AutomationNodeDefinition> =
        ActionType.entries.map(::buildActionDefinition)

    val all: List<AutomationNodeDefinition> = triggerDefinitions + actionDefinitions

    private val byId: Map<String, AutomationNodeDefinition> = all.associateBy { it.id }
    private val triggerByType: Map<TriggerType, AutomationNodeDefinition> =
        TriggerType.entries.zip(triggerDefinitions).toMap()
    private val actionByType: Map<ActionType, AutomationNodeDefinition> =
        ActionType.entries.zip(actionDefinitions).toMap()

    init {
        require(all.map { it.id }.distinct().size == all.size) {
            "Automation node ids must be globally unique"
        }
        require(byId.size == all.size) {
            "Automation node id index lost entries"
        }
        require(triggerByType.keys == TriggerType.entries.toSet()) {
            "Trigger catalog coverage is incomplete"
        }
        require(actionByType.keys == ActionType.entries.toSet()) {
            "Action catalog coverage is incomplete"
        }
    }

    fun definitionFor(type: TriggerType): AutomationNodeDefinition =
        triggerByType.getValue(type)

    fun definitionFor(type: ActionType): AutomationNodeDefinition =
        actionByType.getValue(type)

    fun definitionFor(id: String): AutomationNodeDefinition? = byId[id]

    val discoverableTriggers: List<AutomationNodeDefinition>
        get() = triggerDefinitions.filter { it.visibility == AutomationNodeVisibility.DISCOVERABLE }

    val discoverableActions: List<AutomationNodeDefinition>
        get() = actionDefinitions.filter { it.visibility == AutomationNodeVisibility.DISCOVERABLE }

    private fun buildTriggerDefinition(type: TriggerType): AutomationNodeDefinition {
        val id = "trigger.${type.name.lowercase()}"
        return AutomationNodeDefinition(
            id = id,
            kind = AutomationNodeKind.TRIGGER,
            family = triggerFamilies.getValue(type),
            legacyTypeName = type.name,
            visibility = when (type) {
                TriggerType.CONNECTIVITY -> AutomationNodeVisibility.LEGACY_HIDDEN
                TriggerType.PLUGIN_EVENT -> AutomationNodeVisibility.CONFIGURATION_ONLY
                else -> AutomationNodeVisibility.DISCOVERABLE
            },
            configuration = TriggerNodeSchemas.schemaFor(type)
        )
    }

    private fun buildActionDefinition(type: ActionType): AutomationNodeDefinition {
        val id = "action.${type.name.lowercase()}"
        return AutomationNodeDefinition(
            id = id,
            kind = AutomationNodeKind.ACTION,
            family = actionFamilies.getValue(type),
            legacyTypeName = type.name,
            configuration = ActionNodeSchemas.schemaFor(type)
        )
    }

    private val triggerFamilies: Map<TriggerType, AutomationNodeFamily> by lazy { strictFamilyMap(
        expected = TriggerType.entries.toSet(),
        AutomationNodeFamily.SCHEDULE to listOf(
            TriggerType.TIME,
            TriggerType.CALENDAR,
            TriggerType.TIMEZONE_CHANGED,
            TriggerType.ALARM_SET_CHANGED
        ),
        AutomationNodeFamily.BATTERY to listOf(
            TriggerType.BATTERY,
            TriggerType.CHARGER,
            TriggerType.POWER_SAVER,
            TriggerType.BATTERY_TEMPERATURE
        ),
        AutomationNodeFamily.APPLICATIONS to listOf(
            TriggerType.APPLICATION,
            TriggerType.APP_INSTALLED
        ),
        AutomationNodeFamily.DEVICE to listOf(
            TriggerType.DEVICE,
            TriggerType.SENSOR,
            TriggerType.DARK_MODE,
            TriggerType.BRIGHTNESS_LEVEL,
            TriggerType.STORAGE_LOW,
            TriggerType.AUTO_ROTATE,
            TriggerType.DEVICE_LOCKED,
            TriggerType.SCREEN_ROTATION_STATE,
            TriggerType.USB_CONNECTED,
            TriggerType.HDMI_CONNECTED,
            TriggerType.STAY_AWAKE_STATE,
            TriggerType.AUTO_BRIGHTNESS_STATE,
            TriggerType.SCREEN_TIMEOUT_CHANGED,
            TriggerType.BOOT_COMPLETED
        ),
        AutomationNodeFamily.CONNECTIVITY to listOf(
            TriggerType.CONNECTIVITY,
            TriggerType.WIFI_CONNECTED,
            TriggerType.MOBILE_DATA_CONNECTED,
            TriggerType.HOTSPOT,
            TriggerType.BLUETOOTH_DEVICE,
            TriggerType.NETWORK_MODE,
            TriggerType.AIRPLANE_MODE,
            TriggerType.BLUETOOTH_STATE,
            TriggerType.DATA_SAVER_STATE,
            TriggerType.WIFI_STATE,
            TriggerType.NFC_STATE,
            TriggerType.WIFI_SIGNAL_STRENGTH,
            TriggerType.CELL_SIGNAL_STRENGTH,
            TriggerType.ETHERNET_CONNECTED,
            TriggerType.VPN_CONNECTED,
            TriggerType.DATA_ROAMING_STATE,
            TriggerType.NFC_TAG_SCANNED
        ),
        AutomationNodeFamily.LOCATION to listOf(
            TriggerType.LOCATION,
            TriggerType.LOCATION_STATE
        ),
        AutomationNodeFamily.COMMUNICATION to listOf(
            TriggerType.SMS,
            TriggerType.CALL_STATE,
            TriggerType.INCOMING_CALL
        ),
        AutomationNodeFamily.SOUND to listOf(
            TriggerType.RINGER_MODE,
            TriggerType.HEADPHONE,
            TriggerType.VOLUME_CHANGED,
            TriggerType.DND_STATE
        ),
        AutomationNodeFamily.MEDIA to listOf(
            TriggerType.MEDIA_PLAYING
        ),
        AutomationNodeFamily.NOTIFICATIONS to listOf(
            TriggerType.NOTIFICATION
        ),
        AutomationNodeFamily.NETWORK to listOf(
            TriggerType.WEBHOOK
        ),
        AutomationNodeFamily.ROM to listOf(
            TriggerType.ROM_SETTING
        ),
        AutomationNodeFamily.DATA to listOf(
            TriggerType.CLIPBOARD_CHANGED
        ),
        AutomationNodeFamily.PLUGINS to listOf(
            TriggerType.PLUGIN_EVENT
        )
    ) }

    private val actionFamilies: Map<ActionType, AutomationNodeFamily> by lazy { strictFamilyMap(
        expected = ActionType.entries.toSet(),
        AutomationNodeFamily.DISPLAY to listOf(
            ActionType.SYSTEM_BRIGHTNESS,
            ActionType.SYSTEM_SCREEN_ROTATION,
            ActionType.SYSTEM_SCREEN_TIMEOUT,
            ActionType.SYSTEM_STAY_AWAKE,
            ActionType.SYSTEM_AUTO_BRIGHTNESS,
            ActionType.SYSTEM_DARK_MODE,
            ActionType.SYSTEM_ANIMATIONS,
            ActionType.SYSTEM_WAKE_SCREEN,
            ActionType.SYSTEM_COLOR_INVERSION,
            ActionType.SYSTEM_GRAYSCALE,
            ActionType.SYSTEM_EXTRA_DIM,
            ActionType.SYSTEM_NIGHT_LIGHT,
            ActionType.SYSTEM_FONT_SCALE,
            ActionType.SYSTEM_DISPLAY_DENSITY,
            ActionType.SYSTEM_SCREENSAVER,
            ActionType.SYSTEM_ALWAYS_ON_DISPLAY,
            ActionType.SYSTEM_SHOW_TAPS,
            ActionType.SYSTEM_POINTER_LOCATION,
            ActionType.SYSTEM_OPEN_DISPLAY_SETTINGS,
            ActionType.SYSTEM_TOGGLE_PIP,
            ActionType.SYSTEM_SCREENSAVER_TIMEOUT,
            ActionType.SYSTEM_POINTER_SPEED
        ),
        AutomationNodeFamily.SOUND to listOf(
            ActionType.SYSTEM_VOLUME,
            ActionType.SYSTEM_STREAM_VOLUME,
            ActionType.SYSTEM_DND,
            ActionType.SYSTEM_RINGER_MODE,
            ActionType.SYSTEM_RING_VOLUME,
            ActionType.SYSTEM_SET_RINGTONE,
            ActionType.SYSTEM_VIBRATE,
            ActionType.SYSTEM_HAPTIC_FEEDBACK,
            ActionType.SYSTEM_SOUND_EFFECTS,
            ActionType.SYSTEM_HAPTIC_INTENSITY,
            ActionType.SYSTEM_CAMERA_SHUTTER_SOUND,
            ActionType.SYSTEM_OPEN_SOUND_SETTINGS,
            ActionType.SYSTEM_VIBRATE_PATTERN,
            ActionType.SYSTEM_SET_NOTIFICATION_TONE,
            ActionType.SYSTEM_CALL_VIBRATION
        ),
        AutomationNodeFamily.CONNECTIVITY to listOf(
            ActionType.SYSTEM_WIFI,
            ActionType.SYSTEM_BLUETOOTH,
            ActionType.SYSTEM_AIRPLANE_MODE,
            ActionType.SYSTEM_MOBILE_DATA,
            ActionType.SYSTEM_NETWORK_MODE,
            ActionType.SYSTEM_HOTSPOT,
            ActionType.SYSTEM_NFC,
            ActionType.SYSTEM_DATA_SAVER,
            ActionType.SYSTEM_WIFI_SLEEP_POLICY,
            ActionType.SYSTEM_BLUETOOTH_DISCOVERABILITY,
            ActionType.SYSTEM_WIFI_SCANNING,
            ActionType.SYSTEM_OPEN_WIFI_SETTINGS,
            ActionType.SYSTEM_OPEN_BLUETOOTH_SETTINGS,
            ActionType.SYSTEM_OPEN_DATA_USAGE_SETTINGS,
            ActionType.SYSTEM_WIFI_CONNECT,
            ActionType.SYSTEM_WIFI_FORGET,
            ActionType.SYSTEM_DATA_ROAMING,
            ActionType.SYSTEM_OPEN_NETWORK_SETTINGS,
            ActionType.SYSTEM_OPEN_NFC_SETTINGS,
            ActionType.SYSTEM_OPEN_DATA_SAVER_SETTINGS,
            ActionType.SYSTEM_OPEN_CAST_SETTINGS,
            ActionType.SYSTEM_OPEN_VPN_SETTINGS,
            ActionType.SYSTEM_OPEN_AIRPLANE_MODE_SETTINGS,
            ActionType.SYSTEM_BLUETOOTH_SCAN,
            ActionType.SYSTEM_WIFI_SCAN_NOW
        ),
        AutomationNodeFamily.LOCATION to listOf(
            ActionType.SYSTEM_LOCATION,
            ActionType.SYSTEM_LOCATION_MODE,
            ActionType.SYSTEM_OPEN_LOCATION_SETTINGS,
            ActionType.SYSTEM_OPEN_MAPS
        ),
        AutomationNodeFamily.MEDIA to listOf(
            ActionType.SYSTEM_MEDIA_PLAY_PAUSE,
            ActionType.SYSTEM_MEDIA_NEXT,
            ActionType.SYSTEM_MEDIA_PREVIOUS,
            ActionType.SYSTEM_MEDIA_STOP,
            ActionType.SYSTEM_MEDIA_FAST_FORWARD,
            ActionType.SYSTEM_MEDIA_REWIND,
            ActionType.SYSTEM_MEDIA_PLAY_FROM_SEARCH
        ),
        AutomationNodeFamily.NOTIFICATIONS to listOf(
            ActionType.SYSTEM_SEND_NOTIFICATION,
            ActionType.SYSTEM_BLOCK_NOTIFICATION,
            ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS,
            ActionType.SYSTEM_CLEAR_NOTIFICATIONS,
            ActionType.SYSTEM_SEND_REMINDER,
            ActionType.SYSTEM_OPEN_NOTIFICATIONS,
            ActionType.SYSTEM_EXPAND_STATUS_BAR,
            ActionType.SYSTEM_COLLAPSE_STATUS_BAR,
            ActionType.SYSTEM_TOAST,
            ActionType.SYSTEM_ALERT,
            ActionType.SYSTEM_STATUS_BAR_TOGGLE,
            ActionType.SYSTEM_OPEN_NOTIFICATION_SETTINGS
        ),
        AutomationNodeFamily.COMMUNICATION to listOf(
            ActionType.SYSTEM_SEND_SMS,
            ActionType.SYSTEM_DIAL_NUMBER,
            ActionType.SYSTEM_SEND_EMAIL,
            ActionType.CALL_BLOCK,
            ActionType.CALL_SILENCE
        ),
        AutomationNodeFamily.APPLICATIONS to listOf(
            ActionType.SYSTEM_OPEN_APP,
            ActionType.SYSTEM_OPEN_RECENTS,
            ActionType.SYSTEM_GO_HOME,
            ActionType.APPLICATION_OPEN_APP_SETTINGS,
            ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS,
            ActionType.SYSTEM_OPEN_PLAY_UPDATES,
            ActionType.SYSTEM_OPEN_DEVICE_STORE,
            ActionType.APPLICATION_LAUNCH_APP,
            ActionType.APPLICATION_CLOSE_APP,
            ActionType.SYSTEM_FORCE_STOP_APP,
            ActionType.SYSTEM_CLEAR_APP_DATA,
            ActionType.SYSTEM_OPEN_APP_SETTINGS_LIST,
            ActionType.SYSTEM_OPEN_CAMERA,
            ActionType.SYSTEM_OPEN_PLAY_STORE_APP,
            ActionType.SYSTEM_OPEN_APP_DRAWER,
            ActionType.SYSTEM_INSTALL_APK,
            ActionType.SYSTEM_UNINSTALL_APP,
            ActionType.SYSTEM_DISABLE_APP,
            ActionType.SYSTEM_ENABLE_APP,
            ActionType.SYSTEM_OPEN_CONTACTS,
            ActionType.SYSTEM_OPEN_DEFAULT_APPS_SETTINGS
        ),
        AutomationNodeFamily.BATTERY to listOf(
            ActionType.SYSTEM_POWER_SAVER,
            ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD,
            ActionType.SYSTEM_CHARGING_LIMIT,
            ActionType.SYSTEM_CHARGING_FEEDBACK,
            ActionType.SYSTEM_ADAPTIVE_BATTERY,
            ActionType.SYSTEM_OPEN_BATTERY_SETTINGS,
            ActionType.BATTERY_ALERTS,
            ActionType.BATTERY_CHARGING_NOTIFICATIONS
        ),
        AutomationNodeFamily.SCHEDULE to listOf(
            ActionType.SYSTEM_SET_ALARM,
            ActionType.SYSTEM_SET_TIMER,
            ActionType.SYSTEM_AUTO_TIME,
            ActionType.SYSTEM_AUTO_TIMEZONE,
            ActionType.SYSTEM_OPEN_DATE_SETTINGS,
            ActionType.SYSTEM_SET_TIMEZONE
        ),
        AutomationNodeFamily.NETWORK to listOf(
            ActionType.SYSTEM_OPEN_URL,
            ActionType.SYSTEM_PRIVATE_DNS,
            ActionType.SYSTEM_HTTP_REQUEST
        ),
        AutomationNodeFamily.FILES to listOf(
            ActionType.SYSTEM_OPEN_STORAGE_SETTINGS
        ),
        AutomationNodeFamily.DATA to listOf(
            ActionType.SYSTEM_CLIPBOARD_SET,
            ActionType.SYSTEM_PASTE,
            ActionType.DATA_TEXT,
            ActionType.DATA_ENCODING,
            ActionType.DATA_HASH,
            ActionType.DATA_RANDOM,
            ActionType.DATA_MATH,
            ActionType.DATA_DATE_TIME,
            ActionType.DATA_JSON,
            ActionType.DATA_ARRAY
        ),
        AutomationNodeFamily.FLOW to listOf(
            ActionType.SYSTEM_WAIT
        ),
        AutomationNodeFamily.ROM to listOf(
            ActionType.ROM_CUSTOM_SETTING,
            ActionType.ROM_QS_TILES,
            ActionType.ROM_STATUS_BAR,
            ActionType.ROM_LOCKSCREEN,
            ActionType.ROM_NAVIGATION,
            ActionType.ROM_THEME,
            ActionType.ROM_AMBIENT_AOD,
            ActionType.ROM_NOTIFICATIONS,
            ActionType.ROM_BATCH
        ),
        AutomationNodeFamily.PLUGINS to listOf(
            ActionType.PLUGIN_FIRE
        ),
        AutomationNodeFamily.DEVELOPER to listOf(
            ActionType.ADVANCED_SHIZUKU,
            ActionType.ADVANCED_ROOT,
            ActionType.SYSTEM_SET_SETTING,
            ActionType.SYSTEM_INPUT_TEXT,
            ActionType.SYSTEM_KEY_EVENT,
            ActionType.SYSTEM_INPUT_TAP,
            ActionType.SYSTEM_INPUT_SWIPE,
            ActionType.SYSTEM_OPEN_DEVELOPER_SETTINGS
        ),
        AutomationNodeFamily.DEVICE to listOf(
            ActionType.SYSTEM_FLASHLIGHT,
            ActionType.SYSTEM_LOCK_SCREEN,
            ActionType.SYSTEM_SCREENSHOT
        ),
        AutomationNodeFamily.SYSTEM to listOf(
            ActionType.SYSTEM_OPEN_SETTINGS,
            ActionType.SYSTEM_OPEN_QUICK_SETTINGS,
            ActionType.SYSTEM_OPEN_SYSTEM_UPDATE_SETTINGS,
            ActionType.SYSTEM_OPEN_SECURITY_SETTINGS,
            ActionType.SYSTEM_OPEN_ACCESSIBILITY_SETTINGS,
            ActionType.SYSTEM_OPEN_ABOUT_PHONE,
            ActionType.SYSTEM_REBOOT,
            ActionType.SYSTEM_SHUTDOWN,
            ActionType.SYSTEM_RESTART_SYSTEM_UI,
            ActionType.SYSTEM_SOFT_RESTART,
            ActionType.SYSTEM_OPEN_PRIVACY_SETTINGS,
            ActionType.SYSTEM_OPEN_INPUT_METHOD_SETTINGS,
            ActionType.SYSTEM_OPEN_PRINT_SETTINGS,
            ActionType.SYSTEM_OPEN_DEVICE_ADMIN_SETTINGS,
            ActionType.SYSTEM_OPEN_USAGE_ACCESS_SETTINGS
        )
    ) }

    private fun <T> strictFamilyMap(
        expected: Set<T>,
        vararg groups: Pair<AutomationNodeFamily, List<T>>
    ): Map<T, AutomationNodeFamily> {
        val result = linkedMapOf<T, AutomationNodeFamily>()
        groups.forEach { (family, entries) ->
            entries.forEach { entry ->
                require(result.put(entry, family) == null) {
                    "Catalog entry '$entry' is assigned to more than one family"
                }
            }
        }

        val missing = expected - result.keys
        val unexpected = result.keys - expected
        require(missing.isEmpty() && unexpected.isEmpty()) {
            "Catalog family coverage mismatch. Missing=$missing, unexpected=$unexpected"
        }
        return result
    }
}
