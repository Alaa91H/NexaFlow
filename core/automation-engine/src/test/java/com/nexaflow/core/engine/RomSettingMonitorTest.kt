package com.nexaflow.core.engine

import com.nexaflow.core.rom.CustomSettingsBridge.Namespace
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomSettingMonitorTest {

    @Test
    fun `namespace defaults to SYSTEM`() {
        assertEquals(Namespace.SYSTEM, romSettingNamespaceOf(emptyMap()))
        assertEquals(Namespace.SYSTEM, romSettingNamespaceOf(mapOf("namespace" to "garbage")))
        assertEquals(Namespace.SECURE, romSettingNamespaceOf(mapOf("namespace" to "SECURE")))
        assertEquals(Namespace.GLOBAL, romSettingNamespaceOf(mapOf("namespace" to "GLOBAL")))
    }

    @Test
    fun `target is null when value is blank`() {
        assertNull(romSettingTargetOf(emptyMap()))
        assertNull(romSettingTargetOf(mapOf("value" to "  ")))
        assertEquals("1", romSettingTargetOf(mapOf("value" to "1")))
    }

    @Test
    fun `EQUALS matches when actual equals target`() {
        val trigger = Trigger(
            TriggerType.ROM_SETTING,
            mapOf("namespace" to "SYSTEM", "key" to "rom_x", "operator" to "EQUALS", "value" to "1")
        )
        assertTrue(romSettingMatches(trigger, "1"))
        assertFalse(romSettingMatches(trigger, "0"))
        assertFalse(romSettingMatches(trigger, null))
    }

    @Test
    fun `NOT_EQUALS matches when actual differs`() {
        val trigger = Trigger(
            TriggerType.ROM_SETTING,
            mapOf("namespace" to "SYSTEM", "key" to "rom_x", "operator" to "NOT_EQUALS", "value" to "0")
        )
        assertTrue(romSettingMatches(trigger, "1"))
        assertFalse(romSettingMatches(trigger, null))
        assertFalse(romSettingMatches(trigger, "0"))
    }

    @Test
    fun `aggregate result is unknown when provider cannot read a valid trigger`() {
        val trigger = Trigger(
            TriggerType.ROM_SETTING,
            mapOf("namespace" to "SYSTEM", "key" to "rom_x", "operator" to "NOT_EQUALS", "value" to "0")
        )

        assertEquals(
            RomSettingReadState.UNKNOWN,
            evaluateRomSettingTriggers(listOf(trigger)) { _, _ -> null }
        )
    }

    @Test
    fun `known match dominates another unknown ROM setting read`() {
        val first = Trigger(
            TriggerType.ROM_SETTING,
            mapOf("namespace" to "SYSTEM", "key" to "rom_a", "operator" to "EQUALS", "value" to "1")
        )
        val second = Trigger(
            TriggerType.ROM_SETTING,
            mapOf("namespace" to "SECURE", "key" to "rom_b", "operator" to "EQUALS", "value" to "1")
        )

        assertEquals(
            RomSettingReadState.MATCHED,
            evaluateRomSettingTriggers(listOf(first, second)) { _, key ->
                when (key) {
                    "rom_a" -> null
                    "rom_b" -> "1"
                    else -> null
                }
            }
        )
    }

    @Test
    fun `all known non matching ROM settings produce not matched`() {
        val trigger = Trigger(
            TriggerType.ROM_SETTING,
            mapOf("namespace" to "GLOBAL", "key" to "rom_x", "operator" to "EQUALS", "value" to "1")
        )

        assertEquals(
            RomSettingReadState.NOT_MATCHED,
            evaluateRomSettingTriggers(listOf(trigger)) { _, _ -> "0" }
        )
    }

    @Test
    fun `missing value never matches`() {
        val trigger = Trigger(
            TriggerType.ROM_SETTING,
            mapOf("namespace" to "SYSTEM", "key" to "rom_x", "operator" to "EQUALS")
        )
        assertFalse(romSettingMatches(trigger, "1"))
    }

    @Test
    fun `default operator is EQUALS`() {
        val trigger = Trigger(
            TriggerType.ROM_SETTING,
            mapOf("namespace" to "SYSTEM", "key" to "rom_x", "value" to "1")
        )
        assertTrue(romSettingMatches(trigger, "1"))
        assertFalse(romSettingMatches(trigger, "0"))
    }
}
