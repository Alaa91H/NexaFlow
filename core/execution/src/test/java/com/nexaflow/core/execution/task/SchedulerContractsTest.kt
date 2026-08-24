package com.nexaflow.core.execution.task

import org.junit.Assert.*
import org.junit.Test

class SchedulerContractsTest {

    @Test
    fun `OneShot requires non-blank scheduleId`() {
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleDefinition.OneShot(
                scheduleId = "  ",
                displayName = "test",
                workflowId = "wf1",
                triggerAtEpochMs = System.currentTimeMillis()
            )
        }
    }

    @Test
    fun `Recurring rejects interval below minimum`() {
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleDefinition.Recurring(
                scheduleId = "rec1",
                displayName = "test",
                workflowId = "wf1",
                firstFireAtEpochMs = System.currentTimeMillis(),
                intervalMs = 1_000L  // below 60_000 minimum
            )
        }
    }

    @Test
    fun `Recurring accepts valid interval`() {
        val schedule = ScheduleDefinition.Recurring(
            scheduleId = "rec2",
            displayName = "every hour",
            workflowId = "wf2",
            firstFireAtEpochMs = System.currentTimeMillis(),
            intervalMs = 3_600_000L
        )
        assertEquals(3_600_000L, schedule.intervalMs)
    }

    @Test
    fun `CronLike rejects invalid hour`() {
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleDefinition.CronLike(
                scheduleId = "cron1",
                displayName = "daily",
                workflowId = "wf3",
                hour = 25,
                minute = 0
            )
        }
    }

    @Test
    fun `CronLike accepts valid time`() {
        val schedule = ScheduleDefinition.CronLike(
            scheduleId = "cron2",
            displayName = "8am",
            workflowId = "wf4",
            hour = 8,
            minute = 0,
            daysOfWeek = setOf(1, 2, 3, 4, 5)
        )
        assertEquals(8, schedule.hour)
        assertEquals(setOf(1, 2, 3, 4, 5), schedule.daysOfWeek)
    }

    @Test
    fun `SunRelative rejects out-of-range latitude`() {
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleDefinition.SunRelative(
                scheduleId = "sun1",
                displayName = "sunrise",
                workflowId = "wf5",
                event = SunEvent.SUNRISE,
                latitude = 200.0,
                longitude = 0.0
            )
        }
    }

    @Test
    fun `MisfirePolicy defaults are sensible`() {
        val oneShot = ScheduleDefinition.OneShot(
            scheduleId = "s1",
            displayName = "d",
            workflowId = "w",
            triggerAtEpochMs = 1000L
        )
        assertEquals(MisfirePolicy.FIRE_NOW, oneShot.misfirePolicy)

        val recurring = ScheduleDefinition.Recurring(
            scheduleId = "r1",
            displayName = "r",
            workflowId = "w",
            firstFireAtEpochMs = 1000L,
            intervalMs = 60_000L
        )
        assertEquals(MisfirePolicy.SKIP, recurring.misfirePolicy)
    }
}
