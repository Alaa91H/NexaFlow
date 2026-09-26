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
        val service = service(repository)

        val result = service.create(
            draft = AgentTaskDraftV1(name = ""),
            context = agentContext()
        )

        assertTrue(result is AutomationMutationResult.Rejected)
        assertTrue(repository.current().isEmpty())
    }

    @Test
    fun createPersistsOnlyAfterSuccessfulPreflight() = runTest {
        val repository = FakeAutomationRepository()
        val service = service(repository)

        val result = service.create(
            draft = AgentTaskDraftV1(name = "Agent task"),
            context = agentContext()
        )

        assertTrue(result is AutomationMutationResult.Success)
        assertEquals(1, repository.current().size)
        assertEquals("Agent task", repository.current().single().name)
    }

    @Test
    fun updateRejectsStaleRevisionWithoutWriting() = runTest {
        val existing = automation(updatedAt = 50L)
        val repository = FakeAutomationRepository(listOf(existing))
        val service = service(repository)

        val result = service.update(
            automationId = existing.id,
            draft = AgentTaskDraftV1(name = "Should not persist"),
            context = agentContext(expectedRevision = 49L)
        )

        assertTrue(result is AutomationMutationResult.Conflict)
        assertEquals("Existing", repository.current().single().name)
        assertEquals(50L, repository.current().single().updatedAt)
    }

    @Test
    fun concurrentUpdateAfterPreflightReturnsConflictInsteadOfOverwriting() = runTest {
        val existing = automation(updatedAt = 50L)
        val repository = FakeAutomationRepository(listOf(existing)).apply {
            failNextCompareAndSetWithRevision = 75L
        }
        val service = service(repository)

        val result = service.update(
            automationId = existing.id,
            draft = AgentTaskDraftV1(name = "Agent edit"),
            context = agentContext(expectedRevision = 50L)
        )

        assertTrue(result is AutomationMutationResult.Conflict)
        result as AutomationMutationResult.Conflict
        assertEquals(50L, result.expectedRevision)
        assertEquals(75L, result.currentRevision)
        assertEquals(75L, repository.current().single().updatedAt)
        assertEquals("Existing", repository.current().single().name)
    }

    @Test
    fun concurrentDeleteAfterDependencyCheckReturnsConflictInsteadOfDeletingNewerRevision() = runTest {
        val existing = automation(updatedAt = 50L)
        val repository = FakeAutomationRepository(listOf(existing)).apply {
            failNextDeleteCompareAndSetWithRevision = 80L
        }
        val service = service(repository)

        val result = service.delete(
            automationId = existing.id,
            context = agentContext(expectedRevision = 50L)
        )

        assertTrue(result is AutomationMutationResult.Conflict)
        result as AutomationMutationResult.Conflict
        assertEquals(50L, result.expectedRevision)
        assertEquals(80L, result.currentRevision)
        assertEquals(1, repository.current().size)
        assertEquals(80L, repository.current().single().updatedAt)
    }

    private fun service(repository: AutomationRepository) = AutomationCommandService(
        repository = repository,
        dryRunInspector = AutomationDryRunInspector {
            WorkflowDryRunReport(
                workflowValidation = WorkflowValidationResult(emptyList()),
                capabilityResolutions = emptyList(),
                executable = true,
                summary = "ok"
            )
        },
        clockMillis = { 100L },
        idGenerator = { "agent-generated-id" }
    )

    private fun agentContext(expectedRevision: Long? = null) = AutomationMutationContext(
        actorId = "agent:test",
        origin = AutomationMutationOrigin.AGENT,
        expectedRevision = expectedRevision
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

    private class FakeAutomationRepository(
        initial: List<Automation> = emptyList()
    ) : AutomationRepository {
        private val state = MutableStateFlow(initial)

        var failNextCompareAndSetWithRevision: Long? = null
        var failNextDeleteCompareAndSetWithRevision: Long? = null

        fun current(): List<Automation> = state.value

        override fun getAutomations(): Flow<List<Automation>> = state

        override suspend fun getAutomationById(id: String): Automation? =
            state.value.firstOrNull { it.id == id }

        override suspend fun saveAutomation(automation: Automation) {
            state.value = state.value.filterNot { it.id == automation.id } + automation
        }

        override suspend fun saveAutomationIfRevisionMatches(
            automation: Automation,
            expectedRevision: Long
        ): Boolean {
            failNextCompareAndSetWithRevision?.let { concurrentRevision ->
                failNextCompareAndSetWithRevision = null
                state.value = state.value.map {
                    if (it.id == automation.id) it.copy(updatedAt = concurrentRevision) else it
                }
                return false
            }
            val current = getAutomationById(automation.id) ?: return false
            if (current.updatedAt != expectedRevision) return false
            saveAutomation(automation)
            return true
        }

        override suspend fun deleteAutomation(automation: Automation) {
            state.value = state.value.filterNot { it.id == automation.id }
        }

        override suspend fun deleteAutomationIfRevisionMatches(
            automationId: String,
            expectedRevision: Long
        ): Boolean {
            failNextDeleteCompareAndSetWithRevision?.let { concurrentRevision ->
                failNextDeleteCompareAndSetWithRevision = null
                state.value = state.value.map {
                    if (it.id == automationId) it.copy(updatedAt = concurrentRevision) else it
                }
                return false
            }
            val current = getAutomationById(automationId) ?: return false
            if (current.updatedAt != expectedRevision) return false
            deleteAutomation(current)
            return true
        }

        override suspend fun updateAutomationStatus(id: String, enabled: Boolean) {
            state.value = state.value.map { automation ->
                if (automation.id == id) automation.copy(enabled = enabled) else automation
            }
        }
    }
}
