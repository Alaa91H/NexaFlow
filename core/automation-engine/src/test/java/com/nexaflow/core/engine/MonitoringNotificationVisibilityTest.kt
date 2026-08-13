package com.nexaflow.core.engine

import android.app.NotificationManager
import com.nexaflow.core.datastore.NotificationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Pins the «no permanent notification» contract:
 *  - the monitoring FGS notification is hidden by default (no card in the
 *    notification shade until the user opts back in from
 *    Settings > Notifications);
 *  - the visibility mapping used by [MonitoringService] drops the channel to
 *    IMPORTANCE_NONE when hidden and back to IMPORTANCE_MIN (silent) when
 *    shown.
 */
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
}
