package com.nexaflow.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.automationcontrol.AutomationBatchResult
import com.nexaflow.core.automationcontrol.AutomationMutationCommitRequest
import com.nexaflow.core.automationcontrol.AutomationMutationContext
import com.nexaflow.core.automationcontrol.AutomationMutationKind
import com.nexaflow.core.automationcontrol.AutomationMutationOrigin
import com.nexaflow.core.automationcontrol.AutomationPersistenceResult
import com.nexaflow.core.database.AgentAuditEntity
import com.nexaflow.core.database.AppDatabase
import com.nexaflow.data.mapper.toDomain
import com.nexaflow.data.mapper.toEntity
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.MaintenanceKind
import com.nexaflow.domain.models.MaintenanceProfile
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomAutomationMutationPersistenceTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createAtomicallyStoresDefinitionProvenanceAuditAndHashedIdempotency() = runTest {
        val persistence = persistence()
        val candidate = automation(id = "created", name = "Agent task", updatedAt = 100L)

        val result = persistence.commit(
            request(
                kind = AutomationMutationKind.CREATE,
                automation = candidate,
                idempotencyKey = "caller-secret-key",
                fingerprint = "fingerprint-1",
                occurredAt = 100L
            )
        )

        assertEquals(
            AutomationPersistenceResult.Committed("created", 1L),
            result
        )
        assertEquals(
            "Agent task",
            database.automationDao().getAutomationById("created")?.toDomain()?.name
        )

        val metadata = requireNotNull(
            database.agentPlatformDao().getAutomationMetadata("created")
        )
        assertEquals("AGENT", metadata.origin)
        assertEquals("agent:test", metadata.creatorActorId)
        assertEquals("agent.test", metadata.creatorAgentId)
        assertEquals(1L, metadata.revision)
        assertEquals(100L, metadata.definitionUpdatedAt)

        val audit = database.agentPlatformDao().latestAudit(10)
        assertEquals(1, audit.size)
        assertEquals("TASK_CREATED", audit.single().eventType)
        assertFalse(audit.single().detailsJson.orEmpty().contains("caller-secret-key"))

        val idempotency = database.agentPlatformDao()
            .idempotencyForActor("agent:test", 10)
            .single()
        assertNotEquals("caller-secret-key", idempotency.keyHash)
        assertFalse(idempotency.keyHash.contains("caller-secret-key"))
        assertEquals("fingerprint-1", idempotency.requestFingerprint)
    }

    @Test
    fun storedCreateIdempotencyResolvesWithoutKnowingGeneratedAutomationId() = runTest {
        val persistence = persistence()
        val original = automation(id = "generated-id", name = "Created", updatedAt = 100L)
        val createRequest = request(
            kind = AutomationMutationKind.CREATE,
            automation = original,
            idempotencyKey = "create-key",
            fingerprint = "create-fingerprint",
            occurredAt = 100L
        )
        assertEquals(
            AutomationPersistenceResult.Committed("generated-id", 1L),
            persistence.commit(createRequest)
        )

        val replay = persistence.resolveStoredIdempotency(
            context = createRequest.context,
            kind = AutomationMutationKind.CREATE,
            automationId = null,
            requestFingerprint = "create-fingerprint",
            occurredAt = 120L
        )

        assertEquals(
            AutomationPersistenceResult.IdempotentReplay("generated-id", 1L),
            replay
        )
        assertEquals(
            "IDEMPOTENCY_REPLAY",
            database.agentPlatformDao().latestAudit(1).single().eventType
        )
    }

    @Test
    fun storedDeleteIdempotencyCanBeResolvedAfterDefinitionRemoval() = runTest {
        val persistence = persistence()
        val original = automation(id = "delete-replay", name = "Delete", updatedAt = 100L)
        persistence.commit(
            request(
                kind = AutomationMutationKind.CREATE,
                automation = original,
                occurredAt = 100L
            )
        )
        val deleteRequest = request(
            kind = AutomationMutationKind.DELETE,
            automation = original,
            expectedRevision = 1L,
            baseDefinitionUpdatedAt = 100L,
            idempotencyKey = "delete-key",
            fingerprint = "delete-fingerprint",
            occurredAt = 200L
        )
        assertEquals(
            AutomationPersistenceResult.Committed("delete-replay", 2L),
            persistence.commit(deleteRequest)
        )
        assertTrue(database.automationDao().getAutomationById("delete-replay") == null)

        val replay = persistence.resolveStoredIdempotency(
            context = deleteRequest.context,
            kind = AutomationMutationKind.DELETE,
            automationId = "delete-replay",
            requestFingerprint = "delete-fingerprint",
            occurredAt = 250L
        )
        assertEquals(
            AutomationPersistenceResult.IdempotentReplay("delete-replay", 2L),
            replay
        )

        val conflict = persistence.resolveStoredIdempotency(
            context = deleteRequest.context,
            kind = AutomationMutationKind.DELETE,
            automationId = "delete-replay",
            requestFingerprint = "different-fingerprint",
            occurredAt = 250L
        )
        assertEquals(AutomationPersistenceResult.IdempotencyConflict, conflict)
        assertEquals(
            "IDEMPOTENCY_CONFLICT",
            database.agentPlatformDao().latestAudit(1).single().eventType
        )
    }

    @Test
    fun sameIdempotencyKeyReplaysSameRequestAndRejectsDifferentRequest() = runTest {
        val persistence = persistence()
        val candidate = automation(id = "created", name = "Original", updatedAt = 100L)
        val original = request(
            kind = AutomationMutationKind.CREATE,
            automation = candidate,
            idempotencyKey = "same-key",
            fingerprint = "same-fingerprint",
            occurredAt = 100L
        )

        assertTrue(persistence.commit(original) is AutomationPersistenceResult.Committed)

        val replay = persistence.commit(
            original.copy(
                automation = candidate.copy(name = "Ignored replay"),
                occurredAt = 200L
            )
        )
        assertEquals(
            AutomationPersistenceResult.IdempotentReplay("created", 1L),
            replay
        )

        val conflict = persistence.commit(
            original.copy(
                requestFingerprint = "different-fingerprint",
                occurredAt = 300L
            )
        )
        assertEquals(AutomationPersistenceResult.IdempotencyConflict, conflict)
        assertEquals(
            "Original",
            database.automationDao().getAutomationById("created")?.name
        )
    }

    @Test
    fun revisionCompareAndSetPreventsStaleAgentOverwrite() = runTest {
        val persistence = persistence()
        val original = automation(id = "task", name = "Original", updatedAt = 100L)
        persistence.commit(
            request(
                kind = AutomationMutationKind.CREATE,
                automation = original,
                occurredAt = 100L
            )
        )

        val updated = original.copy(name = "Revision two", updatedAt = 200L)
        val updateResult = persistence.commit(
            request(
                kind = AutomationMutationKind.UPDATE,
                automation = updated,
                expectedRevision = 1L,
                baseDefinitionUpdatedAt = 100L,
                occurredAt = 200L
            )
        )
        assertEquals(
            AutomationPersistenceResult.Committed("task", 2L),
            updateResult
        )

        val stale = persistence.commit(
            request(
                kind = AutomationMutationKind.UPDATE,
                automation = updated.copy(name = "Stale", updatedAt = 300L),
                expectedRevision = 1L,
                baseDefinitionUpdatedAt = 200L,
                occurredAt = 300L
            )
        )
        assertEquals(
            AutomationPersistenceResult.RevisionConflict(2L),
            stale
        )
        assertEquals(
            "Revision two",
            database.automationDao().getAutomationById("task")?.name
        )
    }

    @Test
    fun outOfBandDefinitionEditAdvancesRevisionAndRejectsPreflightRace() = runTest {
        val persistence = persistence()
        val original = automation(id = "task", name = "Original", updatedAt = 100L)
        persistence.commit(
            request(
                kind = AutomationMutationKind.CREATE,
                automation = original,
                occurredAt = 100L
            )
        )

        database.automationDao().insertAutomation(
            original.copy(name = "Human edit", updatedAt = 150L).toEntity()
        )

        val result = persistence.commit(
            request(
                kind = AutomationMutationKind.UPDATE,
                automation = original.copy(name = "Agent stale", updatedAt = 200L),
                expectedRevision = 1L,
                baseDefinitionUpdatedAt = 100L,
                occurredAt = 200L
            )
        )

        assertEquals(
            AutomationPersistenceResult.RevisionConflict(2L),
            result
        )
        val metadata = requireNotNull(
            database.agentPlatformDao().getAutomationMetadata("task")
        )
        assertEquals(2L, metadata.revision)
        assertEquals(150L, metadata.definitionUpdatedAt)
        assertEquals(
            "Human edit",
            database.automationDao().getAutomationById("task")?.name
        )
    }

    @Test
    fun transactionRejectsNewCircularDependency() = runTest {
        val persistence = persistence()
        val first = automation(id = "a", name = "A", updatedAt = 100L)
        val second = automation(id = "b", name = "B", updatedAt = 110L)

        assertTrue(
            persistence.commit(
                request(
                    kind = AutomationMutationKind.CREATE,
                    automation = second,
                    occurredAt = 110L
                )
            ) is AutomationPersistenceResult.Committed
        )
        assertTrue(
            persistence.commit(
                request(
                    kind = AutomationMutationKind.CREATE,
                    automation = first.copy(
                        maintenanceProfile = MaintenanceProfile(
                            kind = MaintenanceKind.AUTOMATION,
                            dependencyAutomationIds = listOf("b")
                        )
                    ),
                    occurredAt = 120L
                )
            ) is AutomationPersistenceResult.Committed
        )

        val result = persistence.commit(
            request(
                kind = AutomationMutationKind.UPDATE,
                automation = second.copy(
                    updatedAt = 200L,
                    maintenanceProfile = MaintenanceProfile(
                        kind = MaintenanceKind.AUTOMATION,
                        dependencyAutomationIds = listOf("a")
                    )
                ),
                expectedRevision = 1L,
                baseDefinitionUpdatedAt = 110L,
                occurredAt = 200L
            )
        )

        assertTrue(result is AutomationPersistenceResult.DependencyConflict)
        assertTrue(
            database.automationDao()
                .getAutomationById("b")
                ?.toDomain()
                ?.maintenanceProfile
                ?.dependencyAutomationIds
                .orEmpty()
                .isEmpty()
        )
        assertEquals(
            1L,
            database.agentPlatformDao().getAutomationMetadata("b")?.revision
        )
    }

    @Test
    fun transactionRejectsDeleteWhenDependencyAppearedAfterPreflight() = runTest {
        val persistence = persistence()
        val target = automation(id = "target", name = "Target", updatedAt = 100L)
        val dependent = automation(
            id = "dependent",
            name = "Dependent",
            updatedAt = 110L
        ).copy(
            maintenanceProfile = MaintenanceProfile(
                kind = MaintenanceKind.AUTOMATION,
                dependencyAutomationIds = listOf("target")
            )
        )

        persistence.commit(
            request(
                kind = AutomationMutationKind.CREATE,
                automation = target,
                occurredAt = 100L
            )
        )
        persistence.commit(
            request(
                kind = AutomationMutationKind.CREATE,
                automation = dependent,
                occurredAt = 110L
            )
        )

        val result = persistence.commit(
            request(
                kind = AutomationMutationKind.DELETE,
                automation = target,
                expectedRevision = 1L,
                baseDefinitionUpdatedAt = 100L,
                occurredAt = 200L
            )
        )

        assertTrue(result is AutomationPersistenceResult.DependencyConflict)
        assertTrue(database.automationDao().getAutomationById("target") != null)
        assertEquals(
            1L,
            database.agentPlatformDao().getAutomationMetadata("target")?.revision
        )
    }

    @Test
    fun auditFailureRollsBackDefinitionMetadataAndIdempotency() = runTest {
        val persistence = persistence(auditIdGenerator = { "same-audit-id" })
        val original = automation(id = "task", name = "Original", updatedAt = 100L)
        persistence.commit(
            request(
                kind = AutomationMutationKind.CREATE,
                automation = original,
                occurredAt = 100L
            )
        )

        val failed = runCatching {
            persistence.commit(
                request(
                    kind = AutomationMutationKind.UPDATE,
                    automation = original.copy(name = "Must rollback", updatedAt = 200L),
                    expectedRevision = 1L,
                    baseDefinitionUpdatedAt = 100L,
                    idempotencyKey = "rollback-key",
                    fingerprint = "rollback-fingerprint",
                    occurredAt = 200L
                )
            )
        }

        assertTrue(failed.isFailure)
        assertEquals(
            "Original",
            database.automationDao().getAutomationById("task")?.name
        )
        assertEquals(
            1L,
            database.agentPlatformDao().getAutomationMetadata("task")?.revision
        )
        assertTrue(
            database.agentPlatformDao()
                .idempotencyForActor("agent:test", 10)
                .isEmpty()
        )
    }

    @Test
    fun auditRetentionPrunesExpiredRowsAndEnforcesNewestRowCap() = runTest {
        val dao = database.agentPlatformDao()
        listOf(
            AgentAuditEntity(
                id = "old",
                eventType = "OLD",
                outcome = "TEST",
                actorId = "agent:test",
                createdAt = 10L
            ),
            AgentAuditEntity(
                id = "mid",
                eventType = "MID",
                outcome = "TEST",
                actorId = "agent:test",
                createdAt = 80L
            ),
            AgentAuditEntity(
                id = "new",
                eventType = "NEW",
                outcome = "TEST",
                actorId = "agent:test",
                createdAt = 90L
            )
        ).forEach { dao.insertAudit(it) }

        val persistence = RoomAutomationMutationPersistence(
            database = database,
            automationDao = database.automationDao(),
            agentPlatformDao = dao,
            auditRetentionMs = 50L,
            maxAuditRows = 2
        )
        val result = persistence.commit(
            request(
                kind = AutomationMutationKind.CREATE,
                automation = automation("retention", "Retention", 100L),
                occurredAt = 100L
            )
        )

        assertTrue(result is AutomationPersistenceResult.Committed)
        val remaining = dao.latestAudit(10)
        assertEquals(2, remaining.size)
        assertEquals(setOf("TASK_CREATED", "NEW"), remaining.map { it.eventType }.toSet())
        assertTrue(remaining.none { it.id == "old" || it.eventType == "MID" })
    }

    @Test
    fun createConflictAuditsExistingRevisionWithoutOverwritingDefinition() = runTest {
        val persistence = persistence()
        val original = automation("same", "Original", 100L)
        assertTrue(
            persistence.commit(
                request(
                    kind = AutomationMutationKind.CREATE,
                    automation = original,
                    occurredAt = 100L
                )
            ) is AutomationPersistenceResult.Committed
        )

        val conflict = persistence.commit(
            request(
                kind = AutomationMutationKind.CREATE,
                automation = original.copy(name = "Must not replace", updatedAt = 200L),
                occurredAt = 200L
            )
        )

        assertEquals(AutomationPersistenceResult.RevisionConflict(1L), conflict)
        assertEquals("Original", database.automationDao().getAutomationById("same")?.name)
        assertEquals("REVISION_CONFLICT", database.agentPlatformDao().latestAudit(1).single().eventType)
    }

    @Test
    fun updateMissingAutomationReturnsNotFoundAndWritesAudit() = runTest {
        val persistence = persistence()
        val missing = automation("missing", "Missing", 100L)

        val result = persistence.commit(
            request(
                kind = AutomationMutationKind.UPDATE,
                automation = missing,
                expectedRevision = 1L,
                occurredAt = 100L
            )
        )

        assertEquals(AutomationPersistenceResult.NotFound, result)
        val audit = database.agentPlatformDao().latestAudit(1).single()
        assertEquals("MUTATION_NOT_FOUND", audit.eventType)
        assertEquals("NOT_FOUND", audit.outcome)
    }

    @Test
    fun enableAndDisableAdvanceRevisionAndProvenance() = runTest {
        val persistence = persistence()
        val original = automation("toggle", "Toggle", 100L)
        persistence.commit(
            request(
                kind = AutomationMutationKind.CREATE,
                automation = original,
                occurredAt = 100L
            )
        )

        val enabled = original.copy(enabled = true, updatedAt = 200L)
        assertEquals(
            AutomationPersistenceResult.Committed("toggle", 2L),
            persistence.commit(
                request(
                    kind = AutomationMutationKind.ENABLE,
                    automation = enabled,
                    expectedRevision = 1L,
                    baseDefinitionUpdatedAt = 100L,
                    occurredAt = 200L
                )
            )
        )
        assertTrue(database.automationDao().getAutomationById("toggle")?.enabled == true)

        val disabled = enabled.copy(enabled = false, updatedAt = 300L)
        assertEquals(
            AutomationPersistenceResult.Committed("toggle", 3L),
            persistence.commit(
                request(
                    kind = AutomationMutationKind.DISABLE,
                    automation = disabled,
                    expectedRevision = 2L,
                    baseDefinitionUpdatedAt = 200L,
                    occurredAt = 300L
                )
            )
        )
        val metadata = requireNotNull(database.agentPlatformDao().getAutomationMetadata("toggle"))
        assertEquals(3L, metadata.revision)
        assertEquals(300L, metadata.definitionUpdatedAt)
        val events = database.agentPlatformDao().latestAudit(10).map { it.eventType }.toSet()
        assertTrue("TASK_ENABLED" in events)
        assertTrue("TASK_DISABLED" in events)
    }

    @Test
    fun reserveIdempotencyIsAtomicAndPreservesFirstOwner() = runTest {
        val first = com.nexaflow.core.database.AgentIdempotencyEntity(
            actorId = "agent:test",
            keyHash = "hash",
            requestFingerprint = "run:a:1",
            operation = "RUN",
            automationId = "a",
            resultRevision = 1L,
            createdAt = 100L,
            expiresAt = 200L
        )
        val duplicate = first.copy(
            requestFingerprint = "run:b:1",
            automationId = "b"
        )

        assertNotEquals(-1L, database.agentPlatformDao().reserveIdempotency(first))
        assertEquals(-1L, database.agentPlatformDao().reserveIdempotency(duplicate))
        assertEquals(
            first,
            database.agentPlatformDao().getIdempotency("agent:test", "hash")
        )
    }

    @Test
    fun deleteRemovesDefinitionAndMetadataAndRecordsIdempotency() = runTest {
        val persistence = persistence()
        val original = automation("delete-me", "Delete", 100L)
        persistence.commit(
            request(
                kind = AutomationMutationKind.CREATE,
                automation = original,
                occurredAt = 100L
            )
        )

        val result = persistence.commit(
            request(
                kind = AutomationMutationKind.DELETE,
                automation = original,
                expectedRevision = 1L,
                baseDefinitionUpdatedAt = 100L,
                idempotencyKey = "delete-once",
                fingerprint = "delete-fingerprint",
                occurredAt = 200L
            )
        )

        assertEquals(AutomationPersistenceResult.Committed("delete-me", 2L), result)
        assertTrue(database.automationDao().getAutomationById("delete-me") == null)
        assertTrue(database.agentPlatformDao().getAutomationMetadata("delete-me") == null)
        val idempotency = database.agentPlatformDao()
            .idempotencyForActor("agent:test", 10)
            .single()
        assertEquals("delete-me", idempotency.automationId)
        assertEquals(2L, idempotency.resultRevision)
        assertEquals("DELETE", idempotency.operation)
    }

    @Test
    fun expiredIdempotencyIsPrunedBeforeAKeyCanBeReused() = runTest {
        val persistence = RoomAutomationMutationPersistence(
            database = database,
            automationDao = database.automationDao(),
            agentPlatformDao = database.agentPlatformDao(),
            idempotencyRetentionMs = 50L
        )
        val original = automation("reuse", "First", 100L)
        assertTrue(
            persistence.commit(
                request(
                    kind = AutomationMutationKind.CREATE,
                    automation = original,
                    idempotencyKey = "reusable",
                    fingerprint = "first",
                    occurredAt = 100L
                )
            ) is AutomationPersistenceResult.Committed
        )

        val updated = original.copy(name = "Second", updatedAt = 200L)
        val result = persistence.commit(
            request(
                kind = AutomationMutationKind.UPDATE,
                automation = updated,
                expectedRevision = 1L,
                baseDefinitionUpdatedAt = 100L,
                idempotencyKey = "reusable",
                fingerprint = "second",
                occurredAt = 200L
            )
        )

        assertEquals(AutomationPersistenceResult.Committed("reuse", 2L), result)
        assertEquals("Second", database.automationDao().getAutomationById("reuse")?.name)
    }

    @Test
    fun invalidMutationContextIsRejectedBeforeAnyWrite() = runTest {
        val persistence = persistence()
        val candidate = automation("invalid", "Invalid", 100L)
        val bad = request(
            kind = AutomationMutationKind.CREATE,
            automation = candidate,
            occurredAt = 100L
        ).copy(
            context = AutomationMutationContext(
                actorId = "contains spaces",
                origin = AutomationMutationOrigin.AGENT,
                transport = "TEST"
            )
        )

        val result = runCatching { persistence.commit(bad) }

        assertTrue(result.isFailure)
        assertTrue(database.automationDao().getAutomationById("invalid") == null)
        assertTrue(database.agentPlatformDao().latestAudit(10).isEmpty())
    }

    @Test
    fun earlyReplayLookupRejectsOversizedMetadataWithoutWritingAudit() = runTest {
        val persistence = persistence()
        val context = AutomationMutationContext(
            actorId = "agent:test",
            origin = AutomationMutationOrigin.AGENT,
            transport = "x".repeat(257),
            idempotencyKey = "lookup-key"
        )

        val result = runCatching {
            persistence.resolveStoredIdempotency(
                context = context,
                kind = AutomationMutationKind.CREATE,
                automationId = null,
                requestFingerprint = "fingerprint",
                occurredAt = 100L
            )
        }

        assertTrue(result.isFailure)
        assertTrue(database.agentPlatformDao().latestAudit(10).isEmpty())
    }

    @Test
    fun blankIdempotencyKeyIsRejectedTransactionally() = runTest {
        val persistence = persistence()
        val candidate = automation("blank-key", "Invalid", 100L)

        val result = runCatching {
            persistence.commit(
                request(
                    kind = AutomationMutationKind.CREATE,
                    automation = candidate,
                    idempotencyKey = " ",
                    occurredAt = 100L
                )
            )
        }

        assertTrue(result.isFailure)
        assertTrue(database.automationDao().getAutomationById("blank-key") == null)
        assertTrue(database.agentPlatformDao().latestAudit(10).isEmpty())
    }

    @Test
    fun batchCreateCommitsEveryDefinitionInOneTransaction() = runTest {
        val persistence = persistence()

        val result = persistence.commitBatch(
            listOf(
                request(
                    kind = AutomationMutationKind.CREATE,
                    automation = automation("batch-a", "Batch A", 100L),
                    fingerprint = "fp-a",
                    occurredAt = 100L
                ),
                request(
                    kind = AutomationMutationKind.CREATE,
                    automation = automation("batch-b", "Batch B", 100L),
                    fingerprint = "fp-b",
                    occurredAt = 100L
                )
            )
        )

        assertTrue(result is AutomationBatchResult.AllCommitted)
        assertEquals(
            listOf("batch-a", "batch-b"),
            (result as AutomationBatchResult.AllCommitted).commits.map { it.automationId }
        )
        assertEquals(
            "Batch A",
            database.automationDao().getAutomationById("batch-a")?.toDomain()?.name
        )
        assertEquals(
            "Batch B",
            database.automationDao().getAutomationById("batch-b")?.toDomain()?.name
        )
    }

    @Test
    fun batchCreateRollsBackEntirelyWhenOneItemCollides() = runTest {
        val persistence = persistence()
        persistence.commit(
            request(
                kind = AutomationMutationKind.CREATE,
                automation = automation("existing", "Existing", 100L),
                fingerprint = "fp-existing",
                occurredAt = 100L
            )
        )

        val result = persistence.commitBatch(
            listOf(
                request(
                    kind = AutomationMutationKind.CREATE,
                    automation = automation("batch-new", "Batch New", 200L),
                    fingerprint = "fp-new",
                    occurredAt = 200L
                ),
                request(
                    kind = AutomationMutationKind.CREATE,
                    automation = automation("existing", "Collision", 200L),
                    fingerprint = "fp-collision",
                    occurredAt = 200L
                )
            )
        )

        assertTrue(result is AutomationBatchResult.Aborted)
        val failures = (result as AutomationBatchResult.Aborted).failures
        assertEquals(listOf("existing"), failures.map { it.automationId })
        assertTrue(
            "rolled-back batch must not leave partial rows",
            database.automationDao().getAutomationById("batch-new") == null
        )
        assertEquals(
            "Existing",
            database.automationDao().getAutomationById("existing")?.toDomain()?.name
        )
    }

    private fun persistence(
        auditIdGenerator: () -> String = { java.util.UUID.randomUUID().toString() }
    ) = RoomAutomationMutationPersistence(
        database = database,
        automationDao = database.automationDao(),
        agentPlatformDao = database.agentPlatformDao(),
        auditIdGenerator = auditIdGenerator
    )

    private fun request(
        kind: AutomationMutationKind,
        automation: Automation,
        expectedRevision: Long? = null,
        baseDefinitionUpdatedAt: Long? = null,
        idempotencyKey: String? = null,
        fingerprint: String = "fingerprint",
        occurredAt: Long
    ) = AutomationMutationCommitRequest(
        kind = kind,
        automation = automation,
        context = AutomationMutationContext(
            actorId = "agent:test",
            origin = AutomationMutationOrigin.AGENT,
            expectedRevision = expectedRevision,
            agentId = "agent.test",
            providerId = "provider.test",
            modelId = "model.test",
            transport = "TEST",
            requestId = "request-$occurredAt",
            conversationId = "conversation.test",
            idempotencyKey = idempotencyKey
        ),
        baseDefinitionUpdatedAt = baseDefinitionUpdatedAt,
        requestFingerprint = fingerprint,
        occurredAt = occurredAt
    )

    private fun automation(
        id: String,
        name: String,
        updatedAt: Long
    ) = Automation(
        id = id,
        name = name,
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "custom",
        priority = 1,
        enabled = false,
        triggers = emptyList(),
        actions = emptyList(),
        cooldownSeconds = 0,
        createdAt = 50L,
        updatedAt = updatedAt
    )
}
