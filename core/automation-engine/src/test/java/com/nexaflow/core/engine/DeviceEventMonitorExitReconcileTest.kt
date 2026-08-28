package com.nexaflow.core.engine

import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The core exit-reliability contract: a task that was triggered BEFORE a
 * process/service restart must still fire its exit behavior when the condition
 * ends. The monitor's in-memory state dies with the process, so it re-arms
 * from the durable [ActiveTriggerStore] ledger on start — and if the condition
 * already ended while the process was down, the missed exit fires immediately
 * on that first reconcile (broadcasts only fire on *changes*, so it would
 * otherwise wait until the state flips again, possibly never).
 *
 * Scenario: a SCREEN_ON task fired, then the process was killed while the
 * screen was still on. The user turns the screen off during downtime, then the
 * app restarts. The fresh monitor must see the durable mark, read the current
 * screen state (off), and run the exit — not wait for a future SCREEN_ON.
 */
@RunWith(RobolectricTestRunner::class)
class DeviceEventMonitorExitReconcileTest {

    private lateinit var context: Context

    // RecordingHistory / FakeRepository / emptyPagingSource / waitUntil live in
    // ExitReconcileTestSupport.kt (shared with the other monitor reconcile tests).

    private fun automation(id: String, event: String): Automation = Automation(
        id = id,
        name = id,
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = listOf(Trigger(TriggerType.DEVICE, mapOf("event" to event))),
        actions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "hi"))),
        exitActions = emptyList(),
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Robolectric shares one Application (and its DataStore cache) across
        // test methods, so reset the device source for isolation.
        runBlocking { ActiveTriggerStore(context).clearSource("device") }
    }

    /**
     * DataStore reads/writes run on real IO threads, so the monitor's
     * re-arm + reconcile completes asynchronously. Poll with a real-time
     * deadline instead of relying on a virtual scheduler.
     */
    private suspend fun waitUntil(timeoutMs: Long = 5_000L, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            kotlinx.coroutines.delay(20)
        }
        throw AssertionError("condition not met within ${timeoutMs}ms")
    }

    private fun monitorFor(
        repository: AutomationRepository,
        engine: ExecutionEngine,
        store: ActiveTriggerStore
    ): DeviceEventMonitor = DeviceEventMonitor(
        context = context,
        repository = repository,
        executionEngine = engine,
        activeStore = store,
        exitCoordinator = ExitExecutionCoordinator(
            executionEngine = engine,
            activeStore = store,
            scope = CoroutineScope(Dispatchers.Default)
        ),
        scope = CoroutineScope(Dispatchers.Default)
    )

    @Test
    fun `restart with condition already ended fires the missed exit on init`() = runBlocking {
        val history = RecordingHistory()
        val engine = ExecutionEngine(
            context = context,
            historyRepository = history,
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(emptyList())
        )
        val repository = FakeRepository(listOf(automation("screen-task", "SCREEN_ON")))
        val store = ActiveTriggerStore(context)
        // The task fired, then the process died while the screen was still on.
        store.markActive("device", "screen-task|SCREEN_ON")
        // The screen is now OFF (condition ended during downtime).
        shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(false)

        // Fresh monitor instance = the restart.
        monitorFor(repository, engine, store).initialize()

        waitUntil { history.exits.isNotEmpty() }
        assertTrue(
            "exit behavior must run when the triggered condition already ended before restart",
            history.exits.isNotEmpty()
        )
        // The ledger is cleared so the exit can never fire twice.
        waitUntil { store.activeKeys("device").isEmpty() }
    }

    @Test
    fun `restart while condition still holds keeps the task active`() = runBlocking {
        val history = RecordingHistory()
        val engine = ExecutionEngine(
            context = context,
            historyRepository = history,
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(emptyList())
        )
        val repository = FakeRepository(listOf(automation("screen-task", "SCREEN_ON")))
        val store = ActiveTriggerStore(context)
        store.markActive("device", "screen-task|SCREEN_ON")
        // The screen is still ON — the condition still holds after the restart.
        shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(true)

        monitorFor(repository, engine, store).initialize()

        // Give the async re-arm a moment, then assert no exit fired.
        Thread.sleep(300)
        assertTrue("no exit while the condition still holds", history.exits.isEmpty())
        assertTrue("active mark survives while the condition holds", store.activeKeys("device").isNotEmpty())
    }

    @Test
    fun `stale mark for a disabled automation is pruned on restart`() = runBlocking {
        val history = RecordingHistory()
        val engine = ExecutionEngine(
            context = context,
            historyRepository = history,
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(emptyList())
        )
        val repository = FakeRepository(
            listOf(automation("screen-task", "SCREEN_ON").copy(enabled = false))
        )
        val store = ActiveTriggerStore(context)
        store.markActive("device", "screen-task|SCREEN_ON")
        shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(false)

        monitorFor(repository, engine, store).initialize()

        // Give the async prune a moment, then assert nothing fired and the
        // stale mark is gone.
        Thread.sleep(300)
        assertTrue("disabled task must not fire a stale exit", history.exits.isEmpty())
        waitUntil { store.activeKeys("device").isEmpty() }
    }
}
