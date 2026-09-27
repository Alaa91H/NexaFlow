package com.nexaflow.core.agentrelay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRelayReplayGuardTest {

    @Test
    fun freshNonceIsAcceptedOnce() {
        val guard = AgentRelayReplayGuard(clockMillis = { 1_000L })

        assertEquals(AgentRelayReplay.Accepted, guard.check(1_000L, "n-1"))
        guard.record("n-1")
        assertEquals(AgentRelayReplay.Replay, guard.check(1_000L, "n-1"))
    }

    @Test
    fun staleAndFutureTimestampsAreRejected() {
        val guard = AgentRelayReplayGuard(
            clockSkewMs = 60_000L,
            clockMillis = { 1_000_000L }
        )

        assertEquals(AgentRelayReplay.Stale, guard.check(1_000_000L - 60_001L, "old"))
        assertEquals(AgentRelayReplay.Stale, guard.check(1_000_000L + 60_001L, "future"))
        assertEquals(AgentRelayReplay.Accepted, guard.check(1_000_000L - 60_000L, "edge"))
    }

    @Test
    fun invalidNonceFormatIsRejected() {
        val guard = AgentRelayReplayGuard(clockMillis = { 0L })

        assertEquals(AgentRelayReplay.InvalidNonce, guard.check(0L, ""))
        assertEquals(AgentRelayReplay.InvalidNonce, guard.check(0L, "has space"))
    }

    @Test
    fun oldestNoncesAreEvictedFirst() {
        val guard = AgentRelayReplayGuard(maxNonces = 16, clockMillis = { 0L })
        repeat(17) { index ->
            guard.check(0L, "n-$index")
            guard.record("n-$index")
        }

        assertEquals(16, guard.observedCount())
        // n-0 was evicted, so it reads as fresh again.
        assertEquals(AgentRelayReplay.Accepted, guard.check(0L, "n-0"))
        assertTrue(guard.observedCount() <= 17)
    }
}
