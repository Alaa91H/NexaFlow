package com.nexaflow.domain.workflow

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T28 — Controlled migration rollout tests: deterministic batching, idempotent
 * journal recovery, fail-closed completion and the per-run failure threshold.
 */
class WorkflowMigrationOrchestratorTest {

    private fun item(id: String): WorkflowMigrationOrchestrator.MigrationItem =
        WorkflowMigrationOrchestrator.MigrationItem(
            id = id,
            automation = Automation(
                id = id,
                name = "Task $id",
                description = "",
                icon = "bolt",
                iconColor = 1L,
                backgroundColor = 2L,
                category = "general",
                priority = 1,
                enabled = true,
                triggers = listOf(
                    Trigger(type = TriggerType.TIME, config = mapOf("start" to "22:00", "end" to "07:00")),
                ),
                actions = listOf(
                    Action(type = ActionType.SYSTEM_WIFI, config = mapOf("enabled" to "true")),
                ),
                createdAt = 1L,
                updatedAt = 2L,
            ),
        )

    private fun items(vararg ids: String) = ids.map(::item)

    private val emptyJournal = WorkflowMigrationOrchestrator.MigrationJournal()

    // ------------------------------------------------------------------
    // Batching
    // ------------------------------------------------------------------

    @Test
    fun batchesAreDeterministicSortedAndBounded() {
        val batches = WorkflowMigrationOrchestrator.planBatches(
            items = items("c", "a", "b", "e", "d"),
            journal = emptyJournal,
            batchSize = 2,
        )

        assertEquals(3, batches.size)
        assertEquals(listOf("a", "b"), batches[0].ids)
        assertEquals(listOf("c", "d"), batches[1].ids)
        assertEquals(listOf("e"), batches[2].ids)
        assertEquals(listOf(0, 1, 2), batches.map { it.index })
    }

    @Test
    fun alreadySettledIdsAreNotReplanned() {
        val journal = WorkflowMigrationOrchestrator.apply(
            WorkflowMigrationOrchestrator.BatchResult(
                batchIndex = 0,
                outcomes = listOf(
                    WorkflowMigrationOrchestrator.MigrationOutcome(
                        id = "a",
                        status = WorkflowMigrationOrchestrator.OutcomeStatus.MIGRATED,
                    ),
                ),
            ),
            emptyJournal,
        )

        val batches = WorkflowMigrationOrchestrator.planBatches(
            items = items("a", "b"),
            journal = journal,
            batchSize = 10,
        )

        assertEquals(1, batches.size)
        assertEquals(listOf("b"), batches[0].ids)
    }

    @Test
    fun batchSizeIsBoundedAndDuplicatesRefused() {
        try {
            WorkflowMigrationOrchestrator.planBatches(
                items = items("a"),
                journal = emptyJournal,
                batchSize = 201,
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("batchSize"))
        }
        try {
            WorkflowMigrationOrchestrator.planBatches(
                items = listOf(item("a"), item("a")),
                journal = emptyJournal,
                batchSize = 10,
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("duplicate"))
        }
    }

    // ------------------------------------------------------------------
    // Batch execution
    // ------------------------------------------------------------------

    @Test
    fun healthyBatchMigratesEveryItem() {
        val items = items("a", "b")
        val byId = items.associateBy { it.id }
        val batch = WorkflowMigrationOrchestrator.planBatches(
            items = items,
            journal = emptyJournal,
            batchSize = 10,
        ).single()

        val result = WorkflowMigrationOrchestrator.attemptBatch(
            batch = batch,
            itemsById = byId,
            attemptedAtEpochMs = 5L,
        )

        assertFalse(result.aborted)
        assertEquals(
            listOf(
                WorkflowMigrationOrchestrator.OutcomeStatus.MIGRATED,
                WorkflowMigrationOrchestrator.OutcomeStatus.MIGRATED,
            ),
            result.outcomes.map { it.status },
        )
    }

    @Test
    fun failureThresholdAbortsTheBatchMidWay() {
        val items = items("a", "b", "c", "d", "e")
        val byId = items.associateBy { it.id }
        val batch = WorkflowMigrationOrchestrator.planBatches(
            items = items,
            journal = emptyJournal,
            batchSize = 10,
        ).single()

        // A systematic conversion bug: item "c" always throws (the kind of
        // repeated failure the threshold must stop the rollout over).
        val result = WorkflowMigrationOrchestrator.attemptBatch(
            batch = WorkflowMigrationOrchestrator.MigrationBatch(index = 0, ids = listOf("a", "b", "c", "d", "e")),
            itemsById = byId,
            maxFailuresPerRun = 1,
            attemptedAtEpochMs = 5L,
            convert = { item ->
                if (item.id == "c") {
                    throw IllegalStateException("systematic conversion bug")
                }
                WorkflowPersistencePolicy.planWrite(
                    automation = item.automation,
                    mode = WorkflowPersistencePolicy.WriteMode.DUAL_WRITE_V3_PRIMARY,
                    storedAtEpochMs = 5L,
                )
            },
        )

        // a and b migrated; c failed; d and e are never attempted once the
        // threshold trips.
        assertTrue(result.aborted)
        assertEquals(listOf("a", "b", "c"), result.outcomes.map { it.id })
        assertEquals(
            listOf(
                WorkflowMigrationOrchestrator.OutcomeStatus.MIGRATED,
                WorkflowMigrationOrchestrator.OutcomeStatus.MIGRATED,
                WorkflowMigrationOrchestrator.OutcomeStatus.FAILED,
            ),
            result.outcomes.map { it.status },
        )
        assertTrue(result.outcomes.last().reason.contains("systematic"))
    }

    @Test
    fun degradedPreparationLandsAsLegacyOnlyNotFailed() {
        // A blank automation id is refused by the document constructor; the
        // T27 policy degrades that to a legacy-only save (the user edit still
        // lands) — the orchestrator records DEGRADED, keeps it retryable, and
        // counts it against the rollout failure budget because V3 did not land.
        val poisoned = WorkflowMigrationOrchestrator.MigrationItem(
            id = "c",
            automation = item("c").automation.copy(id = ""),
        )
        val result = WorkflowMigrationOrchestrator.attemptBatch(
            batch = WorkflowMigrationOrchestrator.MigrationBatch(0, listOf("c")),
            itemsById = mapOf("c" to poisoned),
            maxFailuresPerRun = 0,
            attemptedAtEpochMs = 5L,
        )

        assertFalse(result.aborted)
        assertEquals(
            WorkflowMigrationOrchestrator.OutcomeStatus.DEGRADED_LEGACY_ONLY,
            result.outcomes.single().status,
        )
        assertTrue(result.outcomes.single().reason.isNotBlank())
    }

    // ------------------------------------------------------------------
    // Journal semantics
    // ------------------------------------------------------------------

    @Test
    fun journalApplyIsIdempotentAndRetriesFailures() {
        val batch = WorkflowMigrationOrchestrator.MigrationBatch(index = 0, ids = listOf("a", "b"))
        val items = items("a", "b").associateBy { it.id }
        val first = WorkflowMigrationOrchestrator.attemptBatch(
            batch = batch,
            itemsById = items,
            attemptedAtEpochMs = 5L,
        )
        val journal1 = WorkflowMigrationOrchestrator.apply(first, emptyJournal)
        val journal2 = WorkflowMigrationOrchestrator.apply(first, journal1)

        // Re-applying the same result is a no-op.
        assertEquals(journal1, journal2)

        // A FAILED entry is retried on a later run: the journal keeps the
        // newest outcome for the id.
        val failed = WorkflowMigrationOrchestrator.MigrationOutcome(
            id = "a",
            status = WorkflowMigrationOrchestrator.OutcomeStatus.FAILED,
            reason = "boom",
        )
        val journalWithFailure = WorkflowMigrationOrchestrator.apply(
            WorkflowMigrationOrchestrator.BatchResult(0, listOf(failed)),
            journal1,
        )
        val retried = WorkflowMigrationOrchestrator.attemptBatch(
            batch = WorkflowMigrationOrchestrator.MigrationBatch(index = 0, ids = listOf("a")),
            itemsById = items,
            attemptedAtEpochMs = 6L,
        )
        val journal3 = WorkflowMigrationOrchestrator.apply(retried, journalWithFailure)
        assertEquals(
            WorkflowMigrationOrchestrator.OutcomeStatus.MIGRATED,
            journal3.outcomeFor("a")?.status,
        )
    }

    @Test
    fun failedOutcomesMustCarryATypedReason() {
        try {
            WorkflowMigrationOrchestrator.MigrationOutcome(
                id = "a",
                status = WorkflowMigrationOrchestrator.OutcomeStatus.FAILED,
                reason = "",
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("reason"))
        }
    }

    // ------------------------------------------------------------------
    // Progress + completion gate
    // ------------------------------------------------------------------

    @Test
    fun progressCountsEveryTerminalState() {
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
                        WorkflowMigrationOrchestrator.OutcomeStatus.DEGRADED_LEGACY_ONLY,
                        reason = "v3_write_failed_fell_back_to_legacy_row_only",
                    ),
                    WorkflowMigrationOrchestrator.MigrationOutcome(
                        "c",
                        WorkflowMigrationOrchestrator.OutcomeStatus.FAILED,
                        reason = "boom",
                    ),
                ),
            ),
            emptyJournal,
        )

        val progress = WorkflowMigrationOrchestrator.progress(items("a", "b", "c", "d"), journal)
        assertEquals(4, progress.total)
        assertEquals(1, progress.settled)
        assertEquals(1, progress.degraded)
        assertEquals(1, progress.failed)
        assertEquals(0.25, progress.fraction, 0.0001)
    }

    @Test
    fun degradedRowsAreRetriedAndBlockCompletion() {
        val degraded = WorkflowMigrationOrchestrator.MigrationJournal(
            entries = listOf(
                WorkflowMigrationOrchestrator.MigrationOutcome(
                    id = "a",
                    status = WorkflowMigrationOrchestrator.OutcomeStatus.DEGRADED_LEGACY_ONLY,
                    reason = "v3_write_failed_fell_back_to_legacy_row_only",
                ),
            ),
        )

        val batches = WorkflowMigrationOrchestrator.planBatches(
            items = items("a", "b"),
            journal = degraded,
            batchSize = 10,
        )
        assertEquals(listOf("a", "b"), batches.single().ids)

        try {
            WorkflowMigrationOrchestrator.declareMigrationComplete(items("a"), degraded)
            throw AssertionError("Expected degraded row to block completion")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("unresolved"))
        }
    }

    @Test
    fun completionIsRefusedUntilEveryIdIsSettled() {
        val journal = WorkflowMigrationOrchestrator.apply(
            WorkflowMigrationOrchestrator.BatchResult(
                0,
                listOf(
                    WorkflowMigrationOrchestrator.MigrationOutcome(
                        "a",
                        WorkflowMigrationOrchestrator.OutcomeStatus.MIGRATED,
                    ),
                ),
            ),
            emptyJournal,
        )

        try {
            WorkflowMigrationOrchestrator.declareMigrationComplete(items("a", "b"), journal)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("unresolved"))
        }

        val complete = WorkflowMigrationOrchestrator.apply(
            WorkflowMigrationOrchestrator.BatchResult(
                0,
                listOf(
                    WorkflowMigrationOrchestrator.MigrationOutcome(
                        "b",
                        WorkflowMigrationOrchestrator.OutcomeStatus.MIGRATED,
                    ),
                ),
            ),
            journal,
        )
        // No exception: every id is settled, so T27's V3_ONLY mode unlocks.
        WorkflowMigrationOrchestrator.declareMigrationComplete(items("a", "b"), complete)

        // And the T27 gate actually accepts the declaration.
        val decision = WorkflowPersistencePolicy.planWrite(
            automation = item("a").automation,
            mode = WorkflowPersistencePolicy.WriteMode.V3_ONLY,
            legacyMigrationComplete = true,
            storedAtEpochMs = 1L,
        )
        assertFalse(decision.writeLegacyRow)
    }

    // ------------------------------------------------------------------
    // End-to-end recovery story
    // ------------------------------------------------------------------

    @Test
    fun crashedRunResumesFromTheJournalWithoutDoubleWork() {
        val items = items("a", "b", "c")
        val byId = items.associateBy { it.id }
        var journal = emptyJournal

        // Run 1 crashes after migrating only "a".
        val batches1 = WorkflowMigrationOrchestrator.planBatches(items, journal, batchSize = 2)
        val first = WorkflowMigrationOrchestrator.attemptBatch(
            batch = WorkflowMigrationOrchestrator.MigrationBatch(0, listOf("a")),
            itemsById = byId,
            attemptedAtEpochMs = 1L,
        )
        journal = WorkflowMigrationOrchestrator.apply(first, journal)

        // Run 2 re-plans from the journal: "a" is settled, b and c remain.
        val batches2 = WorkflowMigrationOrchestrator.planBatches(items, journal, batchSize = 2)
        assertFalse(batches2.any { "a" in it.ids })
        assertEquals(listOf(listOf("b", "c")), batches2.map { it.ids })

        val second = WorkflowMigrationOrchestrator.attemptBatch(
            batch = batches2.first(),
            itemsById = byId,
            attemptedAtEpochMs = 2L,
        )
        journal = WorkflowMigrationOrchestrator.apply(second, journal)

        val progress = WorkflowMigrationOrchestrator.progress(items, journal)
        assertEquals(3, progress.settled)
        assertEquals(1.0, progress.fraction, 0.0001)
        WorkflowMigrationOrchestrator.declareMigrationComplete(items, journal)
    }
}
