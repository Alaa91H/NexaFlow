package com.nexaflow.core.engine

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nexaflow.core.execution.NotificationAccess
import com.nexaflow.core.execution.NotificationListenerBridge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * System-bound notification listener. Two responsibilities:
 *
 *  1. **Notification trigger**: hands every posted/removed notification to
 *     [NotificationTriggerMonitor], which fires automations whose NOTIFICATION
 *     trigger matches the package and/or keyword.
 *  2. **Blocking**: when a SYSTEM_BLOCK_NOTIFICATION action is active for a
 *     package, any notification that app posts is cancelled immediately.
 *
 * The system binds this service only while the user has granted "Notification
 * access" in Android settings (managed via the Permission manager screen).
 */
@AndroidEntryPoint
class NotificationListener : NotificationListenerService(), NotificationListenerBridge {

    @Inject
    lateinit var monitor: NotificationTriggerMonitor

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Expose ourselves to the ExecutionEngine so blocking actions can
        // cancel notifications without holding a reference to the service.
        NotificationAccess.listener = this
        // The in-memory blocked set is empty after a process restart; re-apply
        // the blocks declared by enabled automations.
        monitor.restoreBlockedState()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (NotificationAccess.isBlocked(packageName)) {
            // Silenced app: cancel as soon as it posts.
            runCatching { cancelNotification(sbn.key) }
            return
        }
        // Never let our own notifications (reminders, alerts) trigger a task.
        if (packageName == this.packageName) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        monitor.onNotificationPosted(packageName, title, text)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        monitor.onNotificationRemoved(sbn.packageName, title, text)
    }

    /** Cancels every active notification from [packageName] (blocking start or clear action). */
    override fun cancelForPackage(packageName: String) {
        runCatching {
            activeNotifications
                .filter { it.packageName == packageName }
                .forEach { cancelNotification(it.key) }
        }
    }

    override fun onDestroy() {
        if (NotificationAccess.listener === this) {
            NotificationAccess.listener = null
        }
        super.onDestroy()
    }
}
