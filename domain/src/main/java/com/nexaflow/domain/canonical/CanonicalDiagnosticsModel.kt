package com.nexaflow.domain.canonical

import com.nexaflow.domain.workflow.WorkflowMigrationOrchestrator
import com.nexaflow.domain.workflow.WorkflowPersistencePolicy

/**
 * T30 — Diagnostics UI model (plan §T30).
 *
 * The pure presentation layer behind the diagnostics screen: aggregates the
 * canonical platform's runtime surfaces into stable, display-ready rows a
 * Compose host can render directly.
 *
 * Contracts pinned by tests:
 *  - Deterministic: same input state renders byte-identical output;
 *  - Secret-safe: secret-reference values and deep-link tokens never enter
 *    a row's payload (redacted to their stable label);
 *  - Bounded: every rendered list is capped, with an exact overflow count;
 *  - Machine-paired: every row carries a stable diagnostics code so the UI
 *    and log exports reference the same fact.
 */
object CanonicalDiagnosticsModel {

    /** How many rows a section renders before overflow counting kicks in. */
    const val MAX_ROWS_PER_SECTION: Int = 50

    /** Stable diagnostics codes (pinned — log exports depend on them). */
    object Codes {
        const val CUTOVER_READY = "diag.cutover.ready"
        const val CUTOVER_INVALID = "diag.cutover.invalid"
        const val PRESERVED_UNCONSUMED = "diag.preserved.unconsumed"
        const val PERSISTENCE_DUAL_WRITE = "diag.persistence.dual_write"
        const val PERSISTENCE_DEGRADED = "diag.persistence.degraded"
        const val MIGRATION_SETTLED = "diag.migration.settled"
        const val MIGRATION_PENDING = "diag.migration.pending"
        const val MIGRATION_FAILED = "diag.migration.failed"
        const val OPTIMIZER_REMOVED = "diag.optimizer.removed"
        const val VALUE_SECRET_REDACTED = "diag.value.secret_redacted"
    }

    /** Severity a host renders with its own styling. */
    enum class Severity { INFO, WARNING, ERROR }

    /** One display-ready diagnostics row. */
    data class DiagnosticsRow(
        val code: String,
        val severity: Severity,
        /** One-line human summary (already redacted). */
        val summary: String,
        /** Ordered key/value detail pairs (already redacted). */
        val details: List<Pair<String, String>> = emptyList(),
    )

    /** A capped section with the exact number of overflowed rows. */
    data class DiagnosticsSection(
        val title: String,
        val rows: List<DiagnosticsRow>,
        val overflowedCount: Int,
    )

    /** Rendered redaction label replacing any secret-bearing payload. */
    const val SECRET_LABEL: String = "<secret>"

    /** The full diagnostics snapshot a host renders. */
    data class DiagnosticsSnapshot(
        val sections: List<DiagnosticsSection>,
    )

    // ------------------------------------------------------------------
    // Value rendering (secret-safe)
    // ------------------------------------------------------------------

    /**
     * Renders one canonical value for display. [SecretReferenceValue] never
     * renders its reference id — the label proves a secret exists without
     * naming it; every other value renders its [CanonicalValue.toString].
     */
    fun renderValue(value: CanonicalValue): String = when (value) {
        is SecretReferenceValue -> SECRET_LABEL
        else -> value.toString()
    }

    private fun renderArguments(arguments: CanonicalArguments): List<Pair<String, String>> =
        arguments.entries.map { it.id.value to renderValue(it.value) }

    // ------------------------------------------------------------------
    // Cutover section
    // ------------------------------------------------------------------

    /**
     * Renders the outcome of one cutover planning run. Rejected inputs were
     * already refused by [CanonicalRuntimePipeline.planLegacy]; this renders
     * the surviving plan and carries the preserved-but-not-executed legacy
     * payload count.
     */
    fun cutoverRows(plan: CanonicalRuntimePipeline.CanonicalizedPlan): List<DiagnosticsRow> {
        val rows = mutableListOf<DiagnosticsRow>()
        rows += if (plan.verdict.isValid) {
            DiagnosticsRow(
                code = Codes.CUTOVER_READY,
                severity = Severity.INFO,
                summary = "${plan.legacyType} cut over to ${plan.plan.allCommands.size} command(s)",
                details = listOf(
                    "runId" to plan.runId,
                    "commands" to plan.plan.allCommands.size.toString(),
                    "preservedKeys" to plan.preservedKeyCount.toString(),
                ),
            )
        } else {
            DiagnosticsRow(
                code = Codes.CUTOVER_INVALID,
                severity = Severity.ERROR,
                summary = "${plan.legacyType} failed validation at ${plan.verdict.reachedStage}",
                details = plan.verdict.findings.take(MAX_ROWS_PER_SECTION).map {
                    it.stage.name to it.message
                },
            )
        }
        if (plan.preservedKeyCount > 0) {
            rows += DiagnosticsRow(
                code = Codes.PRESERVED_UNCONSUMED,
                severity = Severity.INFO,
                summary = "${plan.preservedKeyCount} legacy key(s) ride along unconsumed",
            )
        }
        return rows
    }

    // ------------------------------------------------------------------
    // Persistence + migration section
    // ------------------------------------------------------------------

    /**
     * Renders the T27/T28 storage state: one row per journal status plus the
     * current write mode. Ids render verbatim (they are user ids, not
     * secrets); reasons render verbatim (they are typed codes).
     */
    fun persistenceRows(
        writeMode: WorkflowPersistencePolicy.WriteMode,
        journal: WorkflowMigrationOrchestrator.MigrationJournal,
        totalItems: Int,
    ): List<DiagnosticsRow> {
        val rows = mutableListOf<DiagnosticsRow>()
        rows += when (writeMode) {
            WorkflowPersistencePolicy.WriteMode.LEGACY_ONLY ->
                DiagnosticsRow(
                    code = Codes.PERSISTENCE_DEGRADED,
                    severity = Severity.WARNING,
                    summary = "legacy-only writes (pre-cutover policy)",
                )
            WorkflowPersistencePolicy.WriteMode.DUAL_WRITE_V3_PRIMARY ->
                DiagnosticsRow(
                    code = Codes.PERSISTENCE_DUAL_WRITE,
                    severity = Severity.INFO,
                    summary = "dual-write active: V3 row + legacy rollback row",
                )
            WorkflowPersistencePolicy.WriteMode.V3_ONLY ->
                DiagnosticsRow(
                    code = Codes.PERSISTENCE_DUAL_WRITE,
                    severity = Severity.INFO,
                    summary = "V3-only writes (legacy rollback retired)",
                )
        }

        val progress = WorkflowMigrationOrchestrator.MigrationProgress(
            total = totalItems,
            migrated = journal.entries.count {
                it.status == WorkflowMigrationOrchestrator.OutcomeStatus.MIGRATED ||
                    it.status == WorkflowMigrationOrchestrator.OutcomeStatus.SKIPPED_UNCHANGED
            },
            degraded = journal.entries.count {
                it.status == WorkflowMigrationOrchestrator.OutcomeStatus.DEGRADED_LEGACY_ONLY
            },
            failed = journal.failedIds.size,
        )
        rows += DiagnosticsRow(
            code = Codes.MIGRATION_SETTLED,
            severity = Severity.INFO,
            summary = "migration ${progress.settled}/${progress.total} settled",
            details = listOf("fraction" to progress.fraction.toString()),
        )
        val pending = progress.total - progress.settled - progress.failed
        if (pending > 0) {
            rows += DiagnosticsRow(
                code = Codes.MIGRATION_PENDING,
                severity = Severity.WARNING,
                summary = "$pending item(s) pending",
            )
        }
        journal.failedIds.sorted().take(MAX_ROWS_PER_SECTION).forEach { id ->
            rows += DiagnosticsRow(
                code = Codes.MIGRATION_FAILED,
                severity = Severity.ERROR,
                summary = "item $id failed: ${journal.outcomeFor(id)?.reason.orEmpty()}",
            )
        }
        return rows
    }

    // ------------------------------------------------------------------
    // Optimizer section
    // ------------------------------------------------------------------

    fun optimizerRow(report: CanonicalConsolidationOptimizer.OptimizationReport): DiagnosticsRow =
        DiagnosticsRow(
            code = Codes.OPTIMIZER_REMOVED,
            severity = Severity.INFO,
            summary = "optimizer removed ${report.removedNodes} node(s) in ${report.passesUsed} pass(es)",
            details = listOf(
                "redefinitions" to report.eliminatedRedefinitions.toString(),
                "waitsMerged" to report.mergedWaits.toString(),
            ),
        )

    // ------------------------------------------------------------------
    // Aggregation
    // ------------------------------------------------------------------

    private fun cap(rows: List<DiagnosticsRow>): Pair<List<DiagnosticsRow>, Int> =
        if (rows.size <= MAX_ROWS_PER_SECTION) {
            rows to 0
        } else {
            rows.take(MAX_ROWS_PER_SECTION) to (rows.size - MAX_ROWS_PER_SECTION)
        }

    /** Builds the full snapshot; deterministic for the same inputs. */
    fun snapshot(
        cutoverPlans: List<CanonicalRuntimePipeline.CanonicalizedPlan>,
        writeMode: WorkflowPersistencePolicy.WriteMode,
        journal: WorkflowMigrationOrchestrator.MigrationJournal,
        totalItems: Int,
        optimizerReport: CanonicalConsolidationOptimizer.OptimizationReport?,
    ): DiagnosticsSnapshot {
        val sections = mutableListOf<DiagnosticsSection>()

        run {
            val rows = cutoverPlans.flatMap(::cutoverRows)
            val (capped, overflow) = cap(rows)
            sections += DiagnosticsSection("Cutover", capped, overflow)
        }
        run {
            val (capped, overflow) = cap(persistenceRows(writeMode, journal, totalItems))
            sections += DiagnosticsSection("Persistence & migration", capped, overflow)
        }
        if (optimizerReport != null) {
            val (capped, overflow) = cap(listOf(optimizerRow(optimizerReport)))
            sections += DiagnosticsSection("Optimizer", capped, overflow)
        }
        return DiagnosticsSnapshot(sections = sections)
    }
}
