package com.nexaflow.core.execution.handler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nexaflow.core.execution.ReminderAlarmReceiver
import com.nexaflow.core.rom.model.SystemControlResult

/** Schedules a one-shot reminder notification via AlarmManager. */
object ReminderScheduler {

    fun schedule(
        context: Context,
        title: String,
        text: String,
        hour: Int,
        minute: Int
    ): SystemControlResult {
        return try {
            val zone = java.time.ZoneId.systemDefault()
            var at = java.time.ZonedDateTime
                .now(zone)
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0)
                .toInstant()
                .toEpochMilli()
            if (at <= System.currentTimeMillis()) {
                at = java.time.ZonedDateTime
                    .now(zone)
                    .plusDays(1)
                    .withHour(hour)
                    .withMinute(minute)
                    .withSecond(0)
                    .withNano(0)
                    .toInstant()
                    .toEpochMilli()
            }
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderAlarmReceiver::class.java)
                .setAction(ReminderAlarmReceiver.ACTION_SHOW_REMINDER)
                .putExtra(ReminderAlarmReceiver.EXTRA_TITLE, title.ifBlank { "Reminder" })
                .putExtra(ReminderAlarmReceiver.EXTRA_TEXT, text.ifBlank { "You asked me to remind you." })
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                at.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            }
            SystemControlResult.ok("Reminder scheduled")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to schedule reminder: ${t.message}")
        }
    }
}
