package com.nexaflow.core.execution.task

import kotlinx.serialization.Serializable

/**
 * Production-grade Scheduler contracts for NexaFlow.
 *
 * The scheduler supports the following schedule types:
 *  - [OneShot]     – fires once at an absolute epoch time
 *  - [Recurring]   – fires on a fixed interval
 *  - [CronLike]    – fires based on time-of-day/week rules
 *  - [SunRelative] – fires relative to local sunrise/sunset
 *
 * All schedule types are:
 *  - Timezone-aware and DST-safe
 *  - Persistent across process death and device reboots
 *  - Subject to a [MisfirePolicy] if the trigger time was missed
 *
 * NexaFlow does NOT create a second scheduler; this extends the existing
 * [TaskManager] by adding a formal contract and persistent state model.
 */
@Serializable
sealed interface ScheduleDefinition {

    /** A unique stable identifier for this schedule. */
    val scheduleId: String

    /** Human-readable name for UI and diagnostics. */
    val displayName: String

    /** What to do when a trigger time was missed (e.g., while the device was off). */
    val misfirePolicy: MisfirePolicy

    /** Optional deadline: the schedule is auto-cancelled after this epoch ms. */
    val expiresAtEpochMs: Long?

    /** The workflow to trigger when the schedule fires. */
    val workflowId: String

    /**
     * Fire once at [triggerAtEpochMs].
     */
    @Serializable
    data class OneShot(
        override val scheduleId: String,
        override val displayName: String,
        override val workflowId: String,
        val triggerAtEpochMs: Long,
        override val misfirePolicy: MisfirePolicy = MisfirePolicy.FIRE_NOW,
        override val expiresAtEpochMs: Long? = null
    ) : ScheduleDefinition {
        init { require(scheduleId.isNotBlank()) { "scheduleId must not be blank" } }
    }

    /**
     * Fires repeatedly at a fixed [intervalMs] after the first fire at [firstFireAtEpochMs].
     * Supports optional [maxFires] to limit total executions.
     */
    @Serializable
    data class Recurring(
        override val scheduleId: String,
        override val displayName: String,
        override val workflowId: String,
        val firstFireAtEpochMs: Long,
        val intervalMs: Long,
        val maxFires: Int = Int.MAX_VALUE,
        val jitterMs: Long = 0L,
        override val misfirePolicy: MisfirePolicy = MisfirePolicy.SKIP,
        override val expiresAtEpochMs: Long? = null
    ) : ScheduleDefinition {
        init {
            require(intervalMs in MIN_INTERVAL_MS..MAX_INTERVAL_MS) {
                "intervalMs must be in $MIN_INTERVAL_MS..$MAX_INTERVAL_MS"
            }
            require(maxFires >= 1) { "maxFires must be at least 1" }
            require(jitterMs >= 0 && jitterMs < intervalMs) {
                "jitterMs must be in 0..<intervalMs"
            }
        }

        companion object {
            const val MIN_INTERVAL_MS = 60_000L          // 1 minute
            const val MAX_INTERVAL_MS = 86_400_000L * 365 // 1 year
        }
    }

    /**
     * Cron-like trigger. Supports:
     * - [hour] + [minute]: fires daily at specified time
     * - [daysOfWeek]: optionally restrict to specific ISO days (1=Mon..7=Sun)
     * - [daysOfMonth]: optionally restrict to specific days of month
     */
    @Serializable
    data class CronLike(
        override val scheduleId: String,
        override val displayName: String,
        override val workflowId: String,
        val hour: Int,
        val minute: Int,
        val daysOfWeek: Set<Int> = emptySet(),
        val daysOfMonth: Set<Int> = emptySet(),
        val timezoneId: String = "UTC",
        override val misfirePolicy: MisfirePolicy = MisfirePolicy.SKIP,
        override val expiresAtEpochMs: Long? = null
    ) : ScheduleDefinition {
        init {
            require(hour in 0..23) { "hour must be in 0..23" }
            require(minute in 0..59) { "minute must be in 0..59" }
            require(daysOfWeek.all { it in 1..7 }) { "daysOfWeek must contain 1..7" }
            require(daysOfMonth.all { it in 1..31 }) { "daysOfMonth must contain 1..31" }
        }
    }

    /**
     * Fires relative to local sunrise or sunset, optionally offset by [offsetMinutes].
     */
    @Serializable
    data class SunRelative(
        override val scheduleId: String,
        override val displayName: String,
        override val workflowId: String,
        val event: SunEvent,
        val offsetMinutes: Int = 0,
        val latitude: Double,
        val longitude: Double,
        val timezoneId: String = "UTC",
        override val misfirePolicy: MisfirePolicy = MisfirePolicy.SKIP,
        override val expiresAtEpochMs: Long? = null
    ) : ScheduleDefinition {
        init {
            require(latitude in -90.0..90.0) { "latitude must be in -90..90" }
            require(longitude in -180.0..180.0) { "longitude must be in -180..180" }
            require(offsetMinutes in -720..720) { "offsetMinutes must be in -720..720" }
        }
    }
}

/**
 * Whether the sun event is sunrise or sunset.
 */
@Serializable
enum class SunEvent { SUNRISE, SUNSET }

/**
 * What to do if the scheduled time passed while the device/app was unavailable.
 */
@Serializable
enum class MisfirePolicy {
    /** Execute immediately when the device is back online. */
    FIRE_NOW,
    /** Skip the missed firing and wait for the next scheduled time. */
    SKIP,
    /** Fire once and then resume the normal schedule. */
    FIRE_ONCE_THEN_RESUME
}

/**
 * Persistent state of a schedule entry.
 */
@Serializable
data class ScheduleState(
    val scheduleId: String,
    val workflowId: String,
    val nextFireAtEpochMs: Long,
    val fireCount: Int = 0,
    val lastFiredAtEpochMs: Long? = null,
    val isActive: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * The primary interface through which NexaFlow registers, manages, and queries schedules.
 * Implementations must persist schedule state across process death and reboots.
 */
interface NexaFlowScheduler {
    /** Registers or updates a schedule. Idempotent: same scheduleId replaces the old entry. */
    suspend fun schedule(definition: ScheduleDefinition)

    /** Cancels a schedule by its ID. Returns false if it was not found. */
    suspend fun cancel(scheduleId: String): Boolean

    /** Returns all currently active schedule states. */
    suspend fun activeSchedules(): List<ScheduleState>

    /** Returns the schedule state for a given ID, or null if not found. */
    suspend fun stateFor(scheduleId: String): ScheduleState?

    /** Called on device boot to restore all durable schedules. */
    suspend fun restoreOnBoot()
}
