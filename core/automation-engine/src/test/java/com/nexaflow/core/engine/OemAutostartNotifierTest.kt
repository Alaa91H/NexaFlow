package com.nexaflow.core.engine

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.rom.OemCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [OemAutostartNotifier] — the one-time engine alert
 * shown when monitoring starts on an OEM ROM with an autostart gate. The
 * internal overload pins the vendor probes, so the dedup / permission / deep
 * link logic is tested without touching real system properties.
 *
 * The alert flag is shared with the in-app Permission Manager OemCompat card
 * via [OemCompat.isHintDelivered]: whichever channel claims it first keeps the
 * other silent, so the user is never alerted twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OemAutostartNotifierTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Fresh prefs + notification state per test.
        context.getSharedPreferences(OEM_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(nm).setNotificationsEnabled(true)
    }

    private fun postedNotifications(): List<Notification> {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return shadowOf(nm).allNotifications
    }

    @Test
    fun gatedRomWithDeepLink_postsHintOnce() {
        OemAutostartNotifier.maybeShow(context, hasAutostartGate = true, autostartDeepLink = Intent(Intent.ACTION_VIEW))

        val posted = postedNotifications()
        assertEquals(1, posted.size)
        assertEquals(
            context.getString(R.string.oem_autostart_title),
            posted[0].extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )

        // A second monitoring start must NOT re-post: one hint per install.
        OemAutostartNotifier.maybeShow(context, hasAutostartGate = true, autostartDeepLink = Intent(Intent.ACTION_VIEW))
        assertEquals(1, postedNotifications().size)
    }

    @Test
    fun cleanRom_doesNotPost() {
        OemAutostartNotifier.maybeShow(context, hasAutostartGate = false, autostartDeepLink = Intent(Intent.ACTION_VIEW))

        assertTrue(postedNotifications().isEmpty())
    }

    @Test
    fun missingDeepLink_doesNotPost() {
        OemAutostartNotifier.maybeShow(context, hasAutostartGate = true, autostartDeepLink = null)

        assertTrue(postedNotifications().isEmpty())
    }

    @Test
    fun cardAlreadyDelivered_suppressesNotification() {
        // The in-app OemCompat card claimed the shared hint first: the engine
        // notification must stay silent even on a gated ROM with a deep link.
        OemCompat.markHintDelivered(context)

        OemAutostartNotifier.maybeShow(context, hasAutostartGate = true, autostartDeepLink = Intent(Intent.ACTION_VIEW))

        assertTrue(postedNotifications().isEmpty())
    }

    @Test
    fun notificationDelivered_marksSharedFlag() {
        // Posting the notification claims the shared hint: the in-app card
        // (which reads OemCompat.isHintDelivered) must not appear afterwards.
        OemAutostartNotifier.maybeShow(context, hasAutostartGate = true, autostartDeepLink = Intent(Intent.ACTION_VIEW))

        assertEquals(1, postedNotifications().size)
        assertTrue(OemCompat.isHintDelivered(context))
    }

    @Test
    fun notificationsDisabled_doesNotPost_andKeepsHintForLater() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(nm).setNotificationsEnabled(false)

        OemAutostartNotifier.maybeShow(context, hasAutostartGate = true, autostartDeepLink = Intent(Intent.ACTION_VIEW))
        assertTrue(postedNotifications().isEmpty())

        // The hint was NOT consumed while notifications were off: once the user
        // enables notifications, the next monitoring start still shows it once.
        shadowOf(nm).setNotificationsEnabled(true)
        OemAutostartNotifier.maybeShow(context, hasAutostartGate = true, autostartDeepLink = Intent(Intent.ACTION_VIEW))
        val posted = postedNotifications()
        assertEquals(1, posted.size)
        assertNotNull(posted[0].contentIntent)
    }

    private companion object {
        const val OEM_PREFS = "nexaflow_oem"
    }
}
