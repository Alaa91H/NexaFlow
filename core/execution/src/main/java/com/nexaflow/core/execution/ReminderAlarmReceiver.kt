package com.nexaflow.core.execution

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Shows a scheduled reminder notification. Fired by AlarmManager through
 * ExecutionEngine's reminder scheduling.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOW_REMINDER) return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val text = intent.getStringExtra(EXTRA_TEXT) ?: "You asked me to remind you."

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "NexaFlow Reminders",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(3001, notification)
    }

    companion object {
        const val ACTION_SHOW_REMINDER = "com.nexaflow.core.execution.action.SHOW_REMINDER"
        const val EXTRA_TITLE = "com.nexaflow.core.execution.extra.REMINDER_TITLE"
        const val EXTRA_TEXT = "com.nexaflow.core.execution.extra.REMINDER_TEXT"
        private const val CHANNEL_ID = "nexaflow_reminders"
    }
}
