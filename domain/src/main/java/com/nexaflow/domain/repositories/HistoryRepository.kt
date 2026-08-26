package com.nexaflow.domain.repositories

import androidx.paging.PagingSource
import com.nexaflow.domain.models.ExecutionRecord
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getExecutionHistory(): Flow<List<ExecutionRecord>>

    /** Pageable history stream for the (potentially 1000-entry) history screen. */
    fun getExecutionPaging(): PagingSource<Int, ExecutionRecord>

    /**
     * Pageable history stream scoped to one routine for evidence-led troubleshooting.
     * Test doubles and legacy implementations safely fall back to the global stream.
     */
    fun getExecutionPaging(automationId: String): PagingSource<Int, ExecutionRecord> = getExecutionPaging()

    suspend fun getExecutionById(id: String): ExecutionRecord?
    suspend fun recordExecution(record: ExecutionRecord)
}
