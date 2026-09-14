package com.nexaflow.core.execution

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.domain.models.Automation
import kotlinx.coroutines.flow.first

/**
 * Live Update run-progress card for executing tasks.
 *
 * One persistent, silent notification per running task shows the action
 * chain advancing (Android 16+ "Live Update" pattern). On API 36+ the card
 * uses the platform [Notification.ProgressStyle]: one progress segment per
 * action, colored by the action's outcome, so the user can see at a glance
 * which step a long chain is on and whether a step failed. Below API 37 the
 * same card renders as a standard determinate progress notification.
 *
 * The card is IMPORTANCE_MIN and silent: run progress is glanceable, never
 * audible, and it is removed the moment the run finishes. It is gated by the
 * user's existing execution-notification preference, and every call is
 * defensive — a notification hiccup must never break task execution.
 *
 * IDs are namespaced per automation: `0x6EF0000 | (idHash % 0x10000)` keeps
 * concurrent runs on distinct cards without colliding with the app's other
 * notification ids.
 */
class TaskRunProgressNotifier(
    private val context: Context,
    private val notificationPreferences: NotificationPreferences
) {

    private val manager: NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    /** Shows the initial card; a no-op when execution notifications are off. */
    suspend fun start(automation: Automation, totalActions: Int) {
        show(automation, totalActions, 0, emptyList())
    }

    /**
     * Advances the card to [stepIndex] (0-based). Completed segments are
     * colored by the outcomes in [outcomes] (parallel to the first N actions)
     * so the card doubles as a mini status strip.
     */
    suspend fun update(
        automation: Automation,
        totalActions: Int,
        stepIndex: Int,
        outcomes: List<Boolean> = emptyList()
    ) {
        show(automation, totalActions, stepIndex, outcomes)
    }

    /** Removes the card when the run finishes (success, failure or skip). */
    fun finish(automationId: String) {
        runCatching { manager?.cancel(notificationId(automationId)) }
    }

    private suspend fun show(
        automation: Automation,
        totalActions: Int,
        stepIndex: Int,
        outcomes: List<Boolean>
    ) {
        if (!isEnabled()) return
        runCatching {
            ensureChannel()
            val id = notificationId(automation.id)
            val notification = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
            ) {
                buildPlatform(automation, totalActions, stepIndex, outcomes, id)
            } else {
                buildCompat(automation, totalActions, stepIndex, outcomes, id)
            }
            manager?.notify(id, notification)
        }
    }

    /** API 36+ card with a per-action ProgressStyle segment strip. */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun buildPlatform(
        automation: Automation,
        totalActions: Int,
        stepIndex: Int,
        outcomes: List<Boolean>,
        id: Int
    ): Notification {
        val style = Notification.ProgressStyle().apply {
            if (totalActions <= 0) {
                setProgressIndeterminate(true)
            } else {
                val segmentLength = SEGMENT_SCALE / totalActions
                repeat(totalActions) { index ->
                    val segment = Notification.ProgressStyle.Segment(segmentLength).apply {
                        color = when {
                            index < outcomes.size -> if (outcomes[index]) COLOR_SUCCESS else COLOR_FAILURE
                            index == stepIndex -> COLOR_ACTIVE
                            else -> COLOR_PENDING
                        }
                    }
                    addProgressSegment(segment)
                }
                setProgress((stepIndex * segmentLength).coerceAtMost(SEGMENT_SCALE))
            }
        }
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow)
            .setContentTitle(automation.name)
            .setContentText(contentText(totalActions, stepIndex))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setStyle(style)
            .build()
            .also { it.extras.putInt(EXTRA_NOTIFICATION_KEY, id) }
    }

    /** Compat card below API 37: standard determinate/indeterminate bar. */
    private fun buildCompat(
        automation: Automation,
        totalActions: Int,
        stepIndex: Int,
        @Suppress("UNUSED_PARAMETER") outcomes: List<Boolean>,
        id: Int
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow)
            .setContentTitle(automation.name)
            .setContentText(contentText(totalActions, stepIndex))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (totalActions <= 0) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(totalActions, stepIndex.coerceIn(0, totalActions), false)
        }
        val notification = builder.build()
        notification.extras.putInt(EXTRA_NOTIFICATION_KEY, id)
        return notification
    }

    private fun contentText(totalActions: Int, stepIndex: Int): String =
        if (totalActions <= 0) {
            context.getString(R.string.run_progress_starting)
        } else {
            context.getString(
                R.string.run_progress_step,
                (stepIndex + 1).coerceAtMost(totalActions),
                totalActions
            )
        }

    private suspend fun isEnabled(): Boolean {
        val settings = notificationPreferences.settings.first()
        return settings.enabled && settings.executionEnabled
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.run_progress_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = context.getString(R.string.run_progress_channel_desc)
            setSound(null, null)
            enableVibration(false)
        }
        manager?.createNotificationChannel(channel)
    }

    /** Notification id namespaced per automation (16-bit hash bucket). */
    private fun notificationId(automationId: String): Int {
        val bucket = automationId.fold(0) { acc, c -> (acc * 31 + c.code) and 0xFFFF }
        return BASE_NOTIFICATION_ID or bucket
    }

    companion object {
        private const val CHANNEL_ID = "nexaflow_run_progress"
        private const val BASE_NOTIFICATION_ID = 0x6EF0000
        private const val SEGMENT_SCALE = 10_000

        /** Distinguishes a live-update card from a result card for the same id. */
        private const val EXTRA_NOTIFICATION_KEY = "nexaflow.run_progress"

        private const val COLOR_SUCCESS = 0xFF2E7D32.toInt()
        private const val COLOR_FAILURE = 0xFFC62828.toInt()
        private const val COLOR_ACTIVE = 0xFF1E88E5.toInt()
        private const val COLOR_PENDING = 0xFF9E9E9E.toInt()
    }
}
