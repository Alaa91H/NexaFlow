package com.nexaflow.core.execution

import com.nexaflow.core.datastore.ActiveExecutionStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Bounds repeated diagnostics when durable execution admission is intentionally
 * deferred. State triggers can be evaluated frequently while recovery work is
 * awaiting review; recording every evaluation would obscure the real issue and
 * exhaust the history window.
 *
 * The throttle is process-local on purpose. A restarted process records one
 * fresh diagnostic so the user still has evidence of the unresolved queue.
 */
class CheckpointAdmissionReportThrottle(
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS
) {
    private val lastReportedAt = ConcurrentHashMap<String, Long>()

    fun shouldReport(
        automationId: String,
        admission: ActiveExecutionStore.CheckpointAdmission,
        now: Long
    ): Boolean {
        val key = "$automationId:${admission.name}"
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
