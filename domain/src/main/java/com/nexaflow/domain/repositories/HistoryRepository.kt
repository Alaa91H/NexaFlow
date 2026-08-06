package com.nexaflow.domain.repositories

import com.nexaflow.domain.models.ExecutionRecord
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getExecutionHistory(): Flow<List<ExecutionRecord>>
    suspend fun getExecutionById(id: String): ExecutionRecord?
    suspend fun recordExecution(record: ExecutionRecord)
}
