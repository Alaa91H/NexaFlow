package com.nexaflow.feature.dashboard

import android.content.Context
import android.os.Looper
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Adversarial coverage of [DashboardViewModel.deleteAutomation], the dashboard
 * delete entry point: engine delegation with a confirmation message on
 * success, and repository-failure isolation (task still exists -> engine state
 * intact + failure message instead of an app crash). Drives the real
 * [ExecutionEngine] and its real durable ledger under Robolectric.
 */
private fun emptyPaging(): PagingSource<Int, ExecutionRecord> =
    object : PagingSource<Int, ExecutionRecord>() {
        override fun getRefreshKey(state: PagingState<Int, ExecutionRecord>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ExecutionRecord> =
            LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
    }

@RunWith(RobolectricTestRunner::class)
class DashboardViewModelDeleteTest {

    private lateinit var context: Context
    private lateinit var engine: ExecutionEngine

    private class FakeHistory : HistoryRepository {
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(emptyList())
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> = emptyPaging()
        override suspend fun getExecutionById(id: String): ExecutionRecord? = null
        override suspend fun recordExecution(record: ExecutionRecord) = Unit
    }

    private class FakeRepository(
        private val throwOnDelete: Boolean = false
    ) : AutomationRepository {
        override fun getAutomations(): Flow<List<Automation>> = flowOf(emptyList())
        override suspend fun getAutomationById(id: String): Automation? = null
        override suspend fun saveAutomation(automation: Automation) = Unit
        override suspend fun deleteAutomation(automation: Automation) {
            if (throwOnDelete) throw IllegalStateException("simulated database write failure")
        }
        override suspend fun updateAutomationStatus(id: String, enabled: Boolean) = Unit
    }

    private fun task(id: String): Automation = Automation(
        id = id,
        name = "Dashboard delete test",
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = listOf(Trigger(TriggerType.CONNECTIVITY, mapOf("state" to "CONNECTED"))),
        actions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, emptyMap())),
        exitActions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, emptyMap())),
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun newEngine(): ExecutionEngine = ExecutionEngine(
        context = context,
        historyRepository = FakeHistory(),
        notificationPreferences = NotificationPreferences(context),
        actionRegistry = ActionRegistry.from(emptyList())
    )

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        engine = newEngine()
        listOf("vm-dash-delete-a", "vm-dash-delete-b").forEach {
            ActiveExecutionStore(context).clear(it)
        }
    }

    private fun arm(id: String) = runBlocking { engine.runAutomation(task(id)) }

    private fun viewModel(repo: AutomationRepository): DashboardViewModel = DashboardViewModel(
        automationRepository = repo,
        executionEngine = engine,
        historyRepository = FakeHistory(),
        appContext = context
    )

    private fun awaitIdle(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("condition not met within ${timeoutMs}ms")
    }

    private fun freshExitMessage(id: String): String = runBlocking {
        newEngine().runExit(task(id)).message
    }

    @Test
    fun successfulDeleteDelegatesToEngineAndConfirms() {
        val id = "vm-dash-delete-a"
        arm(id)
        val viewModel = viewModel(FakeRepository())

        viewModel.deleteAutomation(task(id))
        awaitIdle { viewModel.executionMessage.value != null }

        assertEquals(
            context.getString(R.string.task_deleted, task(id).name),
            viewModel.executionMessage.value
        )
        assertTrue(
            "durable marker must be cleared",
            freshExitMessage(id).contains("task was not active")
        )
    }

    @Test
    fun deleteWhenRepositoryThrowsKeepsEngineStateAndReportsFailure() {
        val id = "vm-dash-delete-b"
        arm(id)
        val viewModel = viewModel(FakeRepository(throwOnDelete = true))

        viewModel.deleteAutomation(task(id))
        awaitIdle { viewModel.executionMessage.value != null }

        // The task still exists (Room rolls the delete back), so its engine
        // state must stay intact and the user must see a failure, not a crash.
        assertTrue(
            "engine marker must remain while the task still exists",
            !freshExitMessage(id).contains("task was not active")
        )
        assertEquals(
            context.getString(R.string.task_delete_failed, task(id).name),
            viewModel.executionMessage.value
        )
    }
}
