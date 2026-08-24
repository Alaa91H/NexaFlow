package com.nexaflow.domain.events

import kotlinx.serialization.Serializable

/**
 * Per-subscription throttle and debounce policy.
 *
 * - [throttleMs]  – minimum gap between two delivered events of the same type.
 *                   Events arriving faster than this are silently dropped.
 * - [debounceMs]  – quiet-period required after the *last* event before the
 *                   subscriber is called. Useful for noisy sensors or typing.
 *                   When both are set, throttle is evaluated first.
 *
 * Both values default to 0, which disables the respective mechanism.
 */
@Serializable
data class EventDeliveryPolicy(
    val throttleMs: Long = 0L,
    val debounceMs: Long = 0L,
    val maxQueueDepth: Int = 64
) {
    init {
        require(throttleMs >= 0) { "throttleMs must be non-negative" }
        require(debounceMs >= 0) { "debounceMs must be non-negative" }
        require(maxQueueDepth in 1..512) { "maxQueueDepth must be in 1..512" }
    }
}

/**
 * Stateful per-subscription throttle/debounce tracker.
 * Thread-safe only through the caller's mutex.
 */
class EventDeliveryTracker(private val policy: EventDeliveryPolicy) {
    private var lastDeliveredAt: Long = 0L
    private var lastSeenAt: Long = 0L

    /**
     * Returns true if the event at [nowMs] should be forwarded to the subscriber.
     */
    fun shouldDeliver(nowMs: Long): Boolean {
        lastSeenAt = nowMs

        // Throttle check: has enough time passed since last delivery?
        if (policy.throttleMs > 0 && (nowMs - lastDeliveredAt) < policy.throttleMs) {
            return false
        }

        // Debounce check: has the stream been quiet for long enough?
        if (policy.debounceMs > 0 && (nowMs - lastSeenAt) < policy.debounceMs) {
            return false
        }

        lastDeliveredAt = nowMs
        return true
    }
}
