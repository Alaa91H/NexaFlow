package com.nexaflow.core.execution

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TriggerStabilityRecheckQueueTest {
    @Test
    fun schedulesOneRecheckPerKeyAndRemovesCompletedEntry() = runTest {
        val queue = TriggerStabilityRecheckQueue(backgroundScope)
        var calls = 0
        assertTrue(queue.schedule("automation:0", 1_000) { calls++ })
        assertTrue(queue.schedule("automation:0", 1_000) { calls += 100 })
        assertEquals(1, queue.pendingCountForTest())

        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, calls)
        assertEquals(0, queue.pendingCountForTest())
    }

    @Test
    fun refusesEntriesPastHardBoundAndCanCancelPrefix() = runTest {
        val queue = TriggerStabilityRecheckQueue(backgroundScope, capacity = 2)
        assertTrue(queue.schedule("a:0", 1_000) {})
        assertTrue(queue.schedule("a:1", 1_000) {})
        assertFalse(queue.schedule("b:0", 1_000) {})
        queue.cancelPrefix("a:")

        assertEquals(0, queue.pendingCountForTest())
        assertTrue(queue.schedule("b:0", 1_000) {})
    }

    @Test
    fun replaceRestartsQuietWindowAndRunsOnlyLatestAction() = runTest {
        val queue = TriggerStabilityRecheckQueue(backgroundScope)
        var calls = 0
        assertTrue(queue.replace("automation:0", 1_000) { calls++ })
        advanceTimeBy(500)
        assertTrue(queue.replace("automation:0", 1_000) { calls += 10 })
        advanceTimeBy(500)
        runCurrent()
        assertEquals(0, calls)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(10, calls)
    }
}
