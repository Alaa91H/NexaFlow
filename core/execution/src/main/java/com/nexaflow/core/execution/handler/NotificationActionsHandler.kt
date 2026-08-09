package com.nexaflow.core.execution.handler

import com.nexaflow.core.execution.NotificationAccess
import com.nexaflow.core.execution.NotificationActionButton
import com.nexaflow.core.execution.NotificationActionButtons
import com.nexaflow.core.execution.R
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType

/** Send/block/clear notifications, reminders, and battery alerts. */
class NotificationActionsHandler : ActionHandler {
    override val supportedTypes: Set<ActionType> = setOf(
        ActionType.SYSTEM_SEND_NOTIFICATION,
        ActionType.SYSTEM_BLOCK_NOTIFICATION,
        ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS,
        ActionType.SYSTEM_CLEAR_NOTIFICATIONS,
        ActionType.SYSTEM_SEND_REMINDER,
        ActionType.BATTERY_ALERTS,
        ActionType.BATTERY_CHARGING_NOTIFICATIONS
    )

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        val notif = ctx.notificationSettings
        return when (action.type) {
            ActionType.SYSTEM_SEND_NOTIFICATION ->
                if (notif.enabled && notif.executionEnabled) {
                    // Optional interactive buttons: each runs a configured task
                    // straight from the notification via a PendingIntent broadcast.
                    val buttons = NotificationActionButton.fromConfig(action.config["action_buttons"])
                    // Tasks that restore their state on exit get a dedicated
                    // "restore original state" button so the user can trigger
                    // runExit directly from the notification (same receiver).
                    val actions = NotificationActionButtons.withRevertActionIfNeeded(
                        context = ctx.appContext,
                        actions = NotificationActionButtons.toNotificationActions(ctx.appContext, buttons),
                        automationId = ctx.automationId,
                        revertOnExit = ctx.revertOnExit,
                        label = ctx.appContext.getString(R.string.notification_action_revert)
                    )
                    ctx.controller.sendNotification(
                        action.config["title"] ?: "NexaFlow",
                        action.config["text"] ?: "Automation executed",
                        sound = action.config["sound"] ?: "DEFAULT",
                        actions = actions
                    )
                } else {
                    SystemControlResult.ok("Notifications disabled")
                }
            ActionType.SYSTEM_BLOCK_NOTIFICATION -> {
                val packages = (action.config["packages"] ?: action.config["package"] ?: "")
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val blocked = action.config["enabled"]?.toBoolean() ?: true
                if (packages.isEmpty()) {
                    SystemControlResult.fail("No app selected")
                } else {
                    packages.forEach { NotificationAccess.setBlocked(it, blocked) }
                    SystemControlResult.ok(
                        if (blocked) "Blocked notifications for ${packages.size} app(s)"
                        else "Unblocked notifications for ${packages.size} app(s)"
                    )
                }
            }
            ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS -> {
                val packages = (action.config["packages"] ?: action.config["package"] ?: "")
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (packages.isEmpty()) {
                    SystemControlResult.fail("No app selected")
                } else {
                    packages.forEach { NotificationAccess.clearForPackage(it) }
                    SystemControlResult.ok("Cleared notifications for ${packages.size} app(s)")
                }
            }
            ActionType.SYSTEM_CLEAR_NOTIFICATIONS ->
                ctx.controller.clearNotifications()
            ActionType.SYSTEM_SEND_REMINDER ->
                if (notif.enabled && notif.remindersEnabled) {
                    ReminderScheduler.schedule(
                        ctx.appContext,
                        action.config["title"] ?: "Reminder",
                        action.config["text"] ?: "",
                        action.config["hour"]?.toIntOrNull() ?: 9,
                        action.config["minute"]?.toIntOrNull() ?: 0,
                        automationId = ctx.automationId
                    )
                } else {
                    SystemControlResult.ok("Reminders disabled")
                }
            ActionType.BATTERY_ALERTS,
            ActionType.BATTERY_CHARGING_NOTIFICATIONS ->
                if (notif.enabled && notif.executionEnabled) {
                    val actions = NotificationActionButtons.withRevertActionIfNeeded(
                        context = ctx.appContext,
                        actions = NotificationActionButtons.toRunNowAndDismissActions(
                            ctx.appContext,
                            ctx.automationId,
                            com.nexaflow.core.rom.SystemController.ACTION_NOTIFICATION_ID
                        ),
                        automationId = ctx.automationId,
                        revertOnExit = ctx.revertOnExit,
                        label = ctx.appContext.getString(R.string.notification_action_revert)
                    )
                    ctx.controller.sendNotification(
                        "Battery Alert",
                        action.config["message"] ?: "Battery alert triggered",
                        sound = action.config["sound"] ?: "DEFAULT",
                        actions = actions
                    )
                } else {
                    SystemControlResult.ok("Notifications disabled")
                }
            else -> SystemControlResult.fail("Unsupported action ${action.type}")
        }
    }
}
