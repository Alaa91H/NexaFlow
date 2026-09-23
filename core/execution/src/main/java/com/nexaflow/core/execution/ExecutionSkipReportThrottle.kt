package com.nexaflow.core.execution

import java.util.concurrent.ConcurrentHashMap

/**
 * Coalesces repeated intentional skips from high-frequency monitors.
 *
 * A skipped evaluation is still returned to its caller immediately, but only
 * the first identical skip in the cooldown window should be persisted to
 * history/diagnostics. This keeps task health useful without losing the first
 * piece of evidence explaining why a routine did not run.
 *
 * State is process-local by design. After process restart the first matching
 * skip is reported again, which gives diagnostics a fresh breadcrumb.
 */
class ExecutionSkipReportThrottle(
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS
) {
    private val lastReportedAt = ConcurrentHashMap<String, Long>()

    fun shouldReport(
        automationId: String,
        reasonKey: String,
        now: Long
    ): Boolean {
        val key = "$automationId:$reasonKey"
        var report = false
        lastReportedAt.compute(key) { _, previous ->
            if (previous == null || now < previous || now - previous >= cooldownMillis) {
                report = true
                now
            } else {
                previous
            }
        }
        return report
    }

    internal companion object {
        const val DEFAULT_COOLDOWN_MILLIS = 5 * 60 * 1000L
    }
}
