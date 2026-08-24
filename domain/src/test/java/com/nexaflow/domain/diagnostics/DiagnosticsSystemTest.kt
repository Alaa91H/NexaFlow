package com.nexaflow.domain.diagnostics

import org.junit.Assert.*
import org.junit.Test

class DiagnosticsSystemTest {

    @Test
    fun `emitted entry is retrievable`() {
        val collector = InMemoryDiagnosticsCollector()
        val entry = DiagnosticEntry.info("d1", DiagnosticCategory.CAPABILITY, "Test", "Detail")
        collector.emit(entry)
        val results = collector.entries()
        assertEquals(1, results.size)
        assertEquals("d1", results.first().id)
    }

    @Test
    fun `severity filter works`() {
        val collector = InMemoryDiagnosticsCollector()
        collector.emit(DiagnosticEntry.info("i1", DiagnosticCategory.SYSTEM, "Info", ""))
        collector.emit(DiagnosticEntry.warn("w1", DiagnosticCategory.BACKEND, "Warn", ""))
        collector.emit(DiagnosticEntry.error("e1", DiagnosticCategory.POLICY, DiagnosticRecoverability.PERMANENT, "Error", ""))
        val errorsOnly = collector.entries(minSeverity = DiagnosticSeverity.ERROR)
        assertEquals(1, errorsOnly.size)
        assertEquals("e1", errorsOnly.first().id)
    }

    @Test
    fun `category filter works`() {
        val collector = InMemoryDiagnosticsCollector()
        collector.emit(DiagnosticEntry.info("c1", DiagnosticCategory.PLUGIN, "Plugin", ""))
        collector.emit(DiagnosticEntry.info("c2", DiagnosticCategory.NETWORK, "Network", ""))
        val pluginOnly = collector.entries(category = DiagnosticCategory.PLUGIN)
        assertEquals(1, pluginOnly.size)
        assertEquals("c1", pluginOnly.first().id)
    }

    @Test
    fun `maxEntries evicts oldest`() {
        val collector = InMemoryDiagnosticsCollector(maxEntries = 3)
        repeat(5) { i ->
            collector.emit(DiagnosticEntry.info("id-$i", DiagnosticCategory.SYSTEM, "T$i", ""))
        }
        val all = collector.entries()
        assertEquals(3, all.size)
        // Oldest (id-0, id-1) should be evicted
        assertFalse(all.any { it.id == "id-0" })
        assertFalse(all.any { it.id == "id-1" })
        assertTrue(all.any { it.id == "id-4" })
    }

    @Test
    fun `clear removes all entries`() {
        val collector = InMemoryDiagnosticsCollector()
        collector.emit(DiagnosticEntry.info("x", DiagnosticCategory.AI, "X", ""))
        collector.clear()
        assertTrue(collector.entries().isEmpty())
    }

    @Test
    fun `isActionable returns true for USER_ACTION_REQUIRED`() {
        val entry = DiagnosticEntry.error(
            id = "perm-1",
            category = DiagnosticCategory.PERMISSION,
            recoverability = DiagnosticRecoverability.USER_ACTION_REQUIRED,
            title = "Permission required",
            detail = "WRITE_SECURE_SETTINGS not granted"
        )
        assertTrue(entry.isActionable)
        assertTrue(entry.isCritical)
    }

    @Test
    fun `DiagnosticEntry rejects blank id`() {
        assertThrows(IllegalArgumentException::class.java) {
            DiagnosticEntry(
                id = "  ",
                severity = DiagnosticSeverity.INFO,
                category = DiagnosticCategory.SYSTEM,
                recoverability = DiagnosticRecoverability.TRANSIENT,
                title = "T",
                technicalDetail = "D"
            )
        }
    }
}
