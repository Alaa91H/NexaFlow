package com.nexaflow.data.repository

import androidx.paging.PagingSource
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.AutomationHealthStatus
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthRepositoryImplTest {

    @Test
    fun `reports include automation without history and derive failure status from existing history`() = runBlocking {
        val repository = HealthRepositoryImpl(
            automationRepository = FakeAutomationRepository(listOf(automation("a"), automation("b"))),
            historyRepository = FakeHistoryRepository(
                listOf(
                    ExecutionRecord("r3", "a", "A", false, "failure", 3L),
                    ExecutionRecord("r2", "a", "A", false, "failure", 2L),
                    ExecutionRecord("r1", "a", "A", false, "failure", 1L)
                )
            )
        )

        val reports = repository.getHealthReports().first().associateBy { it.automationId }

        assertEquals(AutomationHealthStatus.NEEDS_ATTENTION, reports.getValue("a").status)
        assertEquals(AutomationHealthStatus.NO_EXECUTIONS, reports.getValue("b").status)
        assertEquals(3, reports.getValue("a").consecutiveFailures)
    }

    private fun automation(id: String) = Automation(
        id = id,
        name = id,
        description = "",
        icon = "",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "",
        priority = 0,
        enabled = true,
        triggers = emptyList(),
        actions = emptyList(),
        createdAt = 0L,
        updatedAt = 0L
    )

    private class FakeAutomationRepository(
        private val automations: List<Automation>
    ) : AutomationRepository {
        override fun getAutomations(): Flow<List<Automation>> = flowOf(automations)
        override suspend fun getAutomationById(id: String): Automation? = automations.firstOrNull { it.id == id }
        override suspend fun saveAutomation(automation: Automation) = Unit
        override suspend fun deleteAutomation(automation: Automation) = Unit
        override suspend fun updateAutomationStatus(id: String, enabled: Boolean) = Unit
    }

    private class FakeHistoryRepository(
        private val records: List<ExecutionRecord>
    ) : HistoryRepository {
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(records)
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> = error("Not used by health reports")
        override suspend fun getExecutionById(id: String): ExecutionRecord? = records.firstOrNull { it.id == id }
        override suspend fun recordExecution(record: ExecutionRecord) = Unit
    }
}
