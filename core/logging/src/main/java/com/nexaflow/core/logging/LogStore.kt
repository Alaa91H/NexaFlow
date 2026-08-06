package com.nexaflow.core.logging

import kotlinx.coroutines.flow.Flow

/**
 * Framework logging store: execution timeline, error log and performance
 * metrics. The engine and compatibility layer write here; the history UI and
 * debugging tooling read from it. Implementations may persist to Room,
 * DataStore or stay in-memory.
 */
interface LogStore {
    fun timeline(): Flow<List<ExecutionTimelineEntry>>
    fun errors(): Flow<List<ErrorLogEntry>>
    fun metrics(): Flow<List<PerformanceMetric>>

    suspend fun recordExecution(entry: ExecutionTimelineEntry)
    suspend fun recordError(entry: ErrorLogEntry)
    suspend fun recordMetric(metric: PerformanceMetric)

    suspend fun clear()
}
