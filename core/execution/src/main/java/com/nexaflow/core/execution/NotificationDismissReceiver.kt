package com.nexaflow.core.execution

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Cancels a NexaFlow notification when its \"Dismiss\" action button is tapped.
 *
 * Unlike [com.nexaflow.core.engine.NotificationActionReceiver] this receiver is
 * deliberately plain (no Hilt, no engine): dismissing a notification is a pure
 * UI action that only needs the notification id carried as
 * [EXTRA_NOTIFICATION_ID]. Keeping it engine-free means reminders, battery
 * alerts and any future notification can share the same dismiss path from
 * core/execution without touching the engine module.
 */
class NotificationDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISS_NOTIFICATION) return
        val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (id < 0) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id)
    }
}
