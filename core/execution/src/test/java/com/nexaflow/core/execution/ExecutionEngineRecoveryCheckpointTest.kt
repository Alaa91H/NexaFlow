package com.nexaflow.core.execution

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.DurableExecutionStatus
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.execution.handler.ActionExecutionContext
import com.nexaflow.core.execution.handler.ActionHandler
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.core.execution.recovery.ExecutionRecoveryCoordinator
import com.nexaflow.core.execution.recovery.RecoveryDisposition
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the hand-off between an in-flight action and startup recovery. An
 * exception after ACTION_STARTED must not be hidden by the invocation's
 * terminal cleanup, because the external side effect may already exist.
 */
@RunWith(RobolectricTestRunner::class)
class ExecutionEngineRecoveryCheckpointTest {

    private class CrashingHandler : ActionHandler {
        override val supportedTypes: Set<ActionType> = setOf(ActionType.SYSTEM_SEND_NOTIFICATION)

        override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
            throw IllegalStateException("side effect interrupted")
        }
    }

    private class NoopHistory : HistoryRepository {
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(emptyList())
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> =
            object : PagingSource<Int, ExecutionRecord>() {
                override fun getRefreshKey(state: PagingState<Int, ExecutionRecord>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ExecutionRecord> =
                    LoadResult.Page(emptyList(), null, null)
            }
        override suspend fun getExecutionById(id: String): ExecutionRecord? = null
        override suspend fun recordExecution(record: ExecutionRecord) = Unit
    }

    private fun automation() = Automation(
        id = "checkpoint-recovery-task",
        name = "Checkpoint recovery task",
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = emptyList(),
        actions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "test"))),
        exitActions = emptyList(),
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun crashedActionIsRetainedAndClassifiedForVerification() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ActiveExecutionStore(context)
        val runId = "unknown-${System.nanoTime()}"
        val engine = ExecutionEngine(
            context = context,
            historyRepository = NoopHistory(),
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(listOf(CrashingHandler())),
            activeExecutionStore = store
        )
        try {
            val failure = runCatching {
                engine.runAutomation(
                    automation = automation(),
                    runContext = WorkflowRunContext(runId, "checkpoint-recovery-task", 1L)
                )
            }.exceptionOrNull()

            assertNotNull("the handler failure must reach the caller", failure)
            assertEquals(
                DurableExecutionStatus.ACTION_UNKNOWN,
                store.checkpoint(runId)?.status
            )

            val report = ExecutionRecoveryCoordinator(store).reconcileStartup()
            val item = report.items.single { it.checkpoint.runId == runId }
            assertEquals(RecoveryDisposition.VERIFY_OR_COMPENSATE_REQUIRED, item.disposition)
            assertEquals(DurableExecutionStatus.ACTION_UNKNOWN, item.checkpoint.recoverySourceStatus)
            assertEquals(DurableExecutionStatus.RECOVERY_REQUIRED, store.checkpoint(runId)?.status)
        } finally {
            store.clearCheckpoint(runId)
            store.clear("checkpoint-recovery-task")
        }
    }
}
