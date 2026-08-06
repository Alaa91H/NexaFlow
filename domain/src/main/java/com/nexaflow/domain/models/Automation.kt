package com.nexaflow.domain.models

data class Automation(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val iconColor: Long,
    val backgroundColor: Long,
    val category: String,
    val priority: Int,
    val enabled: Boolean,
    val triggers: List<Trigger>,
    val actions: List<Action>,
    /** Actions executed when the task's condition stops being true. */
    val exitActions: List<Action> = emptyList(),
    /** When true, exit restores the device to its pre-run state instead of exitActions. */
    val revertOnExit: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

data class Trigger(
    val type: TriggerType,
    val config: Map<String, String>
)

enum class TriggerType {
    TIME,
    BATTERY,
    APPLICATION,
    DEVICE,
    CONNECTIVITY,
    LOCATION,
    SMS,
    BLUETOOTH_DEVICE,
    RINGER_MODE,
    NOTIFICATION,
    CALENDAR
}

data class Action(
    val type: ActionType,
    val config: Map<String, String>
)

enum class ActionType {
    SYSTEM_BRIGHTNESS,
    SYSTEM_VOLUME,
    SYSTEM_STREAM_VOLUME,
    SYSTEM_DND,
    SYSTEM_SCREEN_ROTATION,
    SYSTEM_OPEN_APP,
    SYSTEM_SEND_NOTIFICATION,
    SYSTEM_BLOCK_NOTIFICATION,
    SYSTEM_CLEAR_APP_NOTIFICATIONS,
    SYSTEM_WIFI,
    SYSTEM_BLUETOOTH,
    SYSTEM_FLASHLIGHT,
    SYSTEM_AIRPLANE_MODE,
    SYSTEM_MEDIA_PLAY_PAUSE,
    SYSTEM_MEDIA_NEXT,
    SYSTEM_MEDIA_PREVIOUS,
    SYSTEM_OPEN_URL,
    SYSTEM_CLEAR_NOTIFICATIONS,
    SYSTEM_EXPAND_STATUS_BAR,
    SYSTEM_COLLAPSE_STATUS_BAR,
    SYSTEM_SCREEN_TIMEOUT,
    SYSTEM_STAY_AWAKE,
    SYSTEM_AUTO_BRIGHTNESS,
    SYSTEM_RINGER_MODE,
    SYSTEM_MOBILE_DATA,
    SYSTEM_HOTSPOT,
    SYSTEM_NFC,
    SYSTEM_POWER_SAVER,
    SYSTEM_ANIMATIONS,
    SYSTEM_LOCK_SCREEN,
    SYSTEM_SET_ALARM,
    SYSTEM_DARK_MODE,
    SYSTEM_OPEN_RECENTS,
    SYSTEM_GO_HOME,
    APPLICATION_OPEN_APP_SETTINGS,
    SYSTEM_RING_VOLUME,
    SYSTEM_LOCATION,
    SYSTEM_OPEN_PLAY_UPDATES,
    SYSTEM_OPEN_GALAXY_STORE,
    SYSTEM_SEND_SMS,
    SYSTEM_SEND_REMINDER,
    SYSTEM_OPEN_SETTINGS,
    SYSTEM_WAIT,
    BATTERY_ALERTS,
    BATTERY_CHARGING_NOTIFICATIONS,
    APPLICATION_LAUNCH_APP,
    APPLICATION_CLOSE_APP,
    ADVANCED_SHIZUKU,
    ADVANCED_ROOT
}
