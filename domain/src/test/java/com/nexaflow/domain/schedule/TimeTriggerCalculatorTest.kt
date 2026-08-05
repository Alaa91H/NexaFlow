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
}
