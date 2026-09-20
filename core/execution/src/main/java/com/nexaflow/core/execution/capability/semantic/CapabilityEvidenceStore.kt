package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId

/**
 * One recorded execution attempt for a (operation, strategy, device) triple.
 * Storage is deliberately minimal and non-sensitive: no raw commands, no
 * parameter values, no user identifiers — only outcomes, timings and counts
 * that a router may safely weigh.
 */
data class CapabilityEvidence(
    val operation: SemanticOperationId,
    val strategy: StrategyId,
    /** Exact-device scope of this record. */
    val deviceKey: String,
    /** Verified-true successes are the strongest positive evidence. */
    val verifiedSuccesses: Long = 0L,
    val unverifiedSuccesses: Long = 0L,
    val failures: Long = 0L,
    val lastSuccessAtMs: Long? = null,
    val lastVerifiedSuccessAtMs: Long? = null,
    val lastFailureAtMs: Long? = null,
    val lastLatencyMs: Long? = null
) {
    /** Weighted score; verified evidence counts most, recent success counts more. */
    fun score(nowMs: Long, halfLifeMs: Long = EVIDENCE_HALF_LIFE_MS): Long {
        val positive = verifiedSuccesses * 3L + unverifiedSuccesses
        if (positive == 0L && failures == 0L) return 0L
        val ageMs = lastSuccessAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: Long.MAX_VALUE
        // Exponential decay by half-life; saturate to avoid overflow on old records.
        val decayFactor = if (ageMs == Long.MAX_VALUE) 0.0
        else Math.pow(0.5, (ageMs.toDouble() / halfLifeMs.toDouble()).coerceAtMost(64.0))
        val negative = failures * 2L
        return (positive * decayFactor).toLong() - negative
    }

    companion object {
        const val EVIDENCE_HALF_LIFE_MS: Long = 14L * 24 * 60 * 60 * 1000
    }
}

/**
 * Append-and-read evidence store for the router. The default implementation is
 * memory-backed; a production substitute can persist it without changing the
 * contract. Records are keyed by the exact device so a factory reset or ROM
 * change starts with no stale trust.
 */
class CapabilityEvidenceStore(
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val byKey = LinkedHashMap<String, CapabilityEvidence>()

    @Synchronized
    fun recordSuccess(
        operation: SemanticOperationId,
        strategy: StrategyId,
        deviceKey: String,
        verified: Boolean,
        latencyMs: Long?
    ) {
        val key = key(operation, strategy, deviceKey)
        val current = byKey[key] ?: CapabilityEvidence(operation, strategy, deviceKey)
        val now = nowMs()
        byKey[key] = current.copy(
            verifiedSuccesses = current.verifiedSuccesses + if (verified) 1L else 0L,
            unverifiedSuccesses = current.unverifiedSuccesses + if (verified) 0L else 1L,
            lastSuccessAtMs = now,
            lastVerifiedSuccessAtMs = if (verified) now else current.lastVerifiedSuccessAtMs,
            lastLatencyMs = latencyMs ?: current.lastLatencyMs
        )
    }

    @Synchronized
    fun recordFailure(operation: SemanticOperationId, strategy: StrategyId, deviceKey: String) {
        val key = key(operation, strategy, deviceKey)
        val current = byKey[key] ?: CapabilityEvidence(operation, strategy, deviceKey)
        byKey[key] = current.copy(failures = current.failures + 1L, lastFailureAtMs = nowMs())
    }

    @Synchronized
    fun evidenceFor(operation: SemanticOperationId, strategy: StrategyId, deviceKey: String): CapabilityEvidence =
        byKey[key(operation, strategy, deviceKey)]
            ?: CapabilityEvidence(operation, strategy, deviceKey)

    /**
     * Targeted invalidation: drops evidence for strategies affected by an
     * environment event (e.g. Shizuku binder death invalidates only Shizuku
     * strategies), optionally scoped to one device fingerprint.
     */
    @Synchronized
    fun invalidate(predicate: (CapabilityEvidence) -> Boolean) {
        byKey.keys.removeAll { k -> predicate(byKey.getValue(k)) }
    }

    @Synchronized
    fun clear() = byKey.clear()

    private fun key(
        operation: SemanticOperationId,
        strategy: StrategyId,
        deviceKey: String
    ): String = "$deviceKey|${operation.name}|${strategy.name}"
}
