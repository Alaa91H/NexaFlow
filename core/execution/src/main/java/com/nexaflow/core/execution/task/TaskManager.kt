package com.nexaflow.core.execution.task

import com.nexaflow.core.common.AppDispatchers
import com.nexaflow.core.common.EpochMillis
import com.nexaflow.core.logging.ErrorLogEntry
import com.nexaflow.core.logging.LogStore
import com.nexaflow.core.logging.PerformanceMetric
import com.nexaflow.core.rom.model.SystemControlResult
import java.util.PriorityQueue
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class TaskPriority { LOW, NORMAL, HIGH, CRITICAL }

/**
 * Retry policy for a task: up to [maxRetries] retries after the first attempt,
 * with exponential backoff (base [initialBackoffMs], multiplier
 * [backoffMultiplier]). `maxRetries = 0` means a single attempt.
 */
data class RetryPolicy(
    val maxRetries: Int = 0,
    val initialBackoffMs: Long = 1_000L,
    val backoffMultiplier: Double = 2.0
) {
    init {
        require(maxRetries >= 0) { "maxRetries must be non-negative" }
        require(initialBackoffMs >= 0) { "initialBackoffMs must be non-negative" }
        require(backoffMultiplier > 0) { "backoffMultiplier must be positive" }
    }

    /** Backoff before retry [attempt] (1-based; the first attempt has no backoff). */
    fun backoffFor(attempt: Int): Long {
        if (attempt <= 1) return 0L
        val factor = Math.pow(backoffMultiplier, (attempt - 1).toDouble())
        return (initialBackoffMs * factor).toLong()
    }
}

/** A unit of work the [TaskManager] can schedule, prioritize, retry and cancel. */
data class PendingTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val retryPolicy: RetryPolicy = RetryPolicy(),
    val timeoutMs: Long? = null,
    val run: suspend () -> SystemControlResult
)

/** Final outcome of a task, published to [TaskManager.results]. */
sealed interface TaskResult {
    data class Success(val taskId: String, val message: String, val attempts: Int) : TaskResult
    data class Failure(val taskId: String, val message: String, val attempts: Int) : TaskResult
    data class TimedOut(val taskId: String, val attempts: Int) : TaskResult
    data class Cancelled(val taskId: String) : TaskResult
}

/**
 * Execution queue with priority ordering (FIFO within the same priority),
 * per-task retry with backoff, optional timeout and cancellation.
 *
 * A single consumer drains the queue in priority order; each task runs in its
 * own child job so [cancel] stops the running task immediately without killing
 * the queue. Device-state actions therefore never run concurrently (matching
 * the legacy sequential engine). Results are exposed as a [StateFlow].
 */
class TaskManager(
    private val dispatchers: AppDispatchers = AppDispatchers.Default,
    private val logStore: LogStore? = null,
    private val epochMillis: EpochMillis = EpochMillis.System
) {

    private data class Envelope(val task: PendingTask, val seq: Long)

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val queue = PriorityQueue<Envelope>(
        compareByDescending<Envelope> { it.task.priority }.thenBy { it.seq }
    )
    private val lock = Any()
    private val seqCounter = AtomicLong(0)
    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()
    private val runningJobs = ConcurrentHashMap<String, Job>()
    private val resultsFlow = MutableStateFlow<List<TaskResult>>(emptyList())

    val results: StateFlow<List<TaskResult>> = resultsFlow.asStateFlow()

    val pendingCount: Int
        get() = synchronized(lock) { queue.size }

    /** True while a task is executing. */
    val isRunning: Boolean get() = activeTaskId.get() != null

    private val activeTaskId = java.util.concurrent.atomic.AtomicReference<String?>(null)

    private val worker = scope.launch { processQueue() }

    /** Enqueues [task] and returns its id. */
    fun enqueue(task: PendingTask): String {
        synchronized(lock) {
            queue.add(Envelope(task, seqCounter.incrementAndGet()))
        }
        return task.id
    }

    /**
     * Cancels [taskId]: queued tasks are skipped, a running task's job is
     * cancelled immediately. The terminal [TaskResult.Cancelled] is published
     * either way. Returns true when the id was known (queued or running).
     */
    fun cancel(taskId: String): Boolean {
        cancelledIds.add(taskId)
        runningJobs[taskId]?.cancel()
        return true
    }

    /** Blocks until the queue drains or [timeoutMs] elapses. Returns true when idle. */
    suspend fun awaitIdle(timeoutMs: Long = 5_000L): Boolean {
        val deadline = epochMillis.now() + timeoutMs
        while (epochMillis.now() < deadline) {
            if (synchronized(lock) { queue.isEmpty() } && !isRunning) return true
            delay(10)
        }
        return synchronized(lock) { queue.isEmpty() } && !isRunning
    }

    /** Stops the worker; queued work is abandoned (shutdown path). */
    fun shutdown() {
        scope.cancel()
    }

    private suspend fun processQueue() {
        while (currentCoroutineContext().isActive) {
            val envelope = synchronized(lock) {
                // Mark the task active atomically with the poll so awaitIdle() never
                // sees an empty queue + idle worker while a task is about to start.
                val polled = queue.poll()
                if (polled != null) activeTaskId.set(polled.task.id)
                polled
            } ?: run {
                delay(25)
                continue
            }
            if (envelope.task.id in cancelledIds) {
                cancelledIds.remove(envelope.task.id)
                // Publish BEFORE clearing the active id: otherwise awaitIdle() can
                // observe an empty queue + idle worker while the Cancelled result
                // is not yet visible, and return before the cancellation lands.
                publish(TaskResult.Cancelled(envelope.task.id))
                activeTaskId.set(null)
                continue
            }
            // Run in a child job so cancel(taskId) can stop it independently.
            val job = scope.launch { runWithRetry(envelope) }
            runningJobs[envelope.task.id] = job
            job.join()
            runningJobs.remove(envelope.task.id)
            // A task that completed (or failed) normally is no longer cancellable;
            // drop any stale cancellation marker to avoid an unbounded set.
            cancelledIds.remove(envelope.task.id)
        }
    }

    private suspend fun runWithRetry(envelope: Envelope) {
        val task = envelope.task
        activeTaskId.set(task.id)
        var attempts = 0
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                if (task.id in cancelledIds) {
                    cancelledIds.remove(task.id)
                    publish(TaskResult.Cancelled(task.id))
                    return
                }
                attempts++
                val outcome = runAttempt(task, attempts)
                // A cancellation that lands while the task is running (e.g. before
                // the child job is registered in runningJobs) must still surface as
                // Cancelled, not Success.
                if (task.id in cancelledIds) {
                    cancelledIds.remove(task.id)
                    publish(TaskResult.Cancelled(task.id))
                    return
                }
                when (outcome) {
                    is Attempt.Result -> {
                        publish(TaskResult.Success(task.id, outcome.message, attempts))
                        logStore?.recordMetric(
                            PerformanceMetric("task.${task.name}", outcome.durationMs, epochMillis.now())
                        )
                        return
                    }
                    is Attempt.Timeout -> {
                        if (attempts > task.retryPolicy.maxRetries) {
                            publish(TaskResult.TimedOut(task.id, attempts))
                            return
                        }
                    }
                    is Attempt.Error -> {
                        if (attempts > task.retryPolicy.maxRetries) {
                            publish(TaskResult.Failure(task.id, outcome.message, attempts))
                            logStore?.recordError(
                                ErrorLogEntry(
                                    id = UUID.randomUUID().toString(),
                                    source = "task-manager",
                                    message = outcome.message,
                                    stackTrace = null,
                                    timestamp = epochMillis.now()
                                )
                            )
                            return
                        }
                    }
                }
                delay(task.retryPolicy.backoffFor(attempts))
            }
        } catch (e: CancellationException) {
            if (task.id in cancelledIds) {
                cancelledIds.remove(task.id)
                publish(TaskResult.Cancelled(task.id))
            }
            throw e
        } finally {
            activeTaskId.set(null)
        }
    }

    private sealed interface Attempt {
        data class Result(val message: String, val durationMs: Long) : Attempt
        data class Timeout(val durationMs: Long) : Attempt
        data class Error(val message: String, val durationMs: Long) : Attempt
    }

    private suspend fun runAttempt(task: PendingTask, attempt: Int): Attempt {
        val startedAt = epochMillis.now()
        return try {
            val result = task.timeoutMs?.let { timeout ->
                withTimeoutOrNull(timeout) { task.run() }
                    ?: return Attempt.Timeout(epochMillis.now() - startedAt)
            } ?: task.run()
            if (result.success) {
                Attempt.Result(result.message, epochMillis.now() - startedAt)
            } else {
                Attempt.Error(result.message, epochMillis.now() - startedAt)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Attempt.Error(t.message ?: t.javaClass.simpleName, epochMillis.now() - startedAt)
        }
    }

    private fun publish(result: TaskResult) {
        synchronized(lock) {
            resultsFlow.value = (resultsFlow.value + result).takeLast(MAX_HISTORY)
        }
    }

    private companion object {
        const val MAX_HISTORY = 200
    }
}
