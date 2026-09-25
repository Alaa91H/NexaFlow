@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

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
    /** When true, show a toast when this task is toggled on/off from the dashboard. */
    val showToastOnToggle: Boolean = true,
    val triggers: List<Trigger>,
    val actions: List<Action>,
    /**
     * Combining rule for [triggers]. ANY = fire when any trigger's monitor
     * fires (historical default). ALL = every trigger needs current evidence:
     * state-readable triggers are verified live and event-only triggers must
     * match the current trigger occurrence that started the automatic
     * evaluation. Past events are never retained as truth.
     */
    val triggerMatch: TriggerMatchMode = TriggerMatchMode.ANY,
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
    val updatedAt: Long,
    /**
     * Persisted workflow semantics version.
     *
     * v1 predates occurrence-aware ALL matching. v2 allows the current trigger
     * occurrence to prove momentary conditions while state-readable siblings
     * are evaluated live. Older event-based ALL definitions stay fail-closed
     * until the user reviews and saves them through the builder.
     */
    val workflowVersion: Int = CURRENT_WORKFLOW_VERSION,
    /** Optional recurring-maintenance metadata; null preserves ordinary automations unchanged. */
    val maintenanceProfile: MaintenanceProfile? = null,
    /**
     * Deep-link capability token (base64url, 256-bit entropy). Null means
     * external deep-link execution is disabled for this task: a `nexaflow://`
     * link only opens the app for review, it never runs anything. Set by an
     * explicit per-task user opt-in; rotatable and revocable. The automation ID
     * alone is deliberately NOT an authorization.
     */
    @kotlinx.serialization.Transient
    val deepLinkToken: String? = null
) {
    init {
        require(workflowVersion in 1..CURRENT_WORKFLOW_VERSION) { "Unsupported workflow version" }
    }

    companion object {
        const val LEGACY_TRIGGER_SEMANTICS_VERSION = 1
        const val OCCURRENCE_AWARE_TRIGGER_SEMANTICS_VERSION = 2
        const val CURRENT_WORKFLOW_VERSION = OCCURRENCE_AWARE_TRIGGER_SEMANTICS_VERSION
    }
}

/** Cooldown as milliseconds for the monitors' event de-duplication. */
val Automation.cooldownMillis: Long get() = (cooldownSeconds.coerceAtLeast(0)) * 1000L

/**
 * True when every configured trigger is a momentary event that has no later
 * opposite state to close the task lifecycle. The execution engine uses this
 * policy as a safe default so end actions are never stranded when a caller
 * omits an explicit completion flag.
 */
val Automation.completesExitOnFinish: Boolean
    get() = triggers.isNotEmpty() && triggers.all { trigger -> trigger.isOneShotEvent() }

/**
 * True when the task has an end behavior that can produce a side effect. The
 * neutral [EndMode.LEAVE] option is deliberately excluded: it means the task
 * has no action to perform when its condition ends.
 */
val Automation.hasExecutableEndBehavior: Boolean
    get() = revertOnExit ||
        exitActions.isNotEmpty() ||
        actions.any { action -> action.endBehavior?.mode != null && action.endBehavior.mode != EndMode.LEAVE }

/**
 * A scheduled point in time has no later end boundary. Combining it with an
 * executable end behavior would apply the main action and then immediately
 * undo or replace it, which is not a meaningful timed lifecycle. Such tasks
 * must be configured as a TIME range with an explicit end time.
 */
val Automation.requiresTimeRangeForEndBehavior: Boolean
    get() = hasExecutableEndBehavior && triggers.any { trigger ->
        trigger.type == TriggerType.TIME && trigger.config["timeMode"] != "RANGE"
    }

/**
 * True when this trigger is a momentary occurrence rather than an ongoing
 * condition. Exposed so stateful source routers can avoid treating a past
 * one-shot event as a condition that keeps an ANY lifecycle open.
 */
fun Trigger.isOneShotEvent(): Boolean = when (type) {
    TriggerType.TIME -> config["timeMode"] != "RANGE"
    TriggerType.SMS,
    TriggerType.WEBHOOK,
    TriggerType.APP_INSTALLED,
    TriggerType.CLIPBOARD_CHANGED,
    TriggerType.TIMEZONE_CHANGED,
    TriggerType.BOOT_COMPLETED,
    TriggerType.NFC_TAG_SCANNED,
    TriggerType.ALARM_SET_CHANGED,
    TriggerType.PLUGIN_EVENT -> true
    else -> false
}

/**
 * How multiple [Trigger]s combine when deciding whether a task may run.
 *
 * - [ANY] (default, historical behavior): any single trigger firing runs the
 *   task — its monitor's own condition was the trigger.
 * - [ALL]: every configured trigger needs evidence in one evaluation.
 *   Workflow v2+ may use the current occurrence for momentary triggers and
 *   live reads for stateful siblings. Legacy v1 event-based ALL definitions
 *   remain fail-closed until reviewed and saved.
 *
 * Serialized on the automation; the default keeps every saved task's meaning
 * unchanged (append-only compatibility contract).
 */
@Serializable
enum class TriggerMatchMode {
    ANY,
    ALL
}

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
    /**
     * Legacy combined Wi-Fi/mobile/network-mode trigger. It remains readable
     * for saved automations but is not offered to new tasks because its former
     * sub-options duplicated dedicated connectivity triggers.
     */
    /**
     * Legacy combined network trigger (Wi-Fi or mobile data, config-selected).
     * Hidden from the trigger picker; kept in the enum so saved tasks keep
     * evaluating. New tasks use [WIFI_CONNECTED] or [MOBILE_DATA_CONNECTED].
     */
    CONNECTIVITY,
    /** Default-network transport is Wi-Fi (or not, per `state`). */
    WIFI_CONNECTED,
    /** Default-network transport is cellular, i.e. mobile data (or not). */
    MOBILE_DATA_CONNECTED,
    /**
     * Wi-Fi tethering state. Config key: `state` (ON/OFF). This is a dedicated
     * trigger so new tasks do not need the legacy combined connectivity menu.
     */
    HOTSPOT,
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
     * Local HTTP webhook: a loopback server accepts requests on `path`, an
     * optional `method`, and a mandatory `token` before firing the task.
     */
    WEBHOOK,
    /**
     * A real vendor custom setting. Reads the actual value
     * from the ROM's Settings provider through [CustomSettingsBridge] and
     * fires when it matches the configured target. Config keys: `namespace`
     * (SYSTEM/SECURE/GLOBAL), `key` (e.g. `rom_disable_animation`),
     * `operator` (EQUALS/NOT_EQUALS), `value`.
     */
    ROM_SETTING,
    /**
     * Headphones / earphones plugged in or unplugged. Config key: `event`
     * (CONNECTED/DISCONNECTED). Shares the device-event monitor.
     */
    HEADPHONE,
    /**
     * Charger plugged in or unplugged (any plug type). Config key: `event`
     * (CONNECTED/DISCONNECTED). Shares the battery monitor.
     */
    CHARGER,
    /**
     * Airplane mode turned on or off. Config key: `state` (ON/OFF).
     */
    AIRPLANE_MODE,
    /**
     * System dark theme turned on or off. Config key: `state` (ON/OFF).
     */
    DARK_MODE,
    /**
     * Telephony call state changed. Config key: `event`
     * (INCOMING/OUTGOING/ENDED). Driven by a PhoneStateListener.
     */
    CALL_STATE,
    /**
     * A call is ringing right now, screened pre-ring through the platform
     * CallScreeningService (with fallback to the call-state listener when the
     * screening role is not held). Config keys: `from` (number or part of it,
     * optional), `matchMode` (CONTAINS/EXACT/ANY — EXACT compares the full
     * dialed number, CONTAINS a substring, ANY ignores the number), and
     * `category` (ANY/UNKNOWN/PRIVATE — UNKNOWN matches calls without a
     * readable number, PRIVATE matches withheld callers). Call-control tasks
     * may use the CALL_BLOCK and CALL_SILENCE actions; the screening decision
     * itself is made synchronously from the same config (see
     * CallPolicyEvaluator), while the remaining actions run through the
     * normal engine path.
     */
    INCOMING_CALL,
    /**
     * A package was installed, removed or updated. Config keys: `event`
     * (INSTALLED/REMOVED/UPDATED), optional `package` filter.
     */
    APP_INSTALLED,
    /**
     * Media playback started or stopped. Config key: `event`
     * (STARTED/STOPPED).
     */
    MEDIA_PLAYING,
    /**
     * A stream volume crossed a threshold. Config keys: `stream`
     * (MUSIC/RING/ALARM/NOTIFICATION), `threshold`, `direction` (ABOVE/BELOW).
     */
    VOLUME_CHANGED,
    /**
     * Battery saver turned on or off. Config key: `state` (ON/OFF).
     */
    POWER_SAVER,
    /**
     * Bluetooth radio turned on or off. Config key: `state` (ON/OFF).
     */
    BLUETOOTH_STATE,
    /**
     * Screen brightness crossed a threshold. Config keys: `threshold`
     * (0-255), `direction` (ABOVE/BELOW).
     */
    BRIGHTNESS_LEVEL,
    /**
     * Free storage fell below a threshold. Config keys: `threshold` (MB),
     * `direction` (BELOW/ABOVE).
     */
    STORAGE_LOW,
    /**
     * Auto-rotate setting turned on or off. Config key: `state` (ON/OFF).
     */
    AUTO_ROTATE,
    /**
     * Data saver turned on or off. Config key: `state` (ON/OFF).
     */
    DATA_SAVER_STATE,
    /**
     * Device lock state changed. Config key: `state` (LOCKED/UNLOCKED).
     */
    DEVICE_LOCKED,
    /**
     * Wi-Fi radio turned on or off. Config key: `state` (ON/OFF).
     */
    WIFI_STATE,
    /**
     * NFC radio turned on or off. Config key: `state` (ON/OFF).
     */
    NFC_STATE,
    /**
     * Location mode changed (off/sensors/battery/high). Config key:
     * `mode` (OFF/SENSORS/BATTERY/HIGH).
     */
    LOCATION_STATE,
    /**
     * Screen rotation (portrait/landscape) changed. Config key: `state`
     * (PORTRAIT/LANDSCAPE).
     */
    SCREEN_ROTATION_STATE,
    /**
     * Wi-Fi signal strength crossed a threshold. Config keys: `threshold`
     * (RSSI -100..0), `direction` (ABOVE/BELOW).
     */
    WIFI_SIGNAL_STRENGTH,
    /**
     * Cellular signal strength crossed a threshold. Config keys: `threshold`
     * (0-31 ASU), `direction` (ABOVE/BELOW).
     */
    CELL_SIGNAL_STRENGTH,
    /**
     * Battery temperature crossed a threshold. Config keys: `threshold` (C),
     * `direction` (ABOVE/BELOW).
     */
    BATTERY_TEMPERATURE,
    /**
     * USB device plugged or unplugged. Config key: `event` (CONNECTED/DISCONNECTED).
     */
    USB_CONNECTED,
    /**
     * HDMI display connected or disconnected. Config key: `event` (CONNECTED/DISCONNECTED).
     */
    HDMI_CONNECTED,
    /**
     * Ethernet link up or down. Config key: `event` (CONNECTED/DISCONNECTED).
     */
    ETHERNET_CONNECTED,
    /**
     * VPN tunnel established or torn down. Config key: `event` (CONNECTED/DISCONNECTED).
     */
    VPN_CONNECTED,
    /**
     * Clipboard content changed. Config key: `contains` (optional substring filter).
     */
    CLIPBOARD_CHANGED,
    /**
     * Do-not-disturb turned on or off. Config key: `state` (ON/OFF).
     */
    DND_STATE,
    /**
     * Stay-awake-while-charging turned on or off. Config key: `state` (ON/OFF).
     */
    STAY_AWAKE_STATE,
    /**
     * Auto-brightness turned on or off. Config key: `state` (ON/OFF).
     */
    AUTO_BRIGHTNESS_STATE,
    /**
     * Screen timeout setting changed to a value. Config key: `seconds` (exact match).
     */
    SCREEN_TIMEOUT_CHANGED,
    /**
     * Data roaming turned on or off. Config key: `state` (ON/OFF).
     */
    DATA_ROAMING_STATE,
    /**
     * System timezone changed. Config key: `zone` (optional IANA match).
     */
    TIMEZONE_CHANGED,
    /**
     * Device finished booting. Fires once after boot.
     */
    BOOT_COMPLETED,
    /**
     * An NFC tag was scanned. Config key: `contains` (optional id substring).
     */
    NFC_TAG_SCANNED,
    /**
     * An alarm clock was set or cleared. Config key: `event` (SET/CLEARED).
     */
    ALARM_SET_CHANGED,
    /**
     * Wear OS companion reachability state. Config keys:
     * `state` (CONNECTED/DISCONNECTED) and optional `watchInstallId`.
     * A blank watchInstallId means any previously discovered NexaFlow watch.
     */
    WEAR_EVENT,
    /**
     * A user-approved event from an explicitly configured plugin component.
     * Config keys: `pluginInstance`, `pluginApproval`, `package`, and
     * `eventComponent`. No Bundle, Intent, token, or arbitrary command is
     * persisted in the trigger.
     */
    PLUGIN_EVENT
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
     * key: `mode` (AUTO/2G/3G/4G/5G). Applied per active SIM through the
     * modern `setAllowedNetworkTypesForReason` bitmask API (Android 11+) with
     * legacy `setPreferredNetworkType` and elevated `settings` writes as
     * fallbacks. Requires MODIFY_PHONE_STATE or a system/privileged install.
     */
    SYSTEM_NETWORK_MODE,
    /** Sets Android device-wide Private DNS. Config keys: `mode` (OFF/AUTOMATIC/HOSTNAME), `hostname`. Requires elevated settings access. */
    SYSTEM_PRIVATE_DNS,
    SYSTEM_HOTSPOT,
    SYSTEM_NFC,
    SYSTEM_POWER_SAVER,
    SYSTEM_ANIMATIONS,
    SYSTEM_LOCK_SCREEN,
    SYSTEM_SET_ALARM,
    /** Starts a countdown timer through a compatible system clock app. Config keys: `seconds`, `message`, `skipUi`. */
    SYSTEM_SET_TIMER,
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
    /**
     * Evaluates the supported, interaction-free Google Play update route for
     * this device. It never opens Play Store UI and skips safely when no
     * official update-discovery source is exposed to the current environment.
     */
    SYSTEM_UPDATE_GOOGLE_PLAY_APPS,
    SYSTEM_OPEN_PLAY_UPDATES,
    /** Opens the device's own app-store client; falls back to Play updates when absent. Legacy serialized name kept readable. */
    @kotlinx.serialization.SerialName("SYSTEM_OPEN_DEVICE_STORE")
    @kotlinx.serialization.json.JsonNames("SYSTEM_OPEN_DEVICE_STORE")
    SYSTEM_OPEN_DEVICE_STORE,
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
    PLUGIN_FIRE,
    SYSTEM_VIBRATE,
    SYSTEM_WAKE_SCREEN,
    SYSTEM_CLIPBOARD_SET,
    SYSTEM_MEDIA_STOP,
    SYSTEM_OPEN_NOTIFICATIONS,
    SYSTEM_OPEN_QUICK_SETTINGS,
    /** Writes any device setting. Config keys: `namespace` (SYSTEM/SECURE/
     * GLOBAL), `key`, `value`. */
    SYSTEM_SET_SETTING,
    /** Captures a screenshot. Config key: `filename` (optional). */
    SYSTEM_SCREENSHOT,
    /** Injects text via `input text`. Config key: `text`. */
    SYSTEM_INPUT_TEXT,
    /** Injects a key event. Config key: `key` (e.g. POWER, CAMERA, HOME...). */
    SYSTEM_KEY_EVENT,
    /** Taps at absolute screen coordinates. Config keys: `x`, `y`. */
    SYSTEM_INPUT_TAP,
    /** Swipes between two points. Config keys: `x1`,`y1`,`x2`,`y2`,`durationMs`. */
    SYSTEM_INPUT_SWIPE,
    /** Toggles accessibility color inversion. Config key: `enabled`. */
    SYSTEM_COLOR_INVERSION,
    /** Toggles grayscale (daltonizer). Config key: `enabled`. */
    SYSTEM_GRAYSCALE,
    /** Toggles extra-dim. Config key: `enabled`. */
    SYSTEM_EXTRA_DIM,
    /** Toggles night light. Config key: `enabled`. */
    SYSTEM_NIGHT_LIGHT,
    /** Toggles haptic feedback. Config key: `enabled`. */
    SYSTEM_HAPTIC_FEEDBACK,
    /** Toggles touch sounds. Config key: `enabled`. */
    SYSTEM_SOUND_EFFECTS,
    /** Force-stops an app. Config key: `package`. */
    SYSTEM_FORCE_STOP_APP,
    /** Clears an app's data. Config key: `package`. */
    SYSTEM_CLEAR_APP_DATA,
    /** Sets the location mode. Config key: `mode` (OFF/SENSORS/BATTERY/HIGH). */
    SYSTEM_LOCATION_MODE,
    /** Toggles data saver. Config key: `enabled`. */
    SYSTEM_DATA_SAVER,
    /** Sets the display font scale. Config key: `scale` (0.85-1.30). */
    SYSTEM_FONT_SCALE,
    /** Sets the display density via `wm density`. Config key: `density` (e.g. 420). */
    SYSTEM_DISPLAY_DENSITY,
    /** Toggles the screensaver (daydream). Config key: `enabled`. */
    SYSTEM_SCREENSAVER,
    /** Sets the battery-saver auto-trigger level. Config key: `level` (0-100). */
    SYSTEM_BATTERY_SAVER_THRESHOLD,
    /** Sets a hardware charge-control end threshold. Config key: `percent` (50-100). Requires a verified writable standard sysfs node. */
    SYSTEM_CHARGING_LIMIT,
    /** Toggles Android charging sound and/or charging vibration. Config keys: `sound`, `vibration`. Requires elevated settings access and verified read-back. */
    SYSTEM_CHARGING_FEEDBACK,
    /** Toggles always-on display (AOD). Config key: `enabled`. */
    SYSTEM_ALWAYS_ON_DISPLAY,
    /** Toggles developer "show taps". Config key: `enabled`. */
    SYSTEM_SHOW_TAPS,
    /** Toggles developer "pointer location". Config key: `enabled`. */
    SYSTEM_POINTER_LOCATION,
    /** Toggles adaptive battery. Config key: `enabled`. */
    SYSTEM_ADAPTIVE_BATTERY,
    /** Sets the Wi-Fi sleep policy. Config key: `policy` (0/1/2). */
    SYSTEM_WIFI_SLEEP_POLICY,
    /** Sets Bluetooth discoverability. Config key: `mode` (0/1/2). */
    SYSTEM_BLUETOOTH_DISCOVERABILITY,
    /** Toggles automatic date & time. Config key: `enabled`. */
    SYSTEM_AUTO_TIME,
    /** Toggles automatic time zone. Config key: `enabled`. */
    SYSTEM_AUTO_TIMEZONE,
    /** Sets haptic intensity. Config key: `level` (0-255). */
    SYSTEM_HAPTIC_INTENSITY,
    /** Toggles the camera shutter sound. Config key: `enabled`. */
    SYSTEM_CAMERA_SHUTTER_SOUND,
    /** Toggles Wi-Fi scanning. Config key: `enabled`. */
    SYSTEM_WIFI_SCANNING,
    /** Opens the Wi-Fi settings page. */
    SYSTEM_OPEN_WIFI_SETTINGS,
    /** Opens the Bluetooth settings page. */
    SYSTEM_OPEN_BLUETOOTH_SETTINGS,
    /** Opens the location settings page. */
    SYSTEM_OPEN_LOCATION_SETTINGS,
    /** Opens the data-usage settings page. */
    SYSTEM_OPEN_DATA_USAGE_SETTINGS,
    /** Opens the battery settings page. */
    SYSTEM_OPEN_BATTERY_SETTINGS,
    /** Opens the display settings page. */
    SYSTEM_OPEN_DISPLAY_SETTINGS,
    /** Opens the sound settings page. */
    SYSTEM_OPEN_SOUND_SETTINGS,
    /** Opens the storage settings page. */
    SYSTEM_OPEN_STORAGE_SETTINGS,
    /** Opens the security settings page. */
    SYSTEM_OPEN_SECURITY_SETTINGS,
    /** Opens the accessibility settings page. */
    SYSTEM_OPEN_ACCESSIBILITY_SETTINGS,
    /** Opens the app list in settings. */
    SYSTEM_OPEN_APP_SETTINGS_LIST,
    /** Opens the About phone page. */
    SYSTEM_OPEN_ABOUT_PHONE,
    /** Skips media forward. */
    SYSTEM_MEDIA_FAST_FORWARD,
    /** Rewinds media. */
    SYSTEM_MEDIA_REWIND,
    /** Dials a phone number. Config key: `number`. */
    SYSTEM_DIAL_NUMBER,
    /** Opens the camera app. */
    SYSTEM_OPEN_CAMERA,
    /** Opens the app's Play Store page. Config key: `package`. */
    SYSTEM_OPEN_PLAY_STORE_APP,
    /** Opens the device system-update settings page; it never checks for or installs an OTA update. */
    SYSTEM_OPEN_SYSTEM_UPDATE_SETTINGS,
    /** Delegates a music search to a compatible media app. Config keys: `query`, optional `package`. */
    SYSTEM_MEDIA_PLAY_FROM_SEARCH,
    /** Reboots the device. Config key: `mode` (NORMAL/RECOVERY/BOOTLOADER). */
    SYSTEM_REBOOT,
    /** Powers the device off. */
    SYSTEM_SHUTDOWN,
    /** Restarts the System UI process. */
    SYSTEM_RESTART_SYSTEM_UI,
    /** Shows a toast message. Config key: `text`. */
    SYSTEM_TOAST,
    /** Shows a full-screen alert dialog. Config keys: `title`, `text`. */
    SYSTEM_ALERT,
    /** Vibrates a Morse-like dot/dash pattern. Config key: `pattern`. */
    SYSTEM_VIBRATE_PATTERN,
    /** Pastes the clipboard into the focused field. */
    SYSTEM_PASTE,
    /** Opens the recents / all-apps drawer. */
    SYSTEM_OPEN_APP_DRAWER,
    /** Toggles picture-in-picture for the foreground activity. */
    SYSTEM_TOGGLE_PIP,
    /** Connects to a Wi-Fi network. Config keys: `ssid`, `password` (optional). */
    SYSTEM_WIFI_CONNECT,
    /** Forgets a saved Wi-Fi network. Config key: `ssid`. */
    SYSTEM_WIFI_FORGET,
    /** Enables/disables data roaming. Config key: `enabled`. */
    SYSTEM_DATA_ROAMING,
    /** Sets the screensaver timeout. Config key: `minutes`. */
    SYSTEM_SCREENSAVER_TIMEOUT,
    /** Sets the pointer speed (-7..7). Config key: `speed`. */
    SYSTEM_POINTER_SPEED,
    /** Installs an APK from a path. Config key: `path`. */
    SYSTEM_INSTALL_APK,
    /** Uninstalls a package. Config key: `package`. */
    SYSTEM_UNINSTALL_APP,
    /** Disables a package. Config key: `package`. */
    SYSTEM_DISABLE_APP,
    /** Enables a disabled package. Config key: `package`. */
    SYSTEM_ENABLE_APP,
    /** Sets the notification sound. Config key: `tone`. */
    SYSTEM_SET_NOTIFICATION_TONE,
    /** Toggles vibration while ringing. Config key: `enabled`. */
    SYSTEM_CALL_VIBRATION,
    /** Opens the wireless/network settings page. */
    SYSTEM_OPEN_NETWORK_SETTINGS,
    /** Opens the NFC settings page. */
    SYSTEM_OPEN_NFC_SETTINGS,
    /** Opens the data saver settings page. */
    SYSTEM_OPEN_DATA_SAVER_SETTINGS,
    /** Opens the developer options page. */
    SYSTEM_OPEN_DEVELOPER_SETTINGS,
    /** Opens a location in the maps app. Config keys: `lat`, `lng`. */
    SYSTEM_OPEN_MAPS,
    /** Performs a soft framework restart (requires root). */
    SYSTEM_SOFT_RESTART,
    /** Toggles the status bar visibility. Config key: `show`. */
    SYSTEM_STATUS_BAR_TOGGLE,
    /** Opens the contacts app. */
    SYSTEM_OPEN_CONTACTS,
    /** Sends an email via the mail client. Config keys: `to`, `subject`, `body`. */
    SYSTEM_SEND_EMAIL,
    /** Opens the notification settings page. */
    SYSTEM_OPEN_NOTIFICATION_SETTINGS,
    /** Opens the privacy settings page. */
    SYSTEM_OPEN_PRIVACY_SETTINGS,
    /** Opens the cast settings page. */
    SYSTEM_OPEN_CAST_SETTINGS,
    /** Opens the input method (keyboard) settings page. */
    SYSTEM_OPEN_INPUT_METHOD_SETTINGS,
    /** Opens the default-apps settings page. */
    SYSTEM_OPEN_DEFAULT_APPS_SETTINGS,
    /** Opens the VPN settings page. */
    SYSTEM_OPEN_VPN_SETTINGS,
    /** Opens the date & time settings page. */
    SYSTEM_OPEN_DATE_SETTINGS,
    /** Opens the print settings page. */
    SYSTEM_OPEN_PRINT_SETTINGS,
    /** Opens the device-administrator settings page. */
    SYSTEM_OPEN_DEVICE_ADMIN_SETTINGS,
    /** Opens the usage-access settings page. */
    SYSTEM_OPEN_USAGE_ACCESS_SETTINGS,
    /** Opens the airplane-mode settings page. */
    SYSTEM_OPEN_AIRPLANE_MODE_SETTINGS,
    /** Starts a Bluetooth discovery scan. */
    SYSTEM_BLUETOOTH_SCAN,
    /** Triggers an immediate Wi-Fi scan. */
    SYSTEM_WIFI_SCAN_NOW,
    /** Sets the system timezone. Config key: `zone` (IANA, e.g. Asia/Riyadh). */
    SYSTEM_SET_TIMEZONE,
    /**
     * Rejects the currently ringing incoming call. Requires the screening
     * role or ANSWER_PHONE_CALLS. When used inside a task with an
     * INCOMING_CALL trigger, the synchronous screening pass applies the same
     * decision pre-ring; the handler records the outcome for the run history.
     */
    CALL_BLOCK,
    /**
     * Silences the currently ringing incoming call without rejecting it —
     * the call continues mutely. Applied pre-ring by the screening pass for
     * INCOMING_CALL tasks and by the handler as a best-effort runtime action.
     */
    CALL_SILENCE,

    // === Custom ROM settings (typed, picker-driven) ===
    /** Writes any custom ROM setting with live picker and category. Config keys: `namespace`, `key`, `value`. Replaces SYSTEM_SET_SETTING for ROM work. Serialized names are append-only: the legacy `EVO_*` aliases keep old automations importable. */
    @kotlinx.serialization.SerialName("ROM_CUSTOM_SETTING")
    @kotlinx.serialization.json.JsonNames("EVO_SET_SETTING")
    ROM_CUSTOM_SETTING,
    /** Configures QS tiles and panel. Config keys: `tiles` (csv), `columns`, `brightness_slider` (0/1), `footer_text`. */
    @kotlinx.serialization.SerialName("ROM_QS_TILES")
    @kotlinx.serialization.json.JsonNames("EVO_QS_TILES")
    ROM_QS_TILES,
    /** Configures status bar (clock, battery, icons). Config keys: `clock_position`, `clock_seconds`, `battery_style`, `battery_percent`, `show_vpn` etc. as json. */
    @kotlinx.serialization.SerialName("ROM_STATUS_BAR")
    @kotlinx.serialization.json.JsonNames("EVO_STATUS_BAR")
    ROM_STATUS_BAR,
    /** Configures lockscreen (clock, shortcuts, weather, UDFPS). Config keys: `clock_style`, `shortcuts`, `weather`, `media_art` etc. */
    @kotlinx.serialization.SerialName("ROM_LOCKSCREEN")
    @kotlinx.serialization.json.JsonNames("EVO_LOCKSCREEN")
    ROM_LOCKSCREEN,
    /** Configures navigation mode. Config keys: `mode` (GESTURE/3BUTTON/2BUTTON), `back_height`, `navbar_height`. */
    @kotlinx.serialization.SerialName("ROM_NAVIGATION")
    @kotlinx.serialization.json.JsonNames("EVO_NAVIGATION")
    ROM_NAVIGATION,
    /** Configures theming/monet. Config keys: `accent`, `monet`, `themed_icons`, `icon_pack`, `font`. */
    @kotlinx.serialization.SerialName("ROM_THEME")
    @kotlinx.serialization.json.JsonNames("EVO_THEME")
    ROM_THEME,
    /** Configures ambient/AOD. Config keys: `aod_enabled`, `aod_schedule`, `doze_*`. */
    @kotlinx.serialization.SerialName("ROM_AMBIENT_AOD")
    @kotlinx.serialization.json.JsonNames("EVO_AMBIENT_AOD")
    ROM_AMBIENT_AOD,
    /** Configures notifications/heads-up. Config keys: `heads_up`, `timeout`, `less_boring`. */
    @kotlinx.serialization.SerialName("ROM_NOTIFICATIONS")
    @kotlinx.serialization.json.JsonNames("EVO_NOTIFICATIONS")
    ROM_NOTIFICATIONS,
    /** Batch apply — writes multiple custom ROM setting keys atomically. Config key: `batch_json` (map of key->value). */
    @kotlinx.serialization.SerialName("ROM_BATCH")
    @kotlinx.serialization.json.JsonNames("EVO_BATCH")
    ROM_BATCH,
    DATA_TEXT,
    DATA_ENCODING,
    DATA_HASH,
    DATA_RANDOM,
    DATA_MATH,
    DATA_DATE_TIME,
    DATA_JSON,
    DATA_ARRAY
}

