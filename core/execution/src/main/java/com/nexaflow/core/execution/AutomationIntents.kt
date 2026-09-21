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

const val WEBHOOK_DEFAULT_PORT = 8765

// ── Wearable Data Layer protocol ─────────────────────────────────────────────
// These path strings and DataMap keys are shared between the phone-side bridge
// (WearSyncManager / WearCommandListenerService) and the watch app's WearProtocol
// object.  Any change here must be reflected in
// wear/src/main/java/com/nexaflow/wear/data/WearProtocol.kt.

/** DataItem path: phone pushes the serialized automation list to the watch here. */
const val WEAR_PATH_AUTOMATIONS = "/nexaflow/automations"

/** MessageClient path: the watch sends a manual force-run request for one automation. */
const val WEAR_PATH_RUN_COMMAND = "/nexaflow/run"

/** MessageClient path: the watch sends an enable/disable toggle for one automation. */
const val WEAR_PATH_TOGGLE_COMMAND = "/nexaflow/toggle"

/** DataMap key carrying the JSON payload (automation list body). */
const val WEAR_KEY_PAYLOAD = "payload"

/**
 * DataMap key: monotonic epoch-millis timestamp that forces the Data Layer to
 * deliver a DATA_CHANGED event even when the JSON payload is identical to the
 * previous push. Without this, the platform de-duplicates identical DataItems.
 */
const val WEAR_KEY_UPDATED_AT = "updatedAt"

/** Separator between automationId and enabled-flag in a toggle message payload. */
const val WEAR_TOGGLE_SEPARATOR = ":"

/**
 * MessageClient path: the watch requests an immediate automation-list push.
 * Sent when the watch UI starts (or regains connectivity) so the user never
 * stares at an eternal "Connecting" spinner just because the phone process
 * started while the watch was away and no data change has happened since.
 * The phone answers by re-pushing the DataItem from [WearSyncManager].
 */
const val WEAR_PATH_SYNC_REQUEST = "/nexaflow/sync-request"

/** CapabilityClient capability name advertised by the phone companion app. */
const val WEAR_CAPABILITY_PHONE_APP = "nexaflow_phone_companion"
