package com.nexaflow.domain.schedule

/**
 * Pure, testable interval schedule: fires every [intervalMinutes], aligned to
 * [startOffsetMillis] (epoch-based). A zero offset aligns to the epoch so the
 * fire times are globally stable; a non-zero offset shifts the alignment.
 */
data class IntervalSchedule(
    val intervalMinutes: Long,
    val startOffsetMillis: Long = 0L
) {
    init {
        require(intervalMinutes > 0) { "intervalMinutes must be positive" }
        require(startOffsetMillis >= 0) { "startOffsetMillis must be non-negative" }
    }

    private val periodMillis: Long = intervalMinutes * 60_000L

    /** Next fire time strictly after [fromMillis]. */
    fun nextFireTime(fromMillis: Long): Long {
        val elapsed = ((fromMillis - startOffsetMillis) % periodMillis + periodMillis) % periodMillis
        return fromMillis + (periodMillis - elapsed)
    }
}
