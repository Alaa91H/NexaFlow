package com.nexaflow.feature.dashboard

import com.nexaflow.domain.models.ExecutionRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The "Today" bento counts only today's runs and buckets them by hour.
 *
 * Both halves matter: a run from yesterday must not inflate today's count, and
 * a run at 23:59 must belong to today's last bucket instead of tomorrow's.
 */
class TodayExecutionHoursTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 11, 1)

    private fun record(id: String, at: LocalDateTime): ExecutionRecord = ExecutionRecord(
        id = id,
        automationId = "automation",
        automationName = "Routine",
        success = true,
        message = "",
        executedAt = at.atZone(zone).toInstant().toEpochMilli(),
    )

    @Test
    fun theDefaultsReadTheWallClock() {
        // The ViewModel calls this without arguments on every execution change,
        // so the defaults themselves are on the hot path: a run recorded now
        // must come back as the hour the device's clock currently shows.
        val now = System.currentTimeMillis()
        val expected = java.time.Instant.ofEpochMilli(now)
            .atZone(ZoneId.systemDefault())
            .hour
        val record = ExecutionRecord(
            id = "now",
            automationId = "automation",
            automationName = "Routine",
            success = true,
            message = "",
            executedAt = now,
        )

        assertEquals(listOf(expected), hoursOfToday(listOf(record)))
    }

    @Test
    fun onlyTodaysRunsAreKept() {
        val hours = hoursOfToday(
            records = listOf(
                record("a", today.atTime(7, 30)),
                record("b", today.minusDays(1).atTime(23, 59)),
                record("c", today.atTime(0, 0)),
            ),
            zone = zone,
            today = today,
        )

        assertEquals(listOf(7, 0), hours)
    }

    @Test
    fun theLastMinuteOfTheDayStaysInTodaysLastBucket() {
        val hours = hoursOfToday(
            records = listOf(record("late", today.atTime(23, 59))),
            zone = zone,
            today = today,
        )

        assertEquals(listOf(23), hours)
    }

    @Test
    fun noRunsTodayIsAnEmptyListNotAFailure() {
        assertEquals(emptyList<Int>(), hoursOfToday(emptyList(), zone, today))
        assertEquals(
            emptyList<Int>(),
            hoursOfToday(listOf(record("old", today.minusDays(2).atTime(9, 0))), zone, today),
        )
    }

    @Test
    fun theSameInstantIsGroupedByTheGivenZone() {
        val berlin = hoursOfToday(
            records = listOf(record("edge", today.atTime(0, 30))),
            zone = zone,
            today = today,
        )
        val utc = hoursOfToday(
            records = listOf(record("edge", today.atTime(0, 30))),
            zone = ZoneId.of("UTC"),
            today = today.minusDays(1),
        )

        assertEquals(listOf(0), berlin)
        // Midnight in Berlin is still the previous day in UTC: the zone decides
        // which day a run belongs to, so the count never drifts by a day.
        assertEquals(listOf(23), utc)
    }
}
