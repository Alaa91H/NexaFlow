package com.nexaflow.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CooldownDebounceTest {

    // ---- Cooldown ----

    @Test
    fun cooldown_firstEventAlwaysAllowed() {
        assertTrue(Cooldown.canFire(null, nowMillis = 1_000L, cooldownMillis = 5_000L))
    }

    @Test
    fun cooldown_blocksWhenTooSoon() {
        assertFalse(Cooldown.canFire(lastFiredAtMillis = 1_000L, nowMillis = 5_999L, cooldownMillis = 5_000L))
    }

    @Test
    fun cooldown_allowsAfterElapsed() {
        assertTrue(Cooldown.canFire(lastFiredAtMillis = 1_000L, nowMillis = 6_000L, cooldownMillis = 5_000L))
    }

    @Test
    fun cooldown_exactlyAtBoundaryAllowed() {
        assertTrue(Cooldown.canFire(lastFiredAtMillis = 1_000L, nowMillis = 6_000L, cooldownMillis = 5_000L))
    }

    // ---- Debounce ----

    @Test
    fun debounce_firesOnceAfterQuietWindow() {
        val debounce = Debounce(windowMillis = 1_000L)
        debounce.signal(nowMillis = 0L)
        assertFalse(debounce.poll(nowMillis = 500L))   // window not elapsed
        assertTrue(debounce.poll(nowMillis = 1_000L))  // quiet for 1s -> fires once
        assertFalse(debounce.poll(nowMillis = 2_000L)) // already consumed
    }

    @Test
    fun debounce_resetsOnNewEvent() {
        val debounce = Debounce(windowMillis = 1_000L)
        debounce.signal(nowMillis = 0L)
        debounce.signal(nowMillis = 900L)              // burst keeps resetting
        assertFalse(debounce.poll(nowMillis = 1_500L)) // still within new window
        assertTrue(debounce.poll(nowMillis = 1_900L))  // now quiet for 1s
    }

    @Test
    fun debounce_nothingPendingNeverFires() {
        val debounce = Debounce(windowMillis = 100L)
        assertFalse(debounce.poll(nowMillis = 1_000L))
    }

    @Test
    fun debounce_resetClearsPending() {
        val debounce = Debounce(windowMillis = 100L)
        debounce.signal(nowMillis = 0L)
        debounce.reset()
        assertFalse(debounce.poll(nowMillis = 500L))
    }
}
