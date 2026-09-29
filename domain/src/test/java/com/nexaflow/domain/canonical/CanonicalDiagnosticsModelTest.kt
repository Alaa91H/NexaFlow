package com.nexaflow.domain.canonical

import com.nexaflow.domain.canonical.CanonicalDiagnosticsModel.Severity
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequirement
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.workflow.WorkflowMigrationOrchestrator
import com.nexaflow.domain.workflow.WorkflowPersistencePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T30 — Diagnostics model tests: deterministic rendering, secret redaction,
 * bounded sections and machine-paired codes.
 */
class CanonicalDiagnosticsModelTest {

    // ------------------------------------------------------------------
    // Value rendering
    // ------------------------------------------------------------------

    @Test
    fun secretReferencesRenderAsTheLabelWithoutNamingTheReference() {
        val rendered = CanonicalDiagnosticsModel.renderValue(
            SecretReferenceValue("legacy.privileged_command"),
        )

        assertEquals(CanonicalDiagnosticsModel.SECRET_LABEL, rendered)
        assertFalse(rendered.contains("privileged"))
    }

    @Test
    fun ordinaryValuesRenderDeterministically() {
        // Non-secret values render their canonical toString (deterministic);
        // the contract only guarantees redaction for SecretReferenceValue.
        assertTrue(
            CanonicalDiagnosticsModel.renderValue(BooleanValue(true)).contains("true"),
        )
    }

    // ------------------------------------------------------------------
    // Cutover rows
    // ------------------------------------------------------------------

    private val pipeline = CanonicalRuntimePipeline()
    private val openSchema = PilotOpenFamily.openSettingsSchema()

    private fun cutOverPlan(
        runId: String = "run-1",
        config: List<LegacyConfigEntry> = listOf(LegacyConfigEntry("page", "WIFI")),
    ): CanonicalRuntimePipeline.CanonicalizedPlan = pipeline.planLegacy(
        runId = runId,
        legacyType = "SYSTEM_OPEN_WIFI_SETTINGS",
        kind = LegacyNodeKind.ACTION,
        schema = openSchema,
        config = config,
        semantics = PilotOpenFamily.semantics,
        capabilityRequirement = CapabilityRequirement.Capability(CapabilityId.SETTINGS_LAUNCH),
    )

    @Test
    fun cutoverRowReportsCommandsAndPreservedKeys() {
        val plan = cutOverPlan(
            config = listOf(
                LegacyConfigEntry("page", "WIFI"),
                LegacyConfigEntry("futureKey", "42"),
            ),
        )
        val rows = CanonicalDiagnosticsModel.cutoverRows(plan)

        val ready = rows.single { it.code == CanonicalDiagnosticsModel.Codes.CUTOVER_READY }
        assertEquals(Severity.INFO, ready.severity)
        assertTrue(ready.details.any { it.first == "preservedKeys" && it.second == "1" })
    }

    @Test
    fun preservedKeysRenderTheirOwnRow() {
        val rows = CanonicalDiagnosticsModel.cutoverRows(
            cutOverPlan(config = listOf(LegacyConfigEntry("page", "WIFI"), LegacyConfigEntry("k", "v"))),
        )

        assertTrue(
            rows.any {
                it.code == CanonicalDiagnosticsModel.Codes.PRESERVED_UNCONSUMED &&
                    it.summary.startsWith("1 legacy key")
            },
        )
    }

    // ------------------------------------------------------------------
    // Persistence + migration rows
    // ------------------------------------------------------------------

    @Test
    fun dualWriteModeRendersInfoAndFailedItemsRenderAsErrors() {
        val journal = WorkflowMigrationOrchestrator.apply(
            WorkflowMigrationOrchestrator.BatchResult(
                0,
                listOf(
                    WorkflowMigrationOrchestrator.MigrationOutcome(
                        "a",
                        WorkflowMigrationOrchestrator.OutcomeStatus.MIGRATED,
                    ),
                    WorkflowMigrationOrchestrator.MigrationOutcome(
                        "b",
                        WorkflowMigrationOrchestrator.OutcomeStatus.FAILED,
                        reason = "boom",
                    ),
                ),
            ),
            WorkflowMigrationOrchestrator.MigrationJournal(),
        )

        val rows = CanonicalDiagnosticsModel.persistenceRows(
            writeMode = WorkflowPersistencePolicy.WriteMode.DUAL_WRITE_V3_PRIMARY,
            journal = journal,
            totalItems = 2,
        )

        assertTrue(
            rows.any {
                it.code == CanonicalDiagnosticsModel.Codes.PERSISTENCE_DUAL_WRITE &&
                    it.severity == Severity.INFO
            },
        )
        val failed = rows.filter { it.code == CanonicalDiagnosticsModel.Codes.MIGRATION_FAILED }
        assertEquals(1, failed.size)
        assertEquals(Severity.ERROR, failed.single().severity)
        assertTrue(failed.single().summary.contains("boom"))
    }

    @Test
    fun legacyOnlyModeRendersAWarning() {
        val rows = CanonicalDiagnosticsModel.persistenceRows(
            writeMode = WorkflowPersistencePolicy.WriteMode.LEGACY_ONLY,
            journal = WorkflowMigrationOrchestrator.MigrationJournal(),
            totalItems = 0,
        )

        assertTrue(
            rows.any {
                it.code == CanonicalDiagnosticsModel.Codes.PERSISTENCE_DEGRADED &&
                    it.severity == Severity.WARNING
            },
        )
    }

    // ------------------------------------------------------------------
    // Optimizer row + snapshot
    // ------------------------------------------------------------------

    @Test
    fun optimizerReportRendersRemovalCounts() {
        val row = CanonicalDiagnosticsModel.optimizerRow(
            CanonicalConsolidationOptimizer.OptimizationReport(
                eliminatedRedefinitions = 2,
                mergedWaits = 1,
                passesUsed = 3,
            ),
        )

        assertEquals(CanonicalDiagnosticsModel.Codes.OPTIMIZER_REMOVED, row.code)
        assertTrue(row.summary.contains("3 node(s)"))
        assertTrue(row.details.any { it.first == "waitsMerged" && it.second == "1" })
    }

    @Test
    fun snapshotIsDeterministicAndSectionsAreOrdered() {
        val plan = cutOverPlan()
        val journal = WorkflowMigrationOrchestrator.MigrationJournal()
        val report = CanonicalConsolidationOptimizer.OptimizationReport(0, 0, 1)

        val first = CanonicalDiagnosticsModel.snapshot(
            cutoverPlans = listOf(plan),
            writeMode = WorkflowPersistencePolicy.WriteMode.DUAL_WRITE_V3_PRIMARY,
            journal = journal,
            totalItems = 0,
            optimizerReport = report,
        )
        val second = CanonicalDiagnosticsModel.snapshot(
            cutoverPlans = listOf(plan),
            writeMode = WorkflowPersistencePolicy.WriteMode.DUAL_WRITE_V3_PRIMARY,
            journal = journal,
            totalItems = 0,
            optimizerReport = report,
        )

        assertEquals(first, second)
        assertEquals(
            listOf("Cutover", "Persistence & migration", "Optimizer"),
            first.sections.map { it.title },
        )
    }

    @Test
    fun oversizedSectionsCapWithAnExactOverflowCount() {
        val plans = (1..60).map { index -> cutOverPlan(runId = "run-$index") }

        val snapshot = CanonicalDiagnosticsModel.snapshot(
            cutoverPlans = plans,
            writeMode = WorkflowPersistencePolicy.WriteMode.DUAL_WRITE_V3_PRIMARY,
            journal = WorkflowMigrationOrchestrator.MigrationJournal(),
            totalItems = 0,
            optimizerReport = null,
        )

        val cutover = snapshot.sections.single { it.title == "Cutover" }
        assertEquals(CanonicalDiagnosticsModel.MAX_ROWS_PER_SECTION, cutover.rows.size)
        // 60 plans each render one ready row (no preserved keys) -> 10 overflow.
        assertEquals(10, cutover.overflowedCount)
    }
}
