package com.nexaflow.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionChangeGateTest {

    @Test
    fun `initial callback only seeds the observed subscription`() {
        val gate = SubscriptionChangeGate()

        assertFalse(gate.changed(4))
        repeat(10_000) { assertFalse(gate.changed(4)) }
    }

    @Test
    fun `only a different subscription requests callback rebinding`() {
        val gate = SubscriptionChangeGate()
        gate.changed(4)

        assertTrue(gate.changed(7))
        repeat(10_000) { assertFalse(gate.changed(7)) }
        assertTrue(gate.changed(4))
    }

    @Test
    fun `reset treats the next callback as the initial observation`() {
        val gate = SubscriptionChangeGate()
        gate.changed(4)
        gate.changed(7)

        gate.reset()

        assertFalse(gate.changed(7))
        assertFalse(gate.changed(7))
        assertTrue(gate.changed(4))
    }
}
