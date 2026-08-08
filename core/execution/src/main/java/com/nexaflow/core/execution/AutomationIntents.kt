package com.nexaflow.core.execution

const val ACTION_AUTOMATIONS_CHANGED = "com.nexaflow.core.execution.action.AUTOMATIONS_CHANGED"

/**
 * Action delivered by the notification action buttons attached to NexaFlow
 * notifications. The [NotificationActionReceiver] routes it to the engine,
 * which runs the task whose id arrives as [EXTRA_AUTOMATION_ID].
 */
const val ACTION_RUN_TASK_FROM_NOTIFICATION =
    "com.nexaflow.core.execution.action.RUN_TASK_FROM_NOTIFICATION"

/** Carries the automation id to run when a notification action button is tapped. */
const val EXTRA_AUTOMATION_ID = "com.nexaflow.core.execution.extra.AUTOMATION_ID"
