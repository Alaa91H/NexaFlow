package com.nexaflow.domain.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure, testable calculation of the next fire time for a TIME trigger.
 *
 * Shared by the alarm scheduler and the dashboard's "Next run" preview so the
 * schedule logic lives in exactly one place. Returns the epoch millis of the
 * next occurrence, or null when no future occurrence exists (e.g. a past
 * one-shot or a finished date range).
 */
object TimeTriggerCalculator {

    const val REPEAT_ONCE = "ONCE"
    const val REPEAT_DAILY = "DAILY"
    const val REPEAT_WEEKDAYS = "WEEKDAYS"
    const val REPEAT_WEEKENDS = "WEEKENDS"
    const val REPEAT_SPECIFIC_DAYS = "SPECIFIC_DAYS"
    const val REPEAT_MONTHLY = "MONTHLY"
    const val REPEAT_SPECIFIC_DATE = "SPECIFIC_DATE"
    const val REPEAT_DATE_RANGE = "DATE_RANGE"

    private const val MAX_SEARCH_DAYS = 370

    fun nextFireTime(config: Map<String, String>, fromMillis: Long): Long? {
        // A time-range window schedules at the window start each day.
        val time = if (config["timeMode"] == "RANGE") config["rangeStart"] ?: config["time"] else config["time"]
        if (time.isNullOrBlank()) return null
        val localTime = runCatching { LocalTime.parse(time) }.getOrNull() ?: return null
        val zone = ZoneId.systemDefault()
        val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zone).toLocalDate()
        val repeat = config["repeat"] ?: REPEAT_DAILY

        // A specific date is treated as a single-day window.
        val specificDate = if (repeat == REPEAT_SPECIFIC_DATE) config["date"]?.let(::parseDate) else null
        // Date-range window bounds (yyyy-MM-dd).
        val startDate = specificDate ?: config["startDate"]?.let(::parseDate)
        val endDate = specificDate ?: config["endDate"]?.let(::parseDate)
        if (endDate != null && today.isAfter(endDate)) return null

        var daysChecked = 0
        var candidate = ZonedDateTime.of(today, localTime, zone)
        while (daysChecked < MAX_SEARCH_DAYS) {
            val day = candidate.toLocalDate()
            if (startDate != null && day.isBefore(startDate)) {
                candidate = ZonedDateTime.of(startDate, localTime, zone)
                continue
            }
            if (endDate != null && day.isAfter(endDate)) return null
            if (matchesRepeat(repeat, config, day)) {
                val millis = candidate.toInstant().toEpochMilli()
                if (millis > fromMillis) return millis
            }
            candidate = candidate.plusDays(1)
            daysChecked++
        }
        return null
    }

    /** True when the repeat mode fires on [day]. ONCE/DAILY always match; the window is handled by [nextFireTime]. */
    fun matchesRepeat(repeat: String, config: Map<String, String>, day: LocalDate): Boolean {
        val dayOfWeek = day.dayOfWeek.value // 1=MON .. 7=SUN
        return when (repeat) {
            REPEAT_WEEKDAYS -> dayOfWeek in 1..5
            REPEAT_WEEKENDS -> dayOfWeek == 6 || dayOfWeek == 7
            REPEAT_SPECIFIC_DAYS -> {
                val days = config["days"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
                dayOfWeek in days
            }
            REPEAT_MONTHLY -> {
                val monthDay = config["monthDay"]?.toIntOrNull() ?: 1
                day.dayOfMonth == monthDay
            }
            REPEAT_SPECIFIC_DATE -> {
                val date = config["date"]?.let(::parseDate)
                date != null && day == date
            }
            else -> true // ONCE and DAILY
        }
    }

    private fun parseDate(value: String): LocalDate {
        return runCatching { LocalDate.parse(value) }.getOrNull()
            ?: LocalDate.parse(value.replace('/', '-'))
    }
}
