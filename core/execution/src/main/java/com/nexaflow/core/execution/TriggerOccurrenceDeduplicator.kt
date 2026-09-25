package com.nexaflow.core.execution

import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived, process-local replay guard for trigger sources that can provide
 * a stable identity for one concrete occurrence.
 *
 * It deliberately does nothing when [TriggerOccurrence.eventId] is absent:
 * inventing fingerprints from mutable payload/state would risk collapsing two
 * legitimate events. Durable lifecycle/scheduler stores remain authoritative
 * across process death; this class only closes same-process redelivery races.
 */
internal class TriggerOccurrenceDeduplicator(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private data class Key(
        val automationId: String,
        val sourceId: String?,
        val eventId: String,
    )

    private val ledger = ConcurrentHashMap<Key, Long>()

    init {
        require(windowMs >= 0L) { "windowMs must be non-negative" }
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    fun tryAdmit(
        automationId: String,
        occurrence: TriggerOccurrence?,
        now: Long,
    ): Boolean {
        require(automationId.isNotBlank()) { "automationId must not be blank" }
        require(now >= 0L) { "now must be non-negative" }

        val eventId = occurrence?.eventId?.takeIf { it.isNotBlank() } ?: return true
        val key = Key(automationId, occurrence.sourceId, eventId)
        var admitted = false

        ledger.compute(key) { _, previous ->
            if (
                previous == null ||
                now < previous ||
                now - previous >= windowMs
            ) {
                admitted = true
                now
            } else {
                previous
            }
        }

        if (ledger.size > maxEntries) prune(now)
        return admitted
    }

    @Synchronized
    private fun prune(now: Long) {
        ledger.entries.toList().forEach { entry ->
            val acceptedAt = entry.value
            if (now < acceptedAt || now - acceptedAt >= windowMs) {
                ledger.remove(entry.key, acceptedAt)
            }
        }
        while (ledger.size > maxEntries) {
            val oldest = ledger.entries.minByOrNull { it.value } ?: break
            ledger.remove(oldest.key, oldest.value)
        }
    }

    internal fun sizeForTest(): Int = ledger.size

    private companion object {
        const val DEFAULT_WINDOW_MS = 30_000L
        const val DEFAULT_MAX_ENTRIES = 2_048
    }
}
