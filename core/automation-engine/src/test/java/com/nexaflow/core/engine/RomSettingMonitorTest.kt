package com.nexaflow.core.engine

import com.nexaflow.core.rom.EvolutionXSettingsBridge.Namespace
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
            mapOf("namespace" to "SYSTEM", "key" to "evo_x", "operator" to "EQUALS", "value" to "1")
        )
        assertTrue(romSettingMatches(trigger, "1"))
        assertFalse(romSettingMatches(trigger, "0"))
        assertFalse(romSettingMatches(trigger, null))
    }

    @Test
    fun `NOT_EQUALS matches when actual differs`() {
        val trigger = Trigger(
            TriggerType.ROM_SETTING,
            mapOf("namespace" to "SYSTEM", "key" to "evo_x", "operator" to "NOT_EQUALS", "value" to "0")
        )
        assertTrue(romSettingMatches(trigger, "1"))
        assertTrue(romSettingMatches(trigger, null))
        assertFalse(romSettingMatches(trigger, "0"))
    }

    @Test
    fun `missing value never matches`() {
        val trigger = Trigger(
            TriggerType.ROM_SETTING,
            mapOf("namespace" to "SYSTEM", "key" to "evo_x", "operator" to "EQUALS")
        )
        assertFalse(romSettingMatches(trigger, "1"))
    }

    @Test
    fun `default operator is EQUALS`() {
        val trigger = Trigger(
            TriggerType.ROM_SETTING,
            mapOf("namespace" to "SYSTEM", "key" to "evo_x", "value" to "1")
        )
        assertTrue(romSettingMatches(trigger, "1"))
        assertFalse(romSettingMatches(trigger, "0"))
    }
}
