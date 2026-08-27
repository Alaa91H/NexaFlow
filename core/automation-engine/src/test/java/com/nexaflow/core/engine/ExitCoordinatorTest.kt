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
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        store = AutomationRuntimeStore(context)
        store.clear("exit-task")
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
        assertTrue("successful exit must consume runtime state", store.activeStates().isEmpty())
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
        assertTrue(store.activeStates().isEmpty())
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

        val limited = coordinator.reconcile(ExitReason.PROCESS_RECOVERY).single()
        assertTrue(limited is ExitCoordinatorResult.RecoveryRequired)
        assertEquals(2, checkNotNull(store.current("exit-task")).exitAttempt)
    }

    @Test
    fun `failed exit remains durable and blocks a new activation`() = runBlocking {
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
        assertTrue("failed exit must not be replaced by a new occurrence", !store.activate(activeState("occurrence-2")))
    }
}
