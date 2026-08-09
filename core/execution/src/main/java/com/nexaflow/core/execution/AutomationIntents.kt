package com.nexaflow.core.execution

const val ACTION_AUTOMATIONS_CHANGED = "com.nexaflow.core.execution.action.AUTOMATIONS_CHANGED"

/**
 * Action delivered by the notification action buttons attached to NexaFlow
 * notifications. The [NotificationActionReceiver] routes it to the engine,
 * which runs the task whose id arrives as [EXTRA_AUTOMATION_ID].
 */
const val ACTION_RUN_TASK_FROM_NOTIFICATION =
    "com.nexaflow.core.execution.action.RUN_TASK_FROM_NOTIFICATION"

/**
 * Action delivered by the special "restore original state" button attached to
 * notifications of tasks with revertOnExit. The [NotificationActionReceiver]
 * routes it to the engine's `runExit`, which restores the device to its
 * pre-run state directly from the notification.
 */
const val ACTION_REVERT_TASK_FROM_NOTIFICATION =
    "com.nexaflow.core.execution.action.REVERT_TASK_FROM_NOTIFICATION"

/**
 * Action delivered by a "Dismiss" button attached to a NexaFlow notification
 * (reminders, battery alerts...). [NotificationDismissReceiver] cancels the
 * notification whose id arrives as [EXTRA_NOTIFICATION_ID].
 */
const val ACTION_DISMISS_NOTIFICATION =
    "com.nexaflow.core.execution.action.DISMISS_NOTIFICATION"

/** Carries the automation id to run when a notification action button is tapped. */
const val EXTRA_AUTOMATION_ID = "com.nexaflow.core.execution.extra.AUTOMATION_ID"

/** Carries the notification id to cancel when a dismiss button is tapped. */
const val EXTRA_NOTIFICATION_ID = "com.nexaflow.core.execution.extra.NOTIFICATION_ID"

/**
 * Carries the name of the global variable (without `%`) that receives the
 * text typed into a reply action button's RemoteInput field.
 */
const val EXTRA_REPLY_VARIABLE = "com.nexaflow.core.execution.extra.REPLY_VARIABLE"

/**
 * Key under which the reply text is delivered via [androidx.core.app.RemoteInput.getResultsFromIntent].
 * Must stay stable — it is baked into the PendingIntent/RemoteInput contract.
 */
const val REMOTE_INPUT_REPLY_KEY = "com.nexaflow.core.execution.remote_input.reply"

/**
 * Port the loopback webhook server listens on (see WebhookServer). Shared with
 * the builder UI so the URL hint stays in sync with the engine.
 */
const val WEBHOOK_DEFAULT_PORT = 8765
