package com.nexaflow.app.validation

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexaflow.core.execution.EXTRA_AUTOMATION_ID
import com.nexaflow.core.execution.ReminderAlarmReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real notification posting/read-back on a connected target.
 *
 * A notification permission or system-level notification block causes a JUnit skip, not a pass.
 * Scheduling/future delivery and OEM background policy remain separate validation obligations.
 */
@RunWith(AndroidJUnit4::class)
class NotificationAndroidTest {

    private lateinit var context: Context
    private lateinit var manager: NotificationManager

    @Before
    fun enableNotificationsWhenThePlatformAllowsIt() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                    context.packageName,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
        assumeTrue(
            "PLATFORM_OR_USER_SETTING_UNAVAILABLE: notifications are disabled for the target app",
            manager.areNotificationsEnabled()
        )
    }

    @After
    fun removeValidationNotification() {
        if (::manager.isInitialized) manager.cancel(ReminderAlarmReceiver.NOTIFICATION_ID)
    }

    @Test
    fun reminderReceiverPostsInspectableNotificationWithTaskAndDismissActions() = runBlocking {
        val title = "NexaFlow Android validation notification"
        val text = "Notification read-back verifies the visible external effect"
        val automationId = "notification-validation-automation"

        ReminderAlarmReceiver().onReceive(
            context,
            Intent(ReminderAlarmReceiver.ACTION_SHOW_REMINDER)
                .putExtra(ReminderAlarmReceiver.EXTRA_TITLE, title)
                .putExtra(ReminderAlarmReceiver.EXTRA_TEXT, text)
                .putExtra(EXTRA_AUTOMATION_ID, automationId)
        )

        val posted = withTimeout<StatusBarNotification?>(5_000L) {
            var found: StatusBarNotification? = null
            while (found == null) {
                found = manager.activeNotifications
                    .firstOrNull { it.id == ReminderAlarmReceiver.NOTIFICATION_ID }
                if (found == null) delay(50L)
            }
            found
        }
        val notification = requireNotNull(posted) { "Reminder notification was never posted" }.notification

        assertEquals(title, notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals(text, notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals(Notification.CATEGORY_REMINDER, notification.category)
        assertTrue("Task-bound reminder must expose Run now and Dismiss actions", notification.actions.size >= 2)
    }
}
