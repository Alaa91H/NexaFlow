package com.nexaflow.core.execution

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Verifies the interactive action buttons on reminder notifications:
 *  - a \"Run task now\" button appears when the reminder was scheduled from a
 *    task (routes to the engine receiver with the task id)
 *  - a \"Dismiss\" button always appears (routes to NotificationDismissReceiver)
 *  - a reminder without a task id gets only the Dismiss button
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderAlarmReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    private val receiver = ReminderAlarmReceiver()

    @Before
    fun setUp() {
        manager.cancelAll()
    }

    private fun showReminder(automationId: String?) {
        val intent = Intent(ReminderAlarmReceiver.ACTION_SHOW_REMINDER)
            .putExtra(ReminderAlarmReceiver.EXTRA_TITLE, "Water the plants")
            .putExtra(ReminderAlarmReceiver.EXTRA_TEXT, "Time to water")
        if (automationId != null) {
            intent.putExtra(EXTRA_AUTOMATION_ID, automationId)
        }
        receiver.onReceive(context, intent)
    }

    private fun postedNotification() = shadowOf(manager).getNotification(ReminderAlarmReceiver.NOTIFICATION_ID)

    @Test
    fun reminderWithTaskId_showsRunNowAndDismiss() {
        showReminder("task-77")
        val notification = postedNotification()
        assertNotNull(notification)
        assertEquals(2, notification!!.actions.size)
        assertEquals("Run task now", notification.actions[0].title.toString())
        assertEquals("Dismiss", notification.actions[1].title.toString())

        val runIntent = shadowOf(notification.actions[0].actionIntent).savedIntent
        assertEquals(ACTION_RUN_TASK_FROM_NOTIFICATION, runIntent.action)
        assertEquals(NotificationActionButtons.RECEIVER_CLASS, runIntent.component?.className)
        assertEquals("task-77", runIntent.getStringExtra(EXTRA_AUTOMATION_ID))

        val dismissIntent = shadowOf(notification.actions[1].actionIntent).savedIntent
        assertEquals(ACTION_DISMISS_NOTIFICATION, dismissIntent.action)
        assertEquals(ReminderAlarmReceiver.NOTIFICATION_ID, dismissIntent.getIntExtra(EXTRA_NOTIFICATION_ID, -1))
    }

    @Test
    fun reminderWithoutTaskId_showsOnlyDismiss() {
        showReminder(null)
        val notification = postedNotification()
        assertNotNull(notification)
        assertEquals(1, notification!!.actions.size)
        assertEquals("Dismiss", notification.actions[0].title.toString())
    }

    @Test
    fun foreignAction_doesNotShowReminder() {
        receiver.onReceive(context, Intent(ACTION_DISMISS_NOTIFICATION))
        assertNull(postedNotification())
    }
}
