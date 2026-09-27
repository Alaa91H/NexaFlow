package com.nexaflow.core.agentrelay

/**
 * Bounded replay protection for inbound relay requests.
 *
 * A nonce is accepted at most once while it stays in the bounded window;
 * timestamps must fall inside the configured clock-skew window. Oldest
 * nonces are evicted first, so memory stays bounded even under adversarial
 * load. All mutations are synchronized; validation itself is side-effect
 * free except for recording an accepted nonce.
 */
class AgentRelayReplayGuard(
    private val clockSkewMs: Long = AgentRelayProtocol.DEFAULT_CLOCK_SKEW_MS,
    private val maxNonces: Int = AgentRelayProtocol.MAX_NONCES,
    private val clockMillis: () -> Long = System::currentTimeMillis
) {
    init {
        require(clockSkewMs > 0L) { "Clock skew window must be positive" }
        require(maxNonces >= 16) { "Nonce window must hold at least 16 entries" }
    }

    private val seen = LinkedHashMap<String, Long>(maxNonces + 16, 0.75f, true)

    @Synchronized
    fun check(timestamp: Long, nonce: String): AgentRelayReplay {
        val now = clockMillis()
        if (timestamp < now - clockSkewMs || timestamp > now + clockSkewMs) {
            return AgentRelayReplay.Stale
        }
        if (!AgentRelayProtocol.NONCE_PATTERN.matches(nonce)) {
            return AgentRelayReplay.InvalidNonce
        }
        if (seen.containsKey(nonce)) {
            return AgentRelayReplay.Replay
        }
        return AgentRelayReplay.Accepted
    }

    @Synchronized
    fun record(nonce: String) {
        seen[nonce] = clockMillis()
        while (seen.size > maxNonces) {
            val oldest = seen.keys.iterator().next()
            seen.remove(oldest)
        }
    }

    @Synchronized
    fun observedCount(): Int = seen.size
}

sealed interface AgentRelayReplay {
    data object Accepted : AgentRelayReplay
    data object Replay : AgentRelayReplay
    data object Stale : AgentRelayReplay
    data object InvalidNonce : AgentRelayReplay
}
