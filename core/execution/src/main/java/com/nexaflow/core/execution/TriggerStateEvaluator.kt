package com.nexaflow.core.execution

import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.Settings
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.schedule.TimeTriggerCalculator
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

/**
 * Best-effort evaluation of whether a task's trigger condition is currently
 * true, used by the manual "run now" gate: when the condition is satisfied
 * the task's actions run; otherwise the exit behavior ("when the task ends")
 * runs instead, so a manual run never executes actions whose condition is
 * not met.
 *
 * Trigger types that cannot be evaluated deterministically without their
 * live monitors (apps, SMS, location, sensors, webhook, calendar, ...)
 * report "satisfied" so a manual run is never blocked by an unknown state.
 */
object TriggerStateEvaluator {

    /** True when at least one trigger is currently satisfied (unknown = satisfied). */
    fun isSatisfied(context: Context, triggers: List<Trigger>): Boolean =
        triggers.isEmpty() || triggers.any { triggerSatisfied(context, it) }

    fun triggerSatisfied(context: Context, trigger: Trigger): Boolean {
        val c = trigger.config
        return when (trigger.type) {
            TriggerType.TIME -> timeTriggerSatisfied(c)
            TriggerType.RINGER_MODE -> ringerModeSatisfied(context, c)
            TriggerType.BATTERY -> batterySatisfied(context, c)
            TriggerType.HEADPHONE -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
                val wantConnected = (c["event"] ?: "CONNECTED") == "CONNECTED"
                audio.isWiredHeadsetOn == wantConnected
            }
            TriggerType.CHARGER -> {
                val battery = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return true
                val wantConnected = (c["event"] ?: "CONNECTED") == "CONNECTED"
                battery.isCharging == wantConnected
            }
            TriggerType.AIRPLANE_MODE -> {
                val on = Settings.Global.getInt(
                    context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0
                ) == 1
                ((c["state"] ?: "ON") == "ON") == on
            }
            TriggerType.DARK_MODE -> {
                val dark = (context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                ((c["state"] ?: "ON") == "ON") == dark
            }
            TriggerType.CALL_STATE -> {
                // Incoming/outgoing calls are satisfied while a call is live;
                // ENDED is satisfied once the line is free again.
                val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager ?: return true
                val busy = telephony.callState != android.telephony.TelephonyManager.CALL_STATE_IDLE
                when (c["event"] ?: "INCOMING") {
                    "ENDED" -> !busy
                    else -> busy
                }
            }
            TriggerType.MEDIA_PLAYING -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
                val playing = audio.isMusicActive
                (c["event"] ?: "STARTED") == "STARTED" == playing
            }
            TriggerType.VOLUME_CHANGED -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
                val stream = when (c["stream"] ?: "MUSIC") {
                    "RING" -> AudioManager.STREAM_RING
                    "ALARM" -> AudioManager.STREAM_ALARM
                    "NOTIFICATION" -> AudioManager.STREAM_NOTIFICATION
                    else -> AudioManager.STREAM_MUSIC
                }
                val level = audio.getStreamVolume(stream)
                val threshold = (c["threshold"] ?: "50").toIntOrNull() ?: 50
                if ((c["direction"] ?: "ABOVE") == "BELOW") level <= threshold else level >= threshold
            }
            // Install/remove events cannot be re-derived statically; an
            // unknown state never blocks a manual run.
            TriggerType.APP_INSTALLED -> true
            else -> true
        }
    }

    /**
     * TIME trigger: a range is active while "now" sits inside it (overnight
     * ranges wrap past midnight); a single time is active within a ±10 minute
     * window around the set time. The repeat schedule must match today too.
     */
    fun timeTriggerSatisfied(
        config: Map<String, String>,
        now: LocalTime = LocalTime.now(),
        today: LocalDate = LocalDate.now()
    ): Boolean {
        val repeat = config["repeat"] ?: TimeTriggerCalculator.REPEAT_DAILY
        if (!TimeTriggerCalculator.matchesRepeat(repeat, config, today)) return false
        return if (config["timeMode"] == "RANGE") {
            val start = parseTime(config["rangeStart"]) ?: return false
            val end = parseTime(config["rangeEnd"]) ?: return false
            if (end.isAfter(start)) {
                !now.isBefore(start) && now.isBefore(end)
            } else {
                // Overnight window (e.g. 22:00 -> 06:00).
                !now.isBefore(start) || now.isBefore(end)
            }
        } else {
            val target = parseTime(config["time"]) ?: return false
            val nowMinutes = now.hour * 60 + now.minute
            val targetMinutes = target.hour * 60 + target.minute
            abs(nowMinutes - targetMinutes) <= 10
        }
    }

    private fun parseTime(value: String?): LocalTime? =
        value?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    private fun ringerModeSatisfied(context: Context, config: Map<String, String>): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
        val wanted = config["mode"] ?: "NORMAL"
        val actual = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
            AudioManager.RINGER_MODE_SILENT -> "SILENT"
            else -> "NORMAL"
        }
        return wanted == actual
    }

    private fun batterySatisfied(context: Context, config: Map<String, String>): Boolean {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return true
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val threshold = (config["above"] ?: config["below"] ?: "80").toIntOrNull() ?: 80
        val direction = config["direction"] ?: "ABOVE"
        return if (direction == "BELOW") level <= threshold else level >= threshold
    }
}
