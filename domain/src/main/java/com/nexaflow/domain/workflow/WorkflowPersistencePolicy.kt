package com.nexaflow.domain.workflow

import com.nexaflow.domain.models.Automation
import kotlinx.serialization.Serializable

/**
 * T27 — Persistence policy: dual-read / V3-write (plan §T27).
 *
 * Storage transition contract between the legacy `Automation` row (today's
 * storage format of every existing task) and the versioned
 * [WorkflowDocumentV1] document (the target source of truth). The policy is
 * pure domain: no Android, no Room, no clocks — the storage layer applies it.
 *
 * Write policy (V3-write):
 *  - [WriteMode.DUAL_WRITE_V3_PRIMARY] is the default: every save persists
 *    the V3 row (typed document JSON + full legacy snapshot) AND the legacy
 *    row, unchanged. If the V3 write cannot be prepared, the save falls back
 *    to the legacy row only and records a typed warning — a persistence
 *    hiccup must never lose a user edit.
 *  - [WriteMode.LEGACY_ONLY] keeps writing the historical row (opt-out,
 *    pre-cutover behavior).
 *  - [WriteMode.V3_ONLY] drops the legacy row; it is refused while
 *    `legacyMigrationComplete` has not been declared (fail closed).
 *
 * Read policy (dual-read):
 *  - no V3 row → read the legacy row ([StorageReadResult.LegacyRowNotFound]);
 *  - V3 row present → the document is authoritative; the legacy snapshot
 *    contributes only the fields the document does not model (today:
 *    `deepLinkToken`). Any document decode/rejection falls back to the
 *    legacy snapshot with a typed reason — never a fabricated default.
 *
 * The document→legacy round-trip is lossless by T-P0.1 contract
 * ([WorkflowDocumentMappers]), so dual-write duplication is temporary by
 * construction: the V3 row is the survivor, the legacy row is the rollback.
 */
object WorkflowPersistencePolicy {

    /** Monotonic policy revision; bump on any persisted-layout change. */
    const val POLICY_VERSION: Int = 1

    /** Marker written alongside V3 rows for diagnostics (never parsed). */
    const val V3_ROW: String = "V3_ROW"

    /**
     * The V3 persisted row: the versioned document plus the lossless legacy
     * snapshot it must be able to reconstruct. Both payloads are stored as
     * JSON strings so the row survives schema evolution of either side.
     */
    @Serializable
    data class PersistedWorkflowRowV3(
        /** Raw [WorkflowDocumentV1] JSON — decode validates the version. */
        val documentJson: String,
        /** Document revision this row was written from. */
        val documentRevision: Long,
        /** Expected [WorkflowDocumentV1.SCHEMA_VERSION]; validated at decode. */
        val documentSchemaVersion: Int = 1,
        /** Full legacy snapshot (see [LegacyAutomationSnapshot]). */
        val legacySnapshotJson: String,
        /** Legacy `workflowVersion` the snapshot was written with. */
        val legacyWorkflowVersion: Int = Automation.CURRENT_WORKFLOW_VERSION,
        /** Write timestamp supplied by the storage layer (opaque here). */
        val storedAtEpochMs: Long,
        /** [POLICY_VERSION] at write time. */
        val policyVersion: Int = POLICY_VERSION,
    ) {
        init {
            require(documentJson.isNotBlank()) { "documentJson must not be blank" }
            require(legacySnapshotJson.isNotBlank()) { "legacySnapshotJson must not be blank" }
            require(documentRevision >= 1L) { "documentRevision must be >= 1" }
        }
    }

    /**
     * The legacy side of the V3 row. `deepLinkToken` is `@Transient` on
     * [Automation] (deliberately never serialized), so it rides explicitly
     * beside the row JSON — the one field the document does not model must
     * still round-trip exactly.
     */
    @Serializable
    data class LegacyAutomationSnapshot(
        val automationJson: String,
        val deepLinkToken: String? = null,
    ) {
        init {
            require(automationJson.isNotBlank()) { "automationJson must not be blank" }
        }
    }

    /** How the storage layer should persist one save. */
    enum class WriteMode {
        /** Legacy row only: historical behavior, pre-cutover opt-out. */
        LEGACY_ONLY,

        /**
         * Default: write the V3 row and the legacy row. Preparing the V3 row
         * may not fail the save — [planWrite] degrades to legacy-only with a
         * typed warning instead.
         */
        DUAL_WRITE_V3_PRIMARY,

        /**
         * V3 row only. Refused while the legacy migration is not declared
         * complete (fail closed: no silent retirement of the rollback path).
         */
        V3_ONLY,
    }

    /** Outcome of planning one save; the storage layer executes it verbatim. */
    data class WriteDecision(
        /** Null when only the legacy row should be written. */
        val row: PersistedWorkflowRowV3?,
        /** Whether the legacy row must be written too. */
        val writeLegacyRow: Boolean,
        /** Typed, non-fatal degradation notes (empty on the happy path). */
        val warnings: List<String>,
    )

    /**
     * Plans one save. Deterministic and side-effect free: same inputs, same
     * decision. [storedAtEpochMs] comes from the caller (storage layer owns
     * clocks); pass it through unchanged when executing the decision.
     */
    fun planWrite(
        automation: Automation,
        mode: WriteMode = WriteMode.DUAL_WRITE_V3_PRIMARY,
        revision: Long = 1L,
        legacyMigrationComplete: Boolean = false,
        storedAtEpochMs: Long,
    ): WriteDecision {
        require(revision >= 1L) { "revision must be >= 1" }
        if (mode == WriteMode.V3_ONLY && !legacyMigrationComplete) {
            throw IllegalArgumentException(
                "V3_ONLY writes require legacyMigrationComplete; refusing to drop the rollback path",
            )
        }
        if (mode == WriteMode.LEGACY_ONLY) {
            return WriteDecision(row = null, writeLegacyRow = true, warnings = emptyList())
        }

        return try {
            val document = with(WorkflowDocumentMappers) {
                automation.toDocument(revision = revision)
            }
            val snapshot = LegacyAutomationSnapshot(
                automationJson = WorkflowDocumentMappers.json.encodeToString(
                    Automation.serializer(),
                    automation,
                ),
                deepLinkToken = automation.deepLinkToken,
            )
            WriteDecision(
                row = PersistedWorkflowRowV3(
                    documentJson = WorkflowDocumentMappers.json.encodeToString(
                        WorkflowDocumentV1.serializer(),
                        document,
                    ),
                    documentRevision = revision,
                    documentSchemaVersion = WorkflowDocumentV1.SCHEMA_VERSION,
                    legacySnapshotJson = WorkflowDocumentMappers.json.encodeToString(
                        LegacyAutomationSnapshot.serializer(),
                        snapshot,
                    ),
                    legacyWorkflowVersion = automation.workflowVersion,
                    storedAtEpochMs = storedAtEpochMs,
                ),
                writeLegacyRow = mode == WriteMode.DUAL_WRITE_V3_PRIMARY,
                warnings = emptyList(),
            )
        } catch (failure: IllegalArgumentException) {
            // V3-write preparation failed (unmappable graph, encode bug, …):
            // degrade to the legacy row so the user edit still lands. V3_ONLY
            // never degrades — fail closed instead of pretending the V3 row
            // exists.
            check(mode != WriteMode.V3_ONLY) {
                "V3 write failed (${failure.message}); refusing to degrade below V3_ONLY"
            }
            WriteDecision(
                row = null,
                writeLegacyRow = true,
                warnings = listOf("v3_write_failed_fell_back_to_legacy_row_only"),
            )
        }
    }

    /** Why a V3 row could not serve the read; every reason is actionable. */
    enum class ReadFallbackReason {
        /** The V3 row carries no document payload (should never persist). */
        DOCUMENT_MISSING,

        /** Document JSON did not decode as [WorkflowDocumentV1]. */
        DOCUMENT_UNPARSABLE,

        /** The document decoded but was rejected by the mapper contract. */
        DOCUMENT_REJECTED,

        /** The legacy snapshot itself is corrupt — repair path required. */
        LEGACY_SNAPSHOT_UNPARSABLE,
    }

    /** Outcome of reading storage for one workflow id. */
    sealed interface StorageReadResult {
        /** No V3 row: read the legacy row instead (the default story). */
        data object LegacyRowNotFound : StorageReadResult

        /** V3 row served the read; [automation] is authoritative. */
        data class V3Row(
            val row: PersistedWorkflowRowV3,
            val automation: Automation,
        ) : StorageReadResult

        /**
         * V3 row exists but cannot serve the read: the storage layer must
         * fall back to its legacy row (or surface a repair error when the
         * snapshot is also unreadable). Never fabricated defaults.
         */
        data class FallbackNeeded(
            val reasons: List<ReadFallbackReason>,
        ) : StorageReadResult
    }

    /**
     * Plans one read from a V3 row. Null means "no V3 row stored" — the
     * caller reads its legacy row. The document is authoritative; the legacy
     * snapshot contributes only unmodeled fields (today: `deepLinkToken`).
     */
    fun planRead(row: PersistedWorkflowRowV3?): StorageReadResult {
        if (row == null) return StorageReadResult.LegacyRowNotFound

        val reasons = mutableListOf<ReadFallbackReason>()
        if (row.documentJson.isBlank()) reasons += ReadFallbackReason.DOCUMENT_MISSING

        val document = if (ReadFallbackReason.DOCUMENT_MISSING in reasons) {
            null
        } else {
            runCatching {
                WorkflowDocumentMappers.json.decodeFromString(
                    WorkflowDocumentV1.serializer(),
                    row.documentJson,
                )
            }.getOrNull()
        }
        if (document == null && ReadFallbackReason.DOCUMENT_MISSING !in reasons) {
            reasons += ReadFallbackReason.DOCUMENT_UNPARSABLE
        }

        val legacy = runCatching {
            WorkflowDocumentMappers.json.decodeFromString(
                LegacyAutomationSnapshot.serializer(),
                row.legacySnapshotJson,
            )
        }.getOrNull()
        if (legacy == null) {
            reasons += ReadFallbackReason.LEGACY_SNAPSHOT_UNPARSABLE
            return StorageReadResult.FallbackNeeded(reasons)
        }

        val legacyAutomation = runCatching {
            WorkflowDocumentMappers.json
                .decodeFromString(Automation.serializer(), legacy.automationJson)
                .copy(deepLinkToken = legacy.deepLinkToken)
        }.getOrNull()
        if (legacyAutomation == null) {
            reasons += ReadFallbackReason.LEGACY_SNAPSHOT_UNPARSABLE
            return StorageReadResult.FallbackNeeded(reasons)
        }

        if (document == null) return StorageReadResult.FallbackNeeded(reasons)

        val fromDocument = try {
            with(WorkflowDocumentMappers) { document.toAutomation() }
        } catch (_: IllegalArgumentException) {
            null
        } ?: return StorageReadResult.FallbackNeeded(
            reasons + ReadFallbackReason.DOCUMENT_REJECTED,
        )

        // The document does not model deepLinkToken (it must never leak into
        // exports); the legacy snapshot is its only carrier.
        return StorageReadResult.V3Row(
            row = row,
            automation = fromDocument.copy(deepLinkToken = legacyAutomation.deepLinkToken),
        )
    }
}
