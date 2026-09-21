package com.nexaflow.core.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExitCoordinatorTest {

    private lateinit var context: Context
    private lateinit var store: AutomationRuntimeStore

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            store = AutomationRuntimeStore(context)
            store.clear("exit-task")
        }
    }

    private fun activeState(
        occurrenceId: String = "occurrence-1",
        expectedEndAt: Long? = null
    ) = AutomationRuntimeState(
        automationId = "exit-task",
        occurrenceId = occurrenceId,
        source = "time-range",
        sourceKey = occurrenceId,
        lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
        activatedAt = 1L,
        expectedEndAt = expectedEndAt,
        scheduleGeneration = "generation-1"
    )

    @Test
    fun `simultaneous end sources execute one logical exit`() = runBlocking {
        val history = RecordingHistory()
        val automation = testAutomation("exit-task", emptyList())
        val repository = FakeRepository(listOf(automation))
        val engine = testEngine(context, history)
        val coordinator = ExitCoordinator(store, engine, repository, history)
        assertTrue(store.activate(activeState()))

        val results = awaitAll(
            async { coordinator.requestExit(automation, ExitReason.TRIGGER_FALSE, "occurrence-1") },
            async { coordinator.requestExit(automation, ExitReason.TIME_WINDOW_ENDED, "occurrence-1") }
        )

        assertEquals(1, results.count { it is ExitCoordinatorResult.Executed })
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
        assertTrue("successful exit must consume its runtime state", store.current("exit-task") == null)
    }

    @Test
    fun `elapsed time window reconciles exit without condition re-evaluation`() = runBlocking {
        val history = RecordingHistory()
        val automation = testAutomation("exit-task", emptyList())
        val repository = FakeRepository(listOf(automation))
        val engine = testEngine(context, history)
        val coordinator = ExitCoordinator(store, engine, repository, history)
        assertTrue(store.activate(activeState(expectedEndAt = 1L)))

        val outcomes = coordinator.reconcile(ExitReason.BOOT_RECOVERY)

        assertEquals(1, outcomes.count { it is ExitCoordinatorResult.Executed })
        assertTrue(history.exits.contains(EXIT_NOOP_MARKER))
        assertTrue(store.current("exit-task") == null)
    }

    @Test
    fun `failed exit receives one bounded automatic recovery attempt`() = runBlocking {
        val history = RecordingHistory()
        val automation = testAutomation("exit-task", emptyList()).copy(
            exitActions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "exit")))
        )
        val repository = FakeRepository(listOf(automation))
        val engine = testEngine(context, history)
        val coordinator = ExitCoordinator(store, engine, repository, history)
        assertTrue(store.activate(activeState()))

        assertTrue(
            coordinator.requestExit(automation, ExitReason.TRIGGER_FALSE, "occurrence-1")
                is ExitCoordinatorResult.RecoveryRequired
        )
        assertTrue(
            coordinator.reconcile(ExitReason.PROCESS_RECOVERY).single()
                is ExitCoordinatorResult.RecoveryRequired
        )
        val afterRetry = checkNotNull(store.current("exit-task"))
        assertEquals(2, afterRetry.exitAttempt)
        assertEquals(AutomationRuntimeLifecycleState.EXIT_FAILED, afterRetry.lifecycleState)

        // MAX_EXIT_ATTEMPTS is now 5 (strict mode) — verify bounded retries up to 5
        repeat(3) {
            val r = coordinator.reconcile(ExitReason.PROCESS_RECOVERY).single()
            assertTrue(r is ExitCoordinatorResult.RecoveryRequired)
        }
        assertEquals(5, checkNotNull(store.current("exit-task")).exitAttempt)
        val limited = coordinator.reconcile(ExitReason.PROCESS_RECOVERY).single()
        assertTrue(limited is ExitCoordinatorResult.RecoveryRequired)
        assertEquals(5, checkNotNull(store.current("exit-task")).exitAttempt)
    }

    @Test
    fun `failed exit within budget remains durable and blocks a new activation`() = runBlocking {
        val history = RecordingHistory()
        val automation = testAutomation("exit-task", emptyList()).copy(
            exitActions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "exit")))
        )
        val repository = FakeRepository(listOf(automation))
        val engine = testEngine(context, history)
        val coordinator = ExitCoordinator(store, engine, repository, history)
        assertTrue(store.activate(activeState()))

        val result = coordinator.requestExit(automation, ExitReason.TRIGGER_FALSE, "occurrence-1")

        assertTrue(result is ExitCoordinatorResult.RecoveryRequired)
        val failed = checkNotNull(store.current("exit-task"))
        assertEquals(AutomationRuntimeLifecycleState.EXIT_FAILED, failed.lifecycleState)
        // A failed row still inside its retry budget is live recovery state:
        // a new activation must not clobber it.
        assertTrue(
            "failed exit within budget must not be replaced by a new occurrence",
            !store.activate(activeState("occurrence-2"))
        )
    }

    @Test
    fun `exhausted failed exit is reaped by a future activation`() = runBlocking {
        val history = RecordingHistory()
        val automation = testAutomation("exit-task", emptyList()).copy(
            exitActions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "exit")))
        )
        val repository = FakeRepository(listOf(automation))
        val engine = testEngine(context, history)
        val coordinator = ExitCoordinator(store, engine, repository, history)
        assertTrue(store.activate(activeState()))

        // First failure: the exit claims the occurrence and its end action
        // fails → EXIT_FAILED with one attempt spent.
        val first = coordinator.requestExit(automation, ExitReason.TRIGGER_FALSE, "occurrence-1")
        assertTrue(first is ExitCoordinatorResult.RecoveryRequired)

        // Drive the recovery pass until the retry budget is exhausted.
        repeat(AutomationRuntimeStore.MAX_EXIT_ATTEMPTS) {
            coordinator.reconcile(ExitReason.PROCESS_RECOVERY)
        }
        val exhausted = checkNotNull(store.current("exit-task"))
        assertEquals(AutomationRuntimeLifecycleState.EXIT_FAILED, exhausted.lifecycleState)
        assertEquals(AutomationRuntimeStore.MAX_EXIT_ATTEMPTS, exhausted.exitAttempt)

        // The recovery ledger is spent, so a NEW occurrence (the user re-enabled
        // the task, or the condition fired again) must win: reaping the stale
        // failed row is what keeps the automation usable instead of silently
        // disabled forever behind the "prior lifecycle requires cleanup" skip.
        assertTrue(
            "exhausted failed row must be reapable by a new activation",
            store.activate(activeState("occurrence-fresh"))
        )
        val fresh = checkNotNull(store.current("exit-task"))
        assertEquals("occurrence-fresh", fresh.occurrenceId)
        assertEquals(AutomationRuntimeLifecycleState.ACTIVE, fresh.lifecycleState)
    }
}
