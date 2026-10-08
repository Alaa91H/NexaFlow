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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
        require(backoffMultiplier > 0 && backoffMultiplier.isFinite()) {
            "backoffMultiplier must be finite and positive"
        }
    }

    /** Backoff before attempt [attempt] (1-based; attempt one has no backoff). */
    fun backoffFor(attempt: Int): Long {
        if (attempt <= 1) return 0L
        if (initialBackoffMs == 0L) return 0L
        val backoff = initialBackoffMs.toDouble() * Math.pow(backoffMultiplier, (attempt - 2).toDouble())
        return if (!backoff.isFinite() || backoff >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE
        else backoff.toLong()
    }
}

/** A unit of work the [TaskManager] can schedule, prioritize, retry and cancel. */
data class PendingTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val retryPolicy: RetryPolicy = RetryPolicy(),
    /** Maximum duration for one attempt; null keeps the policy default. */
    val timeoutMs: Long? = null,
    /** Set only when interruption guarantees that no external effect can remain in flight. */
    val safeToCancel: Boolean = false,
    /** Absolute wall-clock deadline for the whole task lifecycle, including retries. */
    val deadlineAtMs: Long? = null,
    /** Logical execution locks required while this task attempts its work. */
    val resources: Set<TaskResource> = emptySet(),
    val run: suspend () -> SystemControlResult
) {
    init {
        require(id.isNotBlank()) { "Task id must not be blank" }
        require(name.isNotBlank()) { "Task name must not be blank" }
        require(timeoutMs == null || timeoutMs > 0L) { "timeoutMs must be positive when supplied" }
    }
}

/** Final outcome of a task, published to [TaskManager.results]. */
sealed interface TaskResult {
    data class Success(val taskId: String, val message: String, val attempts: Int) : TaskResult
    data class Failure(val taskId: String, val message: String, val attempts: Int) : TaskResult
    data class TimedOut(val taskId: String, val attempts: Int) : TaskResult
    data class DeadlineExceeded(val taskId: String, val attempts: Int) : TaskResult
    data class Cancelled(val taskId: String) : TaskResult
    data class Rejected(val taskId: String, val reason: TaskRejectionReason) : TaskResult
    data class Unknown(val taskId: String, val message: String, val attempts: Int) : TaskResult
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
    private val epochMillis: EpochMillis = EpochMillis.System,
    private val limits: TaskManagerLimits = TaskManagerLimits()
) {

    private data class Envelope(val task: PendingTask, val seq: Long, val enqueuedAtMs: Long)

    private data class ExecutionSnapshot(
        val attempts: Int,
        val dispatched: Boolean,
        val completedAttempt: Attempt?
    )

    /** Shared by the child and its single-result-publishing worker during cancellation races. */
    private class TaskExecutionTracker {
        private var attempts = 0
        private var dispatched = false
        private var completedAttempt: Attempt? = null

        @Synchronized
        fun beginAttempt(number: Int) {
            attempts = number
            dispatched = false
            completedAttempt = null
        }

        @Synchronized
        fun markDispatched() {
            dispatched = true
        }

        @Synchronized
        fun completeAttempt(outcome: Attempt) {
            dispatched = false
            completedAttempt = outcome
        }

        @Synchronized
        fun snapshot(): ExecutionSnapshot = ExecutionSnapshot(attempts, dispatched, completedAttempt)
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val queue = PriorityQueue<Envelope>(
        compareByDescending<Envelope> { it.task.priority }.thenBy { it.seq }
    )
    private val lock = Any()
    private val seqCounter = AtomicLong(0)
    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()
    // A conflated wake-up replaces continuous empty-queue polling. One pending
    // signal is sufficient because the consumer drains the priority queue fully.
    private val queueWakeups = Channel<Unit>(Channel.CONFLATED)
    private val runningJobs = ConcurrentHashMap<String, Job>()
    private val executionTrackers = ConcurrentHashMap<String, TaskExecutionTracker>()
    private val knownTaskIds = ConcurrentHashMap.newKeySet<String>()
    private val resourcePermits = limits.resourceCapacities
        .filterValues { it > 0 }
        .mapValues { (_, capacity) -> Semaphore(capacity) }
    private val resultsFlow = MutableStateFlow<List<TaskResult>>(emptyList())
    private val statusesFlow = MutableStateFlow<Map<String, TaskStatus>>(emptyMap())
    @Volatile private var shutDown = false

    val results: StateFlow<List<TaskResult>> = resultsFlow.asStateFlow()
    /** Latest lifecycle status keyed by task id; terminal history is bounded with results. */
    val statuses: StateFlow<Map<String, TaskStatus>> = statusesFlow.asStateFlow()

    val pendingCount: Int
        get() = synchronized(lock) { queue.size }

    /** True while a task is executing. */
    val isRunning: Boolean get() = activeTaskId.get() != null

    private val activeTaskId = java.util.concurrent.atomic.AtomicReference<String?>(null)

    init {
        scope.launch { processQueue() }
    }

    /**
     * Attempts to admit a task into the existing priority queue. Rejections are
     * explicit and terminal; no task is silently dropped because a limit or
     * deadline was invalid at submission time.
     */
    fun submit(task: PendingTask): TaskAdmission {
        val now = epochMillis.now()
        val rejection = synchronized(lock) {
            when {
                shutDown -> TaskRejectionReason.ManagerShutDown
                task.id in knownTaskIds -> TaskRejectionReason.DuplicateTaskId
                queue.size >= limits.maxPendingTasks -> TaskRejectionReason.QueueCapacityExceeded
                task.timeoutMs != null && task.timeoutMs > limits.maxTaskTimeoutMs ->
                    TaskRejectionReason.TimeoutExceedsPolicy(task.timeoutMs, limits.maxTaskTimeoutMs)
                task.deadlineAtMs != null && task.deadlineAtMs <= now -> TaskRejectionReason.DeadlineAlreadyElapsed
                else -> task.resources.firstOrNull { limits.capacity(it) <= 0 }
                    ?.let(TaskRejectionReason::ResourceUnavailable)
            }
        }
        if (rejection != null) {
            publish(TaskResult.Rejected(task.id, rejection))
            publishRejectedStatus(task, rejection.message())
            return TaskAdmission.Rejected(task.id, rejection)
        }
        synchronized(lock) {
            // The checks are repeated under the same lock that mutates the
            // queue, so concurrent submitters cannot bypass capacity/duplicate
            // admission after the optimistic evaluation above.
            if (shutDown || task.id in knownTaskIds || queue.size >= limits.maxPendingTasks) {
                val racedReason = when {
                    shutDown -> TaskRejectionReason.ManagerShutDown
                    task.id in knownTaskIds -> TaskRejectionReason.DuplicateTaskId
                    else -> TaskRejectionReason.QueueCapacityExceeded
                }
                publish(TaskResult.Rejected(task.id, racedReason))
                publishRejectedStatus(task, racedReason.message())
                return TaskAdmission.Rejected(task.id, racedReason)
            }
            knownTaskIds.add(task.id)
            // Record QUEUED under the same lock that publishes the task to the
            // worker: otherwise the worker can poll the envelope and publish
            // RUNNING before this thread publishes QUEUED, and updateStatus
            // rejects the illegal RUNNING -> QUEUED regression.
            publishStatus(task, TaskLifecycleState.QUEUED)
            queue.add(Envelope(task, seqCounter.incrementAndGet(), now))
        }
        // Non-blocking and conflated: a closed worker is shutting down, while
        // one signal wakes the idle consumer for any number of queued tasks.
        queueWakeups.trySend(Unit)
        return TaskAdmission.Accepted(task.id)
    }

    /** Backward-compatible enqueue facade. Inspect [results] for a rejection. */
    fun enqueue(task: PendingTask): String {
        submit(task)
        return task.id
    }

    /**
     * Cancels [taskId]: queued tasks are skipped, a running task's job is
     * cancelled immediately. The terminal [TaskResult.Cancelled] is published
     * either way. Returns true when the id was known (queued or running).
     */
    fun cancel(taskId: String): Boolean {
        val queuedCancellation = synchronized(lock) {
            if (taskId !in knownTaskIds) return false
            val current = statusFor(taskId)
            if (current == null || !current.state.canTransitionTo(TaskLifecycleState.CANCEL_REQUESTED)) {
                return false
            }
            cancelledIds.add(taskId)
            updateStatus(
                current.copy(
                    state = TaskLifecycleState.CANCEL_REQUESTED,
                    updatedAt = epochMillis.now()
                )
            )
            val queued = queue.firstOrNull { it.task.id == taskId }
            if (queued != null) {
                queue.remove(queued)
                // A queued task has not dispatched an effect and is certainly cancelled.
                publish(TaskResult.Cancelled(taskId))
                publishStatus(queued.task, TaskLifecycleState.CANCELLED)
                cancelledIds.remove(taskId)
                knownTaskIds.remove(taskId)
                return true
            }
            false
        }
        if (!queuedCancellation) runningJobs[taskId]?.cancel()
        return true
    }

    /** Blocks until the queue drains or [timeoutMs] elapses. Returns true when idle. */
    suspend fun awaitIdle(timeoutMs: Long = 5_000L): Boolean {
        val startedAtNanos = System.nanoTime()
        while (timeoutMs > 0L && (System.nanoTime() - startedAtNanos) / 1_000_000L < timeoutMs) {
            if (synchronized(lock) { queue.isEmpty() && activeTaskId.get() == null }) return true
            delay(10)
        }
        return synchronized(lock) { queue.isEmpty() && activeTaskId.get() == null }
    }

    /**
     * Stops the worker and records queued/running tasks as cancelled: no new
     * submissions are admitted, tasks still waiting in the queue are abandoned
     * (published Cancelled), and running tasks receive cancellation requests.
     *
     * Closing the wake-up channel lets an idle worker leave its wait. The
     * worker itself is not cancelled here: it must join the running child so a
     * non-cooperative external effect cannot be reported idle before it ends.
     */
    fun shutdown() {
        val abandoned = synchronized(lock) {
            if (shutDown) return
            shutDown = true
            activeTaskId.get()?.let(cancelledIds::add)
            queue.toList().also { queue.clear() }
        }
        // Abandon queued work synchronously so shutdown() is terminal from the
        // caller's perspective: nothing queued here executes after shutdown.
        abandoned.forEach { envelope ->
            val current = statusFor(envelope.task.id)
            if (current != null && current.state.canTransitionTo(TaskLifecycleState.CANCEL_REQUESTED)) {
                updateStatus(current.copy(state = TaskLifecycleState.CANCEL_REQUESTED, updatedAt = epochMillis.now()))
            }
            knownTaskIds.remove(envelope.task.id)
            cancelledIds.remove(envelope.task.id)
            publish(TaskResult.Cancelled(envelope.task.id))
            publishStatus(envelope.task, TaskLifecycleState.CANCELLED)
        }
        // Cancel running tasks so their jobs unwind (publishing Cancelled) and
        // processEnvelope's finally runs for each.
        runningJobs.keys.forEach { taskId ->
            cancelledIds.add(taskId)
            val current = statusFor(taskId)
            if (current != null && current.state.canTransitionTo(TaskLifecycleState.CANCEL_REQUESTED)) {
                updateStatus(current.copy(state = TaskLifecycleState.CANCEL_REQUESTED, updatedAt = epochMillis.now()))
            }
            runningJobs[taskId]?.cancel()
        }
        // Closing wakes an idle worker. Do not cancel the parent scope here:
        // cancellation would interrupt join() and let shutdown return while
        // an operation is still executing.
        queueWakeups.close()
    }

    private suspend fun processQueue() {
        try {
            while (currentCoroutineContext().isActive) {
                val envelope = pollOrWait()
                if (envelope == null) {
                    if (synchronized(lock) { shutDown }) break
                } else {
                    processEnvelope(envelope)
                }
            }
        } finally {
            // This runs only after processEnvelope joined any dispatched child.
            scope.cancel()
        }
    }

    /**
     * Pops the next task, or waits for an enqueue signal when the queue is
     * empty. Marks the task active atomically with the poll so awaitIdle()
     * never sees an empty queue + idle worker while a task is about to start.
     *
     * The wake-up channel is closed by [shutdown] while an idle worker may be
     * suspended here. [receiveCatching] converts that close into a benign
     * null poll instead of a ClosedReceiveChannelException escaping the
     * SupervisorJob scope (unhandled -> process crash); shutdown observes the
     * null poll and exits the worker loop.
     */
    private suspend fun pollOrWait(): Envelope? {
        val polled = synchronized(lock) {
            val now = epochMillis.now()
            // Do not change the PriorityQueue comparator while entries are in
            // the heap. Promote the oldest task that crossed the aging bound
            // at selection time, preserving FIFO among aged tasks.
            val aged = queue.asSequence()
                .filter { envelope ->
                    now >= envelope.enqueuedAtMs &&
                        now - envelope.enqueuedAtMs >= limits.priorityAgingIntervalMs
                }
                .minByOrNull(Envelope::seq)
            val head = aged?.also(queue::remove) ?: queue.poll()
            if (head != null) activeTaskId.set(head.task.id)
            head
        }
        if (polled == null) queueWakeups.receiveCatching()
        return polled
    }

    private suspend fun processEnvelope(envelope: Envelope) {
        if (envelope.task.id in cancelledIds) {
            cancelledIds.remove(envelope.task.id)
            // Publish BEFORE clearing the active id: otherwise awaitIdle() can
            // observe an empty queue + idle worker while the Cancelled result
            // is not yet visible, and return before the cancellation lands.
            publish(TaskResult.Cancelled(envelope.task.id))
            publishStatus(envelope.task, TaskLifecycleState.CANCELLED)
            knownTaskIds.remove(envelope.task.id)
            activeTaskId.set(null)
            return
        }
        val tracker = TaskExecutionTracker()
        executionTrackers[envelope.task.id] = tracker
        // Register the child before it can execute. A DEFAULT launch can run
        // `task.run()` before this map write, leaving a narrow window where
        // cancel(taskId) records the request but cannot interrupt the running
        // job until its own next suspension. LAZY creation closes that gap.
        val job = scope.launch(start = CoroutineStart.LAZY) { runWithRetry(envelope, tracker) }
        runningJobs[envelope.task.id] = job
        job.start()
        try {
            try {
                job.join()
            } catch (e: CancellationException) {
                // Only the worker's OWN cancellation (scope teardown during
                // shutdown) surfaces here: a child job's cancellation does not
                // throw from join(), it completes normally. Record the child's
                // cancelled outcome before deciding whether to propagate.
                if (job.isCancelled) {
                    publishCancellationOutcome(envelope, tracker)
                }
                // shutdown() cancelled this worker coroutine while it was
                // suspended in join(); propagate so the worker exits.
                if (!currentCoroutineContext().isActive) throw e
            }
            // join() returned: the child completed, either normally (its own
            // terminal publish already happened in runWithRetry) or cancelled by
            // manager.cancel(taskId), which publishes nothing. This worker is the
            // single publisher when cancellation interrupted the child. Resolve
            // from the last observed attempt so a dispatched side effect is not
            // mislabeled as definitely cancelled.
            if (job.isCancelled) {
                publishCancellationOutcome(envelope, tracker)
            }
        } finally {
            // A task that completed (or failed) normally is no longer cancellable;
            // drop any stale cancellation marker to avoid an unbounded set and allow
            // a later explicit re-submit with the same id. Keep activeTaskId set
            // until this lifecycle cleanup commits: awaitIdle() must never report
            // idle while cancel() can still observe the completed task as known.
            runningJobs.remove(envelope.task.id)
            executionTrackers.remove(envelope.task.id)
            cancelledIds.remove(envelope.task.id)
            knownTaskIds.remove(envelope.task.id)
            activeTaskId.compareAndSet(envelope.task.id, null)
        }
    }

    private fun publishCancellationOutcome(envelope: Envelope, tracker: TaskExecutionTracker) {
        val task = envelope.task
        val current = statusFor(task.id)
        if (current?.state?.isTerminal() == true) return

        val snapshot = tracker.snapshot()
        val resultAndState = when (val completed = snapshot.completedAttempt) {
            is Attempt.Result -> TaskResult.Success(task.id, completed.message, snapshot.attempts) to
                TaskLifecycleState.SUCCEEDED
            is Attempt.Unknown -> TaskResult.Unknown(task.id, completed.message, snapshot.attempts) to
                TaskLifecycleState.UNKNOWN
            else -> if (snapshot.dispatched && !task.safeToCancel) {
                TaskResult.Unknown(
                    task.id,
                    "Cancellation interrupted a dispatched effect; its outcome is unconfirmed",
                    snapshot.attempts
                ) to TaskLifecycleState.UNKNOWN
            } else {
                TaskResult.Cancelled(task.id) to TaskLifecycleState.CANCELLED
            }
        }
        publish(resultAndState.first)
        publishStatus(task, resultAndState.second, snapshot.attempts)
    }

    private suspend fun runWithRetry(envelope: Envelope, tracker: TaskExecutionTracker) {
        val task = envelope.task
        activeTaskId.set(task.id)
        var attempts = 0
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                if (task.id in cancelledIds) {
                    throw CancellationException("Task ${task.id} cancelled by manager")
                }
                if (deadlineElapsed(task)) {
                    publish(TaskResult.DeadlineExceeded(task.id, attempts))
                    publishStatus(task, TaskLifecycleState.DEADLINE_EXCEEDED, attempts)
                    return
                }
                attempts++
                tracker.beginAttempt(attempts)
                publishStatus(task, TaskLifecycleState.RUNNING, attempts)
                val outcome = runAttempt(task, tracker)
                when (outcome) {
                    is Attempt.Result -> {
                        publish(TaskResult.Success(task.id, outcome.message, attempts))
                        publishStatus(task, TaskLifecycleState.SUCCEEDED, attempts, outcome.message)
                        logStore?.recordMetric(
                            PerformanceMetric("task.${task.name}", outcome.durationMs, epochMillis.now())
                        )
                        return
                    }
                    is Attempt.Unknown -> {
                        publish(TaskResult.Unknown(task.id, outcome.message, attempts))
                        publishStatus(task, TaskLifecycleState.UNKNOWN, attempts, outcome.message)
                        return
                    }
                    is Attempt.Timeout -> {
                        if (task.id in cancelledIds) {
                            publish(TaskResult.Cancelled(task.id))
                            publishStatus(task, TaskLifecycleState.CANCELLED, attempts)
                            return
                        }
                        if (attempts > task.retryPolicy.maxRetries) {
                            publish(TaskResult.TimedOut(task.id, attempts))
                            publishStatus(task, TaskLifecycleState.TIMED_OUT, attempts)
                            return
                        }
                    }
                    is Attempt.Error -> {
                        if (task.id in cancelledIds) {
                            publish(TaskResult.Cancelled(task.id))
                            publishStatus(task, TaskLifecycleState.CANCELLED, attempts)
                            return
                        }
                        if (attempts > task.retryPolicy.maxRetries) {
                            publish(TaskResult.Failure(task.id, outcome.message, attempts))
                            publishStatus(task, TaskLifecycleState.FAILED, attempts, outcome.message)
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
                if (deadlineElapsed(task)) {
                    publish(TaskResult.DeadlineExceeded(task.id, attempts))
                    publishStatus(task, TaskLifecycleState.DEADLINE_EXCEEDED, attempts)
                    return
                }
                if (task.id in cancelledIds) {
                    publish(TaskResult.Cancelled(task.id))
                    publishStatus(task, TaskLifecycleState.CANCELLED, attempts)
                    return
                }
                publishStatus(task, TaskLifecycleState.RETRY_WAIT, attempts)
                val backoffMs = task.retryPolicy.backoffFor(attempts + 1)
                val remainingMs = task.deadlineAtMs?.let { deadline ->
                    if (deadline <= epochMillis.now()) 0L else deadline - epochMillis.now()
                }
                delay(remainingMs?.let { minOf(backoffMs, it) } ?: backoffMs)
            }
        } finally {
            // processEnvelope clears activeTaskId only after it has removed this
            // task from the running and admission ledgers. That ordering makes
            // awaitIdle() a true terminal-state barrier rather than a race.
        }
    }

    private sealed interface Attempt {
        data class Result(val message: String, val durationMs: Long) : Attempt
        data class Timeout(val durationMs: Long) : Attempt
        data class Error(val message: String, val durationMs: Long) : Attempt
        data class Unknown(val message: String, val durationMs: Long) : Attempt
    }

    private suspend fun runAttempt(task: PendingTask, tracker: TaskExecutionTracker): Attempt {
        val startedAt = epochMillis.now()
        var dispatched = false
        return try {
            val executeWithResources: suspend () -> SystemControlResult = {
                withResources(task.resources) {
                    dispatched = true
                    tracker.markDispatched()
                    task.run()
                }
            }
            val result = task.timeoutMs?.let { timeout ->
                withTimeoutOrNull(timeout) { executeWithResources() }
                    ?: return (if (dispatched && !task.safeToCancel) {
                        Attempt.Unknown(
                            "Task timed out after dispatch; its effect may have completed",
                            epochMillis.now() - startedAt
                        )
                    } else {
                        Attempt.Timeout(epochMillis.now() - startedAt)
                    }).also(tracker::completeAttempt)
            } ?: executeWithResources()
            val outcome = when {
                result.outcomeUncertain -> Attempt.Unknown(
                    result.message,
                    epochMillis.now() - startedAt
                )
                result.success -> Attempt.Result(result.message, epochMillis.now() - startedAt)
                else -> Attempt.Error(result.message, epochMillis.now() - startedAt)
            }
            tracker.completeAttempt(outcome)
            outcome
        } catch (e: CancellationException) {
            // Leave the tracker marked as dispatched so the worker can resolve
            // cancellation as UNKNOWN when an external effect may still land.
            if (!dispatched) tracker.completeAttempt(Attempt.Timeout(epochMillis.now() - startedAt))
            throw e
        } catch (t: Throwable) {
            val outcome = if (dispatched) {
                Attempt.Unknown(
                    "Task failed after dispatch; its effect may have completed: " +
                        (t.message ?: t.javaClass.simpleName),
                    epochMillis.now() - startedAt
                )
            } else {
                Attempt.Error(t.message ?: t.javaClass.simpleName, epochMillis.now() - startedAt)
            }
            tracker.completeAttempt(outcome)
            outcome
        }
    }

    private fun TaskLifecycleState.isTerminal(): Boolean = when (this) {
        TaskLifecycleState.SUCCEEDED,
        TaskLifecycleState.FAILED,
        TaskLifecycleState.TIMED_OUT,
        TaskLifecycleState.DEADLINE_EXCEEDED,
        TaskLifecycleState.CANCELLED,
        TaskLifecycleState.UNKNOWN,
        TaskLifecycleState.REJECTED -> true
        TaskLifecycleState.QUEUED,
        TaskLifecycleState.RUNNING,
        TaskLifecycleState.RETRY_WAIT,
        TaskLifecycleState.CANCEL_REQUESTED -> false
    }

    private fun deadlineElapsed(task: PendingTask): Boolean =
        task.deadlineAtMs?.let { epochMillis.now() >= it } ?: false

    private suspend fun <T> withResources(
        resources: Set<TaskResource>,
        block: suspend () -> T
    ): T {
        val permits = resources.sortedBy { it.ordinal }.map { resource ->
            resourcePermits[resource] ?: error("No semaphore configured for $resource")
        }
        suspend fun acquire(index: Int): T = if (index == permits.size) {
            block()
        } else {
            permits[index].withPermit { acquire(index + 1) }
        }
        return acquire(0)
    }

    private fun publishStatus(
        task: PendingTask,
        state: TaskLifecycleState,
        attempt: Int = 0,
        message: String? = null
    ) = updateStatus(
        TaskStatus(task.id, task.name, state, attempt, epochMillis.now(), message)
    )

    private fun statusFor(taskId: String): TaskStatus? = statusesFlow.value[taskId]

    private fun publishRejectedStatus(task: PendingTask, message: String) {
        val current = statusFor(task.id)
        if (current == null || current.state.canTransitionTo(TaskLifecycleState.REJECTED)) {
            publishStatus(task, TaskLifecycleState.REJECTED, message = message)
        }
    }

    private fun updateStatus(status: TaskStatus) {
        synchronized(lock) {
            val previous = statusesFlow.value[status.taskId]?.state
            check(previous.canStartTransitionTo(status.state)) {
                "Invalid lifecycle transition for ${status.taskId}: $previous -> ${status.state}"
            }
            val next = LinkedHashMap(statusesFlow.value)
            next.remove(status.taskId)
            next[status.taskId] = status
            while (next.size > MAX_STATUS_HISTORY) {
                next.entries.iterator().next().also { next.remove(it.key) }
            }
            statusesFlow.value = next
        }
    }

    private fun publish(result: TaskResult) {
        synchronized(lock) {
            resultsFlow.value = (resultsFlow.value + result).takeLast(MAX_HISTORY)
        }
    }

    private companion object {
        const val MAX_HISTORY = 200
        const val MAX_STATUS_HISTORY = 500
    }
}
