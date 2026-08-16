package com.nexaflow.core.execution

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import com.nexaflow.core.rom.RomIntegrationManager
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType

/**
 * Captures the state of the settings a task may change, so that when the
 * task's condition ends the device can be restored to its original state
 * ("revert on exit") instead of running custom exit actions.
 *
 * Every toggle is captured best-effort into a nullable field: an unreadable
 * setting (unusual ROM, missing permission) stores `null` and is simply
 * skipped when restoring, so a capture failure can never block a run.
 */
class DeviceStateSnapshot private constructor(
    private val musicVolume: Int,
    private val ringVolume: Int,
    private val notificationVolume: Int,
    private val alarmVolume: Int,
    private val voiceCallVolume: Int,
    private val systemVolume: Int,
    private val dtmfVolume: Int,
    private val accessibilityVolume: Int,
    private val ringerMode: Int,
    private val brightness: Int,
    private val autoBrightness: Boolean,
    private val screenTimeout: Int,
    private val stayAwake: Boolean,
    private val autoRotate: Boolean,
    private val darkMode: Boolean,
    private val wifiEnabled: Boolean?,
    private val bluetoothEnabled: Boolean?,
    private val nfcEnabled: Boolean?,
    private val mobileDataEnabled: Boolean?,
    private val networkMode: String?,
    private val hotspotEnabled: Boolean?,
    private val airplaneModeEnabled: Boolean?,
    private val dndEnabled: Boolean?,
    private val flashlightEnabled: Boolean?,
    private val powerSaverEnabled: Boolean?,
    private val animationsEnabled: Boolean?,
    private val locationEnabled: Boolean?,
    private val ringtoneUri: String?,
    private val colorInversion: Boolean?,
    private val grayscale: Boolean?,
    private val extraDim: Boolean?,
    private val nightLight: Boolean?,
    private val hapticFeedback: Boolean?,
    private val soundEffects: Boolean?
) {

    fun restore(context: Context) {
        restoreAudio(context)
        restoreDisplaySettings(context)
        val controller = RomIntegrationManager.controller(context)
        restoreToggle(controller::setWifi, wifiEnabled)
        restoreToggle(controller::setBluetooth, bluetoothEnabled)
        restoreToggle(controller::setNfc, nfcEnabled)
        restoreToggle(controller::setMobileData, mobileDataEnabled)
        networkMode?.let {
            runCatching { controller.setNetworkMode(it) }
        }
        restoreToggle(controller::setHotspot, hotspotEnabled)
        restoreToggle(controller::setAirplaneMode, airplaneModeEnabled)
        restoreToggle(controller::setDoNotDisturb, dndEnabled)
        restoreToggle(controller::setFlashlight, flashlightEnabled)
        restoreToggle(controller::setPowerSaver, powerSaverEnabled)
        restoreToggle(controller::setAnimations, animationsEnabled)
        restoreToggle(controller::setLocationEnabled, locationEnabled)
        restoreSettingsToggle(controller, "SECURE", "accessibility_display_inversion_enabled", colorInversion)
        restoreSettingsToggle(controller, "SECURE", "accessibility_display_daltonizer_enabled", grayscale)
        restoreSettingsToggle(controller, "SECURE", "reduce_bright_colors_activated", extraDim)
        restoreSettingsToggle(controller, "SECURE", "night_display_activated", nightLight)
        restoreSettingsToggle(controller, "SYSTEM", "haptic_feedback_enabled", hapticFeedback)
        restoreSettingsToggle(controller, "SYSTEM", "sound_effects_enabled", soundEffects)
    }

    /**
     * Restores a single setting — used by the per-action adaptive end behavior
     * (an action configured with "restore original" only reverts itself, not
     * the whole device). The full [action] is passed so stream actions know
     * which stream to restore.
     */
    fun restoreSetting(context: Context, action: Action): SystemControlResult {
        val type = action.type
        val controller = RomIntegrationManager.controller(context)
        return when (type) {
            ActionType.SYSTEM_WIFI -> restoreToggle(controller::setWifi, wifiEnabled)
            ActionType.SYSTEM_BLUETOOTH -> restoreToggle(controller::setBluetooth, bluetoothEnabled)
            ActionType.SYSTEM_NFC -> restoreToggle(controller::setNfc, nfcEnabled)
            ActionType.SYSTEM_MOBILE_DATA -> restoreToggle(controller::setMobileData, mobileDataEnabled)
            ActionType.SYSTEM_NETWORK_MODE -> {
                val mode = networkMode ?: return SystemControlResult.ok("Nothing to restore")
                runCatching { controller.setNetworkMode(mode) }.getOrElse {
                    SystemControlResult.fail("Restore failed: ${it.message}")
                }
            }
            ActionType.SYSTEM_HOTSPOT -> restoreToggle(controller::setHotspot, hotspotEnabled)
            ActionType.SYSTEM_AIRPLANE_MODE -> restoreToggle(controller::setAirplaneMode, airplaneModeEnabled)
            ActionType.SYSTEM_DND -> restoreToggle(controller::setDoNotDisturb, dndEnabled)
            ActionType.SYSTEM_FLASHLIGHT -> restoreToggle(controller::setFlashlight, flashlightEnabled)
            ActionType.SYSTEM_POWER_SAVER -> restoreToggle(controller::setPowerSaver, powerSaverEnabled)
            ActionType.SYSTEM_ANIMATIONS -> restoreToggle(controller::setAnimations, animationsEnabled)
            ActionType.SYSTEM_LOCATION -> restoreToggle(controller::setLocationEnabled, locationEnabled)
            ActionType.SYSTEM_STAY_AWAKE -> restoreToggle(controller::setStayAwake, stayAwake)
            ActionType.SYSTEM_AUTO_BRIGHTNESS -> restoreToggle(controller::setAutoBrightness, autoBrightness)
            ActionType.SYSTEM_DARK_MODE -> restoreToggle(controller::setDarkMode, darkMode)
            ActionType.SYSTEM_COLOR_INVERSION ->
                restoreSettingsToggle(controller, "SECURE", "accessibility_display_inversion_enabled", colorInversion)
            ActionType.SYSTEM_GRAYSCALE ->
                restoreSettingsToggle(controller, "SECURE", "accessibility_display_daltonizer_enabled", grayscale)
            ActionType.SYSTEM_EXTRA_DIM ->
                restoreSettingsToggle(controller, "SECURE", "reduce_bright_colors_activated", extraDim)
            ActionType.SYSTEM_NIGHT_LIGHT ->
                restoreSettingsToggle(controller, "SECURE", "night_display_activated", nightLight)
            ActionType.SYSTEM_HAPTIC_FEEDBACK ->
                restoreSettingsToggle(controller, "SYSTEM", "haptic_feedback_enabled", hapticFeedback)
            ActionType.SYSTEM_SOUND_EFFECTS ->
                restoreSettingsToggle(controller, "SYSTEM", "sound_effects_enabled", soundEffects)
            ActionType.SYSTEM_SCREEN_ROTATION -> restoreToggle(controller::setScreenRotation, autoRotate)
            ActionType.SYSTEM_BRIGHTNESS ->
                runCatching { controller.setBrightness(brightness) }.getOrElse { SystemControlResult.fail(it.message ?: "restore failed") }
            ActionType.SYSTEM_VOLUME ->
                runCatching { controller.setVolume(AudioManager.STREAM_MUSIC, musicVolume) }.getOrElse { SystemControlResult.fail(it.message ?: "restore failed") }
            ActionType.SYSTEM_RING_VOLUME ->
                runCatching { controller.setRingVolume(ringVolume) }.getOrElse { SystemControlResult.fail(it.message ?: "restore failed") }
            ActionType.SYSTEM_STREAM_VOLUME ->
                runCatching {
                    val stream = AudioStreams.streamId(action.config["stream"] ?: "MUSIC")
                    controller.setVolume(stream, capturedStreamVolume(stream))
                }.getOrElse { SystemControlResult.fail(it.message ?: "restore failed") }
            ActionType.SYSTEM_RINGER_MODE ->
                runCatching { controller.setRingerMode(ringerModeName(ringerMode)) }.getOrElse { SystemControlResult.fail(it.message ?: "restore failed") }
            ActionType.SYSTEM_SET_RINGTONE -> {
                val uri = ringtoneUri ?: return SystemControlResult.ok("Nothing to restore")
                runCatching { controller.setRingtone(uri) }.getOrElse {
                    SystemControlResult.fail("Restore failed: ${it.message}")
                }
            }
            ActionType.SYSTEM_SCREEN_TIMEOUT ->
                runCatching { controller.setScreenTimeout(screenTimeout / 1000) }.getOrElse { SystemControlResult.fail(it.message ?: "restore failed") }
            else -> SystemControlResult.ok("Nothing to restore for ${type.name}")
        }
    }

    /** Best-effort original volume of a stream captured at snapshot time. */
    private fun capturedStreamVolume(stream: Int): Int = when (stream) {
        AudioManager.STREAM_MUSIC -> musicVolume
        AudioManager.STREAM_RING -> ringVolume
        AudioManager.STREAM_NOTIFICATION -> notificationVolume
        AudioManager.STREAM_ALARM -> alarmVolume
        AudioManager.STREAM_VOICE_CALL -> voiceCallVolume
        AudioManager.STREAM_SYSTEM -> systemVolume
        AudioManager.STREAM_DTMF -> dtmfVolume
        AudioManager.STREAM_ACCESSIBILITY -> accessibilityVolume
        else -> musicVolume
    }

    private fun restoreAudio(context: Context) {
        runCatching {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, musicVolume, 0)
            audio.setStreamVolume(AudioManager.STREAM_RING, ringVolume, 0)
            audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, notificationVolume, 0)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, alarmVolume, 0)
            audio.setStreamVolume(AudioManager.STREAM_VOICE_CALL, voiceCallVolume, 0)
            audio.setStreamVolume(AudioManager.STREAM_SYSTEM, systemVolume, 0)
            audio.setStreamVolume(AudioManager.STREAM_DTMF, dtmfVolume, 0)
            audio.setStreamVolume(AudioManager.STREAM_ACCESSIBILITY, accessibilityVolume, 0)
            audio.ringerMode = ringerMode
        }
    }

    private fun restoreDisplaySettings(context: Context) {
        runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (autoBrightness) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, screenTimeout)
            Settings.System.putInt(context.contentResolver, "stay_on_while_plugged_in", if (stayAwake) 1 else 0)
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (autoRotate) 1 else 0)
            Settings.Secure.putInt(context.contentResolver, "ui_night_mode", if (darkMode) 2 else 1)
        }
    }

    private fun restoreToggle(
        setter: (Boolean) -> SystemControlResult,
        captured: Boolean?
    ): SystemControlResult {
        val value = captured ?: return SystemControlResult.ok("Nothing to restore")
        return runCatching { setter(value) }.getOrElse {
            SystemControlResult.fail("Restore failed: ${it.message}")
        }
    }

    private fun restoreSettingsToggle(
        controller: com.nexaflow.core.rom.SystemController,
        namespace: String,
        key: String,
        captured: Boolean?
    ): SystemControlResult {
        val value = captured ?: return SystemControlResult.ok("Nothing to restore")
        return runCatching {
            controller.writeSetting(namespace, key, if (value) "1" else "0")
        }.getOrElse {
            SystemControlResult.fail("Restore failed: ${it.message}")
        }
    }

    companion object {
        fun capture(context: Context): DeviceStateSnapshot {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            return DeviceStateSnapshot(
                musicVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                ringVolume = audio.getStreamVolume(AudioManager.STREAM_RING),
                notificationVolume = audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION),
                alarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM),
                voiceCallVolume = audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL),
                systemVolume = audio.getStreamVolume(AudioManager.STREAM_SYSTEM),
                dtmfVolume = audio.getStreamVolume(AudioManager.STREAM_DTMF),
                accessibilityVolume = audio.getStreamVolume(AudioManager.STREAM_ACCESSIBILITY),
                ringerMode = audio.ringerMode,
                brightness = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    128
                ),
                autoBrightness = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                screenTimeout = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    30000
                ),
                stayAwake = Settings.System.getInt(
                    context.contentResolver,
                    "stay_on_while_plugged_in",
                    0
                ) == 1,
                autoRotate = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.ACCELEROMETER_ROTATION,
                    0
                ) == 1,
                darkMode = Settings.Secure.getInt(
                    context.contentResolver,
                    "ui_night_mode",
                    1
                ) == 2,
                wifiEnabled = globalBool(context, Settings.Global.WIFI_ON),
                bluetoothEnabled = globalBool(context, Settings.Global.BLUETOOTH_ON),
                nfcEnabled = globalBool(context, "nfc_on"),
                mobileDataEnabled = globalBool(context, "mobile_data"),
                networkMode = runCatching {
                    Settings.Global.getInt(context.contentResolver, "preferred_network_mode", -1)
                }.getOrNull()?.takeIf { it >= 0 }?.toString(),
                colorInversion = secureBool(context, "accessibility_display_inversion_enabled"),
                grayscale = secureBool(context, "accessibility_display_daltonizer_enabled"),
                extraDim = secureBool(context, "reduce_bright_colors_activated"),
                nightLight = secureBool(context, "night_display_activated"),
                hapticFeedback = systemBool(context, "haptic_feedback_enabled"),
                soundEffects = systemBool(context, "sound_effects_enabled"),
                hotspotEnabled = globalBool(context, "tether_on"),
                airplaneModeEnabled = globalBool(context, Settings.Global.AIRPLANE_MODE_ON),
                dndEnabled = runCatching {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    nm.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                }.getOrNull(),
                // There is no public API to read the current torch state, so a
                // flashlight "restore original" cannot be captured. The catalog
                // therefore never offers REVERT for the flashlight action.
                flashlightEnabled = null,
                powerSaverEnabled = globalBool(context, "low_power"),
                animationsEnabled = globalFloat(context, Settings.Global.ANIMATOR_DURATION_SCALE)
                    ?.let { it > 0f },
                // "location_mode" (LOCATION_MODE) is deprecated in newer SDKs
                // but is the only portable way to read the master switch.
                locationEnabled = runCatching {
                    Settings.Secure.getInt(
                        context.contentResolver,
                        "location_mode",
                        0
                    ) != 0
                }.getOrNull(),
                ringtoneUri = RomIntegrationManager.controller(context).currentDefaultRingtone()
            )
        }

        private fun globalBool(context: Context, name: String): Boolean? = runCatching {
            Settings.Global.getInt(context.contentResolver, name) == 1
        }.getOrNull()

        private fun secureBool(context: Context, name: String): Boolean? = runCatching {
            Settings.Secure.getInt(context.contentResolver, name, 0) == 1
        }.getOrNull()

        private fun systemBool(context: Context, name: String): Boolean? = runCatching {
            Settings.System.getInt(context.contentResolver, name, 0) == 1
        }.getOrNull()

        private fun globalFloat(context: Context, name: String): Float? = runCatching {
            Settings.Global.getFloat(context.contentResolver, name)
        }.getOrNull()

        private fun ringerModeName(mode: Int): String = when (mode) {
            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
            AudioManager.RINGER_MODE_SILENT -> "SILENT"
            else -> "NORMAL"
        }
    }
}
