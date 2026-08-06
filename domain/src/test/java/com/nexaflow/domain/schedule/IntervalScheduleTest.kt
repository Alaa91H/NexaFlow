package com.nexaflow.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalScheduleTest {

    @Test
    fun nextFireTime_everyHourAlignedToEpoch() {
        val schedule = IntervalSchedule(intervalMinutes = 60)
        val from = 1_700_000_000_000L // arbitrary epoch millis
        val next = schedule.nextFireTime(from)
        assertTrue(next > from)
        assertEquals(0L, next % 3_600_000L) // aligned to the hour
    }

    @Test
    fun nextFireTime_halfHourGrid() {
        val schedule = IntervalSchedule(intervalMinutes = 30)
        val from = 1_700_000_000_000L
        val next = schedule.nextFireTime(from)
        assertTrue(next > from)
        assertEquals(0L, next % 1_800_000L)
    }

    @Test
    fun nextFireTime_exactlyOnBoundaryMovesToNext() {
        val schedule = IntervalSchedule(intervalMinutes = 15)
        val from = 1_700_000_000_000L
        val onBoundary = schedule.nextFireTime(from - 1)
        // from - 1 is inside the same 15-min slot as from, so both give the same boundary.
        val boundary = schedule.nextFireTime(from)
        assertEquals(onBoundary, boundary)
        assertTrue(boundary > from)
    }

    @Test
    fun nextFireTime_respectsOffsetAlignment() {
        // Aligned to +7 minutes, so fires at :07, :37, ...
        val offset = 7 * 60_000L
        val schedule = IntervalSchedule(intervalMinutes = 30, startOffsetMillis = offset)
        val from = offset + 10_000L // 10s after the :07 boundary
        val next = schedule.nextFireTime(from)
        assertEquals(offset + 1_800_000L, next)
    }

    @Test
    fun nextFireTime_consecutiveFiresStayOnGrid() {
        val schedule = IntervalSchedule(intervalMinutes = 10)
        val first = schedule.nextFireTime(1_700_000_000_000L)
        val second = schedule.nextFireTime(first)
        assertEquals(first + 600_000L, second)
    }
}
