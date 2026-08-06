package com.nexaflow.domain.schedule

/**
 * Minimum-gap gate: an event may fire only when at least [cooldownMillis] has
 * elapsed since the last fire. Used to rate-limit noisy triggers (battery level
 * jitter, connectivity flaps) so an automation never re-runs too often.
 */
object Cooldown {

    /** True when a new fire is allowed at [nowMillis] given the [lastFiredAtMillis]. */
    fun canFire(lastFiredAtMillis: Long?, nowMillis: Long, cooldownMillis: Long): Boolean {
        val last = lastFiredAtMillis ?: return true
        return nowMillis - last >= cooldownMillis
    }
}

/**
 * Trailing-edge debounce: [signal] records an event (resetting the timer); once
 * no new event arrives for [windowMillis], [poll] reports the pending event
 * exactly once. Used to collapse bursts of events (e.g. repeated SMS texts,
 * notification spam) into a single execution.
 */
class Debounce(private val windowMillis: Long) {

    private var pendingAt: Long? = null

    init {
        require(windowMillis >= 0) { "windowMillis must be non-negative" }
    }

    /** Records an event at [nowMillis], restarting the quiet window. */
    fun signal(nowMillis: Long) {
        pendingAt = nowMillis
    }

    /**
     * Returns true exactly once when the quiet window elapsed with no new
     * events, consuming the pending event. False when nothing is pending or the
     * window has not elapsed yet.
     */
    fun poll(nowMillis: Long): Boolean {
        val pending = pendingAt ?: return false
        if (nowMillis - pending >= windowMillis) {
            pendingAt = null
            return true
        }
        return false
    }

    /** Clears any pending event. */
    fun reset() {
        pendingAt = null
    }
}
