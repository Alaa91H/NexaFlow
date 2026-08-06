package com.nexaflow.domain.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CronScheduleTest {

    /**
     * Parses a local date-time ("2026-08-07T09:00") in the system zone — the same
     * zone [CronSchedule.nextFireTime] uses — so every expectation is
     * deterministic on any machine regardless of its timezone/offset.
     */
    private fun zdt(iso: String): Long =
        LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun parse_rejectsMalformedExpressions() {
        assertNull(CronSchedule.parse(""))
        assertNull(CronSchedule.parse("* * * *"))               // only 4 fields
        assertNull(CronSchedule.parse("* * * * * *"))           // 6 fields
        assertNull(CronSchedule.parse("61 * * * *"))            // minute out of range
        assertNull(CronSchedule.parse("* 24 * * *"))            // hour out of range
        assertNull(CronSchedule.parse("*/0 * * * *"))           // step 0
        assertNull(CronSchedule.parse("a b c d e"))             // junk
        assertNull(CronSchedule.parse("* * * 13 *"))            // month 13
    }

    @Test
    fun parse_acceptsEveryMinute() {
        val cron = CronSchedule.parse("* * * * *")
        assertNotNull(cron)
        assertEquals((0..59).toSet(), cron!!.minutes)
        assertEquals((0..23).toSet(), cron.hours)
    }

    @Test
    fun parse_acceptsStepsRangesAndLists() {
        val cron = CronSchedule.parse("*/15 9-17 1,15 * MON-FRI")
        assertNotNull(cron)
        assertEquals(setOf(0, 15, 30, 45), cron!!.minutes)
        assertEquals((9..17).toSet(), cron.hours)
        assertEquals(setOf(1, 15), cron.daysOfMonth)
        assertEquals((1..5).toSet(), cron.daysOfWeek)
    }

    @Test
    fun nextFireTime_everyMinuteFiresNextMinute() {
        val cron = CronSchedule.parse("* * * * *")!!
        val from = zdt("2026-08-06T10:30")
        assertEquals(zdt("2026-08-06T10:31"), cron.nextFireTime(from))
    }

    @Test
    fun nextFireTime_skipsAlreadyPassedMinuteInSameHour() {
        val cron = CronSchedule.parse("30 * * * *")!!
        val from = zdt("2026-08-06T10:30:15")
        assertEquals(zdt("2026-08-06T11:30"), cron.nextFireTime(from))
    }

    @Test
    fun nextFireTime_weekdayMorning() {
        // Friday 2026-08-07 is a weekday; next 09:00 is that morning.
        val cron = CronSchedule.parse("0 9 * * MON-FRI")!!
        val from = zdt("2026-08-06T10:30") // Thursday
        assertEquals(zdt("2026-08-07T09:00"), cron.nextFireTime(from))
    }

    @Test
    fun nextFireTime_weekdaySkipsWeekend() {
        // Friday 2026-08-07 20:00 -> next weekday 09:00 is Monday 2026-08-10.
        val cron = CronSchedule.parse("0 9 * * MON-FRI")!!
        val from = zdt("2026-08-07T20:00")
        assertEquals(zdt("2026-08-10T09:00"), cron.nextFireTime(from))
    }

    @Test
    fun nextFireTime_weeklyOnSunday() {
        val cron = CronSchedule.parse("0 8 * * 0")!! // Sunday 08:00
        val from = zdt("2026-08-06T10:30") // Thursday
        assertEquals(zdt("2026-08-09T08:00"), cron.nextFireTime(from))
    }

    @Test
    fun nextFireTime_weeklyOnSundayAsSeven() {
        // Classic cron also spells Sunday as 7; it must be normalized to 0.
        val cron = CronSchedule.parse("0 8 * * 7")!!
        val from = zdt("2026-08-06T10:30") // Thursday
        assertEquals(zdt("2026-08-09T08:00"), cron.nextFireTime(from))
        assertTrue(cron.matches(LocalDate.of(2026, 8, 9))) // Sunday
    }

    @Test
    fun nextFireTime_questionMarkActsAsWildcard() {
        val cron = CronSchedule.parse("0 12 ? * MON")!!
        val from = zdt("2026-08-06T10:30") // Thursday
        assertEquals(zdt("2026-08-10T12:00"), cron.nextFireTime(from))
    }

    @Test
    fun nextFireTime_annualJanuaryFirst() {
        val cron = CronSchedule.parse("0 0 1 1 *")!!
        val from = zdt("2026-08-06T10:30")
        assertEquals(zdt("2027-01-01T00:00"), cron.nextFireTime(from))
    }

    @Test
    fun nextFireTime_domAndDowOrRule() {
        // Day-of-month OR day-of-week: 1st of any month, or any Monday.
        val cron = CronSchedule.parse("0 0 1 * 1")!!
        // 2026-08-06 is Thursday; next 00:00 is Monday 2026-08-10.
        val from = zdt("2026-08-06T10:30")
        assertEquals(zdt("2026-08-10T00:00"), cron.nextFireTime(from))
    }

    @Test
    fun matches_respectsDomDowOrRule() {
        val cron = CronSchedule.parse("0 0 1 * 1")!!
        // 2026-08-01 is a Saturday (1st of month) -> DOM matches.
        assertTrue(cron.matches(LocalDate.of(2026, 8, 1)))
        // 2026-08-03 is a Monday -> DOW matches.
        assertTrue(cron.matches(LocalDate.of(2026, 8, 3)))
        // 2026-08-04 is a Tuesday, not the 1st -> no match.
        assertTrue(!cron.matches(LocalDate.of(2026, 8, 4)))
    }

    @Test
    fun nextFireTime_returnsNullWhenNoOccurrenceWithinWindow() {
        // Feb 29 only exists in leap years; 2028-02-29 -> 2032-02-29 is 1461 days,
        // beyond the 730-day search window, so no future occurrence is found.
        val cron = CronSchedule.parse("0 0 29 2 *")!!
        val from = zdt("2028-03-01T00:00")
        assertNull(cron.nextFireTime(from))
    }

    @Test
    fun cronDayOfWeek_sundayIsZero() {
        assertEquals(0, CronSchedule.cronDayOfWeek(LocalDate.of(2026, 8, 9))) // Sunday
        assertEquals(1, CronSchedule.cronDayOfWeek(LocalDate.of(2026, 8, 10))) // Monday
        assertEquals(6, CronSchedule.cronDayOfWeek(LocalDate.of(2026, 8, 15))) // Saturday
    }
}
