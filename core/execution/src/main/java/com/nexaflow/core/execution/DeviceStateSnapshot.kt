package com.nexaflow.core.execution

import android.content.Context
import android.media.AudioManager
import android.provider.Settings

/**
 * Captures the state of the settings a task may change, so that when the
 * task's condition ends the device can be restored to its original state
 * ("revert on exit") instead of running custom exit actions.
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
    private val darkMode: Boolean
) {

    fun restore(context: Context) {
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
                ) == 2
            )
        }
    }
}
