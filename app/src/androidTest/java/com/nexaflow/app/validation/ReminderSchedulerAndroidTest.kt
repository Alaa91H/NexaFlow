package com.nexaflow.app.validation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexaflow.core.execution.EXTRA_AUTOMATION_ID
import com.nexaflow.core.execution.ReminderAlarmReceiver
import com.nexaflow.core.execution.handler.ReminderScheduler
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Registers then removes a real app-owned alarm on the connected target.
 *
 * The assertion is deliberately limited to a discoverable PendingIntent after production scheduling.
 * Future delivery while idle, reboot persistence, exact-alarm policy and OEM background behavior need
 * separate physical-device evidence and are not inferred from this test.
 */
@RunWith(AndroidJUnit4::class)
class ReminderSchedulerAndroidTest {

    @Test
    fun scheduleRegistersAppOwnedAlarmPendingIntentAndAllowsCleanup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val zone = ZoneId.systemDefault()
        // Two minutes ahead removes the midnight/elapsed-minute race while preserving the exact
        // calendar calculation the production scheduler performs.
        val target = ZonedDateTime.now(zone)
            .plusMinutes(2)
            .withSecond(0)
            .withNano(0)
        val triggerAt = target.toInstant().toEpochMilli()
        val title = "NexaFlow Android validation reminder"
        val text = "Instrumentation-owned temporary alarm"
        val automationId = "android-scheduler-validation"

        val result = ReminderScheduler.schedule(
            context = context,
            title = title,
            text = text,
            hour = target.hour,
            minute = target.minute,
            automationId = automationId
        )
        assertTrue("Production scheduler rejected an app-owned future reminder: ${result.message}", result.success)

        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ReminderAlarmReceiver.ACTION_SHOW_REMINDER)
            .putExtra(ReminderAlarmReceiver.EXTRA_TITLE, title)
            .putExtra(ReminderAlarmReceiver.EXTRA_TEXT, text)
            .putExtra(EXTRA_AUTOMATION_ID, automationId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            triggerAt.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        assertNotNull("The real scheduler must register its app-owned broadcast intent", pendingIntent)
        val registeredPendingIntent = requireNotNull(pendingIntent)

        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(registeredPendingIntent)
        registeredPendingIntent.cancel()
    }
}
