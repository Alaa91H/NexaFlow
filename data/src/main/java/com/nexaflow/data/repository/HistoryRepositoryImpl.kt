package com.nexaflow.data.repository

import androidx.paging.PagingSource
import com.nexaflow.core.database.ExecutionDao
import com.nexaflow.data.mapper.toDomain
import com.nexaflow.data.mapper.toEntity
import com.nexaflow.data.paging.MappedPagingSource
import com.nexaflow.domain.models.ExecutionHistoryOutcome
import com.nexaflow.domain.models.ExecutionOutcomeClassifier
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class HistoryRepositoryImpl @Inject constructor(
    private val executionDao: ExecutionDao
) : HistoryRepository {

    override fun getExecutionHistory(): Flow<List<ExecutionRecord>> {
        return executionDao.getAllExecutions().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> {
        return MappedPagingSource(executionDao.getExecutionsPaged()) { it.toDomain() }
    }

    override fun getExecutionPaging(automationId: String): PagingSource<Int, ExecutionRecord> {
        return MappedPagingSource(executionDao.getExecutionsPagedForAutomation(automationId)) { it.toDomain() }
    }

    override fun getExecutionPaging(
        automationId: String?,
        success: Boolean?
    ): PagingSource<Int, ExecutionRecord> {
        return MappedPagingSource(
            executionDao.getExecutionsPagedFiltered(automationId, success)
        ) { it.toDomain() }
    }

    override fun getExecutionPaging(
        automationId: String?,
        outcome: ExecutionHistoryOutcome?
    ): PagingSource<Int, ExecutionRecord> {
        return when (outcome) {
            ExecutionHistoryOutcome.SKIPPED -> MappedPagingSource(
                executionDao.getExecutionsPagedSkipped(
                    automationId = automationId,
                    skipMessageLike = ExecutionOutcomeClassifier.SKIPPED_MESSAGE_PREFIX + "%"
                )
            ) { it.toDomain() }
            ExecutionHistoryOutcome.FAILED,
            null -> getExecutionPaging(
                automationId = automationId,
                success = if (outcome == ExecutionHistoryOutcome.FAILED) false else null
            )
        }
    }

    override suspend fun getExecutionById(id: String): ExecutionRecord? {
        return executionDao.getExecutionById(id)?.toDomain()
    }

    override suspend fun recordExecution(record: ExecutionRecord) {
        // Insert + enforce the retention policy (60-day window, 1000-record
        // ceiling) atomically so the history table never grows without bound.
        executionDao.insertWithRetention(record.toEntity())
    }
}
