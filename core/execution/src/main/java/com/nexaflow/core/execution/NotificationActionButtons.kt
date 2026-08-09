package com.nexaflow.core.execution

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import org.json.JSONArray
import org.json.JSONObject

/**
 * One interactive action button attached to a NexaFlow notification. Tapping
 * the button broadcasts [ACTION_RUN_TASK_FROM_NOTIFICATION] with
 * [EXTRA_AUTOMATION_ID], which [com.nexaflow.core.engine.NotificationActionReceiver]
 * routes to the execution engine.
 *
 * Buttons are stored inside the action's `action_buttons` config value as a
 * compact JSON array (label + automation id), so they survive DB round-trips
 * and backup/restore without any schema change.
 *
 * The label is a static snapshot taken when the button is configured — it is
 * deliberately excluded from %variable substitution (the JSON must stay
 * structurally intact), so renaming the target task later leaves the old label
 * on the button while the id keeps routing correctly.
 *
 * When [replyVariable] is set the button turns into a reply action (Tasker-style
 * "ask for input"): the notification shows a text field, and the typed reply
 * is stored into the `%replyVariable` global variable before the task runs,
 * so subsequent actions can reference `%replyVariable`.
 */
data class NotificationActionButton(
    val label: String,
    val automationId: String,
    /** Optional global variable name (without `%`) receiving the typed reply. */
    val replyVariable: String? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("label", label)
        .put("id", automationId)
        .put("replyVariable", replyVariable ?: "")

    companion object {
        /** Serializes a list of buttons to the `action_buttons` config value. */
        fun toConfig(buttons: List<NotificationActionButton>): String =
            JSONArray(buttons.map { it.toJson() }).toString()

        /** Parses the `action_buttons` config value; malformed/blank → empty list. */
        fun fromConfig(json: String?): List<NotificationActionButton> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { index ->
                    val obj = array.optJSONObject(index) ?: return@mapNotNull null
                    val label = obj.optString("label").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val replyVariable = obj.optString("replyVariable").takeIf { it.isNotBlank() }
                    NotificationActionButton(label, id, replyVariable)
                }
            }.getOrDefault(emptyList())
        }
    }
}

/**
 * Builds the PendingIntents that drive notification action buttons.
 *
 * The receiver class is referenced by name (explicit component) so this module
 * never needs a compile-time dependency on the engine module that hosts it —
 * the component is resolved against the merged manifest at runtime.
 */
object NotificationActionButtons {

    const val RECEIVER_CLASS = "com.nexaflow.core.engine.NotificationActionReceiver"
    /**
     * The dismiss button is handled by a plain receiver in core/execution (no
     * Hilt / engine needed — it only cancels a notification by id). Named by
     * string so this module never needs a runtime lookup through DI.
     */
    const val DISMISS_RECEIVER_CLASS = "com.nexaflow.core.execution.NotificationDismissReceiver"
    private const val BASE_REQUEST_CODE = 52000
    private const val DISMISS_REQUEST_CODE_BASE = 56000
    // Revert-button request codes live above the user-button range (which is
    // BASE_REQUEST_CODE + index). The code is derived from the automation id
    // so every task's button is an independent PendingIntent — a single fixed
    // code with FLAG_UPDATE_CURRENT would let task B's button overwrite task
    // A's and restore the wrong task when tapped. Full (unmasked) hash: request
    // codes are ints, so masking only adds collisions.
    private const val REVERT_REQUEST_CODE_BASE = 55000
    // "Run task now" buttons on reminders/battery alerts use their own range.
    // The code is the full (unmasked) automation-id hash — request codes are
    // ints, so masking to 14 bits would only add birthday collisions for no
    // gain.
    private const val RUN_NOW_REQUEST_CODE_BASE = 57000

    /**
     * Creates the PendingIntent that runs [automationId] when the button is
     * tapped. Explicit component + immutable flags keep it stable and safe.
     * When [replyVariable] is set, the reply typed into the RemoteInput field
     * travels with the broadcast and is written into that global variable.
     */
    fun buildPendingIntent(
        context: Context,
        automationId: String,
        index: Int,
        replyVariable: String? = null
    ): PendingIntent {
        val intent = Intent(ACTION_RUN_TASK_FROM_NOTIFICATION)
            .setComponent(ComponentName(context.packageName, RECEIVER_CLASS))
            .putExtra(EXTRA_AUTOMATION_ID, automationId)
        if (!replyVariable.isNullOrBlank()) {
            intent.putExtra(EXTRA_REPLY_VARIABLE, replyVariable)
        }
        return PendingIntent.getBroadcast(
            context,
            BASE_REQUEST_CODE + index,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * PendingIntent behind the "restore original state" button: broadcasts
     * [ACTION_REVERT_TASK_FROM_NOTIFICATION] to the same receiver, which routes
     * it to the engine's `runExit` for [automationId].
     */
    fun buildRevertPendingIntent(context: Context, automationId: String): PendingIntent {
        val intent = Intent(ACTION_REVERT_TASK_FROM_NOTIFICATION)
            .setComponent(ComponentName(context.packageName, RECEIVER_CLASS))
            .putExtra(EXTRA_AUTOMATION_ID, automationId)
        return PendingIntent.getBroadcast(
            context,
            REVERT_REQUEST_CODE_BASE + automationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * PendingIntent behind the "Run task now" button on reminders and battery
     * alerts. The request code is derived from the automation id (same pattern
     * as the revert button) so each task's button stays an independent
     * PendingIntent — a fixed index with FLAG_UPDATE_CURRENT would let one
     * task's notification button overwrite another's and run the wrong task.
     */
    fun buildRunNowPendingIntent(context: Context, automationId: String): PendingIntent {
        val intent = Intent(ACTION_RUN_TASK_FROM_NOTIFICATION)
            .setComponent(ComponentName(context.packageName, RECEIVER_CLASS))
            .putExtra(EXTRA_AUTOMATION_ID, automationId)
        return PendingIntent.getBroadcast(
            context,
            RUN_NOW_REQUEST_CODE_BASE + automationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * PendingIntent behind a "Dismiss" button on any NexaFlow notification
     * (reminders, battery alerts...): broadcasts
     * [ACTION_DISMISS_NOTIFICATION] to [NotificationDismissReceiver], which
     * cancels the notification with id [notificationId]. The request code is
     * derived from the notification id so different notifications never share
     * a PendingIntent (FLAG_UPDATE_CURRENT would otherwise let one notification
     * dismiss the wrong one).
     */
    fun buildDismissPendingIntent(context: Context, notificationId: Int): PendingIntent {
        val intent = Intent(ACTION_DISMISS_NOTIFICATION)
            .setComponent(ComponentName(context.packageName, DISMISS_RECEIVER_CLASS))
            .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        return PendingIntent.getBroadcast(
            context,
            DISMISS_REQUEST_CODE_BASE + notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Converts configured buttons into [NotificationCompat.Action]s for a builder. */
    fun toNotificationActions(
        context: Context,
        buttons: List<NotificationActionButton>
    ): List<NotificationCompat.Action> = buttons.mapIndexedNotNull { index, button ->
        if (button.automationId.isBlank()) return@mapIndexedNotNull null
        val builder = NotificationCompat.Action.Builder(
            com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow,
            button.label,
            buildPendingIntent(context, button.automationId, index, button.replyVariable)
        )
        if (!button.replyVariable.isNullOrBlank()) {
            builder.addRemoteInput(
                RemoteInput.Builder(REMOTE_INPUT_REPLY_KEY)
                    .setLabel(context.getString(R.string.notification_action_reply_label))
                    .build()
            )
        }
        builder.build()
    }

    /**
     * Appends the localized "restore original state" action to [actions] when
     * the enclosing task reverts on exit. No-op when the task does not revert
     * or no automation id is available, so ordinary notifications stay clean.
     */
    fun withRevertActionIfNeeded(
        context: Context,
        actions: List<NotificationCompat.Action>,
        automationId: String?,
        revertOnExit: Boolean,
        label: String
    ): List<NotificationCompat.Action> {
        if (!revertOnExit || automationId.isNullOrBlank()) return actions
        return actions + NotificationCompat.Action.Builder(
            com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow,
            label,
            buildRevertPendingIntent(context, automationId)
        ).build()
    }

    /**
     * Standard button pair for a one-shot notification (battery alerts, ...):
     * "Run task now" (re-runs the enclosing task when its id is available) plus
     * "Dismiss" (cancels notification [notificationId]).
     */
    fun toRunNowAndDismissActions(
        context: Context,
        automationId: String?,
        notificationId: Int
    ): List<NotificationCompat.Action> {
        val actions = mutableListOf<NotificationCompat.Action>()
        if (!automationId.isNullOrBlank()) {
            actions += NotificationCompat.Action.Builder(
                com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow,
                context.getString(R.string.notification_action_run_now),
                buildRunNowPendingIntent(context, automationId)
            ).build()
        }
        actions += NotificationCompat.Action.Builder(
            com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow,
            context.getString(R.string.notification_action_dismiss),
            buildDismissPendingIntent(context, notificationId)
        ).build()
        return actions
    }
}
