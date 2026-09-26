package com.nexaflow.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.automationcontrol.AutomationMutationCommitRequest
import com.nexaflow.core.automationcontrol.AutomationMutationContext
import com.nexaflow.core.automationcontrol.AutomationMutationKind
import com.nexaflow.core.automationcontrol.AutomationMutationOrigin
import com.nexaflow.core.automationcontrol.AutomationPersistenceResult
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
