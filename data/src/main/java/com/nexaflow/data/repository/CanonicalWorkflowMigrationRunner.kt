package com.nexaflow.data.repository

import com.nexaflow.core.database.AutomationDao
import com.nexaflow.core.database.AutomationEntity
import com.nexaflow.data.mapper.toDomain
import com.nexaflow.data.mapper.toEntity
import com.nexaflow.domain.canonical.CanonicalV3WriteState
import com.nexaflow.domain.workflow.WorkflowMigrationOrchestrator
import javax.inject.Inject

/**
 * Production T28 rollout runner over the real Room rows.
 *
 * Each row's canonicalWriteState is the durable migration journal:
 * - V3_READY / V3_WITH_LEGACY_FALLBACK: canonical graph already landed.
 * - LEGACY_ONLY / LEGACY_ONLY_DEGRADED: pending and retried in a later batch.
 *
 * No separate checkpoint table is needed: a successful CAS writes the V3
 * payload and its journal state atomically with the existing row. A concurrent
 * user edit wins; the migration leaves that row untouched and retries it from
 * the next fresh snapshot.
 */
class CanonicalWorkflowMigrationRunner @Inject constructor(
    private val automationDao: AutomationDao,
) {

    data class BatchReport(
        val attempted: Int,
        val migrated: Int,
        val migratedWithLegacyFallback: Int,
        val degraded: Int,
        val conflicted: Int,
        val remaining: Int,
        val aborted: Boolean,
    )

    data class FleetStatus(
        val total: Int,
        val canonicalGraphReady: Int,
        val legacyFallbackRequired: Int,
        val pending: Int,
        val degraded: Int,
        val canonicalGraphMigrationComplete: Boolean,
        val legacyRetirementReady: Boolean,
    )

    suspend fun runNextBatch(
        batchSize: Int = DEFAULT_BATCH_SIZE,
        maxFailuresPerRun: Int = DEFAULT_MAX_FAILURES,
    ): BatchReport {
        require(batchSize in WorkflowMigrationOrchestrator.MIN_BATCH_SIZE..
            WorkflowMigrationOrchestrator.MAX_BATCH_SIZE) {
            "batchSize must be in " +
                "${WorkflowMigrationOrchestrator.MIN_BATCH_SIZE}.." +
                "${WorkflowMigrationOrchestrator.MAX_BATCH_SIZE}"
        }
        require(maxFailuresPerRun >= 0) { "maxFailuresPerRun must be non-negative" }

        val snapshot = automationDao.getAllAutomationsSnapshot()
        val pendingRows = snapshot
            .filterNot(::hasCanonicalGraph)
            .sortedBy { it.id }
            .take(batchSize)

        var migrated = 0
        var migratedWithFallback = 0
        var degraded = 0
        var conflicted = 0
        var failures = 0
        var aborted = false

        for (row in pendingRows) {
            if (failures > maxFailuresPerRun) {
                aborted = true
                break
            }

            val legacy = row.toDomain()
            val prepared = legacy.toEntity()
            val state = prepared.writeStateOrNull()
            val success = state == CanonicalV3WriteState.V3_READY ||
                state == CanonicalV3WriteState.V3_WITH_LEGACY_FALLBACK

            if (!success) {
                failures += 1
                degraded += 1
                // Persist the typed degradation state/error only if the row did
                // not change since this snapshot. It remains pending.
                if (!automationDao.compareAndSetAutomation(
                        automation = prepared,
                        expectedRevision = row.updatedAt,
                    )
                ) {
                    conflicted += 1
                    degraded -= 1
                }
                if (failures > maxFailuresPerRun) {
                    aborted = true
                    break
                }
                continue
            }

            val committed = automationDao.compareAndSetAutomation(
                automation = prepared,
                expectedRevision = row.updatedAt,
            )
            if (!committed) {
                conflicted += 1
                continue
            }

            migrated += 1
            if (state == CanonicalV3WriteState.V3_WITH_LEGACY_FALLBACK) {
                migratedWithFallback += 1
            }
        }

        val remaining = automationDao.getAllAutomationsSnapshot()
            .count { !hasCanonicalGraph(it) }

        return BatchReport(
            attempted = migrated + degraded + conflicted,
            migrated = migrated,
            migratedWithLegacyFallback = migratedWithFallback,
            degraded = degraded,
            conflicted = conflicted,
            remaining = remaining,
            aborted = aborted,
        )
    }

    suspend fun fleetStatus(): FleetStatus =
        statusOf(automationDao.getAllAutomationsSnapshot())

    internal fun statusOf(rows: List<AutomationEntity>): FleetStatus {
        var ready = 0
        var fallback = 0
        var degraded = 0
        var pending = 0

        rows.forEach { row ->
            when (row.writeStateOrNull()) {
                CanonicalV3WriteState.V3_READY -> ready += 1
                CanonicalV3WriteState.V3_WITH_LEGACY_FALLBACK -> fallback += 1
                CanonicalV3WriteState.LEGACY_ONLY_DEGRADED -> degraded += 1
                null -> pending += 1
            }
        }

        val graphReady = ready + fallback
        return FleetStatus(
            total = rows.size,
            canonicalGraphReady = graphReady,
            legacyFallbackRequired = fallback,
            pending = pending,
            degraded = degraded,
            canonicalGraphMigrationComplete =
                graphReady == rows.size && degraded == 0 && pending == 0,
            legacyRetirementReady =
                ready == rows.size && fallback == 0 && degraded == 0 && pending == 0,
        )
    }

    private fun hasCanonicalGraph(row: AutomationEntity): Boolean {
        val state = row.writeStateOrNull()
        return row.canonicalWorkflowJson != null &&
            (state == CanonicalV3WriteState.V3_READY ||
                state == CanonicalV3WriteState.V3_WITH_LEGACY_FALLBACK)
    }

    private fun AutomationEntity.writeStateOrNull(): CanonicalV3WriteState? =
        runCatching { CanonicalV3WriteState.valueOf(canonicalWriteState) }.getOrNull()

    private companion object {
        const val DEFAULT_BATCH_SIZE = 50
        const val DEFAULT_MAX_FAILURES = 3
    }
}
