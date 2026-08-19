package com.nexaflow.core.execution.task

import com.nexaflow.core.rom.model.SystemControlResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskManagerTest {

    private fun okTask(name: String) = PendingTask(name = name) { SystemControlResult.ok(name) }

    @Test
    fun priority_executesHighestFirst() = runBlocking {
        val manager = TaskManager()
        val order = java.util.Collections.synchronizedList(mutableListOf<String>())
        // A CRITICAL gatekeeper holds the worker so all three tasks are queued
        // before the worker picks any of them (avoids the poll-before-enqueue race).
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        manager.enqueue(PendingTask(name = "gatekeeper", priority = TaskPriority.CRITICAL) {
            started.complete(Unit)
            release.await()
            SystemControlResult.ok("gatekeeper")
        })
        started.await()
        manager.enqueue(PendingTask(name = "low", priority = TaskPriority.LOW) {
            order += "low"
            SystemControlResult.ok("low")
        })
        manager.enqueue(PendingTask(name = "high", priority = TaskPriority.HIGH) {
            order += "high"
            SystemControlResult.ok("high")
        })
        manager.enqueue(PendingTask(name = "normal", priority = TaskPriority.NORMAL) {
            order += "normal"
            SystemControlResult.ok("normal")
        })
        release.complete(Unit)
        assertTrue(manager.awaitIdle(timeoutMs = 5_000))
        assertEquals(listOf("high", "normal", "low"), order.toList())
        manager.shutdown()
    }

    @Test
    fun fifo_withinSamePriority() = runBlocking {
        val manager = TaskManager()
        val order = java.util.Collections.synchronizedList(mutableListOf<String>())
        // Same gating trick: enqueue all five before the worker starts draining.
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        manager.enqueue(PendingTask(name = "gatekeeper", priority = TaskPriority.CRITICAL) {
            started.complete(Unit)
            release.await()
            SystemControlResult.ok("gatekeeper")
        })
        started.await()
        (1..5).forEach { i ->
            manager.enqueue(PendingTask(name = "t$i", priority = TaskPriority.NORMAL) {
                order += "t$i"
                SystemControlResult.ok("t$i")
            })
        }
        release.complete(Unit)
        assertTrue(manager.awaitIdle(timeoutMs = 5_000))
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5"), order.toList())
        manager.shutdown()
    }

    @Test
    fun retry_retriesUntilSuccess() = runBlocking {
        val manager = TaskManager()
        val attempts = java.util.concurrent.atomic.AtomicInteger(0)
        val taskId = manager.enqueue(
            PendingTask(
                name = "flaky",
                retryPolicy = RetryPolicy(maxRetries = 3, initialBackoffMs = 1)
            ) {
                if (attempts.incrementAndGet() < 3) SystemControlResult.fail("transient")
                else SystemControlResult.ok("recovered")
            }
        )
        assertTrue(manager.awaitIdle(timeoutMs = 5_000))
        assertEquals(3, attempts.get())
        assertEquals(
            TaskResult.Success(taskId, "recovered", 3),
            manager.results.value.last()
        )
        manager.shutdown()
    }

    @Test
    fun retry_givesUpAfterMaxRetries() = runBlocking {
        val manager = TaskManager()
        val attempts = java.util.concurrent.atomic.AtomicInteger(0)
        val taskId = manager.enqueue(
            PendingTask(
                name = "always-fails",
                retryPolicy = RetryPolicy(maxRetries = 2, initialBackoffMs = 1)
            ) {
                attempts.incrementAndGet()
                SystemControlResult.fail("nope")
            }
        )
        assertTrue(manager.awaitIdle(timeoutMs = 5_000))
        assertEquals(3, attempts.get()) // 1 initial + 2 retries
        assertEquals(
            TaskResult.Failure(taskId, "nope", 3),
            manager.results.value.last()
        )
        manager.shutdown()
    }

    @Test
    fun noRetries_byDefault() = runBlocking {
        val manager = TaskManager()
        val attempts = java.util.concurrent.atomic.AtomicInteger(0)
        val taskId = manager.enqueue(PendingTask(name = "single") {
            attempts.incrementAndGet()
            SystemControlResult.fail("nope")
        })
        assertTrue(manager.awaitIdle(timeoutMs = 5_000))
        assertEquals(1, attempts.get())
        assertEquals(
            TaskResult.Failure(taskId, "nope", 1),
            manager.results.value.last()
        )
        manager.shutdown()
    }

    @Test
    fun timeout_reportsTimedOut() = runBlocking {
        val manager = TaskManager()
        val taskId = manager.enqueue(
            PendingTask(name = "slow", timeoutMs = 50) {
                delay(5_000)
                SystemControlResult.ok("too late")
            }
        )
        assertTrue(manager.awaitIdle(timeoutMs = 5_000))
        assertEquals(TaskResult.TimedOut(taskId, 1), manager.results.value.last())
        manager.shutdown()
    }

    @Test
    fun timeout_retriesThenTimesOut() = runBlocking {
        val manager = TaskManager()
        val attempts = java.util.concurrent.atomic.AtomicInteger(0)
        val taskId = manager.enqueue(
            PendingTask(
                name = "slow-retry",
                timeoutMs = 50,
                retryPolicy = RetryPolicy(maxRetries = 1, initialBackoffMs = 1)
            ) {
                attempts.incrementAndGet()
                delay(5_000)
                SystemControlResult.ok("too late")
            }
        )
        assertTrue(manager.awaitIdle(timeoutMs = 5_000))
        assertEquals(2, attempts.get())
        assertEquals(TaskResult.TimedOut(taskId, 2), manager.results.value.last())
        manager.shutdown()
    }

    @Test
    fun cancel_queuedTaskNeverRuns() = runBlocking {
        val manager = TaskManager()
        val gate = CompletableDeferred<Unit>()
        // First task blocks the worker so the second stays queued.
        manager.enqueue(PendingTask(name = "blocker") {
            gate.await()
            SystemControlResult.ok("blocker")
        })
        val cancelled = manager.enqueue(okTask("cancelled"))
        // Wait until the blocker is running and the second is queued.
        delay(100)
        manager.cancel(cancelled)
        gate.complete(Unit)
        assertTrue(manager.awaitIdle(timeoutMs = 5_000))
        assertTrue(
            manager.results.value.any { it is TaskResult.Cancelled && it.taskId == cancelled }
        )
        manager.shutdown()
    }

    @Test
    fun cancel_runningTaskPublishesCancelled() = runBlocking {
        val manager = TaskManager()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val taskId = manager.enqueue(PendingTask(name = "runnable") {
            started.complete(Unit)
            release.await()
            SystemControlResult.ok("done")
        })
        started.await()
        manager.cancel(taskId)
        release.complete(Unit)
        assertTrue(manager.awaitIdle(timeoutMs = 5_000))
        assertTrue(
            manager.results.value.any { it is TaskResult.Cancelled && it.taskId == taskId }
        )
        manager.shutdown()
    }

    @Test
    fun idleWorker_wakesAndExecutesNewTask() = runBlocking {
        val manager = TaskManager()
        val executed = CompletableDeferred<Unit>()
        try {
            // Give the worker a chance to reach its event-driven idle wait.
            delay(50)
            manager.enqueue(PendingTask(name = "wake-idle-worker") {
                executed.complete(Unit)
                SystemControlResult.ok("woken")
            })

            withTimeout(1_000) { executed.await() }
            assertTrue(manager.awaitIdle(timeoutMs = 1_000))
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun pendingCount_reflectsQueue() = runBlocking {
        val manager = TaskManager()
        val gate = CompletableDeferred<Unit>()
        manager.enqueue(PendingTask(name = "blocker") {
            gate.await()
            SystemControlResult.ok("blocker")
        })
        manager.enqueue(okTask("queued1"))
        manager.enqueue(okTask("queued2"))
        delay(100)
        assertTrue(manager.pendingCount >= 2)
        gate.complete(Unit)
        assertTrue(manager.awaitIdle(timeoutMs = 5_000))
        assertEquals(0, manager.pendingCount)
        assertFalse(manager.isRunning)
        manager.shutdown()
    }
}


// Production-hardening coverage is kept in a separate class so the original
// queue-regression tests remain easy to read and reuse.
class TaskManagerHardeningTest {

    @Test
    fun submit_rejectsInvalidDeadlineTimeoutAndDisabledResource() {
        val manager = TaskManager(
            limits = TaskManagerLimits(
                maxTaskTimeoutMs = 100L,
                resourceCapacities = TaskResource.entries.associateWith {
                    if (it == TaskResource.NETWORK) 0 else 1
                }
            )
        )
        try {
            val expired = manager.submit(
                PendingTask(name = "expired", deadlineAtMs = System.currentTimeMillis() - 1) {
                    SystemControlResult.ok("never")
                }
            )
            val tooLong = manager.submit(
                PendingTask(name = "too-long", timeoutMs = 101L) { SystemControlResult.ok("never") }
            )
            val disabled = manager.submit(
                PendingTask(name = "network", resources = setOf(TaskResource.NETWORK)) {
                    SystemControlResult.ok("never")
                }
            )

            assertTrue(expired is TaskAdmission.Rejected && expired.reason is TaskRejectionReason.DeadlineAlreadyElapsed)
            assertTrue(tooLong is TaskAdmission.Rejected && tooLong.reason is TaskRejectionReason.TimeoutExceedsPolicy)
            assertTrue(disabled is TaskAdmission.Rejected && disabled.reason is TaskRejectionReason.ResourceUnavailable)
            assertEquals(TaskLifecycleState.REJECTED, manager.statuses.value.getValue(expired.taskId).state)
            assertEquals(3, manager.results.value.count { it is TaskResult.Rejected })
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun deadlineThatExpiresWhileQueuedNeverExecutes() = runBlocking {
        val manager = TaskManager()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            manager.enqueue(PendingTask(name = "gate") {
                started.complete(Unit)
                release.await()
                SystemControlResult.ok("gate")
            })
            started.await()
            var executed = false
            val deadlineTask = manager.enqueue(
                PendingTask(
                    name = "deadline",
                    deadlineAtMs = System.currentTimeMillis() + 30L
                ) {
                    executed = true
                    SystemControlResult.ok("should-not-run")
                }
            )
            delay(60)
            release.complete(Unit)
            assertTrue(manager.awaitIdle(5_000L))

            assertFalse(executed)
            assertEquals(TaskLifecycleState.DEADLINE_EXCEEDED, manager.statuses.value.getValue(deadlineTask).state)
            assertTrue(manager.results.value.any { it is TaskResult.DeadlineExceeded && it.taskId == deadlineTask })
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun duplicateIdIsRejectedUntilOriginalTaskReachesTerminalState() = runBlocking {
        val manager = TaskManager()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val taskId = "stable-id"
        try {
            assertTrue(
                manager.submit(PendingTask(id = taskId, name = "first") {
                    started.complete(Unit)
                    release.await()
                    SystemControlResult.ok("first")
                }) is TaskAdmission.Accepted
            )
            started.await()
            val duplicate = manager.submit(PendingTask(id = taskId, name = "duplicate") { SystemControlResult.ok("duplicate") })
            assertTrue(duplicate is TaskAdmission.Rejected && duplicate.reason is TaskRejectionReason.DuplicateTaskId)

            release.complete(Unit)
            assertTrue(manager.awaitIdle(5_000L))
            assertEquals(TaskLifecycleState.SUCCEEDED, manager.statuses.value.getValue(taskId).state)
            assertFalse(manager.cancel(taskId))
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun cancellationTransitionsThroughRequestToTerminalState() = runBlocking {
        val manager = TaskManager()
        val started = CompletableDeferred<Unit>()
        try {
            val taskId = manager.enqueue(PendingTask(name = "cancel-state") {
                started.complete(Unit)
                delay(5_000L)
                SystemControlResult.ok("late")
            })
            started.await()
            assertTrue(manager.cancel(taskId))
            assertTrue(manager.awaitIdle(5_000L))
            assertEquals(TaskLifecycleState.CANCELLED, manager.statuses.value.getValue(taskId).state)
            assertTrue(manager.results.value.any { it is TaskResult.Cancelled && it.taskId == taskId })
        } finally {
            manager.shutdown()
        }
    }
}
