package com.nexaflow.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionChangeGateTest {
    @Test
    fun initialAndRepeatedDeliveryDoesNotRebindButSimSwitchDoes() {
        val gate = SubscriptionChangeGate()
        assertFalse(gate.changed(1))
        repeat(10_000) { assertFalse(gate.changed(1)) }
        assertTrue(gate.changed(2))
        assertFalse(gate.changed(2))
        assertTrue(gate.changed(-1))
        gate.reset()
        assertFalse(gate.changed(2))
    }
}
