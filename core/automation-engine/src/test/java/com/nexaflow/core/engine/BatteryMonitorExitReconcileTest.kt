package com.nexaflow.core.engine

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
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
 * Battery exit-reliability contract: a task triggered by a battery-level
 * crossing BEFORE a process/service restart must still fire its exit behavior
 * when the level crosses back. The monitor's in-memory active set dies with
 * the process, so it re-arms from the durable [ActiveTriggerStore] ledger on
 * start — and because ACTION_BATTERY_CHANGED is sticky, registering the
 * receiver delivers the current level immediately, so a missed crossing (the
 * level already left the threshold while the process was down) fires the exit
 * on that first delivery instead of waiting for the level to cross again.
 *
 * Scenario: a BELOW-20 task fired, then the process was killed while the
 * battery was at 15%. The battery charges to 90% during downtime, then the
 * app restarts. The fresh monitor must see the durable mark, read the current
 * level (90% — above the threshold), and run the exit.
 */
@RunWith(RobolectricTestRunner::class)
class BatteryMonitorExitReconcileTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Robolectric shares one Application (and its DataStore cache) across
        // test methods, so reset the battery source for isolation.
        runBlocking {
            ActiveTriggerStore(context).clearSource("battery")
            ActiveExecutionStore(context).clear("batt-task")
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

    private fun monitorFor(
        repository: FakeRepository,
        engine: com.nexaflow.core.execution.ExecutionEngine,
        store: ActiveTriggerStore
    ): BatteryMonitor = BatteryMonitor(
        context = context,
        repository = repository,
        executionEngine = engine,
        activeStore = store,
        scope = CoroutineScope(Dispatchers.Default)
    )

    @Test
    fun `restart with battery already above threshold fires the missed exit on init`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(listOf(batteryAutomation("batt-task", 20)))
        val store = ActiveTriggerStore(context)
        // The task fired below 20%, then the process died while still low.
        store.markActive("battery", "batt-task")
        ActiveExecutionStore(context).markStarted("batt-task")
        // The battery is now at 90% — the BELOW-20 condition ended during downtime.
        context.sendStickyBroadcast(
            batteryIntent(90, BatteryManager.BATTERY_STATUS_DISCHARGING, 0)
        )

        val monitor = monitorFor(repository, engine, store)
        monitor.initialize()

        // The sticky delivery on registration re-evaluates the crossed-back
        // level and fires the missed exit.
        waitUntil { history.exits.any { it == EXIT_NOOP_MARKER } }
        // The ledger is cleared so the exit can never fire twice.
        waitUntil { store.activeKeys("battery").isEmpty() }
        monitor.stop()
    }

    @Test
    fun `restart while battery still below threshold keeps the task active`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(listOf(batteryAutomation("batt-task", 20)))
        val store = ActiveTriggerStore(context)
        store.markActive("battery", "batt-task")
        // The battery is still at 10% — the condition still holds after restart.
        context.sendStickyBroadcast(
            batteryIntent(10, BatteryManager.BATTERY_STATUS_DISCHARGING, 0)
        )

        val monitor = monitorFor(repository, engine, store)
        monitor.initialize()

        // Give the async re-arm + sticky delivery a moment, then assert no exit.
        Thread.sleep(300)
        assertTrue(
            "no exit while the battery is still below the threshold",
            history.exits.none { it == EXIT_NOOP_MARKER }
        )
        assertTrue(
            "active mark survives while the condition holds",
            store.activeKeys("battery").isNotEmpty()
        )
        monitor.stop()
    }

    @Test
    fun `stale mark for a disabled automation is pruned on restart`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(
            listOf(batteryAutomation("batt-task", 20).copy(enabled = false))
        )
        val store = ActiveTriggerStore(context)
        store.markActive("battery", "batt-task")
        context.sendStickyBroadcast(
            batteryIntent(90, BatteryManager.BATTERY_STATUS_DISCHARGING, 0)
        )

        val monitor = monitorFor(repository, engine, store)
        monitor.initialize()

        // Give the async prune a moment, then assert nothing fired and the
        // stale mark is gone.
        Thread.sleep(300)
        assertTrue(
            "disabled task must not fire a stale exit",
            history.exits.none { it == EXIT_NOOP_MARKER }
        )
        waitUntil { store.activeKeys("battery").isEmpty() }
        monitor.stop()
    }
}
