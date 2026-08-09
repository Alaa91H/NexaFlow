package com.nexaflow.core.execution

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Verifies the plain [NotificationDismissReceiver]: a DISMISS broadcast cancels
 * exactly the notification whose id arrives as [EXTRA_NOTIFICATION_ID] — and
 * ignores everything else (other actions, missing id).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationDismissReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val receiver = NotificationDismissReceiver()

    @Before
    fun setUp() {
        manager.createNotificationChannel(
            android.app.NotificationChannel("test", "Test", android.app.NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.cancelAll()
    }

    private fun post(id: Int) {
        manager.notify(
            id,
            NotificationCompat.Builder(context, "test")
                .setSmallIcon(com.nexaflow.core.rom.R.drawable.ic_stat_nexaflow)
                .setContentTitle("t")
                .build()
        )
    }

    @Test
    fun dismiss_cancelsOnlyTheTargetedNotification() {
        post(3001)
        post(1001)
        receiver.onReceive(context, Intent(ACTION_DISMISS_NOTIFICATION).putExtra(EXTRA_NOTIFICATION_ID, 3001))
        assertNull(shadowOf(manager).getNotification(3001))
        // The other notification is untouched.
        assertTrue(shadowOf(manager).getNotification(1001) is Notification)
    }

    @Test
    fun dismiss_ignoresMissingId() {
        post(3001)
        receiver.onReceive(context, Intent(ACTION_DISMISS_NOTIFICATION))
        assertTrue(shadowOf(manager).getNotification(3001) is Notification)
    }

    @Test
    fun dismiss_ignoresOtherActions() {
        post(3001)
        receiver.onReceive(context, Intent(ACTION_RUN_TASK_FROM_NOTIFICATION).putExtra(EXTRA_NOTIFICATION_ID, 3001))
        assertTrue(shadowOf(manager).getNotification(3001) is Notification)
    }

    @Test
    fun dismiss_receiverClassNameMatchesBuilderContract() {
        // The builder names this class by string; a rename would silently break
        // every dismiss button. Pin the coupling here.
        assertEquals(
            NotificationDismissReceiver::class.java.name,
            NotificationActionButtons.DISMISS_RECEIVER_CLASS
        )
    }
}
