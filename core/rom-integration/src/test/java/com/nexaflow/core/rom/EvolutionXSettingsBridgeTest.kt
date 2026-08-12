package com.nexaflow.core.rom

import com.nexaflow.core.rom.EvolutionXSettingsBridge.Namespace
import com.nexaflow.core.rom.EvolutionXSettingsBridge.parseSettingsList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the `settings list` parsing of the deep Evolution X integration:
 * Evolver keys must be picked up from all three namespaces, non-ROM keys must
 * be dropped, and the display key must round-trip back to the shell command.
 */
class EvolutionXSettingsBridgeTest {

    @Test
    fun `evolution and lineage prefixed keys are parsed from system`() {
        val output = """
            evo_status_bar_show_battery_percent=1
            lineage_quick_settings_tiles=wifi,bt
            some_other_key=42
        """.trimIndent()
        val entries = parseSettingsList(Namespace.SYSTEM, output)
        assertEquals(2, entries.size)
        assertEquals("evo_status_bar_show_battery_percent", entries[0].key)
        assertEquals("1", entries[0].value)
        assertEquals("lineage_quick_settings_tiles", entries[1].key)
        assertEquals("wifi,bt", entries[1].value)
        assertTrue(entries.all { it.namespace == Namespace.SYSTEM })
    }

    @Test
    fun `secure and global namespaces are parsed and tagged`() {
        val secure = parseSettingsList(
            Namespace.SECURE,
            "sysui_tuner_enabled=1\nplain_key=x\n"
        )
        assertEquals(1, secure.size)
        assertEquals(Namespace.SECURE, secure[0].namespace)

        val global = parseSettingsList(
            Namespace.GLOBAL,
            "dex_legacy_allowlisting=0\n"
        )
        assertEquals(1, global.size)
        assertEquals(Namespace.GLOBAL, global[0].namespace)
    }

    @Test
    fun `blank output and blank lines produce no entries`() {
        assertTrue(parseSettingsList(Namespace.SYSTEM, "").isEmpty())
        assertTrue(parseSettingsList(Namespace.SECURE, "\n\n\n").isEmpty())
        assertTrue(parseSettingsList(Namespace.GLOBAL, "no_equals_sign").isEmpty())
    }

    @Test
    fun `display key round-trips to the shell namespace`() {
        val entry = parseSettingsList(
            Namespace.SECURE,
            "evolution_status_bar_clock_seconds=1\n"
        ).single()
        assertEquals("secure.key:evolution_status_bar_clock_seconds", entry.displayKey)
    }

    @Test
    fun `lineage and evo prefixes match case-insensitively`() {
        val entries = parseSettingsList(
            Namespace.SYSTEM,
            "EVO_show_icon=1\nLineage_Theme=dark\n"
        )
        assertEquals(2, entries.size)
    }
}
