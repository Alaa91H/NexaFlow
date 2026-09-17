package com.nexaflow.feature.history

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import com.nexaflow.domain.models.ExecutionHistoryOutcome
import com.nexaflow.domain.models.ExecutionOutcomeClassifier
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import com.nexaflow.domain.usecases.GetExecutionPagingUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * History ViewModel contract: the outcome filter selected in the UI must be
 * honored by the paging pipeline (Room-shaped predicate), and the routine
 * scope from the saved-state route narrows the stream to that routine only.
 */
class HistoryViewModelFilterTest {

    private fun record(automationId: String, success: Boolean) = ExecutionRecord(
        id = "$automationId-${if (success) "ok" else "fail"}",
        automationId = automationId,
        automationName = "Task $automationId",
        success = success,
        message = if (success) "ran" else "failed",
        executedAt = 100L
    )

    private open class FakeHistory(
        records: List<ExecutionRecord>
    ) : HistoryRepository {
        private val state = MutableStateFlow(records)
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = state
        override suspend fun getExecutionById(id: String): ExecutionRecord? = state.value.firstOrNull { it.id == id }
        override suspend fun recordExecution(record: ExecutionRecord) {
            state.value = state.value + record
        }

        private fun source(
            automationId: String?,
            outcome: ExecutionHistoryOutcome?
        ): PagingSource<Int, ExecutionRecord> {
            val filtered = state.value.filter { r ->
                (automationId == null || r.automationId == automationId) &&
                    (outcome == null || ExecutionOutcomeClassifier.classify(r) == outcome)
            }
            return object : PagingSource<Int, ExecutionRecord>() {
                override fun getRefreshKey(state: PagingState<Int, ExecutionRecord>): Int? = null
                override suspend fun load(params: PagingSource.LoadParams<Int>): PagingSource.LoadResult<Int, ExecutionRecord> =
                    PagingSource.LoadResult.Page(filtered, null, null)
            }
        }

        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> = source(null, null)
        override fun getExecutionPaging(automationId: String): PagingSource<Int, ExecutionRecord> = source(automationId, null)
        override fun getExecutionPaging(automationId: String?, outcome: ExecutionHistoryOutcome?): PagingSource<Int, ExecutionRecord> =
            source(automationId, outcome)
    }

    @Test
    fun `failed outcome filter returns only failed runs`() = runTest {
        val history = FakeHistory(
            listOf(record("a", success = true), record("b", success = false), record("c", success = false))
        )
        val useCase = GetExecutionPagingUseCase(history)
        val flow: Flow<PagingData<ExecutionRecord>> = Pager(PagingConfig(pageSize = 10)) {
            useCase(null, ExecutionHistoryOutcome.FAILED)
        }.flow
        val snapshot = flow.asSnapshot()
        assertEquals(listOf("b", "c"), snapshot.map { it.automationId })
        Unit
    }

    @Test
    fun `routine scope narrows the paging stream to that routine`() = runTest {
        val history = FakeHistory(
            listOf(record("a", success = true), record("a", success = false), record("b", success = true))
        )
        val useCase = GetExecutionPagingUseCase(history)
        val flow: Flow<PagingData<ExecutionRecord>> = Pager(PagingConfig(pageSize = 10)) {
            useCase("a", null)
        }.flow
        val snapshot = flow.asSnapshot()
        assertEquals(listOf("a", "a"), snapshot.map { it.automationId })
    }
}
