package com.nexaflow.core.execution

import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.location.LocationManager
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlin.math.abs

/**
 * Best-effort, read-only snapshot of the *current* device value targeted by one
 * action. This powers the expanded dashboard card; it never changes device
 * state and it never upgrades an unreadable value into a guessed value.
 *
 * Unsupported actions return null. Supported-but-unreadable actions return a
 * state with [rawValue] == null so the UI can say "Unavailable" explicitly.
 */
data class ActionCurrentState(
    val kind: ActionStateValueKind,
    val rawValue: String?,
    /** True/false only when the action has a concrete comparable target. */
    val matchesTarget: Boolean?
)

enum class ActionStateValueKind {
    BOOLEAN,
    NUMBER,
    DECIMAL,
    DURATION_SECONDS,
    RINGER_MODE
}

object ActionStateReader {

    private val supportedTypes = setOf(
        ActionType.SYSTEM_WIFI,
        ActionType.SYSTEM_BLUETOOTH,
        ActionType.SYSTEM_NFC,
        ActionType.SYSTEM_MOBILE_DATA,
        ActionType.SYSTEM_AIRPLANE_MODE,
        ActionType.SYSTEM_DND,
        ActionType.SYSTEM_POWER_SAVER,
        ActionType.SYSTEM_ANIMATIONS,
        ActionType.SYSTEM_LOCATION,
        ActionType.SYSTEM_STAY_AWAKE,
        ActionType.SYSTEM_AUTO_BRIGHTNESS,
        ActionType.SYSTEM_DARK_MODE,
        ActionType.SYSTEM_SCREEN_ROTATION,
        ActionType.SYSTEM_COLOR_INVERSION,
        ActionType.SYSTEM_GRAYSCALE,
        ActionType.SYSTEM_EXTRA_DIM,
        ActionType.SYSTEM_NIGHT_LIGHT,
        ActionType.SYSTEM_HAPTIC_FEEDBACK,
        ActionType.SYSTEM_SOUND_EFFECTS,
        ActionType.SYSTEM_DATA_SAVER,
        ActionType.SYSTEM_SCREENSAVER,
        ActionType.SYSTEM_ALWAYS_ON_DISPLAY,
        ActionType.SYSTEM_SHOW_TAPS,
        ActionType.SYSTEM_POINTER_LOCATION,
        ActionType.SYSTEM_ADAPTIVE_BATTERY,
        ActionType.SYSTEM_AUTO_TIME,
        ActionType.SYSTEM_AUTO_TIMEZONE,
        ActionType.SYSTEM_CAMERA_SHUTTER_SOUND,
        ActionType.SYSTEM_WIFI_SCANNING,
        ActionType.SYSTEM_DATA_ROAMING,
        ActionType.SYSTEM_CALL_VIBRATION,
        ActionType.SYSTEM_BRIGHTNESS,
        ActionType.SYSTEM_VOLUME,
        ActionType.SYSTEM_STREAM_VOLUME,
        ActionType.SYSTEM_RING_VOLUME,
        ActionType.SYSTEM_SCREEN_TIMEOUT,
        ActionType.SYSTEM_RINGER_MODE,
        ActionType.SYSTEM_POINTER_SPEED,
        ActionType.SYSTEM_SCREENSAVER_TIMEOUT,
        ActionType.SYSTEM_FONT_SCALE,
        ActionType.SYSTEM_DISPLAY_DENSITY,
        ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD
    )

    fun supports(type: ActionType): Boolean = type in supportedTypes

    fun read(context: Context, action: Action): ActionCurrentState? {
        val app = context.applicationContext
        return when (action.type) {
            ActionType.SYSTEM_WIFI ->
                booleanState(globalBool(app, Settings.Global.WIFI_ON), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_BLUETOOTH ->
                booleanState(globalBool(app, Settings.Global.BLUETOOTH_ON), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_NFC ->
                booleanState(globalBool(app, "nfc_on"), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_MOBILE_DATA ->
                booleanState(globalBool(app, "mobile_data"), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_AIRPLANE_MODE ->
                booleanState(globalBool(app, Settings.Global.AIRPLANE_MODE_ON), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_DND -> {
                val current = runCatching {
                    val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                }.getOrNull()
                booleanState(current, (action.config["enabled"] ?: "true"))
            }

            ActionType.SYSTEM_POWER_SAVER -> {
                val current = runCatching {
                    app.getSystemService(PowerManager::class.java)?.isPowerSaveMode
                }.getOrNull()
                booleanState(current, (action.config["enabled"] ?: "true"))
            }

            ActionType.SYSTEM_ANIMATIONS -> {
                val current = runCatching {
                    listOf(
                        Settings.Global.WINDOW_ANIMATION_SCALE,
                        Settings.Global.TRANSITION_ANIMATION_SCALE,
                        Settings.Global.ANIMATOR_DURATION_SCALE
                    ).all { key ->
                        Settings.Global.getFloat(app.contentResolver, key) > 0f
                    }
                }.getOrNull()
                booleanState(current, (action.config["enabled"] ?: "true"))
            }

            ActionType.SYSTEM_LOCATION -> {
                val current = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        app.getSystemService(LocationManager::class.java)?.isLocationEnabled
                    } else {
                        Settings.Secure.getInt(
                            app.contentResolver,
                            "location_mode",
                            0
                        ) != 0
                    }
                }.getOrNull()
                booleanState(current, (action.config["enabled"] ?: "true"))
            }

            ActionType.SYSTEM_STAY_AWAKE -> {
                val current = runCatching {
                    Settings.Global.getInt(
                        app.contentResolver,
                        Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                        0
                    ) != 0
                }.getOrNull()
                booleanState(current, (action.config["enabled"] ?: "true"))
            }

            ActionType.SYSTEM_AUTO_BRIGHTNESS -> {
                val current = runCatching {
                    Settings.System.getInt(
                        app.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                }.getOrNull()
                booleanState(current, (action.config["enabled"] ?: "true"))
            }

            ActionType.SYSTEM_DARK_MODE -> {
                val current = runCatching {
                    (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
                }.getOrNull()
                booleanState(current, (action.config["enabled"] ?: "true"))
            }

            ActionType.SYSTEM_SCREEN_ROTATION -> {
                val current = systemBool(app, Settings.System.ACCELEROMETER_ROTATION)
                booleanState(current, (action.config["autoRotate"] ?: "true"))
            }

            ActionType.SYSTEM_COLOR_INVERSION ->
                booleanState(
                    secureBool(app, "accessibility_display_inversion_enabled"),
                    action.config["enabled"] ?: "true"
                )

            ActionType.SYSTEM_GRAYSCALE ->
                booleanState(
                    secureBool(app, "accessibility_display_daltonizer_enabled"),
                    action.config["enabled"] ?: "true"
                )

            ActionType.SYSTEM_EXTRA_DIM ->
                booleanState(
                    secureBool(app, "reduce_bright_colors_activated"),
                    action.config["enabled"] ?: "true"
                )

            ActionType.SYSTEM_NIGHT_LIGHT ->
                booleanState(
                    secureBool(app, "night_display_activated"),
                    action.config["enabled"] ?: "true"
                )

            ActionType.SYSTEM_HAPTIC_FEEDBACK ->
                booleanState(systemBool(app, "haptic_feedback_enabled"), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_SOUND_EFFECTS ->
                booleanState(systemBool(app, "sound_effects_enabled"), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_DATA_SAVER ->
                booleanState(globalBool(app, "data_saver"), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_SCREENSAVER ->
                booleanState(secureBool(app, "screensaver_enabled"), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_ALWAYS_ON_DISPLAY ->
                booleanState(secureBool(app, "always_on_display_enabled"), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_SHOW_TAPS ->
                booleanState(systemBool(app, "show_touches"), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_POINTER_LOCATION ->
                booleanState(systemBool(app, "pointer_location"), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_ADAPTIVE_BATTERY ->
                booleanState(
                    globalBool(app, "adaptive_battery_management_enabled"),
                    action.config["enabled"] ?: "true"
                )

            ActionType.SYSTEM_AUTO_TIME ->
                booleanState(globalBool(app, "auto_time"), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_AUTO_TIMEZONE ->
                booleanState(globalBool(app, "auto_time_zone"), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_CAMERA_SHUTTER_SOUND ->
                booleanState(systemBool(app, "camera_sound"), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_WIFI_SCANNING ->
                booleanState(globalBool(app, "wifi_scan_always_enabled"), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_DATA_ROAMING ->
                booleanState(globalBool(app, "data_roaming"), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_CALL_VIBRATION ->
                booleanState(systemBool(app, "vibrate_when_ringing"), (action.config["enabled"] ?: "true"))

            ActionType.SYSTEM_BRIGHTNESS -> {
                val current = runCatching {
                    Settings.System.getInt(
                        app.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS
                    )
                }.getOrNull()
                numberState(
                    current = current,
                    expected = (action.config["value"] ?: "128").toIntOrNull()?.coerceIn(0, 255)
                )
            }

            ActionType.SYSTEM_VOLUME -> {
                val audio = app.getSystemService(AudioManager::class.java)
                streamVolumeState(audio, AudioManager.STREAM_MUSIC, (action.config["value"] ?: "50"))
            }

            ActionType.SYSTEM_STREAM_VOLUME -> {
                val audio = app.getSystemService(AudioManager::class.java)
                val stream = AudioStreams.streamId(action.config["stream"] ?: "MUSIC")
                streamVolumeState(audio, stream, (action.config["value"] ?: "50"))
            }

            ActionType.SYSTEM_RING_VOLUME -> {
                val audio = app.getSystemService(AudioManager::class.java)
                streamVolumeState(audio, AudioManager.STREAM_RING, (action.config["value"] ?: "50"))
            }

            ActionType.SYSTEM_SCREEN_TIMEOUT -> {
                val currentSeconds = runCatching {
                    Settings.System.getInt(
                        app.contentResolver,
                        Settings.System.SCREEN_OFF_TIMEOUT
                    ) / 1000
                }.getOrNull()
                durationState(
                    currentSeconds,
                    (action.config["seconds"] ?: "60").toIntOrNull()?.coerceIn(10, 1800)
                )
            }

            ActionType.SYSTEM_RINGER_MODE -> {
                val current = runCatching {
                    app.getSystemService(AudioManager::class.java)?.ringerMode?.let(::ringerModeName)
                }.getOrNull()
                textState(
                    kind = ActionStateValueKind.RINGER_MODE,
                    current = current,
                    expected = (action.config["mode"] ?: "NORMAL").uppercase()
                )
            }

            ActionType.SYSTEM_POINTER_SPEED -> {
                val current = runCatching {
                    Settings.System.getInt(app.contentResolver, "pointer_speed", 0)
                }.getOrNull()
                numberState(
                    current,
                    (action.config["speed"] ?: "0").toIntOrNull()?.coerceIn(-7, 7)
                )
            }

            ActionType.SYSTEM_SCREENSAVER_TIMEOUT -> {
                val currentMinutes = runCatching {
                    Settings.Secure.getInt(app.contentResolver, "screensaver_timeout") / 60_000
                }.getOrNull()
                durationState(
                    currentMinutes?.times(60),
                    (action.config["minutes"] ?: "10").toIntOrNull()?.coerceIn(1, 1440)?.times(60)
                )
            }

            ActionType.SYSTEM_FONT_SCALE -> {
                val current = runCatching {
                    Settings.System.getFloat(app.contentResolver, "font_scale", 1f)
                }.getOrNull()
                decimalState(current, (action.config["scale"] ?: "1.0").toFloatOrNull())
            }

            ActionType.SYSTEM_DISPLAY_DENSITY -> {
                val current = runCatching {
                    Settings.Global.getString(app.contentResolver, "display_density_forced")
                        ?.toIntOrNull()
                        ?: app.resources.displayMetrics.densityDpi
                }.getOrNull()
                numberState(
                    current,
                    (action.config["dpi"] ?: action.config["density"] ?: "440").toIntOrNull()
                )
            }

            ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD -> {
                val current = runCatching {
                    Settings.Global.getInt(app.contentResolver, "low_power_trigger_level")
                }.getOrNull()
                numberState(
                    current,
                    (action.config["percent"] ?: action.config["level"] ?: "20")
                        .toIntOrNull()
                        ?.coerceIn(0, 100)
                )
            }

            else -> null
        }
    }

    private fun streamVolumeState(
        audio: AudioManager?,
        stream: Int,
        configured: String?
    ): ActionCurrentState {
        val current = runCatching { audio?.getStreamVolume(stream) }.getOrNull()
        val max = runCatching { audio?.getStreamMaxVolume(stream) }.getOrNull()
        val expected = configured?.toIntOrNull()?.let { value ->
            if (max != null) value.coerceIn(0, max) else null
        }
        return numberState(current, expected)
    }

    private fun booleanState(current: Boolean?, configured: String?): ActionCurrentState {
        val expected = configured?.toBooleanStrictOrNull()
        return ActionCurrentState(
            kind = ActionStateValueKind.BOOLEAN,
            rawValue = current?.toString(),
            matchesTarget = if (current != null && expected != null) current == expected else null
        )
    }

    private fun numberState(current: Int?, expected: Int?): ActionCurrentState =
        ActionCurrentState(
            kind = ActionStateValueKind.NUMBER,
            rawValue = current?.toString(),
            matchesTarget = if (current != null && expected != null) current == expected else null
        )

    private fun durationState(currentSeconds: Int?, expectedSeconds: Int?): ActionCurrentState =
        ActionCurrentState(
            kind = ActionStateValueKind.DURATION_SECONDS,
            rawValue = currentSeconds?.toString(),
            matchesTarget = if (currentSeconds != null && expectedSeconds != null) {
                currentSeconds == expectedSeconds
            } else {
                null
            }
        )

    private fun decimalState(current: Float?, expected: Float?): ActionCurrentState =
        ActionCurrentState(
            kind = ActionStateValueKind.DECIMAL,
            rawValue = current?.let(::trimFloat),
            matchesTarget = if (current != null && expected != null) {
                abs(current - expected) < 0.001f
            } else {
                null
            }
        )

    private fun textState(
        kind: ActionStateValueKind,
        current: String?,
        expected: String?
    ): ActionCurrentState =
        ActionCurrentState(
            kind = kind,
            rawValue = current,
            matchesTarget = if (current != null && expected != null) {
                current.equals(expected, ignoreCase = true)
            } else {
                null
            }
        )

    private fun globalBool(context: Context, key: String): Boolean? = runCatching {
        Settings.Global.getInt(context.contentResolver, key) == 1
    }.getOrNull()

    private fun secureBool(context: Context, key: String): Boolean? = runCatching {
        Settings.Secure.getInt(context.contentResolver, key) == 1
    }.getOrNull()

    private fun systemBool(context: Context, key: String): Boolean? = runCatching {
        Settings.System.getInt(context.contentResolver, key) == 1
    }.getOrNull()

    private fun ringerModeName(mode: Int): String = when (mode) {
        AudioManager.RINGER_MODE_SILENT -> "SILENT"
        AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
        else -> "NORMAL"
    }

    private fun trimFloat(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString()
}
