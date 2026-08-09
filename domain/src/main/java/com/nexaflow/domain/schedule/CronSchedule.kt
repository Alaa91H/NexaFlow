package com.nexaflow.domain.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Pure, testable 5-field cron expression (minute hour day-of-month month
 * day-of-week) with next-fire-time calculation.
 *
 * Field syntax: `*`, `?`, steps (`*` with a `/N` suffix, e.g. every 15 minutes),
 * ranges `a-b`, lists `a,b,c`, single values, plus the 3-letter English names
 * for months and weekdays. `?` is accepted anywhere as a synonym for `*`
 * (as in Quartz).
 *
 * Day-of-week follows the classic convention 0 and 7 = Sunday, 1..6 = Mon..Sat.
 * When both day-of-month and day-of-week are restricted, the cron OR rule
 * applies: the expression fires when EITHER field matches.
 */
data class CronSchedule(
    val minutes: Set<Int>,
    val hours: Set<Int>,
    val daysOfMonth: Set<Int>,
    val months: Set<Int>,
    val daysOfWeek: Set<Int>
) {

    /** True when this cron matches [date] (including the DOM/DOW OR rule). */
    fun matches(date: LocalDate): Boolean {
        if (date.monthValue !in months) return false
        val domRestricted = daysOfMonth != ALL_DAY_OF_MONTH
        val dowRestricted = daysOfWeek != ALL_DAY_OF_WEEK
        val domMatch = date.dayOfMonth in daysOfMonth
        val dowMatch = cronDayOfWeek(date) in daysOfWeek // daysOfWeek is normalized to 0..6
        return when {
            domRestricted && dowRestricted -> domMatch || dowMatch
            domRestricted -> domMatch
            dowRestricted -> dowMatch
            else -> true
        }
    }

    /**
     * Next fire time strictly after [fromMillis], searching up to 2 years ahead.
     * Returns null when no future occurrence exists.
     */
    fun nextFireTime(fromMillis: Long): Long? {
        val start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), ZoneId.systemDefault())
            .truncatedTo(ChronoUnit.MINUTES)
        var day = start.toLocalDate()
        repeat(MAX_SEARCH_DAYS) {
            if (matches(day)) {
                val fire = firstFireInDay(day, start)
                if (fire != null) return fire.toInstant().toEpochMilli()
            }
            day = day.plusDays(1)
        }
        return null
    }

    private fun firstFireInDay(day: LocalDate, after: ZonedDateTime): ZonedDateTime? {
        val zone = after.zone
        val sameDay = day == after.toLocalDate()
        for (h in hours.sorted()) {
            if (sameDay && h < after.hour) continue
            for (m in minutes.sorted()) {
                if (sameDay && (h < after.hour || (h == after.hour && m <= after.minute))) continue
                return ZonedDateTime.of(day, LocalTime.of(h, m), zone)
            }
        }
        return null
    }

    companion object {
        private const val MAX_SEARCH_DAYS = 730

        private val ALL_DAY_OF_MONTH = (1..31).toSet()
        // Parsed DOW is normalized to 0..6 (7 -> 0), so the "unrestricted" set
        // used for the DOM/DOW OR-rule detection must match the normalized form.
        private val ALL_DAY_OF_WEEK = (0..6).toSet()

        private val MONTH_NAMES = mapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
            "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
        )

        private val WEEKDAY_NAMES = mapOf(
            "sun" to 0, "mon" to 1, "tue" to 2, "wed" to 3,
            "thu" to 4, "fri" to 5, "sat" to 6
        )

        /** Normalizes a java.time weekday to cron convention (0 = Sunday). */
        fun cronDayOfWeek(date: LocalDate): Int = date.dayOfWeek.value % 7

        /** Parses a 5-field expression, or returns null for malformed input. */
        fun parse(expression: String): CronSchedule? {
            val fields = expression.trim().split(Regex("\\s+"))
            if (fields.size != 5) return null
            val minutes = parseField(fields[0], 0, 59) ?: return null
            val hours = parseField(fields[1], 0, 23) ?: return null
            val daysOfMonth = parseField(fields[2], 1, 31) ?: return null
            val months = parseField(fields[3], 1, 12, MONTH_NAMES) ?: return null
            val daysOfWeek = parseField(fields[4], 0, 7, WEEKDAY_NAMES)
                ?.map { if (it == 7) 0 else it } // classic 7 = Sunday, normalize to 0
                ?.toSet()
                ?: return null
            return CronSchedule(minutes, hours, daysOfMonth, months, daysOfWeek)
        }

        /** Parses one field into the set of matching values, or null if invalid. */
        private fun parseField(
            raw: String,
            min: Int,
            max: Int,
            names: Map<String, Int>? = null
        ): Set<Int>? {
            val result = mutableSetOf<Int>()
            for (part in raw.split(',')) {
                val stepMatch = Regex("^(.+?)/(\\d+)$").matchEntire(part)
                val step = stepMatch?.groupValues?.get(2)?.toIntOrNull() ?: 1
                if (step < 1) return null
                val rangeSpec = stepMatch?.groupValues?.get(1) ?: part
                val (lo, hi) = when {
                    rangeSpec == "*" || rangeSpec == "?" -> min to max
                    rangeSpec.contains('-') -> {
                        val bounds = rangeSpec.split('-')
                        if (bounds.size != 2) return null
                        val a = parseValue(bounds[0], names) ?: return null
                        val b = parseValue(bounds[1], names) ?: return null
                        a to b
                    }
                    else -> {
                        val v = parseValue(rangeSpec, names) ?: return null
                        v to v
                    }
                }
                if (lo < min || hi > max || lo > hi) return null
                var v = lo
                while (v <= hi) {
                    result += v
                    v += step
                }
            }
            return result.ifEmpty { null }
        }

        private fun parseValue(raw: String, names: Map<String, Int>?): Int? {
            raw.toIntOrNull()?.let { return it }
            return names?.get(raw.lowercase())
        }
    }
}
