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
    /** Google-Tasks style: e.g. "first Monday" or "last Friday" of the month. */
    const val REPEAT_MONTHLY_WEEKDAY = "MONTHLY_WEEKDAY"
    const val REPEAT_SPECIFIC_DATE = "SPECIFIC_DATE"
    const val REPEAT_DATE_RANGE = "DATE_RANGE"

    private const val MAX_SEARCH_DAYS = 370

    /**
     * [zone] is injectable for testing DST transitions; production callers use
     * the system default. Wall-clock schedules are computed against this zone,
     * so a daily 08:00 keeps firing at 08:00 local across daylight-saving
     * shifts (the classic "now fires at 09:00" bug would need RTC-based math).
     */
    fun nextFireTime(
        config: Map<String, String>,
        fromMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        // A time-range window schedules at the window start each day.
        val time = if (config["timeMode"] == "RANGE") config["rangeStart"] ?: config["time"] else config["time"]
        if (time.isNullOrBlank()) return null
        val localTime = runCatching { LocalTime.parse(time) }.getOrNull() ?: return null
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

    /**
     * For a RANGE time trigger, returns the exact millis when the window that
     * starts at [windowStartMillis] ends. Handles overnight ranges (e.g.
     * 22:00 -> 06:00 ends the next day). Returns null for non-range configs.
     */
    fun windowEndMillis(
        config: Map<String, String>,
        windowStartMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (config["timeMode"] != "RANGE") return null
        val start = config["rangeStart"] ?: return null
        val end = config["rangeEnd"] ?: return null
        val startTime = runCatching { LocalTime.parse(start) }.getOrNull() ?: return null
        val endTime = runCatching { LocalTime.parse(end) }.getOrNull() ?: return null
        val startZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(windowStartMillis), zone)
        val startMinutes = startTime.hour * 60 + startTime.minute
        val endMinutes = endTime.hour * 60 + endTime.minute
        // A zero-length window (end == start) is treated as a full 24h window so
        // the end alarm never collides with the start alarm.
        val overnight = endMinutes <= startMinutes
        val endDate = if (overnight) startZdt.toLocalDate().plusDays(1) else startZdt.toLocalDate()
        return ZonedDateTime.of(endDate, endTime, zone).toInstant().toEpochMilli()
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
            REPEAT_MONTHLY_WEEKDAY -> {
                val weekday = config["weekday"]?.toIntOrNull() ?: 0
                if (dayOfWeek != weekday) return false
                val weekOfMonth = config["weekOfMonth"] ?: "1"
                // Occurrence of this weekday inside the month: (dayOfMonth - 1) / 7 + 1,
                // which yields 1..5. "LAST" matches the final occurrence of the month.
                val occurrence = (day.dayOfMonth - 1) / 7 + 1
                if (weekOfMonth == "LAST") {
                    // The last occurrence is when adding 7 days crosses into the next month.
                    day.plusDays(7).month != day.month
                } else {
                    occurrence == (weekOfMonth.toIntOrNull() ?: -1)
                }
            }
            REPEAT_SPECIFIC_DATE -> {
                val date = config["date"]?.let(::parseDate)
                date != null && day == date
            }
            else -> true // ONCE and DAILY
        }
    }

    private fun parseDate(value: String): LocalDate? {
        // A malformed stored date (legacy data, manual edit, localized format)
        // must degrade to "no date bound", never throw: this runs in the
        // scheduler's startup collect and the dashboard's next-run preview, so
        // an uncaught DateTimeParseException would force-close the app on open.
        return runCatching { LocalDate.parse(value) }.getOrNull()
            ?: runCatching { LocalDate.parse(value.replace('/', '-')) }.getOrNull()
    }
}
