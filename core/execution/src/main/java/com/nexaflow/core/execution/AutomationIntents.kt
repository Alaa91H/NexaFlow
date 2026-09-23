package com.nexaflow.core.execution

import com.nexaflow.core.wearprotocol.WearProtocol

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

const val WEBHOOK_DEFAULT_PORT = 8765

// ── Wearable Data Layer protocol ─────────────────────────────────────────────
// Compatibility aliases live here because existing execution/app code imports
// them from core:execution. The literal values themselves are owned only by
// core:wear-protocol so phone and watch cannot silently drift apart.

const val WEAR_PROTOCOL_VERSION = WearProtocol.CURRENT_VERSION

const val WEAR_PATH_AUTOMATIONS = WearProtocol.PATH_AUTOMATIONS
const val WEAR_PATH_RUN_COMMAND = WearProtocol.PATH_RUN_COMMAND
const val WEAR_PATH_TOGGLE_COMMAND = WearProtocol.PATH_TOGGLE_COMMAND
const val WEAR_PATH_SYNC_REQUEST = WearProtocol.PATH_SYNC_REQUEST

const val WEAR_PATH_COMMAND_V1 = WearProtocol.PATH_COMMAND_V1
const val WEAR_PATH_EVENT_V1 = WearProtocol.PATH_EVENT_V1
const val WEAR_PATH_RESULT_V1 = WearProtocol.PATH_RESULT_V1
const val WEAR_PATH_DEVICE_STATE_V1 = WearProtocol.PATH_DEVICE_STATE_V1
const val WEAR_PATH_CAPABILITIES_V1 = WearProtocol.PATH_CAPABILITIES_V1

const val WEAR_KEY_PAYLOAD = WearProtocol.KEY_PAYLOAD
const val WEAR_KEY_UPDATED_AT = WearProtocol.KEY_UPDATED_AT
const val WEAR_KEY_PROTOCOL_VERSION = WearProtocol.KEY_PROTOCOL_VERSION
const val WEAR_KEY_MESSAGE_ID = WearProtocol.KEY_MESSAGE_ID

const val WEAR_TOGGLE_SEPARATOR = WearProtocol.TOGGLE_SEPARATOR

const val WEAR_CAPABILITY_PHONE_APP = WearProtocol.CAPABILITY_PHONE_APP
const val WEAR_CAPABILITY_WATCH_APP = WearProtocol.CAPABILITY_WATCH_APP
