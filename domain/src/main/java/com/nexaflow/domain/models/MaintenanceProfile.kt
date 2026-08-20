package com.nexaflow.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.time.LocalTime

/**
 * Metadata for an [Automation] that represents recurring maintenance. It does
 * not execute anything by itself: AutomationScheduler and ExecutionEngine stay
 * the single scheduling and execution paths.
 */
@Immutable
@Serializable
data class MaintenanceProfile(
    val kind: MaintenanceKind,
    val window: MaintenanceWindow? = null,
    val retryPolicy: MaintenanceRetryPolicy = MaintenanceRetryPolicy(),
    val notificationPolicy: MaintenanceNotificationPolicy = MaintenanceNotificationPolicy.IMPORTANT_EVENTS,
    val dependencyAutomationIds: List<String> = emptyList(),
    val recoveryPolicy: MaintenanceRecoveryPolicy = MaintenanceRecoveryPolicy.DEFAULT
) {
    init {
        require(dependencyAutomationIds.all { it.isNotBlank() }) {
            "Maintenance dependencies must have non-blank automation ids"
        }
        require(dependencyAutomationIds.distinct().size == dependencyAutomationIds.size) {
            "Maintenance dependencies must not contain duplicates"
        }
    }
}

@Serializable
enum class MaintenanceKind {
    DAILY,
    WEEKLY,
    MONTHLY,
    MORNING,
    NIGHT,
    APP,
    STORAGE,
    AUTOMATION
}

/**
 * A bounded preferred execution window. Time-trigger calculation remains in
 * TimeTriggerCalculator; this model only expresses the additional resource
 * gate that must pass before side effects may start.
 */
@Immutable
@Serializable
data class MaintenanceWindow(
    /** Local `HH:mm`; null leaves the lower bound to the time trigger. */
    val startTime: String? = null,
    /** Local `HH:mm`; null leaves the upper bound open. */
    val endTime: String? = null,
    /** ISO day-of-week values: Monday=1 through Sunday=7; empty allows all days. */
    val allowedDays: Set<Int> = emptySet(),
    val minimumBatteryPercent: Int? = null,
    val chargingRequired: Boolean = false,
    val unmeteredWifiRequired: Boolean = false,
    val screenOffRequired: Boolean = false,
    val deviceIdleRequired: Boolean = false,
    /** PowerManager thermal status threshold; null leaves thermal ungated. */
    val maximumThermalStatus: Int? = null,
    val minimumFreeStorageBytes: Long? = null
) {
    init {
        require(endTime == null || startTime != null) {
            "Maintenance end time requires a start time"
        }
        startTime?.let(::parseLocalTime)
        endTime?.let(::parseLocalTime)
        require(allowedDays.all { it in 1..7 }) {
            "Maintenance allowed days must use ISO values 1..7"
        }
        require(minimumBatteryPercent == null || minimumBatteryPercent in 0..100) {
            "Maintenance minimum battery must be in 0..100"
        }
        require(maximumThermalStatus == null || maximumThermalStatus >= 0) {
            "Maintenance thermal status must be non-negative"
        }
        require(minimumFreeStorageBytes == null || minimumFreeStorageBytes >= 0L) {
            "Maintenance free-storage requirement must be non-negative"
        }
    }

    private fun parseLocalTime(value: String) {
        require(runCatching { LocalTime.parse(value) }.isSuccess) {
            "Maintenance time must use HH:mm"
        }
    }
}

@Immutable
@Serializable
data class MaintenanceRetryPolicy(
    val maxAttempts: Int = 1,
    val initialDelayMs: Long = 15 * 60 * 1000L,
    val backoffMultiplier: Double = 2.0,
    val maxDelayMs: Long = 6 * 60 * 60 * 1000L
) {
    init {
        require(maxAttempts >= 1) { "Maintenance attempts must be at least one" }
        require(initialDelayMs >= 0L) { "Maintenance initial delay must be non-negative" }
        require(backoffMultiplier >= 1.0) { "Maintenance backoff multiplier must be at least one" }
        require(maxDelayMs >= initialDelayMs) { "Maintenance maximum delay must not precede initial delay" }
    }
}

@Serializable
enum class MaintenanceNotificationPolicy {
    NEVER,
    ERRORS_ONLY,
    IMPORTANT_EVENTS,
    EVERY_RUN,
    DAILY_SUMMARY,
    WEEKLY_SUMMARY
}

@Serializable
enum class MaintenanceRecoveryPolicy {
    DEFAULT,
    RETRY_TRANSIENT_ONLY,
    REQUIRE_MANUAL_REVIEW
}
