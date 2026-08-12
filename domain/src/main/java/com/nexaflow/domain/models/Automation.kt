package com.nexaflow.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * A user-defined task. Treated as immutable (Compose @Immutable contract):
 * never mutate an instance or its nested config maps after construction —
 * replace via copy() instead.
 */
@Immutable
@Serializable
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
    /**
     * Gate checks (MacroDroid-style constraints) that must ALL pass before the
     * task's actions run. When any fails, the run is skipped.
     */
    val constraints: List<Constraint> = emptyList(),
    /** Actions executed when the task's condition stops being true. */
    val exitActions: List<Action> = emptyList(),
    /** When true, exit restores the device to its pre-run state instead of exitActions. */
    val revertOnExit: Boolean = false,
    /** Minimum gap (seconds) between two runs of this task from the same event. */
    val cooldownSeconds: Int = 10,
    val createdAt: Long,
    val updatedAt: Long
)

/** Cooldown as milliseconds for the monitors' event de-duplication. */
val Automation.cooldownMillis: Long get() = (cooldownSeconds.coerceAtLeast(0)) * 1000L

@Immutable
@Serializable
// config must never be mutated in place (Compose @Immutable contract).
data class Trigger(
    val type: TriggerType,
    val config: Map<String, String>
)

@Serializable
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
    /**
     * Cellular network generation (2G/3G/4G/5G). A standalone trigger that
     * fires when the device's data network matches the configured generation.
     * Config key: `state` (AUTO/2G/3G/4G/5G).
     */
    NETWORK_MODE,
    NOTIFICATION,
    CALENDAR,
    /**
     * Device sensor events (proximity, shake, light, step counter). Config
     * keys: `sensor` (PROXIMITY/SHAKE/LIGHT/STEP), `event` (COVERED/UNCOVERED,
     * ABOVE/BELOW for light), `threshold` (lux for LIGHT), `sensitivity`
     * (shake g-force threshold, default 14).
     */
    SENSOR,
    /**
     * Local HTTP webhook: a loopback server accepts requests on `path` (and
     * optionally `method` + `token`) and fires the task, Tasker-webhook style.
     */
    WEBHOOK,
    /**
     * A real Evolution X / LineageOS custom setting. Reads the actual value
     * from the ROM's Settings provider through [EvolutionXSettingsBridge] and
     * fires when it matches the configured target. Config keys: `namespace`
     * (SYSTEM/SECURE/GLOBAL), `key` (e.g. `evo_disable_animation`),
     * `operator` (EQUALS/NOT_EQUALS), `value`.
     */
    ROM_SETTING
}

@Immutable
@Serializable
// config must never be mutated in place (Compose @Immutable contract).
data class Action(
    val type: ActionType,
    val config: Map<String, String>,
    /** Per-action behavior applied when the task ends (null = leave as is). */
    val endBehavior: EndBehavior? = null
) {
    /** Returns a copy whose run config is replaced (used for SET_VALUE end behavior). */
    fun withConfig(config: Map<String, String>): Action = copy(config = config)
}

@Serializable
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
    /**
     * Forces the preferred cellular network generation (2G/3G/4G/5G). Config
     * key: `mode` (AUTO/2G/3G/4G/5G). Requires elevated/root (the setting is
     * radio-global, `preferred_network_mode`).
     */
    SYSTEM_NETWORK_MODE,
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
    /**
     * Sets the device's default ringtone to the picked notification/ringtone
     * URI. Config key: `uri`. Revert restores the previous default ringtone.
     */
    SYSTEM_SET_RINGTONE,
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
    ADVANCED_ROOT,
    /** HTTP request (GET/POST/...) — URL and body support %variable injection. */
    SYSTEM_HTTP_REQUEST,
    /**
     * External plugin action (Locale protocol): fires a plugin's FIRE_SETTING
     * receiver with the saved config bundle. Config keys: `package` (app
     * package), `receiver` (receiver class), `bundleJson` (serialized config,
     * opaque to %variable resolution), `blurb` (display summary).
     */
    PLUGIN_FIRE
}
