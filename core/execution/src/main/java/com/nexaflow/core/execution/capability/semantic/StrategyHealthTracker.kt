package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId

/**
 * Health lifecycle of one strategy, mirroring the requirement taxonomy:
 * UNKNOWN → HEALTHY / DEGRADED / FAILED / TEMPORARILY_UNAVAILABLE / DISABLED.
 * The tracker never retries aggressively a strategy proven to fail repeatedly:
 * consecutive failures arm a cooldown that the router honors.
 */
class StrategyHealthTracker(
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    enum class State { UNKNOWN, HEALTHY, DEGRADED, FAILED, TEMPORARILY_UNAVAILABLE, DISABLED }

    data class Health(
        val state: State,
        val consecutiveFailures: Int,
        /** Milliseconds since epoch until which the strategy is bypassed. */
        val cooldownUntilMs: Long?,
        val lastSuccessAtMs: Long?,
        val lastFailureAtMs: Long?
    )

    private val states = LinkedHashMap<String, Health>()

    @Synchronized
    fun healthFor(strategy: StrategyId, deviceKey: String): Health =
        states[key(strategy, deviceKey)]
            ?: Health(State.UNKNOWN, 0, null, null, null)

    @Synchronized
    fun recordSuccess(strategy: StrategyId, deviceKey: String, atMs: Long = nowMs()) {
        states[key(strategy, deviceKey)] = Health(
            state = State.HEALTHY,
            consecutiveFailures = 0,
            cooldownUntilMs = null,
            lastSuccessAtMs = atMs,
            lastFailureAtMs = null
        )
    }

    @Synchronized
    fun recordFailure(strategy: StrategyId, deviceKey: String, atMs: Long = nowMs()) {
        val key = key(strategy, deviceKey)
        val current = states[key] ?: Health(State.UNKNOWN, 0, null, null, null)
        val consecutive = current.consecutiveFailures + 1
        val (state, cooldown) = when {
            consecutive >= DISABLE_THRESHOLD -> State.TEMPORARILY_UNAVAILABLE to
                (atMs + cooldownFor(consecutive))
            consecutive >= DEGRADED_THRESHOLD -> State.DEGRADED to
                (atMs + cooldownFor(consecutive))
            else -> State.FAILED to null
        }
        states[key] = current.copy(
            state = state,
            consecutiveFailures = consecutive,
            cooldownUntilMs = cooldown,
            lastFailureAtMs = atMs
        )
    }

    @Synchronized
    fun disable(strategy: StrategyId, deviceKey: String) {
        states[key(strategy, deviceKey)] =
            Health(State.DISABLED, 0, null, null, null)
    }

    @Synchronized
    fun reset(strategy: StrategyId, deviceKey: String) {
        states.remove(key(strategy, deviceKey))
    }

    /** True when the strategy is inside a cooldown or disabled window. */
    fun isCoolingDown(strategy: StrategyId, deviceKey: String, nowMs: Long = this.nowMs()): Boolean {
        val health = healthFor(strategy, deviceKey)
        if (health.state == State.DISABLED) return true
        return health.cooldownUntilMs?.let { nowMs < it } ?: false
    }

    private fun cooldownFor(consecutiveFailures: Int): Long {
        // Bounded exponential backoff: 30s, 1m, 2m, … capped at 15 minutes.
        val shift = (consecutiveFailures - DEGRADED_THRESHOLD).coerceIn(0, 5)
        return (30_000L shl shift).coerceAtMost(15L * 60 * 1000)
    }

    private fun key(strategy: StrategyId, deviceKey: String) = "$deviceKey|${strategy.name}"

    private companion object {
        const val DEGRADED_THRESHOLD = 2
        const val DISABLE_THRESHOLD = 4
    }
}
