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
 *
 * Interactive actions: "Run task now" (when the reminder was scheduled from a
 * task — routes to the engine via the shared [NotificationActionReceiver]) and
 * "Dismiss" (cancels this notification through [NotificationDismissReceiver]).
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOW_REMINDER) return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val text = intent.getStringExtra(EXTRA_TEXT) ?: "You asked me to remind you."
        val automationId = intent.getStringExtra(EXTRA_AUTOMATION_ID)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "NexaFlow Reminders",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow)
            // M3: brand-tinted small icon + action icons; reminder semantics.
            // Colorized (API 31+) fills the header with the brand color.
            .setColor(context.getColor(com.nexaflow.core.rom.R.color.notification_brand_color))
            .setColorized(true)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        // Run the enclosing task straight from the reminder when the scheduler
        // carried its id; the shared action receiver routes it to the engine.
        if (!automationId.isNullOrBlank()) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow,
                    context.getString(R.string.notification_action_run_now),
                    NotificationActionButtons.buildRunNowPendingIntent(context, automationId)
                ).build()
            )
        }
        builder.addAction(
            NotificationCompat.Action.Builder(
                com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow,
                context.getString(R.string.notification_action_dismiss),
                NotificationActionButtons.buildDismissPendingIntent(context, NOTIFICATION_ID)
            ).build()
        )
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        const val ACTION_SHOW_REMINDER = "com.nexaflow.core.execution.action.SHOW_REMINDER"
        const val EXTRA_TITLE = "com.nexaflow.core.execution.extra.REMINDER_TITLE"
        const val EXTRA_TEXT = "com.nexaflow.core.execution.extra.REMINDER_TEXT"
        const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "nexaflow_reminders"
    }
}
