package com.nexaflow.core.execution.task

/** Explicit lifecycle used by status UI, debug traces and later recovery checkpoints. */
enum class TaskLifecycleState {
    QUEUED,
    RUNNING,
    RETRY_WAIT,
    CANCEL_REQUESTED,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    DEADLINE_EXCEEDED,
    CANCELLED,
    REJECTED
}

/**
 * Canonical transition contract for the existing task runtime.
 * Terminal states are immutable; a cancellation request is allowed from any
 * non-terminal admitted state and must end in CANCELLED.
 */
fun TaskLifecycleState.canTransitionTo(next: TaskLifecycleState): Boolean = when (this) {
    TaskLifecycleState.QUEUED -> next in setOf(
        TaskLifecycleState.RUNNING,
        TaskLifecycleState.CANCEL_REQUESTED,
        TaskLifecycleState.CANCELLED,
        TaskLifecycleState.DEADLINE_EXCEEDED,
        TaskLifecycleState.REJECTED
    )
    TaskLifecycleState.RUNNING -> next in setOf(
        TaskLifecycleState.RETRY_WAIT,
        TaskLifecycleState.CANCEL_REQUESTED,
        TaskLifecycleState.SUCCEEDED,
        TaskLifecycleState.FAILED,
        TaskLifecycleState.TIMED_OUT,
        TaskLifecycleState.DEADLINE_EXCEEDED,
        TaskLifecycleState.CANCELLED
    )
    TaskLifecycleState.RETRY_WAIT -> next in setOf(
        TaskLifecycleState.RUNNING,
        TaskLifecycleState.CANCEL_REQUESTED,
        TaskLifecycleState.CANCELLED,
        TaskLifecycleState.DEADLINE_EXCEEDED
    )
    TaskLifecycleState.CANCEL_REQUESTED -> next == TaskLifecycleState.CANCELLED
    TaskLifecycleState.SUCCEEDED,
    TaskLifecycleState.FAILED,
    TaskLifecycleState.TIMED_OUT,
    TaskLifecycleState.DEADLINE_EXCEEDED,
    TaskLifecycleState.CANCELLED,
    TaskLifecycleState.REJECTED -> next == this
}

/** A task status entry may be created in any state, but later writes must follow the contract. */
fun TaskLifecycleState?.canStartTransitionTo(next: TaskLifecycleState): Boolean =
    this == null || this.canTransitionTo(next)

data class TaskStatus(
    val taskId: String,
    val name: String,
    val state: TaskLifecycleState,
    val attempt: Int = 0,
    val updatedAt: Long,
    val message: String? = null
)

/** Resources are logical execution locks, not direct Android permission grants. */
enum class TaskResource {
    NETWORK,
    BLUETOOTH,
    LOCATION,
    FILE_IO,
    HEAVY_COMPUTE
}

/** Queue/timeout/resource limits owned by the existing TaskManager. */
data class TaskManagerLimits(
    val maxPendingTasks: Int = 1_000,
    val maxTaskTimeoutMs: Long = 15 * 60 * 1_000L,
    val resourceCapacities: Map<TaskResource, Int> = TaskResource.entries.associateWith { 1 }
) {
    init {
        require(maxPendingTasks > 0) { "maxPendingTasks must be positive" }
        require(maxTaskTimeoutMs > 0) { "maxTaskTimeoutMs must be positive" }
        require(resourceCapacities.values.all { it >= 0 }) { "Resource capacities cannot be negative" }
    }

    fun capacity(resource: TaskResource): Int = resourceCapacities[resource] ?: 0
}

sealed interface TaskAdmission {
    val taskId: String

    data class Accepted(override val taskId: String) : TaskAdmission
    data class Rejected(
        override val taskId: String,
        val reason: TaskRejectionReason
    ) : TaskAdmission
}

sealed interface TaskRejectionReason {
    data object ManagerShutDown : TaskRejectionReason
    data object DuplicateTaskId : TaskRejectionReason
    data object QueueCapacityExceeded : TaskRejectionReason
    data class TimeoutExceedsPolicy(val requestedMs: Long, val maximumMs: Long) : TaskRejectionReason
    data class ResourceUnavailable(val resource: TaskResource) : TaskRejectionReason
    data object DeadlineAlreadyElapsed : TaskRejectionReason
}

fun TaskRejectionReason.message(): String = when (this) {
    TaskRejectionReason.ManagerShutDown -> "Task manager is shut down"
    TaskRejectionReason.DuplicateTaskId -> "A task with this id is already known"
    TaskRejectionReason.QueueCapacityExceeded -> "Task queue capacity exceeded"
    is TaskRejectionReason.TimeoutExceedsPolicy ->
        "Task timeout $requestedMs ms exceeds policy maximum $maximumMs ms"
    is TaskRejectionReason.ResourceUnavailable -> "Resource $resource is disabled by policy"
    TaskRejectionReason.DeadlineAlreadyElapsed -> "Task deadline has already elapsed"
}
