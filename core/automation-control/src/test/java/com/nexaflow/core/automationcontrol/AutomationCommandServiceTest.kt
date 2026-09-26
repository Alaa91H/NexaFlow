package com.nexaflow.core.automationcontrol

import com.nexaflow.core.automationcontrol.api.AgentTaskDraftV1
import com.nexaflow.core.execution.dryrun.WorkflowDryRunReport
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.workflow.WorkflowValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationCommandServiceTest {

    @Test
    fun createRejectsInvalidDraftBeforePersistence() = runTest {
        val repository = FakeAutomationRepository()
        val persistence = RecordingPersistence(repository)
        val service = service(repository, persistence)

        val result = service.create(
            draft = AgentTaskDraftV1(name = ""),
            context = agentContext()
        )

        assertTrue(result is AutomationMutationResult.Rejected)
        assertEquals(0, persistence.commitCount)
        assertTrue(repository.current().isEmpty())
    }

    @Test
    fun createPersistsOnlyThroughTransactionalBoundaryAfterPreflight() = runTest {
        val repository = FakeAutomationRepository()
        val persistence = RecordingPersistence(repository)
        val service = service(repository, persistence)

        val result = service.create(
            draft = AgentTaskDraftV1(name = "Agent task"),
            context = agentContext(idempotencyKey = "create-1")
        )

        assertTrue(result is AutomationMutationResult.Success)
        assertEquals(1, persistence.commitCount)
        assertEquals(AutomationMutationKind.CREATE, persistence.lastRequest?.kind)
        assertEquals("create-1", persistence.lastRequest?.context?.idempotencyKey)
        assertEquals(1, repository.current().size)
        assertEquals("Agent task", repository.current().single().name)
        assertEquals(1L, (result as AutomationMutationResult.Success).revision)
    }

    @Test
    fun updateMapsTransactionalRevisionConflictWithoutWriting() = runTest {
        val existing = automation(updatedAt = 50L)
        val repository = FakeAutomationRepository(listOf(existing))
        val persistence = RecordingPersistence(
            repository = repository,
            forcedResult = AutomationPersistenceResult.RevisionConflict(7L)
        )
        val service = service(repository, persistence)

        val result = service.update(
            automationId = existing.id,
            draft = AgentTaskDraftV1(name = "Should not persist"),
            context = agentContext(expectedRevision = 6L)
        )

        assertTrue(result is AutomationMutationResult.Conflict)
        assertEquals(7L, (result as AutomationMutationResult.Conflict).currentRevision)
        assertEquals("Existing", repository.current().single().name)
        assertEquals(50L, repository.current().single().updatedAt)
    }

    @Test
    fun deleteRetryCanReplayAfterDefinitionWasAlreadyRemoved() = runTest {
        val existing = automation(updatedAt = 50L)
        val repository = FakeAutomationRepository(listOf(existing))
        val persistence = RecordingPersistence(repository)
        val service = service(repository, persistence)
        val context = agentContext(expectedRevision = 1L, idempotencyKey = "delete-1")

        val first = service.delete(existing.id, context)
        assertTrue(first is AutomationMutationResult.Success)
        assertTrue(repository.current().isEmpty())

        persistence.storedReplay = AutomationPersistenceResult.IdempotentReplay(
            automationId = existing.id,
            revision = 2L
        )
        val replay = service.delete(existing.id, context)

        assertEquals(
            AutomationMutationResult.IdempotentReplay(existing.id, 2L),
            replay
        )
        assertEquals(1, persistence.commitCount)
        assertEquals(1, persistence.resolveCount)
    }

    @Test
    fun idempotentReplayIsReturnedWithoutSecondDefinitionWrite() = runTest {
        val repository = FakeAutomationRepository()
        val persistence = RecordingPersistence(
            repository = repository,
            forcedResult = AutomationPersistenceResult.IdempotentReplay(
                automationId = "original",
                revision = 3L
            )
        )
        val service = service(repository, persistence)

        val result = service.create(
            draft = AgentTaskDraftV1(name = "Replay"),
            context = agentContext(idempotencyKey = "same-key")
        )

        assertEquals(
            AutomationMutationResult.IdempotentReplay("original", 3L),
            result
        )
        assertTrue(repository.current().isEmpty())
    }

    private fun service(
        repository: AutomationRepository,
        persistence: AutomationMutationPersistence
    ) = AutomationCommandService(
        repository = repository,
        dryRunInspector = AutomationDryRunInspector {
            WorkflowDryRunReport(
                workflowValidation = WorkflowValidationResult(emptyList()),
                capabilityResolutions = emptyList(),
                executable = true,
                summary = "ok"
            )
        },
        mutationPersistence = persistence,
        clockMillis = { 100L },
        idGenerator = { "agent-generated-id" }
    )

    private fun agentContext(
        expectedRevision: Long? = null,
        idempotencyKey: String? = null
    ) = AutomationMutationContext(
        actorId = "agent:test",
        origin = AutomationMutationOrigin.AGENT,
        expectedRevision = expectedRevision,
        agentId = "agent.test",
        transport = "TEST",
        idempotencyKey = idempotencyKey
    )

    private fun automation(updatedAt: Long) = Automation(
        id = "existing-id",
        name = "Existing",
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
        createdAt = 10L,
        updatedAt = updatedAt
    )

    private class RecordingPersistence(
        private val repository: FakeAutomationRepository,
        private val forcedResult: AutomationPersistenceResult? = null
    ) : AutomationMutationPersistence {
        var storedReplay: AutomationPersistenceResult? = null
        var resolveCount: Int = 0
            private set
        var commitCount: Int = 0
            private set
        var lastRequest: AutomationMutationCommitRequest? = null
            private set

        override suspend fun commit(
            request: AutomationMutationCommitRequest
        ): AutomationPersistenceResult {
            commitCount += 1
            lastRequest = request
            forcedResult?.let { return it }

            return when (request.kind) {
                AutomationMutationKind.DELETE -> {
                    repository.deleteAutomation(request.automation)
                    AutomationPersistenceResult.Committed(
                        automationId = request.automation.id,
                        revision = 2L
                    )
                }
                else -> {
                    repository.saveAutomation(request.automation)
                    AutomationPersistenceResult.Committed(
                        automationId = request.automation.id,
                        revision = 1L
                    )
                }
            }
        }

        override suspend fun resolveStoredIdempotency(
            context: AutomationMutationContext,
            kind: AutomationMutationKind,
            automationId: String?,
            requestFingerprint: String,
            occurredAt: Long
        ): AutomationPersistenceResult? {
            resolveCount += 1
            return storedReplay
        }
    }

    private class FakeAutomationRepository(
        initial: List<Automation> = emptyList()
    ) : AutomationRepository {
        private val state = MutableStateFlow(initial)

        fun current(): List<Automation> = state.value

        override fun getAutomations(): Flow<List<Automation>> = state

        override suspend fun getAutomationById(id: String): Automation? =
            state.value.firstOrNull { it.id == id }

        override suspend fun saveAutomation(automation: Automation) {
            state.value = state.value.filterNot { it.id == automation.id } + automation
        }

        override suspend fun deleteAutomation(automation: Automation) {
            state.value = state.value.filterNot { it.id == automation.id }
        }

        override suspend fun updateAutomationStatus(id: String, enabled: Boolean) {
            state.value = state.value.map { automation ->
                if (automation.id == id) automation.copy(enabled = enabled) else automation
            }
        }
    }
}
