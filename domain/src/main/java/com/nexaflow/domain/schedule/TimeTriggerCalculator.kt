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
 * next occurrence, or null when no future occurrence exists (for example a
 * past one-shot, an elapsed end date, or an exhausted occurrence limit).
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
    /** Custom Google-Tasks-style interval. Config: interval + intervalUnit + startDate. */
    const val REPEAT_INTERVAL = "INTERVAL"

    const val INTERVAL_DAY = "DAY"
    const val INTERVAL_WEEK = "WEEK"
    const val INTERVAL_MONTH = "MONTH"
    const val INTERVAL_YEAR = "YEAR"

    const val END_NEVER = "NEVER"
    const val END_ON_DATE = "ON_DATE"
    const val END_AFTER_OCCURRENCES = "AFTER_OCCURRENCES"

    // A daily scan is intentionally bounded. The UI limits custom intervals to
    // 99 units, and this horizon still covers yearly schedules for up to 100 years.
    private const val MAX_SEARCH_DAYS = 366 * 100

    /**
     * [zone] is injectable for testing DST transitions; production callers use
     * the system default. Wall-clock schedules are computed against this zone,
     * so a daily 08:00 keeps firing at 08:00 local across daylight-saving shifts.
     */
    fun nextFireTime(
        config: Map<String, String>,
        fromMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        val time = if (config["timeMode"] == "RANGE") config["rangeStart"] ?: config["time"] else config["time"]
        if (time.isNullOrBlank()) return null
        val localTime = runCatching { LocalTime.parse(time) }.getOrNull() ?: return null
        val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zone).toLocalDate()
        val repeat = config["repeat"] ?: REPEAT_DAILY

        val specificDate = if (repeat == REPEAT_SPECIFIC_DATE) config["date"]?.let(::parseDate) else null
        val startDate = specificDate ?: config["startDate"]?.let(::parseDate)
        val endDate = specificDate ?: effectiveEndDate(repeat, config)
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) return null
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
                if (millis > fromMillis) {
                    val limit = occurrenceLimit(config)
                    if (limit != null && startDate != null && occurrenceNumber(repeat, config, startDate, day) > limit) {
                        return null
                    }
                    return millis
                }
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
        val overnight = endMinutes <= startMinutes
        val endDate = if (overnight) startZdt.toLocalDate().plusDays(1) else startZdt.toLocalDate()
        return ZonedDateTime.of(endDate, endTime, zone).toInstant().toEpochMilli()
    }

    /** True when the repeat mode fires on [day]. Date windows and end limits are handled by [nextFireTime]. */
    fun matchesRepeat(repeat: String, config: Map<String, String>, day: LocalDate): Boolean {
        val dayOfWeek = day.dayOfWeek.value // 1=MON .. 7=SUN
        return when (repeat) {
            REPEAT_WEEKDAYS -> dayOfWeek in 1..5
            REPEAT_WEEKENDS -> dayOfWeek == 6 || dayOfWeek == 7
            REPEAT_SPECIFIC_DAYS -> selectedWeekdays(config).let { dayOfWeek in it }
            REPEAT_MONTHLY -> {
                val monthDay = config["monthDay"]?.toIntOrNull() ?: 1
                day.dayOfMonth == monthDay
            }
            REPEAT_MONTHLY_WEEKDAY -> {
                val weekday = config["weekday"]?.toIntOrNull() ?: 0
                if (dayOfWeek != weekday) return false
                val weekOfMonth = config["weekOfMonth"] ?: "1"
                val occurrence = (day.dayOfMonth - 1) / 7 + 1
                if (weekOfMonth == "LAST") day.plusDays(7).month != day.month
                else occurrence == (weekOfMonth.toIntOrNull() ?: -1)
            }
            REPEAT_SPECIFIC_DATE -> config["date"]?.let(::parseDate) == day
            REPEAT_INTERVAL -> matchesInterval(config, day)
            else -> true // ONCE and DAILY retain their legacy next-occurrence behavior.
        }
    }

    private fun effectiveEndDate(repeat: String, config: Map<String, String>): LocalDate? = when {
        repeat == REPEAT_DATE_RANGE -> config["endDate"]?.let(::parseDate)
        config["endMode"] == END_ON_DATE -> config["endDate"]?.let(::parseDate)
        else -> null
    }

    private fun occurrenceLimit(config: Map<String, String>): Int? {
        if (config["endMode"] != END_AFTER_OCCURRENCES) return null
        return config["endCount"]?.toIntOrNull()?.coerceIn(1, 999)
    }

    private fun occurrenceNumber(
        repeat: String,
        config: Map<String, String>,
        startDate: LocalDate,
        candidateDate: LocalDate
    ): Int {
        var count = 0
        var day = startDate
        while (!day.isAfter(candidateDate)) {
            if (matchesRepeat(repeat, config, day)) count++
            day = day.plusDays(1)
        }
        return count
    }

    private fun matchesInterval(config: Map<String, String>, day: LocalDate): Boolean {
        val anchor = config["startDate"]?.let(::parseDate) ?: day
        if (day.isBefore(anchor)) return false
        val interval = config["interval"]?.toIntOrNull()?.coerceIn(1, 99) ?: 1
        return when (config["intervalUnit"] ?: INTERVAL_DAY) {
            INTERVAL_WEEK -> {
                val anchorWeekStart = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
                val dayWeekStart = day.minusDays((day.dayOfWeek.value - 1).toLong())
                val weeks = java.time.temporal.ChronoUnit.DAYS.between(anchorWeekStart, dayWeekStart) / 7
                weeks % interval == 0L && day.dayOfWeek.value in selectedWeekdays(config, anchor.dayOfWeek.value)
            }
            INTERVAL_MONTH -> {
                val months = (day.year - anchor.year) * 12 + day.monthValue - anchor.monthValue
                val monthDay = config["monthDay"]?.toIntOrNull() ?: anchor.dayOfMonth
                months >= 0 && months % interval == 0 && day.dayOfMonth == monthDay
            }
            INTERVAL_YEAR -> {
                val years = day.year - anchor.year
                years >= 0 && years % interval == 0 && day.monthValue == anchor.monthValue && day.dayOfMonth == anchor.dayOfMonth
            }
            else -> {
                val days = java.time.temporal.ChronoUnit.DAYS.between(anchor, day)
                days >= 0 && days % interval == 0L
            }
        }
    }

    private fun selectedWeekdays(config: Map<String, String>, fallback: Int? = null): Set<Int> {
        val selected = config["days"]
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in 1..7 }
            ?.toSet()
            .orEmpty()
        return selected.ifEmpty { fallback?.let(::setOf).orEmpty() }
    }

    private fun parseDate(value: String): LocalDate? {
        // A malformed stored date (legacy data, manual edit, localized format)
        // must degrade to "no date bound", never throw: this runs in the
        // scheduler's startup collect and the dashboard's next-run preview.
        return runCatching { LocalDate.parse(value) }.getOrNull()
            ?: runCatching { LocalDate.parse(value.replace('/', '-')) }.getOrNull()
    }
}
