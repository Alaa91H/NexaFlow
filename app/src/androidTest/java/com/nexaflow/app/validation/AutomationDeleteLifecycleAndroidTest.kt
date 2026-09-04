package com.nexaflow.app.validation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexaflow.core.database.AppDatabase
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.engine.MonitoringService
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.data.repository.HistoryRepositoryImpl
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device proof of the delete lifecycle contract, driving the REAL
 * [ExecutionEngine], REAL on-device DataStore ledger, and REAL app process
 * (no mocks, no Robolectric):
 *
 *  - After [ExecutionEngine.onAutomationDeleted], the durable active marker is
 *    gone, so a fresh engine instance (the process-restart view) can never run
 *    a stale end behavior for an automation that no longer exists.
 *  - Before the delete, the same marker IS honored exactly once (baseline), so
 *    the test cannot pass vacuously.
 *
 * The action registry is intentionally empty: every dispatch is recorded with
 * its message in the in-memory Room history, which is what the assertions read.
 */
@RunWith(AndroidJUnit4::class)
class AutomationDeleteLifecycleAndroidTest {

    private lateinit var context: Context
    private val engineIds = mutableListOf<String>()

    /** A fresh engine over the real DataStore ledger with its own in-memory history. */
    private fun newEngine(): EngineHarness = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val history = HistoryRepositoryImpl(database.executionDao())
        val engine = ExecutionEngine(
            context = context,
            historyRepository = history,
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(emptyList())
        )
        EngineHarness(database, history, engine)
    }

    private class EngineHarness(
        val database: AppDatabase,
        val history: HistoryRepositoryImpl,
        val engine: ExecutionEngine
    )

    private fun statefulTask(id: String): Automation = Automation(
        id = id,
        name = id,
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

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        // Leave the real on-device ledger as we found it.
        engineIds.forEach { id ->
            runBlocking { ActiveExecutionStore(context).clear(id) }
        }
    }

    @Test
    fun activeTaskRunsItsExitOnceBeforeDelete() = runBlocking {
        val id = "delete-baseline-ondevice"
        engineIds += id
        val engine = newEngine()
        try {
            engine.engine.runAutomation(statefulTask(id))
            assertTrue(
                "main run must be recorded before delete",
                records(engine).any { it.automationId == id }
            )

            // Baseline: the durable marker is honored and dispatches the exit.
            engine.engine.runExit(statefulTask(id))
            val messages = records(engine).map { it.message }
            assertTrue(
                "exit must dispatch while the task exists (not be skipped); got $messages",
                messages.any { it.contains("No handler registered") } &&
                    messages.none { it.contains("task was not active") }
            )
        } finally {
            engine.database.close()
        }
    }

    @Test
    fun deleteClearsDurableLifecycleSoANewEngineNeverRunsAStaleExit() = runBlocking {
        val id = "delete-lifecycle-ondevice"
        engineIds += id
        val owner = newEngine()
        try {
            owner.engine.runAutomation(statefulTask(id))
            assertTrue(
                "main run must be recorded before delete",
                records(owner).any { it.automationId == id }
            )

            owner.engine.onAutomationDeleted(id)

            // Same engine: in-memory marker gone, exit becomes a no-op.
            owner.engine.runExit(statefulTask(id))
            val ownerMessages = records(owner).map { it.message }
            assertTrue(
                "same engine must skip the exit after delete; got $ownerMessages",
                ownerMessages.any { it.contains("task was not active") }
            )

            // Fresh engine over the SAME durable ledger: the marker is gone
            // there too, so a process restart can never dispatch a stale exit
            // for the deleted automation (the regression this pins).
            val fresh = newEngine()
            try {
                fresh.engine.runExit(statefulTask(id))
                val freshMessages = records(fresh).map { it.message }
                assertTrue(
                    "fresh engine must skip the exit after delete; got $freshMessages",
                    freshMessages.singleOrNull()?.contains("task was not active") == true
                )
            } finally {
                fresh.database.close()
            }
        } finally {
            owner.database.close()
        }
    }

    private suspend fun records(harness: EngineHarness): List<ExecutionRecord> =
        harness.history.getExecutionHistory().first()

    private companion object {
        /**
         * NexaFlowApplication boots the REAL MonitoringService in the test
         * process. Stop it once so only the test harness drives the engine.
         */
        @BeforeClass
        @JvmStatic
        fun stopRealMonitoringService() {
            MonitoringService.stop(ApplicationProvider.getApplicationContext<Context>())
        }
    }
}
