package com.nexaflow.core.logging

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryLogStoreTest {

    @Test
    fun recordExecution_appearsInTimeline() = runTest {
        val store = InMemoryLogStore()
        store.recordExecution(
            ExecutionTimelineEntry(
                id = "e1", automationId = "a1", automationName = "Task",
                kind = "RUN", success = true, message = "ok",
                startedAt = 100L, durationMs = 5L
            )
        )
        val timeline = store.timeline().first()
        assertEquals(1, timeline.size)
        assertEquals("a1", timeline.first().automationId)
    }

    @Test
    fun recordError_appearsInErrors() = runTest {
        val store = InMemoryLogStore()
        store.recordError(
            ErrorLogEntry(
                id = "err1", source = "engine", message = "boom",
                stackTrace = "at ...", timestamp = 200L
            )
        )
        assertEquals(1, store.errors().first().size)
        assertEquals("boom", store.errors().first().first().message)
    }

    @Test
    fun recordMetric_appearsInMetrics() = runTest {
        val store = InMemoryLogStore()
        store.recordMetric(PerformanceMetric("action", 42L, 300L))
        assertEquals(1, store.metrics().first().size)
        assertEquals(42L, store.metrics().first().first().valueMs)
    }

    @Test
    fun boundedHistory_keepsNewestEntries() = runTest {
        val store = InMemoryLogStore(maxEntries = 2)
        store.recordMetric(PerformanceMetric("a", 1L, 1L))
        store.recordMetric(PerformanceMetric("b", 2L, 2L))
        store.recordMetric(PerformanceMetric("c", 3L, 3L))
        val metrics = store.metrics().first()
        assertEquals(2, metrics.size)
        assertEquals("b", metrics.first().name)
        assertEquals("c", metrics.last().name)
    }

    @Test
    fun clear_emptiesAllStores() = runTest {
        val store = InMemoryLogStore()
        store.recordMetric(PerformanceMetric("a", 1L, 1L))
        store.recordExecution(
            ExecutionTimelineEntry(
                id = "e1", automationId = "a", automationName = "T",
                kind = "RUN", success = true, message = "", startedAt = 1L, durationMs = 1L
            )
        )
        store.clear()
        assertTrue(store.timeline().first().isEmpty())
        assertTrue(store.metrics().first().isEmpty())
        assertTrue(store.errors().first().isEmpty())
    }
}
