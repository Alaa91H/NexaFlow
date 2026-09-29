package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.nexaflow.domain.canonical.CanonicalDiagnosticsModel.Severity

/**
 * T35 — Accessibility & RTL contract tests: screen-reader statements derive
 * deterministically from diagnostics rows, secrets never announce, overflow
 * counts are exact, and RTL rules mirror visuals without reordering.
 */
class CanonicalAccessibilityModelTest {

    private fun row(
        code: String = "diag.cutover.ready",
        severity: Severity = Severity.INFO,
        summary: String = "Open Wi-Fi settings",
        details: List<Pair<String, String>> = emptyList(),
    ) = CanonicalDiagnosticsModel.DiagnosticsRow(code, severity, summary, details)

    // ------------------------------------------------------------------
    // Statements
    // ------------------------------------------------------------------

    @Test
    fun infoRowAnnouncesPlainSummaryWithStableId() {
        val statement = CanonicalAccessibilityModel.statementFor(row())

        assertEquals("diag.cutover.ready.statement", statement.id)
        assertEquals("Open Wi-Fi settings", statement.text)
        assertFalse(statement.actionable)
    }

    @Test
    fun warningAndErrorRowsAnnounceTheirSeverity() {
        assertEquals(
            "Warning: hot device",
            CanonicalAccessibilityModel.statementFor(
                row(severity = Severity.WARNING, summary = "hot device"),
            ).text,
        )
        assertEquals(
            "Error: migration failed",
            CanonicalAccessibilityModel.statementFor(
                row(severity = Severity.ERROR, summary = "migration failed"),
            ).text,
        )
    }

    @Test
    fun detailPairsAnnounceKeyFirst() {
        val statement = CanonicalAccessibilityModel.statementFor(
            row(details = listOf("commands" to "3", "preservedKeys" to "1")),
        )

        assertEquals(
            "Open Wi-Fi settings, commands 3, preservedKeys 1",
            statement.text,
        )
    }

    // ------------------------------------------------------------------
    // Secret safety
    // ------------------------------------------------------------------

    @Test
    fun secretLabelInDetailsNeverExpands() {
        val statement = CanonicalAccessibilityModel.statementFor(
            row(
                code = "diag.value.secret_redacted",
                summary = "credential attached",
                details = listOf("authToken" to "<secret> reference legacy.http_auth_token"),
            ),
        )

        assertEquals("credential attached, authToken <secret>", statement.text)
        assertFalse(statement.text.contains("legacy.http_auth_token"))
    }

    // ------------------------------------------------------------------
    // Sections + overflow
    // ------------------------------------------------------------------

    @Test
    fun sectionRendersHeadingRowsAndExactOverflow() {
        val rows = (1..52).map { index -> row(summary = "row $index") }
        val section = CanonicalDiagnosticsModel.DiagnosticsSection(
            title = "Cutover",
            rows = rows.take(CanonicalDiagnosticsModel.MAX_ROWS_PER_SECTION),
            overflowedCount = 2,
        )

        val statements = CanonicalAccessibilityModel.statementsFor(section)

        assertEquals("section.Cutover.heading", statements.first().id)
        assertEquals("Cutover", statements.first().text)
        val overflow = statements.last()
        assertEquals("section.Cutover.overflow", overflow.id)
        assertEquals("2 more entries not shown", overflow.text)
    }

    @Test
    fun snapshotFlattensSectionsInOrder() {
        val snapshot = CanonicalDiagnosticsModel.DiagnosticsSnapshot(
            sections = listOf(
                CanonicalDiagnosticsModel.DiagnosticsSection(
                    title = "Cutover",
                    rows = listOf(row()),
                    overflowedCount = 0,
                ),
                CanonicalDiagnosticsModel.DiagnosticsSection(
                    title = "Optimizer",
                    rows = listOf(
                        row(code = "diag.optimizer.removed", summary = "optimizer removed 3 node(s)"),
                    ),
                    overflowedCount = 0,
                ),
            ),
        )

        val statements = CanonicalAccessibilityModel.statementsFor(snapshot)

        assertTrue(statements.any { it.text == "Cutover" })
        assertTrue(statements.any { it.text == "Optimizer" })
        // Logical order preserved: Cutover heading precedes Optimizer heading.
        assertTrue(
            statements.indexOfFirst { it.text == "Cutover" } <
                statements.indexOfFirst { it.text == "Optimizer" },
        )
    }

    // ------------------------------------------------------------------
    // RTL policy
    // ------------------------------------------------------------------

    @Test
    fun rtlPolicyPinsMirrorWithoutReorder() {
        assertTrue(CanonicalAccessibilityModel.RtlPolicy.MIRROR_WITHOUT_REORDER)
        assertEquals("trailing", CanonicalAccessibilityModel.RtlPolicy.OVERFLOW_BADGE_EDGE)
        assertEquals("key_first_logical", CanonicalAccessibilityModel.RtlPolicy.DETAIL_ORDER)
    }

    // ------------------------------------------------------------------
    // Node announcements
    // ------------------------------------------------------------------

    @Test
    fun nodeAnnouncementIncludesArgumentValues() {
        val statement = CanonicalAccessibilityModel.nodeAnnouncement(
            nodeTitle = "Open settings",
            arguments = CanonicalArguments(
                listOf(
                    CanonicalArgument(
                        CanonicalFieldId("page"),
                        EnumTokenValue("core.system.settings", "WIFI"),
                    ),
                ),
            ),
        )

        assertEquals("node.Open settings.announcement", statement.id)
        assertTrue(statement.text.startsWith("Open settings"))
        assertTrue(statement.text.contains("page"))
    }

    @Test
    fun nodeAnnouncementRedactsSecretArguments() {
        val statement = CanonicalAccessibilityModel.nodeAnnouncement(
            nodeTitle = "HTTP request",
            arguments = CanonicalArguments(
                listOf(
                    CanonicalArgument(
                        CanonicalFieldId("authToken"),
                        SecretReferenceValue("legacy.http_auth_token"),
                    ),
                ),
            ),
        )

        assertTrue(statement.text.contains(CanonicalDiagnosticsModel.SECRET_LABEL))
        assertFalse(statement.text.contains("legacy.http_auth_token"))
    }
}
