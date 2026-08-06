package com.nexaflow.core.logging

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.Collections

/** In-memory [LogStore] — thread-safe, bounded history, used for tests. */
class InMemoryLogStore(
    private val maxEntries: Int = 500
) : LogStore {

    private val timeline = Collections.synchronizedList(mutableListOf<ExecutionTimelineEntry>())
    private val errors = Collections.synchronizedList(mutableListOf<ErrorLogEntry>())
    private val metrics = Collections.synchronizedList(mutableListOf<PerformanceMetric>())

    private val timelineFlow = MutableStateFlow<List<ExecutionTimelineEntry>>(emptyList())
    private val errorsFlow = MutableStateFlow<List<ErrorLogEntry>>(emptyList())
    private val metricsFlow = MutableStateFlow<List<PerformanceMetric>>(emptyList())

    override fun timeline(): Flow<List<ExecutionTimelineEntry>> = timelineFlow.asStateFlow()
    override fun errors(): Flow<List<ErrorLogEntry>> = errorsFlow.asStateFlow()
    override fun metrics(): Flow<List<PerformanceMetric>> = metricsFlow.asStateFlow()

    override suspend fun recordExecution(entry: ExecutionTimelineEntry) {
        synchronized(timeline) {
            timeline.add(entry)
            while (timeline.size > maxEntries) timeline.removeAt(0)
            timelineFlow.value = timeline.toList()
        }
    }

    override suspend fun recordError(entry: ErrorLogEntry) {
        synchronized(errors) {
            errors.add(entry)
            while (errors.size > maxEntries) errors.removeAt(0)
            errorsFlow.value = errors.toList()
        }
    }

    override suspend fun recordMetric(metric: PerformanceMetric) {
        synchronized(metrics) {
            metrics.add(metric)
            while (metrics.size > maxEntries) metrics.removeAt(0)
            metricsFlow.value = metrics.toList()
        }
    }

    override suspend fun clear() {
        synchronized(timeline) {
            timeline.clear()
            timelineFlow.value = emptyList()
        }
        synchronized(errors) {
            errors.clear()
            errorsFlow.value = emptyList()
        }
        synchronized(metrics) {
            metrics.clear()
            metricsFlow.value = emptyList()
        }
    }
}
