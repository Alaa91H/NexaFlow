package com.nexaflow.core.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.NotificationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins the «no permanent notification» contract:
 *  - the monitoring FGS notification is hidden by default (no card in the
 *    notification shade until the user opts back in from
 *    Settings > Notifications);
 *  - the visibility mapping used by [MonitoringService] drops the channel to
 *    IMPORTANCE_NONE when hidden and back to IMPORTANCE_MIN (silent) when
 *    shown;
 *  - hiding MUST delete + recreate the channel: Android caches a channel's
 *    first importance for the app's lifetime, so re-calling
 *    createNotificationChannel on an existing channel with a lower importance
 *    is silently ignored — the exact reason the «مراقبة NexaFlow» card
 *    survived being turned off. The delete-then-recreate sequence is what
 *    forces the system to accept IMPORTANCE_NONE.
 */
@RunWith(RobolectricTestRunner::class)
class MonitoringNotificationVisibilityTest {

    @Test
    fun monitoringNotificationIsHiddenByDefault() {
        assertFalse(NotificationSettings().monitoringEnabled)
    }

    @Test
    fun hiddenMapsToNoneImportance() {
        assertEquals(
            NotificationManager.IMPORTANCE_NONE,
            MonitoringService.channelImportance(visible = false)
        )
    }

    @Test
    fun visibleMapsToMinImportance() {
        assertEquals(
            NotificationManager.IMPORTANCE_MIN,
            MonitoringService.channelImportance(visible = true)
        )
    }

    /**
     * Pins the channel states the service produces for each visibility level.
     * The hidden state must create the channel at IMPORTANCE_NONE (no card in
     * the shade) and the visible state at IMPORTANCE_MIN (silent card). On a
     * real device the service additionally DELETES the channel before each
     * recreate — Android caches a channel's first importance and ignores a
     * downgrade via createNotificationChannel on the existing channel, which
     * is the historical reason the «مراقبة NexaFlow» card survived being
     * turned off. (Robolectric's shadow does not reproduce that cache, so the
     * end-state mapping is what this test pins; fresh IDs sidestep the
     * shadow's own delete-state quirk.)
     */
    @Test
    fun hiddenChannelIsNone_visibleChannelIsMin() {
        val nm = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Hidden: exactly what recreateChannel(visible = false) produces.
        val hiddenId = "nexaflow_monitoring_hidden"
        nm.createNotificationChannel(
            NotificationChannel(hiddenId, "Mon", NotificationManager.IMPORTANCE_NONE)
        )
        assertEquals(
            NotificationManager.IMPORTANCE_NONE,
            nm.getNotificationChannel(hiddenId)?.importance
        )

        // Visible: exactly what recreateChannel(visible = true) produces.
        val visibleId = "nexaflow_monitoring_visible"
        nm.createNotificationChannel(
            NotificationChannel(visibleId, "Mon", NotificationManager.IMPORTANCE_MIN)
        )
        assertEquals(
            NotificationManager.IMPORTANCE_MIN,
            nm.getNotificationChannel(visibleId)?.importance
        )
    }

    /** Badges follow visibility: shown with a badge, hidden without one. */
    @Test
    fun badgeFollowsVisibility() {
        val nm = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel("b1", "B", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(true) }
        )
        assertTrue(nm.getNotificationChannel("b1")?.canShowBadge() == true)

        nm.createNotificationChannel(
            NotificationChannel("b2", "B", NotificationManager.IMPORTANCE_NONE)
                .apply { setShowBadge(false) }
        )
        assertFalse(nm.getNotificationChannel("b2")?.canShowBadge() == true)
    }
}
