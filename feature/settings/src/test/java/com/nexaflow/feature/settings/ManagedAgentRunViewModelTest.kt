package com.nexaflow.feature.settings

import androidx.lifecycle.SavedStateHandle
import com.nexaflow.core.agentruntime.AgentRun
import com.nexaflow.core.agentruntime.AgentRunEventType
import com.nexaflow.core.agentruntime.ManagedAgentRunEvent
import com.nexaflow.core.agentruntime.ManagedAgentRunUseCase
import com.nexaflow.core.database.AgentApprovalEntity
import com.nexaflow.core.database.AgentDefinitionDao
import com.nexaflow.core.database.AgentDefinitionEntity
import com.nexaflow.core.database.AgentRunDao
import com.nexaflow.core.database.AgentRunEntity
import com.nexaflow.core.database.AgentRunEventEntity
import com.nexaflow.data.agents.AgentDefinitionRepository
import com.nexaflow.data.agents.AgentRunRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManagedAgentRunViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectingARunStreamsItsEventsAndDismissingClearsTheSelection() = runBlocking {
        val event = AgentRunEventEntity(
            runId = "run-1",
            sequence = 1,
            id = "event-1",
            type = AgentRunEventType.QUEUED.name,
            safeCode = "queued",
            createdAtMillis = 10
        )
        val viewModel = ManagedAgentRunViewModel(
            savedStateHandle = SavedStateHandle(mapOf("agentId" to "agent-1")),
            definitions = AgentDefinitionRepository(EmptyDefinitionDao()),
            runUseCase = FakeUseCase(),
            runs = AgentRunRepository(FakeRunDao(listOf(event)))
        )
        val collection = launch { viewModel.state.collect { } }

        viewModel.selectRun("run-1")
        val selected = withTimeout(5_000) {
            viewModel.state.first { it.selectedRunId == "run-1" }
        }
        assertEquals("run-1", selected.selectedRunId)
        assertEquals(1, selected.selectedRunEvents.size)
        assertEquals("queued", selected.selectedRunEvents.single().safeCode)

        viewModel.dismissRunDetails()
        val dismissed = withTimeout(5_000) {
            viewModel.state.first { it.selectedRunId == null }
        }
        assertNull(dismissed.selectedRunId)
        assertTrue(dismissed.selectedRunEvents.isEmpty())
        collection.cancel()
    }

    private class FakeUseCase : ManagedAgentRunUseCase {
        override fun observeRuns(agentId: String): Flow<List<AgentRun>> = flowOf(emptyList())

        override fun runAgent(
            agentId: String,
            prompt: String,
            idempotencyKey: String
        ): Flow<ManagedAgentRunEvent> = emptyFlow()

        override suspend fun resolveApproval(approvalId: String, approved: Boolean): Boolean = false
    }

    private class EmptyDefinitionDao : AgentDefinitionDao {
        override fun observeAll(): Flow<List<AgentDefinitionEntity>> = flowOf(emptyList())
        override suspend fun findById(id: String): AgentDefinitionEntity? = null
        override suspend fun insertIfAbsent(entity: AgentDefinitionEntity): Long = 0L
        override suspend fun updateIfRevisionMatches(
            id: String,
            name: String,
            description: String,
            systemInstructions: String,
            providerProfileId: String?,
            modelId: String?,
            policyJson: String,
            budgetJson: String,
            enabled: Boolean,
            newRevision: Long,
            updatedAtMillis: Long,
            expectedRevision: Long
        ): Int = 0
        override suspend fun deleteIfRevisionMatches(id: String, expectedRevision: Long): Int = 0
    }

    /** Implements only the reads the view model uses; every write fails loudly. */
    private class FakeRunDao(
        private val events: List<AgentRunEventEntity>
    ) : AgentRunDao() {
        override suspend fun findByIdempotency(agentId: String, keyHash: String): AgentRunEntity? = null
        override suspend fun findById(runId: String): AgentRunEntity? = null
        override fun observeForAgent(agentId: String, limit: Int): Flow<List<AgentRunEntity>> =
            flowOf(emptyList())
        override fun observeEvents(runId: String): Flow<List<AgentRunEventEntity>> =
            flowOf(events.filter { it.runId == runId })
        override suspend fun events(runId: String): List<AgentRunEventEntity> =
            events.filter { it.runId == runId }
        override suspend fun findApproval(approvalId: String): AgentApprovalEntity? = null
        override fun observeApproval(approvalId: String): Flow<AgentApprovalEntity?> =
            flowOf(null)
        override fun observePendingApprovals(runId: String): Flow<List<AgentApprovalEntity>> =
            flowOf(emptyList())
        override suspend fun inFlight(): List<AgentRunEntity> = emptyList()

        override suspend fun insertRun(run: AgentRunEntity): Long = error("unused")
        override suspend fun insertEvent(event: AgentRunEventEntity) = error("unused")
        override suspend fun countActiveRuns(agentId: String): Int = 0
        override suspend fun lastEventSequence(runId: String): Long = 0L
        override suspend fun transition(
            runId: String,
            expectedStatus: String,
            nextStatus: String,
            expectedRevision: Long,
            now: Long
        ): Int = 0
        override suspend fun setTerminal(
            runId: String,
            terminalStatus: String,
            outcome: String,
            expectedRevision: Long,
            now: Long
        ): Int = 0
        override suspend fun updateUsage(
            runId: String,
            turns: Int,
            toolCalls: Int,
            outputCharacters: Int,
            costMicros: Long,
            providerId: String?,
            modelId: String?,
            expectedRevision: Long,
            now: Long
        ): Int = 0
        override suspend fun insertApproval(approval: AgentApprovalEntity) = error("unused")
        override suspend fun invalidatePendingApprovals(runId: String, now: Long): Int = 0
        override suspend fun resolveApproval(
            approvalId: String,
            runId: String,
            agentId: String,
            definitionRevision: Long,
            callFingerprint: String,
            deviceBindingHash: String,
            decision: String,
            now: Long
        ): Int = 0
        override suspend fun expireApproval(approvalId: String, runId: String, now: Long): Int = 0
    }
}
