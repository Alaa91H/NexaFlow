package com.nexaflow.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
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

    // ── DST transitions ────────────────────────────────────────────────────────
    // Wall-clock schedules must survive daylight-saving transitions. These tests
    // pass an explicit zone so they pin the behavior regardless of the host zone.

    private fun zoned(zoneId: String, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of(zoneId))
            .toInstant()
            .toEpochMilli()

    @Test
    fun `daily schedule across spring-forward keeps local wall-clock time`() {
        // 2026-03-29 02:00 CET -> CEST (spring forward, 23h day).
        // A 08:00 schedule fired on the 28th at 07:00 UTC; the next fire on
        // the 29th must still be 08:00 CEST = 06:00 UTC (1h earlier UTC).
        val berlin = ZoneId.of("Europe/Berlin")
        // After 08:00 on the 28th, so the next fire is on the 29th — the day
        // the clocks jump forward.
        val before = zoned("Europe/Berlin", 2026, 3, 28, 12, 0)
        val next = TimeTriggerCalculator.nextFireTime(mapOf("time" to "08:00"), before, berlin)
        assertNotNull(next)
        val zdt = Instant.ofEpochMilli(next!!).atZone(berlin)
        assertEquals("must remain 08:00 local", 8, zdt.hour)
        assertEquals(LocalDate.of(2026, 3, 29), zdt.toLocalDate())
        // 08:00 CEST == 06:00 UTC; without DST awareness it would be 07:00 UTC.
        assertEquals("08:00 CEST", 6, zdt.toInstant().atZone(java.time.ZoneOffset.UTC).hour)
    }

    @Test
    fun `daily schedule across fall-back keeps local wall-clock time`() {
        // 2026-10-25 03:00 CEST -> CET (fall back, 25h day).
        // A 08:00 schedule on the 25th must still be 08:00 CET = 07:00 UTC.
        val berlin = ZoneId.of("Europe/Berlin")
        // After 08:00 on the 24th, so the next fire is on the 25th — the day
        // the clocks fall back.
        val before = zoned("Europe/Berlin", 2026, 10, 24, 12, 0)
        val next = TimeTriggerCalculator.nextFireTime(mapOf("time" to "08:00"), before, berlin)
        assertNotNull(next)
        val zdt = Instant.ofEpochMilli(next!!).atZone(berlin)
        assertEquals("must remain 08:00 local", 8, zdt.hour)
        assertEquals(LocalDate.of(2026, 10, 25), zdt.toLocalDate())
        assertEquals("08:00 CET", 7, zdt.toInstant().atZone(java.time.ZoneOffset.UTC).hour)
    }

    @Test
    fun `daily schedule at the spring-forward gap hour resolves to the later instant`() {
        // 02:30 on 2026-03-29 does not exist (02:00-03:00 jumps to 03:00
        // CEST). ZonedDateTime resolves to the shifted instant; the result
        // must still be a valid local time on the 29th.
        val berlin = ZoneId.of("Europe/Berlin")
        val before = zoned("Europe/Berlin", 2026, 3, 28, 12, 0)
        val next = TimeTriggerCalculator.nextFireTime(mapOf("time" to "02:30"), before, berlin)
        assertNotNull(next)
        val zdt = Instant.ofEpochMilli(next!!).atZone(berlin)
        assertEquals(LocalDate.of(2026, 3, 29), zdt.toLocalDate())
        assertTrue("02:30 resolves into 03:00-04:00 window", zdt.hour in 3..4)
    }

    @Test
    fun `weekly schedule on a DST day keeps weekday and local time`() {
        // 2026-03-08 02:00 EST -> EDT (spring forward in the US).
        val ny = ZoneId.of("America/New_York")
        // After 09:00 on the 7th, so the next fire is Sunday the 8th — the day
        // the US springs forward.
        val before = zoned("America/New_York", 2026, 3, 7, 12, 0)
        val next = TimeTriggerCalculator.nextFireTime(
            mapOf("time" to "09:00", "repeat" to "SPECIFIC_DAYS", "days" to "7"),
            before,
            ny
        )
        assertNotNull(next)
        val zdt = Instant.ofEpochMilli(next!!).atZone(ny)
        assertEquals(DayOfWeek.SUNDAY, zdt.dayOfWeek)
        assertEquals("must remain 09:00 local", 9, zdt.hour)
        // 09:00 EDT == 13:00 UTC; without DST awareness it would be 14:00 UTC.
        assertEquals(13, zdt.toInstant().atZone(java.time.ZoneOffset.UTC).hour)
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
    fun `monthly first day fires only when it matches a selected weekday`() {
        // 1 Aug 2026 is Saturday. With Tuesday/Wednesday selected, the first
        // eligible day-one is Tuesday 1 Sep 2026.
        val from = millisOf(2026, 8, 1, 0, 0)
        val config = mapOf(
            "time" to "08:00",
            "repeat" to "INTERVAL",
            "interval" to "1",
            "intervalUnit" to "MONTH",
            "startDate" to "2026-08-01",
            "monthlyDayMode" to "FIRST_DAY",
            "days" to "2,3"
        )

        val next = TimeTriggerCalculator.nextFireTime(config, from)

        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 9, 1), localDateOf(next!!))
    }

    @Test
    fun `monthly first day retains legacy behavior when no weekday is selected`() {
        val from = millisOf(2026, 8, 1, 0, 0)
        val config = mapOf(
            "time" to "08:00",
            "repeat" to "INTERVAL",
            "interval" to "1",
            "intervalUnit" to "MONTH",
            "startDate" to "2026-08-01",
            "monthlyDayMode" to "FIRST_DAY"
        )

        val next = TimeTriggerCalculator.nextFireTime(config, from)

        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 1), localDateOf(next!!))
    }

    @Test
    fun `monthly last day skips months whose last day is outside selected weekdays`() {
        // Aug 31 and Sep 30, 2026 are Monday and Wednesday; Oct 31 is Saturday.
        val from = millisOf(2026, 8, 1, 0, 0)
        val config = mapOf(
            "time" to "08:00",
            "repeat" to "INTERVAL",
            "interval" to "1",
            "intervalUnit" to "MONTH",
            "startDate" to "2026-08-01",
            "monthlyDayMode" to "LAST_DAY",
            "days" to "6,7"
        )

        val next = TimeTriggerCalculator.nextFireTime(config, from)

        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 10, 31), localDateOf(next!!))
    }

    @Test
    fun `monthly weekday first monday fires on the first monday of the month`() {
        // August 2026: Mondays are the 3rd, 10th, 17th, 24th, 31st.
        val from = millisOf(2026, 8, 1, 0, 0)
        val config = mapOf(
            "time" to "08:00",
            "repeat" to "MONTHLY_WEEKDAY",
            "weekday" to "1",
            "weekOfMonth" to "1"
        )
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 3), localDateOf(next!!))
    }

    @Test
    fun `monthly weekday second wednesday fires on the 2nd wednesday`() {
        // August 2026: Wednesdays are the 5th, 12th, 19th, 26th.
        val from = millisOf(2026, 8, 1, 0, 0)
        val config = mapOf(
            "time" to "08:00",
            "repeat" to "MONTHLY_WEEKDAY",
            "weekday" to "3",
            "weekOfMonth" to "2"
        )
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 12), localDateOf(next!!))
    }

    @Test
    fun `monthly weekday last friday fires on the final friday`() {
        // August 2026: Fridays are the 7th, 14th, 21st, 28th.
        val from = millisOf(2026, 8, 1, 0, 0)
        val config = mapOf(
            "time" to "08:00",
            "repeat" to "MONTHLY_WEEKDAY",
            "weekday" to "5",
            "weekOfMonth" to "LAST"
        )
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 28), localDateOf(next!!))
    }

    @Test
    fun `monthly weekday rolls to the next month when the occurrence passed`() {
        // After the 1st Monday of August (the 3rd), the next 1st Monday is in September.
        val from = millisOf(2026, 8, 4, 0, 0)
        val config = mapOf(
            "time" to "08:00",
            "repeat" to "MONTHLY_WEEKDAY",
            "weekday" to "1",
            "weekOfMonth" to "1"
        )
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 9, 7), localDateOf(next!!))
    }

    @Test
    fun `monthly weekday does not fire on the same weekday of a different occurrence`() {
        // 2026-08-10 is the 2nd Monday; config asks for the 1st Monday only.
        val from = millisOf(2026, 8, 10, 0, 0)
        val config = mapOf(
            "time" to "08:00",
            "repeat" to "MONTHLY_WEEKDAY",
            "weekday" to "1",
            "weekOfMonth" to "1"
        )
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        // Skips to the 1st Monday of September (the 7th).
        assertEquals(LocalDate.of(2026, 9, 7), localDateOf(next!!))
    }

    @Test
    fun `monthly weekday time range schedules at range start on the matching day`() {
        // First Sunday of August 2026 is the 2nd.
        val from = millisOf(2026, 8, 1, 7, 0)
        val config = mapOf(
            "timeMode" to "RANGE",
            "rangeStart" to "22:00",
            "rangeEnd" to "06:00",
            "repeat" to "MONTHLY_WEEKDAY",
            "weekday" to "7",
            "weekOfMonth" to "1"
        )
        val next = TimeTriggerCalculator.nextFireTime(config, from)
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 2), localDateOf(next!!))
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

    @Test
    fun `custom interval every three days keeps its start-date anchor`() {
        val config = mapOf(
            "time" to "08:00",
            "repeat" to "INTERVAL",
            "interval" to "3",
            "intervalUnit" to "DAY",
            "startDate" to "2026-08-01"
        )
        val next = TimeTriggerCalculator.nextFireTime(config, millisOf(2026, 8, 5, 9, 0))

        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 7), localDateOf(next!!))
    }

    @Test
    fun `custom interval every two weeks observes selected weekdays`() {
        val config = mapOf(
            "time" to "08:00",
            "repeat" to "INTERVAL",
            "interval" to "2",
            "intervalUnit" to "WEEK",
            "days" to "1,5",
            "startDate" to "2026-08-03"
        )
        val next = TimeTriggerCalculator.nextFireTime(config, millisOf(2026, 8, 7, 9, 0))

        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 8, 17), localDateOf(next!!))
    }

    @Test
    fun `custom interval every two months preserves the anchor day`() {
        val config = mapOf(
            "time" to "08:00",
            "repeat" to "INTERVAL",
            "interval" to "2",
            "intervalUnit" to "MONTH",
            "startDate" to "2026-01-15"
        )
        val next = TimeTriggerCalculator.nextFireTime(config, millisOf(2026, 2, 20, 9, 0))

        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 3, 15), localDateOf(next!!))
    }

    @Test
    fun `custom interval stops on the selected end date`() {
        val config = mapOf(
            "time" to "08:00",
            "repeat" to "INTERVAL",
            "interval" to "1",
            "intervalUnit" to "DAY",
            "startDate" to "2026-08-01",
            "endMode" to "ON_DATE",
            "endDate" to "2026-08-03"
        )

        assertNull(TimeTriggerCalculator.nextFireTime(config, millisOf(2026, 8, 3, 9, 0)))
    }

    @Test
    fun `custom interval stops after the configured occurrence count`() {
        val config = mapOf(
            "time" to "08:00",
            "repeat" to "INTERVAL",
            "interval" to "1",
            "intervalUnit" to "DAY",
            "startDate" to "2026-08-01",
            "endMode" to "AFTER_OCCURRENCES",
            "endCount" to "3"
        )

        val third = TimeTriggerCalculator.nextFireTime(config, millisOf(2026, 8, 2, 9, 0))
        assertNotNull(third)
        assertEquals(LocalDate.of(2026, 8, 3), localDateOf(third!!))
        assertNull(TimeTriggerCalculator.nextFireTime(config, millisOf(2026, 8, 3, 9, 0)))
    }
}
