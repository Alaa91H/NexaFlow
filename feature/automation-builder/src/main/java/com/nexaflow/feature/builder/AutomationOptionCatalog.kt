package com.nexaflow.feature.builder

import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType

/**
 * Progressive-disclosure metadata for product discovery.
 *
 * Compatibility remains owned by CompatibilityGate. This catalog only decides
 * how an already-compatible option is presented: the normal family browser
 * or the explicit advanced surface.
 */
internal enum class OptionTier {
    BROWSE,
    ADVANCED,
}

internal object AutomationOptionCatalog {

    private val advancedTriggers: Set<TriggerType> = setOf(
        TriggerType.WEBHOOK,
        TriggerType.ROM_SETTING,
        TriggerType.SENSOR,
        TriggerType.WIFI_SIGNAL_STRENGTH,
        TriggerType.CELL_SIGNAL_STRENGTH,
        TriggerType.NFC_TAG_SCANNED,
        TriggerType.CLIPBOARD_CHANGED,
        TriggerType.PLUGIN_EVENT,
        TriggerType.WEAR_EVENT,
    )

    private val advancedActions: Set<ActionType> = buildSet {
        add(ActionType.SYSTEM_HTTP_REQUEST)
        add(ActionType.SYSTEM_SET_SETTING)
        add(ActionType.SYSTEM_INPUT_TEXT)
        add(ActionType.SYSTEM_KEY_EVENT)
        add(ActionType.SYSTEM_INPUT_TAP)
        add(ActionType.SYSTEM_INPUT_SWIPE)
        add(ActionType.ADVANCED_SHIZUKU)
        add(ActionType.ADVANCED_ROOT)
        add(ActionType.PLUGIN_FIRE)
        add(ActionType.SYSTEM_INSTALL_APK)
        add(ActionType.SYSTEM_REBOOT)
        add(ActionType.SYSTEM_SHUTDOWN)
        add(ActionType.SYSTEM_SOFT_RESTART)
        add(ActionType.SYSTEM_RESTART_SYSTEM_UI)
        addAll(
            listOf(
                ActionType.DATA_TEXT,
                ActionType.DATA_ENCODING,
                ActionType.DATA_HASH,
                ActionType.DATA_RANDOM,
                ActionType.DATA_MATH,
                ActionType.DATA_DATE_TIME,
                ActionType.DATA_JSON,
                ActionType.DATA_ARRAY,
                ActionType.ROM_CUSTOM_SETTING,
                ActionType.ROM_QS_TILES,
                ActionType.ROM_STATUS_BAR,
                ActionType.ROM_LOCKSCREEN,
                ActionType.ROM_NAVIGATION,
                ActionType.ROM_THEME,
                ActionType.ROM_AMBIENT_AOD,
                ActionType.ROM_NOTIFICATIONS,
                ActionType.ROM_BATCH,
            ),
        )
    }

    fun tierFor(type: TriggerType): OptionTier =
        if (type in advancedTriggers) OptionTier.ADVANCED else OptionTier.BROWSE

    fun tierFor(type: ActionType): OptionTier =
        if (type in advancedActions) OptionTier.ADVANCED else OptionTier.BROWSE

    /**
     * Stable order retained for routines/templates that want a deterministic
     * suggested-action sequence. It is not a second source of compatibility.
     */
    internal val recurringActionOrder = listOf(
        ActionType.SYSTEM_MEDIA_PLAY_PAUSE,
        ActionType.SYSTEM_MEDIA_PLAY_FROM_SEARCH,
        ActionType.SYSTEM_STREAM_VOLUME,
        ActionType.SYSTEM_DND,
        ActionType.SYSTEM_RINGER_MODE,
        ActionType.SYSTEM_WIFI,
        ActionType.SYSTEM_BLUETOOTH,
        ActionType.SYSTEM_LOCATION,
        ActionType.APPLICATION_LAUNCH_APP,
        ActionType.SYSTEM_OPEN_APP,
        ActionType.SYSTEM_SET_ALARM,
        ActionType.SYSTEM_SET_TIMER,
        ActionType.SYSTEM_SEND_NOTIFICATION,
        ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS,
        ActionType.SYSTEM_OPEN_PLAY_UPDATES,
        ActionType.SYSTEM_OPEN_DEVICE_STORE,
        ActionType.SYSTEM_OPEN_SYSTEM_UPDATE_SETTINGS,
    )
}
