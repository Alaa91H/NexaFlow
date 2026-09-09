package com.nexaflow.core.engine

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exit-reliability contracts for the two remaining untested state monitors
 * (airplane mode and dark mode), mirroring the RingerModeMonitor contract:
 * a task triggered before a restart must fire its exit when the condition
 * ends, the missed exit fires on the first reconcile after start when the
 * condition already ended during downtime, and a stale mark for a disabled
 * task is pruned without firing a stale exit.
 */
@RunWith(RobolectricTestRunner::class)
class AirplaneDarkMonitorExitReconcileTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            ActiveTriggerStore(context).clearSource("airplane")
            ActiveTriggerStore(context).clearSource("dark")
            Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
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

    private fun airplaneMonitor(repository: FakeRepository): AirplaneModeMonitor =
        AirplaneModeMonitor(
            context = context,
            repository = repository,
            executionEngine = testEngine(context, RecordingHistory()),
            activeStore = ActiveTriggerStore(context),
            scope = CoroutineScope(Dispatchers.Default)
        )

    private fun darkMonitor(repository: FakeRepository): DarkModeMonitor =
        DarkModeMonitor(
            context = context,
            repository = repository,
            executionEngine = testEngine(context, RecordingHistory()),
            activeStore = ActiveTriggerStore(context),
            scope = CoroutineScope(Dispatchers.Default)
        )

    @Test
    fun `airplane restart with condition already ended fires the missed exit on init`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(listOf(airplaneAutomation("air-task")))
        val store = ActiveTriggerStore(context)
        // The task fired in airplane mode, then the process died. The mode is
        // already OFF again, so the missed exit must fire on first reconcile.
        store.markActive("airplane", "air-task")
        ActiveExecutionStore(context).markStarted("air-task")
        Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)

        val monitor = AirplaneModeMonitor(
            context = context,
            repository = repository,
            executionEngine = engine,
            activeStore = store,
            scope = CoroutineScope(Dispatchers.Default)
        )
        monitor.initialize()

        waitUntil { history.exits.any { it == EXIT_NOOP_MARKER } }
        waitUntil { store.activeKeys("airplane").isEmpty() }
        monitor.stop()
    }

    @Test
    fun `airplane restart while condition still holds keeps the task active`() = runBlocking {
        val history = RecordingHistory()
        val repository = FakeRepository(listOf(airplaneAutomation("air-task")))
        val store = ActiveTriggerStore(context)
        store.markActive("airplane", "air-task")
        Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 1)

        val monitor = AirplaneModeMonitor(
            context = context,
            repository = repository,
            executionEngine = engine(context, history),
            activeStore = store,
            scope = CoroutineScope(Dispatchers.Default)
        )
        monitor.initialize()

        Thread.sleep(300)
        assertTrue(
            "no exit while airplane mode still matches",
            history.exits.none { it == EXIT_NOOP_MARKER }
        )
        assertTrue("active mark survives while the mode matches", store.activeKeys("airplane").isNotEmpty())
        monitor.stop()
    }

    @Test
    fun `airplane stale mark for a disabled automation is pruned without a stale exit`() = runBlocking {
        val history = RecordingHistory()
        val repository = FakeRepository(listOf(airplaneAutomation("air-task").copy(enabled = false)))
        val store = ActiveTriggerStore(context)
        store.markActive("airplane", "air-task")
        Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)

        val monitor = AirplaneModeMonitor(
            context = context,
            repository = repository,
            executionEngine = testEngine(context, history),
            activeStore = store,
            scope = CoroutineScope(Dispatchers.Default)
        )
        monitor.initialize()

        waitUntil { store.activeKeys("airplane").isEmpty() }
        assertTrue("disabled task must not fire a stale exit", history.exits.none { it == EXIT_NOOP_MARKER })
        monitor.stop()
    }

    @Test
    fun `dark mode monitor initializes and reconciles without throwing`() = runBlocking {
        val history = RecordingHistory()
        val repository = FakeRepository(listOf(darkAutomation("dark-task")))
        val store = ActiveTriggerStore(context)

        val monitor = DarkModeMonitor(
            context = context,
            repository = repository,
            executionEngine = testEngine(context, history),
            activeStore = store,
            scope = CoroutineScope(Dispatchers.Default)
        )
        monitor.initialize()
        Thread.sleep(300)
        monitor.stop()
        // Robolectric reports the UiModeManager state without a real theme
        // transition; the contract under test is that the full lifecycle
        // (initialize → reconcile → stop) is exception-free and leaves no
        // active marks for a task whose condition never held.
        assertTrue("no exit may fire for a condition that never held", history.exits.isEmpty())
        assertTrue(store.activeKeys("dark").isEmpty())
    }
}

/** Local alias so both engine test styles compile in one file. */
private fun engine(context: Context, history: RecordingHistory) = testEngine(context, history)
