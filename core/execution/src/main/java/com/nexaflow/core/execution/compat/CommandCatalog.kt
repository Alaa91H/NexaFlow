package com.nexaflow.core.execution.compat

import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType

/**
 * The master compatibility table. Every [ActionType] and [TriggerType] maps to
 * a [CommandSpec] describing the device requirements and the preferred
 * execution strategy.
 *
 * Rules of thumb applied here, matching the actual [com.nexaflow.core.rom.SystemController]
 * implementations:
 * - Pure intents / toasts / clipboard → UNIVERSAL (DIRECT).
 * - `settings put`-style writes → SHELL when the key is public, ELEVATED when
 *   it needs root/Shizuku on stock ROMs.
 * - Radio/telephony (network mode, mobile data, hotspot, airplane, NFC,
 *   bluetooth scan/discoverability) → SHELL with a capability or ELEVATED
 *   fallback.
 * - Power ops (reboot/shutdown/restart UI/soft restart) → ELEVATED only.
 * - OEM skins (MIUI/HyperOS, ColorOS, One UI) often hard-block `cmd` paths:
 *   those commands get a [CommandSpec.deniedFamilies] so the engine hides them
 *   there instead of showing a command that would silently fail.
 */
object CommandCatalog {

    // ── Elevation helpers ────────────────────────────────────────────────
    private fun elevated(sdk: Int = 1) = CommandSpec(
        minSdk = sdk,
        requiresIntegration = IntegrationLevel.ROOT,
        strategy = ExecutionStrategy.ELEVATED
    )

    private fun shell(caps: Set<RomCapability> = emptySet()) = CommandSpec(
        strategy = ExecutionStrategy.SHELL,
        capabilities = caps
    )

    private fun direct(
        caps: Set<RomCapability> = emptySet(),
        permissions: Set<String> = emptySet()
    ) = CommandSpec(
        strategy = ExecutionStrategy.DIRECT,
        capabilities = caps,
        permissions = permissions
    )

    // ── Actions ──────────────────────────────────────────────────────────
    private val actions: Map<ActionType, CommandSpec> = mapOf(
        // DISPLAY
        ActionType.SYSTEM_BRIGHTNESS to shell(setOf(RomCapability.WRITE_SETTINGS)),
        ActionType.SYSTEM_AUTO_BRIGHTNESS to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_SCREEN_ROTATION to shell(setOf(RomCapability.WRITE_SETTINGS)),
        ActionType.SYSTEM_SCREEN_TIMEOUT to shell(setOf(RomCapability.WRITE_SETTINGS)),
        ActionType.SYSTEM_STAY_AWAKE to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_DARK_MODE to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_COLOR_INVERSION to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_GRAYSCALE to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_NIGHT_LIGHT to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_EXTRA_DIM to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_FONT_SCALE to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_DISPLAY_DENSITY to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_SCREENSAVER to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_SCREENSAVER_TIMEOUT to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_ALWAYS_ON_DISPLAY to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_SHOW_TAPS to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_POINTER_LOCATION to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_POINTER_SPEED to shell(setOf(RomCapability.WRITE_SETTINGS)),
        ActionType.SYSTEM_SCREENSHOT to elevated(),
        ActionType.SYSTEM_WAKE_SCREEN to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_ANIMATIONS to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),

        // SOUND / AUDIO
        ActionType.SYSTEM_VOLUME to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_STREAM_VOLUME to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_RING_VOLUME to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_RINGER_MODE to shell(setOf(RomCapability.WRITE_SETTINGS)),
        ActionType.SYSTEM_SET_RINGTONE to direct(setOf(RomCapability.WRITE_SETTINGS)),
        ActionType.SYSTEM_SET_NOTIFICATION_TONE to direct(setOf(RomCapability.WRITE_SETTINGS)),
        ActionType.SYSTEM_DND to shell(setOf(RomCapability.DND_ACCESS)),
        ActionType.SYSTEM_SOUND_EFFECTS to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_HAPTIC_FEEDBACK to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_HAPTIC_INTENSITY to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_CALL_VIBRATION to shell(setOf(RomCapability.WRITE_SETTINGS)),
        ActionType.SYSTEM_CAMERA_SHUTTER_SOUND to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_VIBRATE to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_VIBRATE_PATTERN to CommandSpec.UNIVERSAL,

        // CONNECTIVITY / RADIO
        ActionType.SYSTEM_WIFI to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_WIFI_CONNECT to shell(),
        ActionType.SYSTEM_WIFI_FORGET to shell(),
        ActionType.SYSTEM_WIFI_SCANNING to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_WIFI_SCAN_NOW to shell(),
        ActionType.SYSTEM_WIFI_SLEEP_POLICY to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_BLUETOOTH to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_BLUETOOTH_DISCOVERABILITY to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_BLUETOOTH_SCAN to elevated(),
        ActionType.SYSTEM_MOBILE_DATA to shell(setOf(RomCapability.MODIFY_PHONE_STATE)),
        ActionType.SYSTEM_NETWORK_MODE to shell(setOf(RomCapability.MODIFY_PHONE_STATE)),
        ActionType.SYSTEM_HOTSPOT to shell(setOf(RomCapability.MODIFY_PHONE_STATE)),
        ActionType.SYSTEM_NFC to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_AIRPLANE_MODE to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_LOCATION to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_LOCATION_MODE to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_DATA_ROAMING to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_DATA_SAVER to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_ADAPTIVE_BATTERY to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_POWER_SAVER to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_AUTO_TIME to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_AUTO_TIMEZONE to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_SET_TIMEZONE to direct(setOf(RomCapability.WRITE_SECURE_SETTINGS)),

        // NOTIFICATIONS
        ActionType.SYSTEM_SEND_NOTIFICATION to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_SEND_REMINDER to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_CLEAR_NOTIFICATIONS to elevated(),
        ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_BLOCK_NOTIFICATION to shell(setOf(RomCapability.WRITE_SECURE_SETTINGS)),
        ActionType.SYSTEM_EXPAND_STATUS_BAR to direct(setOf(RomCapability.STATUS_BAR_CONTROL)),
        ActionType.SYSTEM_COLLAPSE_STATUS_BAR to direct(setOf(RomCapability.STATUS_BAR_CONTROL)),
        ActionType.SYSTEM_STATUS_BAR_TOGGLE to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_TOAST to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_ALERT to CommandSpec.UNIVERSAL,
        ActionType.BATTERY_ALERTS to CommandSpec.UNIVERSAL,
        ActionType.BATTERY_CHARGING_NOTIFICATIONS to CommandSpec.UNIVERSAL,

        // APPS
        ActionType.APPLICATION_LAUNCH_APP to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_APP to CommandSpec.UNIVERSAL,
        ActionType.APPLICATION_CLOSE_APP to elevated(),
        ActionType.SYSTEM_FORCE_STOP_APP to elevated(),
        ActionType.SYSTEM_CLEAR_APP_DATA to elevated(),
        ActionType.APPLICATION_OPEN_APP_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_APP_SETTINGS_LIST to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_UNINSTALL_APP to elevated(),
        ActionType.SYSTEM_DISABLE_APP to elevated(),
        ActionType.SYSTEM_ENABLE_APP to elevated(),
        ActionType.SYSTEM_INSTALL_APK to elevated(),
        ActionType.SYSTEM_OPEN_APP_DRAWER to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_TOGGLE_PIP to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_PLAY_STORE_APP to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_PLAY_UPDATES to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_GALAXY_STORE to CommandSpec.UNIVERSAL,

        // INPUT / MEDIA / SYSTEM OPS
        ActionType.SYSTEM_INPUT_TEXT to elevated(),
        ActionType.SYSTEM_KEY_EVENT to elevated(),
        ActionType.SYSTEM_INPUT_TAP to elevated(),
        ActionType.SYSTEM_INPUT_SWIPE to elevated(),
        ActionType.SYSTEM_PASTE to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_CLIPBOARD_SET to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_MEDIA_PLAY_PAUSE to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_MEDIA_NEXT to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_MEDIA_PREVIOUS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_MEDIA_STOP to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_MEDIA_FAST_FORWARD to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_MEDIA_REWIND to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_CAMERA to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_DIAL_NUMBER to direct(permissions = setOf("android.permission.CALL_PHONE")),
        ActionType.SYSTEM_SEND_SMS to direct(permissions = setOf("android.permission.SEND_SMS")),
        ActionType.SYSTEM_SEND_EMAIL to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_URL to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_MAPS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_CONTACTS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_SET_ALARM to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_SET_SETTING to shell(setOf(RomCapability.WRITE_SETTINGS)),
        ActionType.SYSTEM_HTTP_REQUEST to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_WAIT to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_FLASHLIGHT to CommandSpec.UNIVERSAL,

        // POWER
        ActionType.SYSTEM_LOCK_SCREEN to elevated(),
        ActionType.SYSTEM_GO_HOME to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_RECENTS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_QUICK_SETTINGS to direct(setOf(RomCapability.STATUS_BAR_CONTROL)),
        ActionType.SYSTEM_OPEN_NOTIFICATIONS to direct(setOf(RomCapability.STATUS_BAR_CONTROL)),
        ActionType.SYSTEM_REBOOT to elevated(),
        ActionType.SYSTEM_SHUTDOWN to elevated(),
        ActionType.SYSTEM_SOFT_RESTART to elevated(),
        ActionType.SYSTEM_RESTART_SYSTEM_UI to elevated(),

        // SETTINGS LAUNCHERS — intents, universally available
        ActionType.SYSTEM_OPEN_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_WIFI_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_BLUETOOTH_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_NETWORK_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_NFC_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_AIRPLANE_MODE_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_DATA_SAVER_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_DATA_USAGE_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_DEVELOPER_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_DEVICE_ADMIN_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_USAGE_ACCESS_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_VPN_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_DISPLAY_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_SOUND_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_STORAGE_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_BATTERY_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_LOCATION_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_SECURITY_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_PRIVACY_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_ABOUT_PHONE to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_CAST_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_DEFAULT_APPS_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_ACCESSIBILITY_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_INPUT_METHOD_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_DATE_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_PRINT_SETTINGS to CommandSpec.UNIVERSAL,
        ActionType.SYSTEM_OPEN_NOTIFICATION_SETTINGS to CommandSpec.UNIVERSAL,

        // ADVANCED / PLUGINS
        ActionType.ADVANCED_ROOT to elevated(),
        ActionType.ADVANCED_SHIZUKU to CommandSpec(
            requiresIntegration = IntegrationLevel.SHIZUKU,
            strategy = ExecutionStrategy.ELEVATED
        ),
        ActionType.PLUGIN_FIRE to CommandSpec.UNIVERSAL
    )

    // ── Triggers ─────────────────────────────────────────────────────────
    // Triggers are monitors (broadcast receivers / observers). Most run on any
    // ROM; a few need runtime permissions or hardware that some devices lack.
    private val triggers: Map<TriggerType, CommandSpec> = mapOf(
        TriggerType.TIME to CommandSpec.UNIVERSAL,
        TriggerType.CALENDAR to direct(permissions = setOf("android.permission.READ_CALENDAR")),
        TriggerType.BATTERY to CommandSpec.UNIVERSAL,
        TriggerType.BATTERY_TEMPERATURE to CommandSpec.UNIVERSAL,
        TriggerType.CHARGER to CommandSpec.UNIVERSAL,
        TriggerType.DEVICE to CommandSpec.UNIVERSAL,
        TriggerType.RINGER_MODE to CommandSpec.UNIVERSAL,
        TriggerType.NOTIFICATION to CommandSpec.UNIVERSAL,
        TriggerType.SENSOR to CommandSpec.UNIVERSAL,
        TriggerType.ROM_SETTING to CommandSpec.UNIVERSAL,
        TriggerType.HEADPHONE to CommandSpec.UNIVERSAL,
        TriggerType.AIRPLANE_MODE to CommandSpec.UNIVERSAL,
        TriggerType.DARK_MODE to CommandSpec.UNIVERSAL,
        TriggerType.CALL_STATE to direct(permissions = setOf("android.permission.READ_PHONE_STATE")),
        TriggerType.MEDIA_PLAYING to CommandSpec.UNIVERSAL,
        TriggerType.VOLUME_CHANGED to CommandSpec.UNIVERSAL,
        TriggerType.POWER_SAVER to CommandSpec.UNIVERSAL,
        TriggerType.BRIGHTNESS_LEVEL to CommandSpec.UNIVERSAL,
        TriggerType.STORAGE_LOW to CommandSpec.UNIVERSAL,
        TriggerType.AUTO_ROTATE to CommandSpec.UNIVERSAL,
        TriggerType.DEVICE_LOCKED to CommandSpec.UNIVERSAL,
        TriggerType.SCREEN_ROTATION_STATE to CommandSpec.UNIVERSAL,
        TriggerType.CONNECTIVITY to CommandSpec.UNIVERSAL,
        TriggerType.NETWORK_MODE to CommandSpec.UNIVERSAL,
        TriggerType.WIFI_STATE to CommandSpec.UNIVERSAL,
        TriggerType.WIFI_SIGNAL_STRENGTH to CommandSpec.UNIVERSAL,
        TriggerType.BLUETOOTH_STATE to CommandSpec.UNIVERSAL,
        TriggerType.BLUETOOTH_DEVICE to CommandSpec.UNIVERSAL,
        TriggerType.LOCATION to CommandSpec.UNIVERSAL,
        TriggerType.LOCATION_STATE to CommandSpec.UNIVERSAL,
        TriggerType.NFC_STATE to CommandSpec.UNIVERSAL,
        TriggerType.NFC_TAG_SCANNED to CommandSpec.UNIVERSAL,
        TriggerType.USB_CONNECTED to CommandSpec.UNIVERSAL,
        TriggerType.HDMI_CONNECTED to CommandSpec.UNIVERSAL,
        TriggerType.ETHERNET_CONNECTED to CommandSpec.UNIVERSAL,
        TriggerType.VPN_CONNECTED to CommandSpec.UNIVERSAL,
        TriggerType.CELL_SIGNAL_STRENGTH to CommandSpec.UNIVERSAL,
        TriggerType.CLIPBOARD_CHANGED to CommandSpec.UNIVERSAL,
        TriggerType.DND_STATE to CommandSpec.UNIVERSAL,
        TriggerType.STAY_AWAKE_STATE to CommandSpec.UNIVERSAL,
        TriggerType.AUTO_BRIGHTNESS_STATE to CommandSpec.UNIVERSAL,
        TriggerType.SCREEN_TIMEOUT_CHANGED to CommandSpec.UNIVERSAL,
        TriggerType.DATA_ROAMING_STATE to CommandSpec.UNIVERSAL,
        TriggerType.DATA_SAVER_STATE to CommandSpec.UNIVERSAL,
        TriggerType.TIMEZONE_CHANGED to CommandSpec.UNIVERSAL,
        TriggerType.BOOT_COMPLETED to CommandSpec.UNIVERSAL,
        TriggerType.ALARM_SET_CHANGED to CommandSpec.UNIVERSAL,
        TriggerType.SMS to direct(permissions = setOf("android.permission.RECEIVE_SMS")),
        TriggerType.WEBHOOK to CommandSpec.UNIVERSAL,
        TriggerType.APPLICATION to CommandSpec.UNIVERSAL,
        TriggerType.APP_INSTALLED to CommandSpec.UNIVERSAL
    )

    fun specFor(type: Any): CommandSpec? {
        return when (type) {
            is ActionType -> actions[type]
            is TriggerType -> triggers[type]
            else -> null
        }
    }

    /**
     * Duplicate commands that are unified into a single entry. The key is the
     * alias (hidden from the UI as if it did not exist); the value is the
     * canonical command it maps to. Persisted automations keep working because
     * the execution layer resolves the alias to the canonical handler.
     */
    val unifiedAliases: Map<ActionType, ActionType> = mapOf(
        // SYSTEM_VOLUME == SYSTEM_STREAM_VOLUME with stream fixed to MUSIC.
        ActionType.SYSTEM_VOLUME to ActionType.SYSTEM_STREAM_VOLUME,
        // SYSTEM_RING_VOLUME == SYSTEM_STREAM_VOLUME with stream fixed to RING.
        ActionType.SYSTEM_RING_VOLUME to ActionType.SYSTEM_STREAM_VOLUME,
        // APPLICATION_LAUNCH_APP == SYSTEM_OPEN_APP with a single package.
        ActionType.APPLICATION_LAUNCH_APP to ActionType.SYSTEM_OPEN_APP
    )

    /** Resolves an alias to its canonical command (identity for non-aliases). */
    fun canonical(type: ActionType): ActionType = unifiedAliases[type] ?: type

    /** True when [type] is a unified duplicate that should be hidden. */
    fun isUnifiedAlias(type: ActionType): Boolean = type in unifiedAliases
}
