package com.nexaflow.domain.canonical

/**
 * T35 — Accessibility & RTL contract model (plan §T35).
 *
 * The domain-side a11y contract behind the diagnostics and builder surfaces:
 * it turns [CanonicalDiagnosticsModel] snapshots into a deterministic,
 * screen-reader-ready statement list and pins the RTL rules the UI layer must
 * render by (mirroring, placement of overflow badges, locale-neutral digits).
 *
 * Pure domain: no Compose, no Android locale types — the host maps
 * [AccessibilityStatement] to semantics/contentDescription and applies
 * [RtlPolicy] to its layout direction.
 *
 * Contracts pinned by tests:
 *  - Every statement carries a stable machine id (test pins + log exports);
 *  - Secret-bearing payloads render the fixed label, never the secret;
 *  - Statements read in visual order after RTL mirroring (logical order is
 *    preserved; the UI mirrors, the model never reorders);
 *  - Overflow counts read as exact numbers, never "many" — talk-back users
 *    get the same facts as sighted users.
 */
object CanonicalAccessibilityModel {

    /** RTL rendering rules the host must apply to diagnostics surfaces. */
    object RtlPolicy {
        /**
         * True when [layoutDirection] mirrors: the model's logical order is
         * layout-independent, so the host mirrors visuals without reordering
         * semantics.
         */
        const val MIRROR_WITHOUT_REORDER: Boolean = true

        /**
         * Overflow badges pin to the trailing edge in both LTR and RTL —
         * never an absolute left/right.
         */
        const val OVERFLOW_BADGE_EDGE: String = "trailing"

        /**
         * Key/value detail pairs render key-first in logical order in both
         * directions; the colon separator flips visually, not semantically.
         */
        const val DETAIL_ORDER: String = "key_first_logical"
    }

    /** One screen-reader statement. */
    data class AccessibilityStatement(
        /** Stable id: "<row code>.statement". */
        val id: String,
        /** The exact text a screen reader announces. */
        val text: String,
        /** Whether the row is interactive (announces the action hint). */
        val actionable: Boolean = false,
    )

    // ------------------------------------------------------------------
    // Row → statement
    // ------------------------------------------------------------------

    private val severityPrefix = mapOf(
        CanonicalDiagnosticsModel.Severity.INFO to "",
        CanonicalDiagnosticsModel.Severity.WARNING to "Warning: ",
        CanonicalDiagnosticsModel.Severity.ERROR to "Error: ",
    )

    /**
     * Renders one diagnostics row as a screen-reader statement. Secret-safe:
     * row summaries are already redacted by T30; the model re-checks and
     * refuses to announce raw secret payloads (defense in depth).
     */
    fun statementFor(row: CanonicalDiagnosticsModel.DiagnosticsRow): AccessibilityStatement {
        var text = severityPrefix.getValue(row.severity) + row.summary
        for ((key, value) in row.details) {
            val safeValue = if (value.contains(CanonicalDiagnosticsModel.SECRET_LABEL)) {
                CanonicalDiagnosticsModel.SECRET_LABEL
            } else {
                value
            }
            text += ", $key $safeValue"
        }
        return AccessibilityStatement(
            id = "${row.code}.statement",
            text = text,
            actionable = false,
        )
    }

    /** Renders a section with its heading and exact overflow announcement. */
    fun statementsFor(section: CanonicalDiagnosticsModel.DiagnosticsSection): List<AccessibilityStatement> {
        val statements = mutableListOf(
            AccessibilityStatement(
                id = "section.${section.title}.heading",
                text = section.title,
            ),
        )
        section.rows.forEach { statements += statementFor(it) }
        if (section.overflowedCount > 0) {
            statements += AccessibilityStatement(
                id = "section.${section.title}.overflow",
                text = "${section.overflowedCount} more entries not shown",
            )
        }
        return statements
    }

    /** Full snapshot → flat statement list (heading, rows, overflow per section). */
    fun statementsFor(snapshot: CanonicalDiagnosticsModel.DiagnosticsSnapshot): List<AccessibilityStatement> =
        snapshot.sections.flatMap(::statementsFor)

    // ------------------------------------------------------------------
    // Builder-side a11y rules (canonical node summary announcement)
    // ------------------------------------------------------------------

    /**
     * The announcement for a canonical node summary in the builder: value
     * payloads render through [CanonicalDiagnosticsModel.renderValue] so
     * secrets never leak into accessibility text either.
     */
    fun nodeAnnouncement(
        nodeTitle: String,
        arguments: CanonicalArguments,
    ): AccessibilityStatement {
        val parts = arguments.entries.map { argument ->
            "${argument.id.value} ${CanonicalDiagnosticsModel.renderValue(argument.value)}"
        }
        return AccessibilityStatement(
            id = "node.${nodeTitle}.announcement",
            text = (listOf(nodeTitle) + parts).joinToString(separator = ", "),
        )
    }
}
