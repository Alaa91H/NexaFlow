package com.nexaflow.domain.workflow

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T27 — Persistence policy tests: dual-read / V3-write contracts, fail-closed
 * degradation paths, and the lossless write→read round trip.
 */
class WorkflowPersistencePolicyTest {

    private fun automation(
        id: String = "task-1",
        deepLinkToken: String? = "token-abc-123",
    ): Automation = Automation(
        id = id,
        name = "Night Wi-Fi",
        description = "Turn Wi-Fi on at night",
        icon = "wifi",
        iconColor = 0xFF123456,
        backgroundColor = 0xFF654321,
        category = "connectivity",
        priority = 9,
        enabled = true,
        showToastOnToggle = false,
        triggers = listOf(
            Trigger(type = TriggerType.TIME, config = mapOf("start" to "22:00", "end" to "07:00")),
        ),
        actions = listOf(
            Action(type = ActionType.SYSTEM_WIFI, config = mapOf("enabled" to "true")),
            Action(
                type = ActionType.SYSTEM_BRIGHTNESS,
                config = mapOf("value" to "120"),
                endBehavior = EndBehavior(
                    mode = EndMode.SET_VALUE,
                    config = mapOf("mode" to "SET_VALUE", "value" to "80"),
                ),
            ),
        ),
        triggerMatch = TriggerMatchMode.ALL,
        constraints = listOf(
            Constraint(
                type = ConstraintType.BATTERY,
                config = mapOf("direction" to "ABOVE", "level" to "50"),
            ),
        ),
        exitActions = listOf(
            Action(
                type = ActionType.SYSTEM_WIFI,
                config = mapOf("enabled" to "false"),
                endBehavior = EndBehavior(mode = EndMode.LEAVE),
            ),
        ),
        revertOnExit = false,
        cooldownSeconds = 30,
        createdAt = 1_000L,
        updatedAt = 2_000L,
        deepLinkToken = deepLinkToken,
    )

    // ------------------------------------------------------------------
    // Write policy
    // ------------------------------------------------------------------

    @Test
    fun dualWriteIsTheDefaultAndCarriesBothPayloads() {
        val decision = WorkflowPersistencePolicy.planWrite(
            automation = automation(),
            storedAtEpochMs = 42_000L,
        )

        assertTrue(decision.writeLegacyRow)
        assertTrue(decision.warnings.isEmpty())
        val row = decision.row!!
        assertEquals(1L, row.documentRevision)
        assertEquals(42_000L, row.storedAtEpochMs)
        assertEquals(WorkflowPersistencePolicy.POLICY_VERSION, row.policyVersion)
        assertEquals(WorkflowDocumentV1.SCHEMA_VERSION, row.documentSchemaVersion)

        val document = WorkflowDocumentMappers.json.decodeFromString(
            WorkflowDocumentV1.serializer(),
            row.documentJson,
        )
        assertEquals("task-1", document.id)

        val snapshot = WorkflowDocumentMappers.json.decodeFromString(
            WorkflowPersistencePolicy.LegacyAutomationSnapshot.serializer(),
            row.legacySnapshotJson,
        )
        assertEquals("token-abc-123", snapshot.deepLinkToken)
        assertTrue(snapshot.automationJson.contains("Night Wi-Fi"))
    }

    @Test
    fun legacyOnlyModeWritesNoV3Row() {
        val decision = WorkflowPersistencePolicy.planWrite(
            automation = automation(),
            mode = WorkflowPersistencePolicy.WriteMode.LEGACY_ONLY,
            storedAtEpochMs = 1L,
        )

        assertNull(decision.row)
        assertTrue(decision.writeLegacyRow)
        assertTrue(decision.warnings.isEmpty())
    }

    @Test
    fun v3OnlyWithoutCompletedMigrationFailsClosed() {
        try {
            WorkflowPersistencePolicy.planWrite(
                automation = automation(),
                mode = WorkflowPersistencePolicy.WriteMode.V3_ONLY,
                legacyMigrationComplete = false,
                storedAtEpochMs = 1L,
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("legacyMigrationComplete"))
        }
    }

    @Test
    fun v3OnlyAfterCompletedMigrationDropsTheLegacyRow() {
        val decision = WorkflowPersistencePolicy.planWrite(
            automation = automation(),
            mode = WorkflowPersistencePolicy.WriteMode.V3_ONLY,
            legacyMigrationComplete = true,
            storedAtEpochMs = 7L,
        )

        assertTrue(decision.row != null)
        assertFalse(decision.writeLegacyRow)
    }

    @Test
    fun writePlanningIsDeterministic() {
        val first = WorkflowPersistencePolicy.planWrite(
            automation = automation(),
            storedAtEpochMs = 99L,
        )
        val second = WorkflowPersistencePolicy.planWrite(
            automation = automation(),
            storedAtEpochMs = 99L,
        )

        assertEquals(first, second)
    }

    @Test
    fun v3WritePreparationDegradesToLegacyOnlyWithATypedWarning() {
        // A linear-run refusal (nested graph is not representable) is the
        // mapper's documented failure mode; any IllegalArgumentException in
        // preparation must degrade, never abort the save.
        val unmappable = automation(id = "\t")

        val decision = WorkflowPersistencePolicy.planWrite(
            automation = unmappable,
            storedAtEpochMs = 1L,
        )

        assertNull(decision.row)
        assertTrue(decision.writeLegacyRow)
        assertEquals(
            listOf("v3_write_failed_fell_back_to_legacy_row_only"),
            decision.warnings,
        )
    }

    // ------------------------------------------------------------------
    // Read policy
    // ------------------------------------------------------------------

    @Test
    fun readWithoutAV3RowRequestsTheLegacyPath() {
        assertEquals(
            WorkflowPersistencePolicy.StorageReadResult.LegacyRowNotFound,
            WorkflowPersistencePolicy.planRead(row = null),
        )
    }

    @Test
    fun readServesTheAuthoritativeDocumentWithUnmodeledFieldsMerged() {
        val row = WorkflowPersistencePolicy.planWrite(
            automation = automation(),
            storedAtEpochMs = 5L,
        ).row!!

        val result = WorkflowPersistencePolicy.planRead(row) as
            WorkflowPersistencePolicy.StorageReadResult.V3Row

        // The document is authoritative: every modeled field comes from it.
        assertEquals("Night Wi-Fi", result.automation.name)
        assertEquals(TriggerMatchMode.ALL, result.automation.triggerMatch)
        assertEquals(30, result.automation.cooldownSeconds)
        // deepLinkToken is modeled nowhere in the document; the snapshot
        // carries it and the merge restores it.
        assertEquals("token-abc-123", result.automation.deepLinkToken)
    }

    @Test
    fun writeReadRoundTripIsLossless() {
        val original = automation()

        val row = WorkflowPersistencePolicy.planWrite(
            automation = original,
            storedAtEpochMs = 5L,
        ).row!!
        val result = WorkflowPersistencePolicy.planRead(row) as
            WorkflowPersistencePolicy.StorageReadResult.V3Row

        assertEquals(original, result.automation)
    }

    @Test
    fun corruptDocumentFallsBackToTheLegacySnapshot() {
        val row = WorkflowPersistencePolicy.planWrite(
            automation = automation(),
            storedAtEpochMs = 5L,
        ).row!!.copy(documentJson = "{not json")

        val result = WorkflowPersistencePolicy.planRead(row) as
            WorkflowPersistencePolicy.StorageReadResult.FallbackNeeded

        assertTrue(
            WorkflowPersistencePolicy.ReadFallbackReason.DOCUMENT_UNPARSABLE in
                result.reasons,
        )
    }

    @Test
    fun futureDocumentVersionIsATypedFallbackNotACrash() {
        val row = WorkflowPersistencePolicy.planWrite(
            automation = automation(),
            storedAtEpochMs = 5L,
        ).row!!.copy(documentJson = "{\"schemaVersion\":99,\"id\":\"x\"}")

        val result = WorkflowPersistencePolicy.planRead(row) as
            WorkflowPersistencePolicy.StorageReadResult.FallbackNeeded

        assertTrue(
            WorkflowPersistencePolicy.ReadFallbackReason.DOCUMENT_UNPARSABLE in
                result.reasons,
        )
    }

    @Test
    fun corruptLegacySnapshotIsUnrepairableAndFailClosed() {
        val row = WorkflowPersistencePolicy.planWrite(
            automation = automation(),
            storedAtEpochMs = 5L,
        ).row!!.copy(legacySnapshotJson = "{broken")

        val result = WorkflowPersistencePolicy.planRead(row) as
            WorkflowPersistencePolicy.StorageReadResult.FallbackNeeded

        assertTrue(
            WorkflowPersistencePolicy.ReadFallbackReason.LEGACY_SNAPSHOT_UNPARSABLE in
                result.reasons,
        )
    }

    @Test
    fun deepLinkTokenNeverRidesTheDocumentJson() {
        val row = WorkflowPersistencePolicy.planWrite(
            automation = automation(),
            storedAtEpochMs = 5L,
        ).row!!

        // The secret-capable token exists only inside the legacy snapshot;
        // the document (export/authoring surface) never carries it.
        assertFalse(row.documentJson.contains("deepLinkToken"))
        assertFalse(row.documentJson.contains("token-abc-123"))
        assertTrue(row.legacySnapshotJson.contains("token-abc-123"))
    }
}
