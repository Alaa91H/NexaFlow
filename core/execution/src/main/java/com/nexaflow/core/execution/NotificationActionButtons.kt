package com.nexaflow.core.execution

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
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
 */
data class NotificationActionButton(
    val label: String,
    val automationId: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("label", label)
        .put("id", automationId)

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
                    NotificationActionButton(label, id)
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
    private const val BASE_REQUEST_CODE = 52000

    /**
     * Creates the PendingIntent that runs [automationId] when the button is
     * tapped. Explicit component + immutable flags keep it stable and safe.
     */
    fun buildPendingIntent(context: Context, automationId: String, index: Int): PendingIntent {
        val intent = Intent(ACTION_RUN_TASK_FROM_NOTIFICATION)
            .setComponent(ComponentName(context.packageName, RECEIVER_CLASS))
            .putExtra(EXTRA_AUTOMATION_ID, automationId)
        return PendingIntent.getBroadcast(
            context,
            BASE_REQUEST_CODE + index,
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
        NotificationCompat.Action.Builder(
            com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow,
            button.label,
            buildPendingIntent(context, button.automationId, index)
        ).build()
    }
}
