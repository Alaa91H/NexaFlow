package com.nexaflow.core.engine

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the time-change handling in [AutomationAlarmReceiver]: the receiver
 * must react to clock / time-zone broadcasts (TIME_SET, TIMEZONE_CHANGED and
 * the Android 17 TIMEZONE_OFFSET_CHANGED) so RTC-based alarms are recomputed
 * after travel or DST transitions. These tests pin the manifest/intent action
 * strings so a rename never silently breaks the re-scheduling path.
 */
@RunWith(RobolectricTestRunner::class)
class AutomationAlarmReceiverTimeChangeTest {

    @Test
    fun timeChangeActions_matchSystemBroadcasts() {
        // The receiver must listen to the exact platform actions; a typo here
        // would silently drop the reschedule on travel/clock edits.
        assertEquals(Intent.ACTION_TIME_CHANGED, "android.intent.action.TIME_SET")
        assertEquals(Intent.ACTION_TIMEZONE_CHANGED, "android.intent.action.TIMEZONE_CHANGED")
    }

    @Test
    fun timezoneOffsetChangedAction_isAndroid17Broadcast() {
        // Android 17 adds a dedicated offset-change broadcast (fixed-UTC-offset
        // zones, e.g. India, without a zone-id switch).
        assertEquals(
            "android.intent.action.TIMEZONE_OFFSET_CHANGED",
            AutomationAlarmReceiver.TIMEZONE_OFFSET_CHANGED_ACTION
        )
        assertTrue(AutomationAlarmReceiver.TIMEZONE_OFFSET_CHANGED_ACTION.isNotBlank())
        // Must stay distinct from the zone-id broadcast so the receiver can
        // treat each path (or both) as a full reschedule trigger.
        assertTrue(
            AutomationAlarmReceiver.TIMEZONE_OFFSET_CHANGED_ACTION !=
                Intent.ACTION_TIMEZONE_CHANGED
        )
    }

    @Test
    fun rescheduleAction_setIncludesEveryScheduleInvalidatingBroadcast() {
        val handled = AutomationAlarmReceiver.rescheduleActions
        assertTrue("TIME_SET handled", handled.contains(Intent.ACTION_TIME_CHANGED))
        assertTrue("TIMEZONE_CHANGED handled", handled.contains(Intent.ACTION_TIMEZONE_CHANGED))
        assertTrue(
            "TIMEZONE_OFFSET_CHANGED handled",
            handled.contains(AutomationAlarmReceiver.TIMEZONE_OFFSET_CHANGED_ACTION)
        )
        assertTrue(
            "Exact-alarm permission change re-arms all enabled time tasks",
            handled.contains(AutomationAlarmReceiver.ALARM_PERMISSION_CHANGED_ACTION)
        )
    }
}
