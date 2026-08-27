package com.nexaflow.core.engine

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
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
 * Battery exit-reliability contract: a task triggered by a battery-level
 * crossing before a process/service restart must still fire its end behavior
 * when the level crosses back. The durable occurrence ledger, not a surviving
 * in-memory collection, is the authority for the one allowed exit transition.
 */
@RunWith(RobolectricTestRunner::class)
class BatteryMonitorExitReconcileTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            ActiveTriggerStore(context).clearSource("battery")
            AutomationRuntimeStore(context).clear("batt-task")
        }
    }

    private fun batteryAutomation(id: String, belowThreshold: Int = 20): com.nexaflow.domain.models.Automation =
        testAutomation(
            id = id,
            triggers = listOf(
                Trigger(
                    TriggerType.BATTERY,
                    mapOf(
                        "direction" to "BELOW",
                        "above" to belowThreshold.toString(),
                        "chargerType" to "ANY",
                        "chargingState" to "ANY"
                    )
                )
            )
        )

    private fun batteryIntent(level: Int, status: Int, plugged: Int): Intent =
        Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, level)
            .putExtra(BatteryManager.EXTRA_STATUS, status)
            .putExtra(BatteryManager.EXTRA_PLUGGED, plugged)

    @Suppress("DEPRECATION")
    private fun setStickyBatteryState(level: Int, status: Int, plugged: Int) {
        context.sendStickyBroadcast(batteryIntent(level, status, plugged))
    }

    private fun monitorFor(
        repository: FakeRepository,
        engine: com.nexaflow.core.execution.ExecutionEngine,
        exitCoordinator: ExitCoordinator,
        runtimeStore: AutomationRuntimeStore,
        store: ActiveTriggerStore
    ): BatteryMonitor = BatteryMonitor(
        context = context,
        repository = repository,
        executionEngine = engine,
        exitCoordinator = exitCoordinator,
        runtimeStore = runtimeStore,
        activeStore = store,
        scope = CoroutineScope(Dispatchers.Default)
    )

    private suspend fun seedActiveOccurrence(runtimeStore: AutomationRuntimeStore) {
        runtimeStore.activate(
            AutomationRuntimeState(
                automationId = "batt-task",
                occurrenceId = "battery:batt-task:restarted",
                source = "battery",
                sourceKey = "batt-task",
                lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                activatedAt = 1L
            )
        )
    }

    @Test
    fun `restart with battery already above threshold fires the missed exit on init`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(listOf(batteryAutomation("batt-task", 20)))
        val store = ActiveTriggerStore(context)
        val runtimeStore = AutomationRuntimeStore(context)
        seedActiveOccurrence(runtimeStore)
        setStickyBatteryState(90, BatteryManager.BATTERY_STATUS_DISCHARGING, 0)

        val monitor = monitorFor(
            repository,
            engine,
            ExitCoordinator(runtimeStore, engine, repository, history),
            runtimeStore,
            store
        )
        monitor.initialize()

        waitUntil { history.exits.any { it == EXIT_NOOP_MARKER } }
        waitUntil { store.activeKeys("battery").isEmpty() }
        assertTrue("successful exit consumes the runtime occurrence", runtimeStore.current("batt-task") == null)
        monitor.stop()
    }

    @Test
    fun `restart while battery still below threshold keeps the task active`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(listOf(batteryAutomation("batt-task", 20)))
        val store = ActiveTriggerStore(context)
        val runtimeStore = AutomationRuntimeStore(context)
        // Existing installations can carry the compatibility key as well as
        // the new occurrence record; rearm must preserve this active state.
        store.markActive("battery", "batt-task")
        seedActiveOccurrence(runtimeStore)
        setStickyBatteryState(10, BatteryManager.BATTERY_STATUS_DISCHARGING, 0)

        val monitor = monitorFor(
            repository,
            engine,
            ExitCoordinator(runtimeStore, engine, repository, history),
            runtimeStore,
            store
        )
        monitor.initialize()

        waitUntil { store.activeKeys("battery").isNotEmpty() }
        assertTrue("no exit while the battery condition still holds", history.exits.none { it == EXIT_NOOP_MARKER })
        assertTrue("active mark survives while the condition holds", store.activeKeys("battery").isNotEmpty())
        assertTrue("runtime occurrence survives while active", runtimeStore.current("batt-task") != null)
        monitor.stop()
    }

    @Test
    fun `stale mark for a disabled automation is pruned on restart`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(listOf(batteryAutomation("batt-task", 20).copy(enabled = false)))
        val store = ActiveTriggerStore(context)
        val runtimeStore = AutomationRuntimeStore(context)
        store.markActive("battery", "batt-task")
        setStickyBatteryState(90, BatteryManager.BATTERY_STATUS_DISCHARGING, 0)

        val monitor = monitorFor(
            repository,
            engine,
            ExitCoordinator(runtimeStore, engine, repository, history),
            runtimeStore,
            store
        )
        monitor.initialize()

        assertTrue("disabled task must not fire a stale exit", history.exits.none { it == EXIT_NOOP_MARKER })
        waitUntil { store.activeKeys("battery").isEmpty() }
        monitor.stop()
    }
}
