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
    val conditions: List<Condition>,
    val actions: List<Action>,
    val createdAt: Long,
    val updatedAt: Long
)

data class Trigger(
    val type: TriggerType,
    val config: Map<String, String>
)

enum class TriggerType {
    TIME,
    APPLICATION,
    DEVICE,
    CONNECTIVITY,
    LOCATION
}

data class Condition(
    val type: ConditionType,
    val config: Map<String, String>,
    val nestedConditions: List<Condition>? = null
)

enum class ConditionType {
    AND,
    OR,
    NOT,
    BATTERY_PERCENTAGE,
    TIME_RANGE,
    // Add more specific conditions as needed
}

data class Action(
    val type: ActionType,
    val config: Map<String, String>
)

enum class ActionType {
    SYSTEM_BRIGHTNESS,
    SYSTEM_VOLUME,
    SYSTEM_DND,
    SYSTEM_SCREEN_ROTATION,
    SYSTEM_OPEN_APP,
    SYSTEM_SEND_NOTIFICATION,
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
    BATTERY_ALERTS,
    BATTERY_CHARGING_NOTIFICATIONS,
    APPLICATION_LAUNCH_APP,
    APPLICATION_CLOSE_APP,
    ADVANCED_SHIZUKU,
    ADVANCED_ROOT
}
