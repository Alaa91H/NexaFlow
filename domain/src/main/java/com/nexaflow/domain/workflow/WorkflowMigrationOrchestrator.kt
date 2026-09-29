package com.nexaflow.domain.workflow

import com.nexaflow.domain.models.Automation
import kotlinx.serialization.Serializable

/**
 * T28 — Controlled migration rollout with batching and recovery (plan §T28).
 *
 * Drives the T27 storage policy across the whole existing fleet:
 *
 * - Batched: rows are converted in deterministic, bounded batches so a
 *   rollout can be paused, observed and resumed without a giant commit.
 * - Idempotent: every conversion goes through [WorkflowPersistencePolicy]
 *   (deterministic planning), and the journal records per-id outcomes, so a
 *   crashed run is resumed by re-planning from the journal — an item whose
 *   V3 row already landed is skipped, never double-persisted by the planner.
 * - Fail closed: completion may only be declared when the journal covers
 *   every input id with a terminal outcome; the per-run failure threshold
 *   aborts the rollout before a systematic conversion bug damages the fleet.
 *
 * Pure domain: no storage, no clocks — the caller supplies timestamps and
 * executes the planned decisions with the storage layer.
 */
object WorkflowMigrationOrchestrator {

    /** Hard bounds for a batch; oversized batches defeat observability. */
    const val MIN_BATCH_SIZE: Int = 1
    const val MAX_BATCH_SIZE: Int = 200

    /** One fleet row to migrate: identity plus the current legacy model. */
    data class MigrationItem(
        val id: String,
        val automation: Automation,
    )

    /** Deterministic, bounded batch of pending ids. */
    data class MigrationBatch(
        val index: Int,
        val ids: List<String>,
    )

    /** Terminal per-item outcomes recorded in the durable journal. */
    enum class OutcomeStatus { MIGRATED, DEGRADED_LEGACY_ONLY, FAILED, SKIPPED_UNCHANGED }

    /** One journal entry: the durable result of attempting one id. */
    @Serializable
    data class MigrationOutcome(
        val id: String,
        val status: OutcomeStatus,
        /** Typed reason for FAILED/DEGRADED entries; empty otherwise. */
        val reason: String = "",
        /** Attempt epoch ms supplied by the runner (opaque here). */
        val attemptedAtEpochMs: Long = 0L,
    ) {
        init {
            require(id.isNotBlank()) { "id must not be blank" }
            require(status != OutcomeStatus.FAILED || reason.isNotBlank()) {
                "FAILED outcomes must carry a typed reason"
            }
        }
    }

    /** Durable journal: apply is idempotent per id (last write wins). */
    @Serializable
    data class MigrationJournal(
        val entries: List<MigrationOutcome> = emptyList(),
    ) {
        private val byId: Map<String, MigrationOutcome> = entries.associateBy { it.id }

        /**
         * Only rows with a canonical payload are settled. A degraded
         * legacy-only write remains pending and is retried on later runs.
         */
        val completedIds: Set<String>
            get() = byId.values
                .filter {
                    it.status == OutcomeStatus.MIGRATED ||
                        it.status == OutcomeStatus.SKIPPED_UNCHANGED
                }
                .mapTo(mutableSetOf()) { it.id }

        val failedIds: Set<String>
            get() = byId.values
                .filter { it.status == OutcomeStatus.FAILED }
                .mapTo(mutableSetOf()) { it.id }

        fun outcomeFor(id: String): MigrationOutcome? = byId[id]
    }

    /** Result of executing one batch against the (pure) conversion step. */
    data class BatchResult(
        val batchIndex: Int,
        val outcomes: List<MigrationOutcome>,
        /** True when the failure threshold stopped the run mid-batch. */
        val aborted: Boolean = false,
    )

    /**
     * Plans the pending batches. Deterministic: ids are sorted, already
     * settled ids are skipped, and the remainder splits into batches of
     * exactly [batchSize] (the last one may be smaller).
     */
    fun planBatches(
        items: List<MigrationItem>,
        journal: MigrationJournal,
        batchSize: Int,
    ): List<MigrationBatch> {
        require(batchSize in MIN_BATCH_SIZE..MAX_BATCH_SIZE) {
            "batchSize must be in $MIN_BATCH_SIZE..$MAX_BATCH_SIZE"
        }
        require(items.map { it.id }.size == items.map { it.id }.toSet().size) {
            "duplicate migration item ids"
        }
        val pending = items
            .map { it.id }
            .filter { it !in journal.completedIds }
            .sorted()
        return pending.chunked(batchSize).mapIndexed { index, ids ->
            MigrationBatch(index = index, ids = ids)
        }
    }

    /**
     * Attempts one batch. Each conversion is delegated to [convert] (the
     * production default plans a T27 DUAL_WRITE_V3_PRIMARY decision); a
     * prepared V3 row means MIGRATED, a degraded decision means
     * DEGRADED_LEGACY_ONLY and remains retryable, and any thrown conversion
     * failure means FAILED. Both degraded and failed conversions consume the
     * failure budget. When [maxFailuresPerRun] is exceeded the batch aborts:
     * remaining ids
     * are left unattempted so the rollout stops before repeating the same
     * systematic failure. The failure counter is per-run; historical journal
     * failures are retried by a later run instead of blocking it.
     */
    fun attemptBatch(
        batch: MigrationBatch,
        itemsById: Map<String, MigrationItem>,
        maxFailuresPerRun: Int = 3,
        attemptedAtEpochMs: Long = 0L,
        convert: (MigrationItem) -> WorkflowPersistencePolicy.WriteDecision = {
            WorkflowPersistencePolicy.planWrite(
                automation = it.automation,
                mode = WorkflowPersistencePolicy.WriteMode.DUAL_WRITE_V3_PRIMARY,
                storedAtEpochMs = attemptedAtEpochMs,
            )
        },
    ): BatchResult {
        require(batch.ids.all { it in itemsById }) {
            "batch references unknown ids"
        }
        val outcomes = mutableListOf<MigrationOutcome>()
        var failures = 0

        for (id in batch.ids) {
            if (failures > maxFailuresPerRun) {
                return BatchResult(
                    batchIndex = batch.index,
                    outcomes = outcomes,
                    aborted = true,
                )
            }
            val item = itemsById.getValue(id)
            val decision = try {
                convert(item)
            } catch (failure: Exception) {
                failures += 1
                outcomes += MigrationOutcome(
                    id = id,
                    status = OutcomeStatus.FAILED,
                    reason = failure.message ?: failure::class.simpleName ?: "conversion failed",
                    attemptedAtEpochMs = attemptedAtEpochMs,
                )
                if (failures > maxFailuresPerRun) {
                    return BatchResult(
                        batchIndex = batch.index,
                        outcomes = outcomes,
                        aborted = true,
                    )
                }
                continue
            }
            if (decision.row != null) {
                outcomes += MigrationOutcome(
                    id = id,
                    status = OutcomeStatus.MIGRATED,
                    attemptedAtEpochMs = attemptedAtEpochMs,
                )
            } else {
                failures += 1
                outcomes += MigrationOutcome(
                    id = id,
                    status = OutcomeStatus.DEGRADED_LEGACY_ONLY,
                    reason = decision.warnings.joinToString(separator = ";"),
                    attemptedAtEpochMs = attemptedAtEpochMs,
                )
                if (failures > maxFailuresPerRun) {
                    return BatchResult(
                        batchIndex = batch.index,
                        outcomes = outcomes,
                        aborted = true,
                    )
                }
            }
        }
        return BatchResult(batchIndex = batch.index, outcomes = outcomes)
    }

    /**
     * Idempotent journal update: re-applying a batch (crash recovery) with
     * the same outcomes yields the same journal. FAILED entries are retried
     * on a later run by design — the journal keeps the newest outcome.
     */
    fun apply(batchResult: BatchResult, journal: MigrationJournal): MigrationJournal {
        val merged = journal.entries.associateBy { it.id }.toMutableMap()
        batchResult.outcomes.forEach { merged[it.id] = it }
        return MigrationJournal(entries = merged.values.sortedBy { it.id })
    }

    /** Fleet progress snapshot for rollout dashboards and gates. */
    data class MigrationProgress(
        val total: Int,
        val migrated: Int,
        val degraded: Int,
        val failed: Int,
    ) {
        /** Canonical-ready rows only; degraded rows are intentionally pending. */
        val settled: Int get() = migrated
        val fraction: Double get() = if (total == 0) 1.0 else migrated.toDouble() / total
    }

    fun progress(items: List<MigrationItem>, journal: MigrationJournal): MigrationProgress {
        val total = items.size
        var migrated = 0
        var degraded = 0
        var failed = 0
        for (item in items) {
            when (journal.outcomeFor(item.id)?.status) {
                OutcomeStatus.MIGRATED, OutcomeStatus.SKIPPED_UNCHANGED -> migrated++
                OutcomeStatus.DEGRADED_LEGACY_ONLY -> degraded++
                OutcomeStatus.FAILED -> failed++
                null -> Unit
            }
        }
        return MigrationProgress(total, migrated, degraded, failed)
    }

    /**
     * Declares the legacy migration complete — the T27 `legacyMigrationComplete`
     * flag that unlocks V3_ONLY writes. Fails closed unless every id has a
     * non-FAILED terminal outcome.
     */
    fun declareMigrationComplete(
        items: List<MigrationItem>,
        journal: MigrationJournal,
    ) {
        val unresolved = items.map { it.id }.filter { id ->
            when (journal.outcomeFor(id)?.status) {
                OutcomeStatus.MIGRATED,
                OutcomeStatus.SKIPPED_UNCHANGED -> false
                OutcomeStatus.DEGRADED_LEGACY_ONLY,
                OutcomeStatus.FAILED,
                null -> true
            }
        }
        require(unresolved.isEmpty()) {
            "cannot declare migration complete: ${unresolved.size} unresolved ids " +
                "(first: ${unresolved.firstOrNull()})"
        }
    }
}
