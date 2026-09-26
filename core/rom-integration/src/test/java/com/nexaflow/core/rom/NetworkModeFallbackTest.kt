package com.nexaflow.core.rom

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkModeFallbackTest {

    @Test
    fun `fallback descriptors preserve every bounded modem slot`() {
        assertEquals(
            listOf(
                NetworkSubscriptionRef(subscriptionId = -1, simSlotIndex = 0),
                NetworkSubscriptionRef(subscriptionId = -2, simSlotIndex = 1),
                NetworkSubscriptionRef(subscriptionId = -3, simSlotIndex = 2),
            ),
            fallbackSubscriptionRefs(3)
        )
    }

    @Test
    fun `fallback descriptors always remain inside supported slot bounds`() {
        assertEquals(1, fallbackSubscriptionRefs(0).size)
        assertEquals(4, fallbackSubscriptionRefs(99).size)
        assertEquals(listOf(0, 1, 2, 3), fallbackSubscriptionRefs(99).map { it.simSlotIndex })
    }
}
