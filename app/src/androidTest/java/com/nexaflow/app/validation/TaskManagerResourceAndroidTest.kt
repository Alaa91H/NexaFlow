package com.nexaflow.app.validation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexaflow.core.execution.task.PendingTask
import com.nexaflow.core.execution.task.TaskAdmission
import com.nexaflow.core.execution.task.TaskManager
import com.nexaflow.core.execution.task.TaskManagerLimits
import com.nexaflow.core.execution.task.TaskResource
import com.nexaflow.core.execution.task.TaskResult
import com.nexaflow.core.rom.model.SystemControlResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected-device contract for the production logical-resource semaphore path.
 *
 * TaskResource is not an Android permission. The test establishes that cancellation unwinds the
 * actual TaskManager withPermit chain and leaves no FILE_IO lease blocking a subsequent task.
 */
@RunWith(AndroidJUnit4::class)
class TaskManagerResourceAndroidTest {

    @Test
    fun cancellingRunningTaskReleasesResourcePermitForFollower() = runBlocking {
        val limits = TaskManagerLimits(
            resourceCapacities = TaskResource.entries.associateWith { resource ->
                if (resource == TaskResource.FILE_IO) 1 else 1
            }
        )
        val manager = TaskManager(limits = limits)
        val holderStarted = Channel<Unit>(capacity = 1)
        val holderFinally = Channel<Unit>(capacity = 1)
        val followerRan = AtomicBoolean(false)
        val holder = PendingTask(
            id = "android-resource-holder",
            name = "resource holder",
            resources = setOf(TaskResource.FILE_IO),
            run = {
                holderStarted.trySend(Unit)
                try {
                    awaitCancellation()
                } finally {
                    holderFinally.trySend(Unit)
                }
            }
        )
        val follower = PendingTask(
            id = "android-resource-follower",
            name = "resource follower",
            resources = setOf(TaskResource.FILE_IO),
            run = {
                followerRan.set(true)
                SystemControlResult.ok("follower acquired released permit")
            }
        )

        try {
            assertTrue(manager.submit(holder) is TaskAdmission.Accepted)
            withTimeout(5_000L) { holderStarted.receive() }
            assertTrue(manager.cancel(holder.id))
            withTimeout(5_000L) { holderFinally.receive() }
            awaitResult(manager, holder.id) { it is TaskResult.Cancelled }

            assertTrue(manager.submit(follower) is TaskAdmission.Accepted)
            assertTrue(manager.awaitIdle(5_000L))
            assertTrue(followerRan.get())
            assertTrue(manager.results.value.any { it is TaskResult.Success && it.taskId == follower.id })
        } finally {
            manager.shutdown()
        }
    }

    private suspend fun awaitResult(
        manager: TaskManager,
        taskId: String,
        predicate: (TaskResult) -> Boolean
    ) = withTimeout(5_000L) {
        while (manager.results.value.none { result -> result.taskId() == taskId && predicate(result) }) {
            delay(10L)
        }
    }

    private fun TaskResult.taskId(): String = when (this) {
        is TaskResult.Success -> taskId
        is TaskResult.Failure -> taskId
        is TaskResult.TimedOut -> taskId
        is TaskResult.DeadlineExceeded -> taskId
        is TaskResult.Cancelled -> taskId
        is TaskResult.Rejected -> taskId
    }
}
