package com.nexaflow.core.execution

import java.util.concurrent.ConcurrentHashMap
import java.security.MessageDigest

/**
 * Short-lived, process-local replay guard for trigger sources that can provide
 * a stable identity for one concrete occurrence.
 *
 * It deliberately does nothing unless the source and concrete event identity
 * are both present. Inventing fingerprints from mutable payload/state would
 * risk collapsing two legitimate events. Stable identities also have a
 * hash-only durable key so the execution store can close process-death races.
 */
internal class TriggerOccurrenceDeduplicator(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private data class Key(
        val automationId: String,
        val sourceId: String,
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

        val sourceId = occurrence?.sourceId?.takeIf { it.isNotBlank() } ?: return true
        val eventId = occurrence.eventId?.takeIf { it.isNotBlank() } ?: return true
        val key = Key(automationId, sourceId, eventId)
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

    companion object {
        private const val DEFAULT_WINDOW_MS = 30_000L
        private const val DEFAULT_MAX_ENTRIES = 2_048

        /** Hashes only a caller-supplied stable source/event identity; payloads are never used. */
        internal fun durableOccurrenceKey(
            automationId: String,
            occurrence: TriggerOccurrence?
        ): String? {
            require(automationId.isNotBlank()) { "automationId must not be blank" }
            val sourceId = occurrence?.sourceId?.takeIf { it.isNotBlank() } ?: return null
            val eventId = occurrence.eventId?.takeIf { it.isNotBlank() } ?: return null
            val material = buildString {
                append(automationId.length).append(':').append(automationId)
                append(sourceId.length).append(':').append(sourceId)
                append(eventId.length).append(':').append(eventId)
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(material.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
        }
    }

}
