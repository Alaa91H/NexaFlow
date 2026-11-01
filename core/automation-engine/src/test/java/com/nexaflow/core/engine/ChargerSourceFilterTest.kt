package com.nexaflow.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The charger trigger can be limited to one kind of source. "Any" has to stay
 * permissive so tasks saved before the filter existed keep firing.
 */
class ChargerSourceFilterTest {

    @Test
    fun `missing or any filter accepts every source`() {
        assertTrue(ChargerSourceFilter.matches(null, "AC"))
        assertTrue(ChargerSourceFilter.matches("", "USB"))
        assertTrue(ChargerSourceFilter.matches("ANY", "WIRELESS"))
        assertTrue(ChargerSourceFilter.matches("any", "AC"))
    }

    @Test
    fun `a specific filter accepts only that source`() {
        assertTrue(ChargerSourceFilter.matches("USB", "USB"))
        assertTrue(ChargerSourceFilter.matches("usb", "USB"))
        assertFalse(ChargerSourceFilter.matches("USB", "AC"))
        assertFalse(ChargerSourceFilter.matches("AC", "WIRELESS"))
        assertFalse(ChargerSourceFilter.matches("WIRELESS", "USB"))
    }
}
