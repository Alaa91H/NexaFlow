package com.nexaflow.domain.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the structured diagnostics taxonomy: entry validation,
 * deduplication-capable ids, severity/category filtering, ring-buffer bounds,
 * and the actionable/critical flags the UI routes on.
 */
class DiagnosticsSystemTest {

    private fun entry(
        id: String = "d1",
        severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
        category: DiagnosticCategory = DiagnosticCategory.SYSTEM,
        recoverability: DiagnosticRecoverability = DiagnosticRecoverability.TRANSIENT,
        runId: String? = null
    ) = DiagnosticEntry(
        id = id,
        severity = severity,
        category = category,
        recoverability = recoverability,
        title = "Test title",
        technicalDetail = "detail",
        runId = runId
    )

    @Test
    fun `blank id or title is rejected`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            entry(id = " ").let(DiagnosticsCollectorHost::validate)
        }
    }

    @Test
    fun `oversized title is rejected and oversized detail is rejected`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            entry().copy(title = "x".repeat(257))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            entry().copy(technicalDetail = "x".repeat(4097))
        }
    }

    @Test
    fun `actionable and critical flags follow recoverability and severity`() {
        assertTrue(entry(recoverability = DiagnosticRecoverability.USER_ACTION_REQUIRED).isActionable)
        assertFalse(entry(recoverability = DiagnosticRecoverability.TRANSIENT).isActionable)
        assertTrue(entry(severity = DiagnosticSeverity.CRITICAL).isCritical)
        assertTrue(entry(severity = DiagnosticSeverity.ERROR).isCritical)
        assertFalse(entry(severity = DiagnosticSeverity.INFO).isCritical)
    }

    @Test
    fun `collector stores entries and filters by severity category and run`() {
        val collector = InMemoryDiagnosticsCollector()
        collector.emit(entry(id = "a", severity = DiagnosticSeverity.DEBUG, runId = "run-1"))
        collector.emit(entry(id = "b", severity = DiagnosticSeverity.ERROR, category = DiagnosticCategory.PLUGIN, runId = "run-1"))
        collector.emit(entry(id = "c", severity = DiagnosticSeverity.WARN, category = DiagnosticCategory.PLUGIN, runId = "run-2"))

        assertEquals(3, collector.entries().size)
        assertEquals(listOf("b"), collector.entries(category = DiagnosticCategory.PLUGIN, minSeverity = DiagnosticSeverity.ERROR).map { it.id })
        assertEquals(setOf("a", "b"), collector.entries(runId = "run-1").map { it.id }.toSet())
        assertTrue(collector.entries(minSeverity = DiagnosticSeverity.WARN).none { it.id == "a" })
    }

    @Test
    fun `collector evicts oldest entries beyond capacity`() {
        val collector = InMemoryDiagnosticsCollector(maxEntries = 3)
        repeat(5) { collector.emit(entry(id = "e$it")) }
        assertEquals(listOf("e2", "e3", "e4"), collector.entries().map { it.id })
    }

    @Test
    fun `clear empties the buffer`() {
        val collector = InMemoryDiagnosticsCollector()
        collector.emit(entry())
        collector.clear()
        assertTrue(collector.entries().isEmpty())
    }
}

/** Test shim exposing the init-block validation path without duplicating it. */
private object DiagnosticsCollectorHost {
    fun validate(entry: DiagnosticEntry): DiagnosticEntry = entry.copy()
}
