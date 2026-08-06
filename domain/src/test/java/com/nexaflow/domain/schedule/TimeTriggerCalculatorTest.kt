package com.nexaflow.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeTriggerCalculatorTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun localDateOf(millis: Long): LocalDate {
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    }

    @Test
    fun `no time config returns null`() {
        assertNull(TimeTriggerCalculator.nextFireTime(emptyMap(), System.currentTimeMillis()))
        assertNull(TimeTriggerCalculator.nextFireTime(mapOf("time" to ""), System.currentTimeMillis()))
        assertNull(TimeTriggerCalculator.nextFireTime(mapOf("time" to "not-a-time"), System.currentTimeMillis()))
    }

    @Test
    fun `daily returns tomorrow when today's time already passed`() {
        val from = millisOf(2026, 8, 5, 18, 0)
        val config = mapOf("time" to "08:00")
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 6), localDateOf(next!!))
    }

    @Test
    fun `daily returns today when today's time is still ahead`() {
        val from = millisOf(2026, 8, 5, 7, 0)
        val config = mapOf("time" to "08:00")
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 5), localDateOf(next!!))
    }

    @Test
    fun `once after today's time passes returns the next day`() {
        // ONCE always yields the next occurrence; the scheduler refuses to
        // reschedule after it fires, which is what makes it one-shot.
        val from = millisOf(2026, 8, 10, 12, 0)
        val config = mapOf("time" to "08:00", "repeat" to "ONCE")
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 11), localDateOf(next!!))
    }

    @Test
    fun `once in the future returns that day`() {
        val from = millisOf(2026, 8, 5, 12, 0)
        val config = mapOf("time" to "08:00", "repeat" to "ONCE")
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 6), localDateOf(next!!))
    }

    @Test
    fun `specific days matches only selected weekdays`() {
        // 2026-08-05 is a Wednesday (day 3).
        val from = millisOf(2026, 8, 5, 0, 0)
        val config = mapOf("time" to "08:00", "repeat" to "SPECIFIC_DAYS", "days" to "1,3")
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 5), localDateOf(next!!))
    }

    @Test
    fun `specific days skips non-selected weekdays`() {
        // 2026-08-05 is Wednesday; next Friday (7) should be selected with days "5".
        val from = millisOf(2026, 8, 5, 0, 0)
        val config = mapOf("time" to "08:00", "repeat" to "SPECIFIC_DAYS", "days" to "5")
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 7), localDateOf(next!!))
    }

    @Test
    fun `specific date in the future fires once on that date`() {
        val from = millisOf(2026, 8, 5, 12, 0)
        val config = mapOf("time" to "08:00", "repeat" to "SPECIFIC_DATE", "date" to "2026-08-20")
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 20), localDateOf(next!!))
    }

    @Test
    fun `specific date in the past returns null`() {
        val from = millisOf(2026, 8, 25, 12, 0)
        val config = mapOf("time" to "08:00", "repeat" to "SPECIFIC_DATE", "date" to "2026-08-20")
        assertNull(TimeTriggerCalculator.nextFireTime(config, from))
    }

    @Test
    fun `date range respects window bounds`() {
        val from = millisOf(2026, 8, 5, 7, 0)
        val config = mapOf(
            "time" to "08:00",
            "repeat" to "DATE_RANGE",
            "startDate" to "2026-08-10",
            "endDate" to "2026-08-12"
        )
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 10), localDateOf(next!!))
    }

    @Test
    fun `date range past the end returns null`() {
        val from = millisOf(2026, 8, 13, 7, 0)
        val config = mapOf(
            "time" to "08:00",
            "repeat" to "DATE_RANGE",
            "startDate" to "2026-08-10",
            "endDate" to "2026-08-12"
        )
        assertNull(TimeTriggerCalculator.nextFireTime(config, from))
    }

    @Test
    fun `inverted date range never fires`() {
        val from = millisOf(2026, 8, 5, 7, 0)
        val config = mapOf(
            "time" to "08:00",
            "repeat" to "DATE_RANGE",
            "startDate" to "2026-08-20",
            "endDate" to "2026-08-10"
        )
        assertNull(TimeTriggerCalculator.nextFireTime(config, from))
    }

    @Test
    fun `monthly fires on the configured day of month`() {
        val from = millisOf(2026, 8, 1, 0, 0)
        val config = mapOf("time" to "08:00", "repeat" to "MONTHLY", "monthDay" to "15")
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 15), localDateOf(next!!))
    }

    @Test
    fun `monthly skips to next month when the configured day already passed`() {
        // 2026-08-20 is past the configured 15th — must roll to September.
        val from = millisOf(2026, 8, 20, 0, 0)
        val config = mapOf("time" to "08:00", "repeat" to "MONTHLY", "monthDay" to "15")
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 9, 15), localDateOf(next!!))
    }

    @Test
    fun `monthly with day 31 skips months without a 31st`() {
        // February 2026 has 28 days; day 31 must roll to the next matching month.
        val from = millisOf(2026, 2, 1, 0, 0)
        val config = mapOf("time" to "08:00", "repeat" to "MONTHLY", "monthDay" to "31")
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        // The calculator only fires when the day-of-month matches exactly; for
        // months without a 31st it falls through to the next matching month.
        assertEquals(LocalDate.of(2026, 3, 31), localDateOf(next!!))
    }

    @Test
    fun `weekdays fires monday to friday only`() {
        // 2026-08-07 is a Friday; the next fire must stay on a weekday.
        val from = millisOf(2026, 8, 7, 12, 0)
        val config = mapOf("time" to "08:00", "repeat" to "WEEKDAYS")
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        // Friday's 08:00 already passed, so the next fire is Monday 08:00.
        assertEquals(LocalDate.of(2026, 8, 10), localDateOf(next!!))
    }

    @Test
    fun `weekdays fires on a weekday later the same day`() {
        // 2026-08-05 is a Wednesday before 08:00.
        val from = millisOf(2026, 8, 5, 7, 0)
        val config = mapOf("time" to "08:00", "repeat" to "WEEKDAYS")
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 5), localDateOf(next!!))
    }

    @Test
    fun `weekends fires saturday and sunday only`() {
        // 2026-08-08 is a Saturday before 08:00.
        val from = millisOf(2026, 8, 8, 7, 0)
        val config = mapOf("time" to "08:00", "repeat" to "WEEKENDS")
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 8), localDateOf(next!!))
    }

    @Test
    fun `weekends skips weekdays`() {
        // 2026-08-10 is a Monday; next weekend day is Saturday 2026-08-15.
        val from = millisOf(2026, 8, 10, 12, 0)
        val config = mapOf("time" to "08:00", "repeat" to "WEEKENDS")
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 15), localDateOf(next!!))
    }

    @Test
    fun `time range schedules at the range start`() {
        // A range trigger fires at rangeStart (22:00) each day.
        val from = millisOf(2026, 8, 5, 7, 0)
        val config = mapOf(
            "timeMode" to "RANGE",
            "rangeStart" to "22:00",
            "rangeEnd" to "06:00"
        )
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 5), localDateOf(next!!))
    }

    @Test
    fun `time range with specific days respects both constraints`() {
        // 2026-08-05 is Wednesday (selected), but time already passed on
        // Wednesday; next selected weekday with a future start is Thursday.
        val from = millisOf(2026, 8, 5, 23, 0)
        val config = mapOf(
            "timeMode" to "RANGE",
            "rangeStart" to "22:00",
            "rangeEnd" to "06:00",
            "repeat" to "SPECIFIC_DAYS",
            "days" to "3,4"
        )
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 6), localDateOf(next!!))
    }

    @Test
    fun `window end for overnight range is the next morning`() {
        // 22:00 → 06:00 crosses midnight; the window ends the next day at 06:00.
        val start = millisOf(2026, 8, 5, 22, 0)
        val config = mapOf(
            "timeMode" to "RANGE",
            "rangeStart" to "22:00",
            "rangeEnd" to "06:00"
        )
        val end = TimeTriggerCalculator.windowEndMillis(config, start)
        assertNotNull(end)
        assertEquals(millisOf(2026, 8, 6, 6, 0), end)
    }

    @Test
    fun `window end for same-day range is the same day`() {
        // 10:00 → 18:00 stays on the same calendar day.
        val start = millisOf(2026, 8, 5, 10, 0)
        val config = mapOf(
            "timeMode" to "RANGE",
            "rangeStart" to "10:00",
            "rangeEnd" to "18:00"
        )
        val end = TimeTriggerCalculator.windowEndMillis(config, start)
        assertNotNull(end)
        assertEquals(millisOf(2026, 8, 5, 18, 0), end)
    }

    @Test
    fun `window end for zero-length range is treated as a full day`() {
        // end == start is treated as a full 24h window so the end alarm never
        // collides with the start alarm.
        val start = millisOf(2026, 8, 5, 10, 0)
        val config = mapOf(
            "timeMode" to "RANGE",
            "rangeStart" to "10:00",
            "rangeEnd" to "10:00"
        )
        val end = TimeTriggerCalculator.windowEndMillis(config, start)
        assertNotNull(end)
        assertEquals(millisOf(2026, 8, 6, 10, 0), end)
    }

    @Test
    fun `window end is null for non-range configs`() {
        val config = mapOf("time" to "08:00")
        assertNull(TimeTriggerCalculator.windowEndMillis(config, System.currentTimeMillis()))
    }

    @Test
    fun `window end is null when range times are missing or invalid`() {
        assertNull(
            TimeTriggerCalculator.windowEndMillis(
                mapOf("timeMode" to "RANGE"),
                System.currentTimeMillis()
            )
        )
        assertNull(
            TimeTriggerCalculator.windowEndMillis(
                mapOf("timeMode" to "RANGE", "rangeStart" to "nope", "rangeEnd" to "06:00"),
                System.currentTimeMillis()
            )
        )
    }
}
