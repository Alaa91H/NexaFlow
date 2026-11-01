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
 *
 * The service itself only forwards: the projection and dispatch rules live in
 * the internal functions below, so they are covered by plain unit tests instead
 * of depending on a bound system service.
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
        val current = runCatching { activeNotifications.toActiveNotifications(packageName) }
            .getOrDefault(emptyList())
        monitor.reconcileActiveNotifications(current)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handleNotificationPosted(
            sbn = sbn,
            ownPackage = packageName,
            monitor = monitor,
            cancel = { key -> cancelNotification(key) },
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        handleNotificationRemoved(sbn, monitor)
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

/**
 * Projects the system's active notifications into the monitor's own data:
 * other apps only, minus the packages a blocking action silenced, and never the
 * listener's own notifications (reminders and alerts must not trigger a task).
 */
internal fun Array<StatusBarNotification>.toActiveNotifications(
    ownPackage: String,
): List<NotificationTriggerMonitor.ActiveNotification> = asSequence()
    .filter { it.packageName != ownPackage }
    .filterNot { NotificationAccess.isBlocked(it.packageName) }
    .map { it.toActiveNotification() }
    .toList()

internal fun StatusBarNotification.toActiveNotification(): NotificationTriggerMonitor.ActiveNotification {
    val extras = notification.extras
    return NotificationTriggerMonitor.ActiveNotification(
        key = key,
        packageName = packageName,
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        category = notification.category,
    )
}

/**
 * The posted-notification rules: a silenced package is cancelled on the spot,
 * our own notifications are ignored, and everything else reaches the monitor
 * with its platform category attached.
 */
internal fun handleNotificationPosted(
    sbn: StatusBarNotification,
    ownPackage: String,
    monitor: NotificationTriggerMonitor,
    cancel: (String) -> Unit,
) {
    val packageName = sbn.packageName
    if (NotificationAccess.isBlocked(packageName)) {
        // Silenced app: cancel as soon as it posts.
        runCatching { cancel(sbn.key) }
        return
    }
    // Never let our own notifications (reminders, alerts) trigger a task.
    if (packageName == ownPackage) return
    val extras = sbn.notification.extras
    monitor.onNotificationPosted(
        notificationKey = sbn.key,
        packageName = packageName,
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        category = sbn.notification.category,
    )
}

internal fun handleNotificationRemoved(
    sbn: StatusBarNotification,
    monitor: NotificationTriggerMonitor,
) {
    val extras = sbn.notification.extras
    monitor.onNotificationRemoved(
        notificationKey = sbn.key,
        packageName = sbn.packageName,
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        category = sbn.notification.category,
    )
}
