package com.nexaflow.feature.automations

import android.content.Context
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
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
import com.nexaflow.domain.models.AutomationHealthReport
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HealthRepository
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
 * Adversarial coverage of [AutomationDetailsViewModel.delete], the real delete
 * entry point for the details screen: delegation to the engine, navigation
 * once, repo-failure isolation (task still exists -> engine state intact, no
 * navigation, no crash), row-already-gone cleanup, and double-tap protection.
 * Drives the real [ExecutionEngine] and its real durable ledger under
 * Robolectric, exactly like the core:execution delete lifecycle tests.
 */
private fun emptyPaging(): PagingSource<Int, ExecutionRecord> =
    object : PagingSource<Int, ExecutionRecord>() {
        override fun getRefreshKey(state: PagingState<Int, ExecutionRecord>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ExecutionRecord> =
            LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
    }

@RunWith(RobolectricTestRunner::class)
class AutomationDetailsViewModelDeleteTest {

    private lateinit var context: Context
    private lateinit var engine: ExecutionEngine

    private class FakeHistory : HistoryRepository {
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(emptyList())
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> = emptyPaging()
        override suspend fun getExecutionById(id: String): ExecutionRecord? = null
        override suspend fun recordExecution(record: ExecutionRecord) = Unit
    }

    private class FakeHealth : HealthRepository {
        override suspend fun getHealthReport(automationId: String): AutomationHealthReport =
            throw NotImplementedError()
        override fun getHealthReports(): Flow<List<AutomationHealthReport>> = flowOf(emptyList())
    }

    private class FakeRepository(
        private val automation: Automation?,
        private val throwOnDelete: Boolean = false
    ) : AutomationRepository {
        override fun getAutomations(): Flow<List<Automation>> =
            flowOf(automation?.let(::listOf) ?: emptyList())
        override suspend fun getAutomationById(id: String): Automation? =
            automation?.takeIf { it.id == id }
        override suspend fun saveAutomation(automation: Automation) = Unit
        override suspend fun deleteAutomation(automation: Automation) {
            if (throwOnDelete) throw IllegalStateException("simulated database write failure")
        }
        override suspend fun updateAutomationStatus(id: String, enabled: Boolean) = Unit
    }

    private fun task(id: String): Automation = Automation(
        id = id,
        name = "Delete test",
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
        // Unique ids per test isolate the JVM-wide DataStore singleton; clear
        // first so the class stays re-runnable in one JVM.
        listOf("vm-delete-a", "vm-delete-b", "vm-delete-c", "vm-delete-d").forEach {
            ActiveExecutionStore(context).clear(it)
        }
    }

    /** Arms the durable active marker for [id] through the real engine. */
    private fun arm(id: String) = runBlocking { engine.runAutomation(task(id)) }

    private fun vm(id: String, repo: AutomationRepository): AutomationDetailsViewModel =
        AutomationDetailsViewModel(
            repository = repo,
            healthRepository = FakeHealth(),
            executionEngine = engine,
            savedStateHandle = SavedStateHandle(mapOf("automationId" to id)),
            appContext = context
        )

    /**
     * Drives the viewModelScope coroutine (posted to the Robolectric main
     * looper) while the real DataStore completes on its own threads.
     */
    private fun awaitIdle(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("condition not met within ${timeoutMs}ms")
    }

    /** A fresh engine over the same durable ledger reports the exit outcome. */
    private fun freshExitMessage(id: String): String = runBlocking {
        newEngine().runExit(task(id)).message
    }

    @Test
    fun successfulDeleteDelegatesToEngineAndNavigatesOnce() {
        val id = "vm-delete-a"
        arm(id)
        var navigated = 0
        val viewModel = vm(id, FakeRepository(automation = task(id)))

        viewModel.delete { navigated++ }
        awaitIdle { navigated == 1 }

        assertEquals("navigation must fire exactly once", 1, navigated)
        assertTrue(
            "durable marker must be cleared so no stale exit can run",
            freshExitMessage(id).contains("task was not active")
        )
    }

    @Test
    fun deleteWhenRepositoryThrowsKeepsEngineStateAndDoesNotNavigate() {
        val id = "vm-delete-b"
        arm(id)
        var navigated = 0
        val viewModel = vm(id, FakeRepository(automation = task(id), throwOnDelete = true))

        viewModel.delete { navigated++ }
        awaitIdle { viewModel.executionMessage.value != null }

        // The task still exists (Room rolls the delete back), so its engine
        // state must stay intact and the user must not be navigated away.
        assertEquals("must not navigate on a failed delete", 0, navigated)
        assertTrue(
            "engine marker must remain while the task still exists",
            !freshExitMessage(id).contains("task was not active")
        )
        assertEquals(
            "the failure must be surfaced instead of crashing the app",
            context.getString(R.string.task_delete_failed),
            viewModel.executionMessage.value
        )
    }

    @Test
    fun deleteWhenRowAlreadyGoneStillCleansUpAndNavigates() {
        val id = "vm-delete-c"
        arm(id)
        var navigated = 0
        // The row is gone (deleted elsewhere): getAutomationById returns null.
        val viewModel = vm(id, FakeRepository(automation = null))

        viewModel.delete { navigated++ }
        awaitIdle { navigated == 1 }

        assertEquals(1, navigated)
        assertTrue(
            "engine cleanup must still run for the orphaned marker",
            freshExitMessage(id).contains("task was not active")
        )
    }

    @Test
    fun doubleTapDeletesExactlyOnce() {
        val id = "vm-delete-d"
        arm(id)
        var navigated = 0
        val viewModel = vm(id, FakeRepository(automation = task(id)))

        viewModel.delete { navigated++ }
        viewModel.delete { navigated++ }
        awaitIdle { navigated == 1 }
        Thread.sleep(150)
        shadowOf(Looper.getMainLooper()).idle()

        // A second pop would remove the screen below the details screen.
        assertEquals("the second tap must be ignored", 1, navigated)
    }
}
