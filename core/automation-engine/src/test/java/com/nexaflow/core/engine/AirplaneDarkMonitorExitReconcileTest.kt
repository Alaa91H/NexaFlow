package com.nexaflow.core.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Deterministic restart/exit contracts for the AIRPLANE_MODE and DARK_MODE
 * stateful monitors. Platform receiver registration is intentionally outside
 * these tests; [reconcileState] is the lifecycle boundary that both callbacks
 * and startup reconciliation execute.
 */
@RunWith(RobolectricTestRunner::class)
class AirplaneDarkMonitorExitReconcileTest {

    private lateinit var context: Context
    private lateinit var activeStore: ActiveTriggerStore
    private lateinit var runtimeStore: AutomationRuntimeStore

    private val ids = listOf(
        "air-task",
        "air-disabled",
        "dark-task",
        "dark-disabled"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        activeStore = ActiveTriggerStore(context)
        runtimeStore = AutomationRuntimeStore(context)
        runBlocking {
            activeStore.clearSource("airplane")
            activeStore.clearSource("dark-mode")
            ids.forEach {
                runtimeStore.clear(it)
                ActiveExecutionStore(context).clear(it)
            }
        }
    }

    private fun airplaneAutomation(id: String, wantOn: String = "ON") = testAutomation(
        id = id,
        triggers = listOf(Trigger(TriggerType.AIRPLANE_MODE, mapOf("state" to wantOn)))
    )

    private fun darkAutomation(id: String, wantOn: String = "ON") = testAutomation(
        id = id,
        triggers = listOf(Trigger(TriggerType.DARK_MODE, mapOf("state" to wantOn)))
    )

    private fun airplaneMonitor(
        repository: FakeRepository,
        history: RecordingHistory
    ): AirplaneModeMonitor {
        val engine = testEngine(context, history)
        return AirplaneModeMonitor(
            context = context,
            repository = repository,
            executionEngine = engine,
            activeStore = activeStore,
            runtimeStore = runtimeStore,
            exitCoordinator = ExitCoordinator(runtimeStore, engine, repository, history),
            scope = CoroutineScope(Dispatchers.Default)
        )
    }

    private fun darkMonitor(
        repository: FakeRepository,
        history: RecordingHistory
    ): DarkModeMonitor {
        val engine = testEngine(context, history)
        return DarkModeMonitor(
            context = context,
            repository = repository,
            executionEngine = engine,
            activeStore = activeStore,
            runtimeStore = runtimeStore,
            exitCoordinator = ExitCoordinator(runtimeStore, engine, repository, history),
            scope = CoroutineScope(Dispatchers.Default)
        )
    }

    @Test
    fun `airplane legacy marker is promoted before an opposite state can exit it`() = runBlocking {
        val task = airplaneAutomation("air-task")
        val history = RecordingHistory()
        val monitor = airplaneMonitor(FakeRepository(listOf(task)), history)
        activeStore.markActive("airplane", task.id)

        monitor.reconcileState(on = false)

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("airplane").none { it.startsWith(task.id) })
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `airplane matching state activates once and remains durable`() = runBlocking {
        val task = airplaneAutomation("air-task")
        val history = RecordingHistory()
        val monitor = airplaneMonitor(FakeRepository(listOf(task)), history)

        monitor.reconcileState(on = true)
        val first = runtimeStore.current(task.id)
        monitor.reconcileState(on = true)
        val second = runtimeStore.current(task.id)

        assertEquals(AutomationRuntimeLifecycleState.ACTIVE, second?.lifecycleState)
        assertEquals(first?.occurrenceId, second?.occurrenceId)
        assertTrue(activeStore.activeKeys("airplane").any { it.startsWith(task.id) })
        assertTrue(history.exits.none { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `disabled airplane occurrence exits before compatibility state clears`() = runBlocking {
        val task = airplaneAutomation("air-disabled").copy(enabled = false)
        val history = RecordingHistory()
        val monitor = airplaneMonitor(FakeRepository(listOf(task)), history)
        activeStore.markActive("airplane", task.id)

        monitor.reconcileState(on = true)

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("airplane").none { it.startsWith(task.id) })
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `dark mode activation and opposite state use one durable occurrence`() = runBlocking {
        val task = darkAutomation("dark-task")
        val history = RecordingHistory()
        val monitor = darkMonitor(FakeRepository(listOf(task)), history)

        monitor.reconcileState(dark = true)
        val active = runtimeStore.current(task.id)
        assertEquals(AutomationRuntimeLifecycleState.ACTIVE, active?.lifecycleState)
        assertTrue(activeStore.activeKeys("dark-mode").any { it.startsWith(task.id) })

        monitor.reconcileState(dark = false)

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("dark-mode").none { it.startsWith(task.id) })
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `dark mode legacy marker survives restart while condition still matches`() = runBlocking {
        val task = darkAutomation("dark-task")
        val history = RecordingHistory()
        val monitor = darkMonitor(FakeRepository(listOf(task)), history)
        activeStore.markActive("dark-mode", task.id)

        monitor.reconcileState(dark = true)

        assertEquals(
            AutomationRuntimeLifecycleState.ACTIVE,
            runtimeStore.current(task.id)?.lifecycleState
        )
        assertTrue(activeStore.activeKeys("dark-mode").any { it.startsWith(task.id) })
        assertTrue(history.exits.none { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `disabled dark mode occurrence exits through coordinator`() = runBlocking {
        val task = darkAutomation("dark-disabled").copy(enabled = false)
        val history = RecordingHistory()
        val monitor = darkMonitor(FakeRepository(listOf(task)), history)
        activeStore.markActive("dark-mode", task.id)

        monitor.reconcileState(dark = true)

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("dark-mode").none { it.startsWith(task.id) })
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }
}
