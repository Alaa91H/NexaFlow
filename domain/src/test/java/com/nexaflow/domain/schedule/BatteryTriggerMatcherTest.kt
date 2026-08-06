package com.nexaflow.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryTriggerMatcherTest {

    private val baseConfig = mapOf(
        "direction" to "ABOVE",
        "above" to "80",
        "chargerType" to "ANY"
    )

    @Test
    fun `plug type names map to the correct values`() {
        assertEquals("AC", BatteryTriggerMatcher.plugTypeName(BatteryTriggerMatcher.PLUGGED_AC))
        assertEquals("USB", BatteryTriggerMatcher.plugTypeName(BatteryTriggerMatcher.PLUGGED_USB))
        assertEquals("WIRELESS", BatteryTriggerMatcher.plugTypeName(BatteryTriggerMatcher.PLUGGED_WIRELESS))
        assertEquals("NONE", BatteryTriggerMatcher.plugTypeName(0))
    }

    @Test
    fun `combined plugged bitmask prefers wireless`() {
        assertEquals(
            "WIRELESS",
            BatteryTriggerMatcher.plugTypeName(
                BatteryTriggerMatcher.PLUGGED_AC or BatteryTriggerMatcher.PLUGGED_WIRELESS
            )
        )
    }

    @Test
    fun `any charger type fires whenever the level crosses`() {
        assertTrue(BatteryTriggerMatcher.isActive(baseConfig, 85, BatteryTriggerMatcher.PLUGGED_AC))
        assertTrue(BatteryTriggerMatcher.isActive(baseConfig, 85, BatteryTriggerMatcher.PLUGGED_WIRELESS))
        // Not plugged at all still counts with ANY (level-only trigger).
        assertTrue(BatteryTriggerMatcher.isActive(baseConfig, 85, 0))
    }

    @Test
    fun `level below threshold is never active`() {
        assertFalse(BatteryTriggerMatcher.isActive(baseConfig, 50, BatteryTriggerMatcher.PLUGGED_AC))
    }

    @Test
    fun `specific charger type requires a matching plug`() {
        val acConfig = baseConfig + ("chargerType" to "AC")
        assertTrue(BatteryTriggerMatcher.isActive(acConfig, 90, BatteryTriggerMatcher.PLUGGED_AC))
        assertFalse(BatteryTriggerMatcher.isActive(acConfig, 90, BatteryTriggerMatcher.PLUGGED_USB))
        assertFalse(BatteryTriggerMatcher.isActive(acConfig, 90, BatteryTriggerMatcher.PLUGGED_WIRELESS))
        assertFalse(BatteryTriggerMatcher.isActive(acConfig, 90, 0))
    }

    @Test
    fun `usb and wireless charger types are distinguished`() {
        val usbConfig = baseConfig + ("chargerType" to "USB")
        assertTrue(BatteryTriggerMatcher.isActive(usbConfig, 90, BatteryTriggerMatcher.PLUGGED_USB))
        assertFalse(BatteryTriggerMatcher.isActive(usbConfig, 90, BatteryTriggerMatcher.PLUGGED_AC))

        val wirelessConfig = baseConfig + ("chargerType" to "WIRELESS")
        assertTrue(BatteryTriggerMatcher.isActive(wirelessConfig, 90, BatteryTriggerMatcher.PLUGGED_WIRELESS))
        assertFalse(BatteryTriggerMatcher.isActive(wirelessConfig, 90, BatteryTriggerMatcher.PLUGGED_USB))
    }

    @Test
    fun `below direction respects the maximum threshold`() {
        val belowConfig = baseConfig + mapOf("direction" to "BELOW", "above" to "20")
        assertTrue(BatteryTriggerMatcher.isActive(belowConfig, 15, BatteryTriggerMatcher.PLUGGED_USB))
        assertFalse(BatteryTriggerMatcher.isActive(belowConfig, 30, BatteryTriggerMatcher.PLUGGED_USB))
    }

    @Test
    fun `below direction with charger filter needs both conditions`() {
        val config = baseConfig + mapOf(
            "direction" to "BELOW",
            "above" to "20",
            "chargerType" to "USB"
        )
        assertTrue(BatteryTriggerMatcher.isActive(config, 10, BatteryTriggerMatcher.PLUGGED_USB))
        assertFalse(BatteryTriggerMatcher.isActive(config, 10, BatteryTriggerMatcher.PLUGGED_WIRELESS))
        assertFalse(BatteryTriggerMatcher.isActive(config, 30, BatteryTriggerMatcher.PLUGGED_USB))
    }

    @Test
    fun `missing chargerType defaults to any`() {
        val config = mapOf("direction" to "ABOVE", "above" to "80")
        assertTrue(BatteryTriggerMatcher.isActive(config, 90, 0))
    }

    @Test
    fun `missing threshold defaults to 80`() {
        val config = mapOf("chargerType" to "AC")
        assertFalse(BatteryTriggerMatcher.isActive(config, 70, BatteryTriggerMatcher.PLUGGED_AC))
        assertTrue(BatteryTriggerMatcher.isActive(config, 90, BatteryTriggerMatcher.PLUGGED_AC))
    }
}
