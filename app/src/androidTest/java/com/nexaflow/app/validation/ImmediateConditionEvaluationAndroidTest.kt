package com.nexaflow.app.validation

import android.content.Context
import android.provider.Settings
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexaflow.core.database.AppDatabase
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.engine.AirplaneModeMonitor
import com.nexaflow.core.engine.MonitoringService
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.data.repository.AutomationRepositoryImpl
import com.nexaflow.data.repository.HistoryRepositoryImpl
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device proof of the immediate-condition-evaluation contract, driving the
 * REAL production monitor against the REAL device settings provider, Room
 * database, and DataStore ledgers (no mocks, no Robolectric):
 *
 *  1. Enabling a task whose state condition ALREADY holds fires it right away
 *     (reconcile reads the current state instead of waiting for a broadcast).
 *  2. If the condition does not hold when the task is enabled, nothing fires;
 *     when the condition later turns true the task runs, and when it ends the
 *     exit behavior runs.
 *  3. Disabling a task while its condition still holds prunes its durable
 *     marker without firing a stale exit (the app-wide tested contract).
 *
 * Requires the shell to pre-grant the WRITE_SETTINGS app-op so the test can
 * drive Settings.Global.AIRPLANE_MODE_ON (the exact value the production
 * monitor samples). The setting is restored afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ImmediateConditionEvaluationAndroidTest {

    private lateinit var context: Context
    private var originalAirplaneMode = 0
    private var currentHarness: Harness? = null

    private class Harness(
        val monitor: AirplaneModeMonitor,
        val repository: AutomationRepository,
        val history: HistoryRepositoryImpl,
        val store: ActiveTriggerStore,
        val executionStore: ActiveExecutionStore,
        val database: AppDatabase,
        val scope: CoroutineScope
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalAirplaneMode = Settings.Global.getInt(
            context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0
        )
        runBlocking {
            ActiveTriggerStore(context).clearSource(SOURCE_AIRPLANE)
        }
    }

    @After
    fun tearDown() {
        // Stop this test's monitor coroutines BEFORE the next test runs: they
        // share the real on-device ActiveTriggerStore ledger, and a leftover
        // read-modify-write edit from a prior test can clobber a fresh mark
        // made by the next test (the cause of an intermittent activeKeys == 0).
        currentHarness?.scope?.cancel()
        currentHarness = null
        // Restore whatever airplane state the phone had before this test ran.
        Settings.Global.putInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            originalAirplaneMode
        )
    }

    private fun airplaneTask(id: String): Automation = Automation(
        id = id,
        name = id,
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = listOf(Trigger(TriggerType.AIRPLANE_MODE, mapOf("state" to "ON"))),
        actions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "hi"))),
        exitActions = emptyList(),
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun harness(id: String): Harness {
        return runBlocking {
            val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repository = AutomationRepositoryImpl(database.automationDao())
        val history = HistoryRepositoryImpl(database.executionDao())
        val executionEngine = ExecutionEngine(
            context = context,
            historyRepository = history,
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(emptyList())
        )
        val store = ActiveTriggerStore(context)
        val executionStore = ActiveExecutionStore(context)
        executionStore.clear(id)
        val scope = CoroutineScope(Dispatchers.Default)
        val monitor = AirplaneModeMonitor(
            context = context,
            repository = repository,
            executionEngine = executionEngine,
            activeStore = store,
            scope = scope
        )
            repository.saveAutomation(airplaneTask(id))
            Harness(monitor, repository, history, store, executionStore, database, scope).also {
                currentHarness = it
            }
        }
    }

    private fun setAirplaneMode(on: Boolean) {
        Settings.Global.putInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            if (on) 1 else 0
        )
    }

    private fun airplaneModeOn(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    /** Real DataStore/Room reads are async; poll with a real-time deadline. */
    private suspend fun waitUntil(timeoutMs: Long = 10_000L, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            delay(25)
        }
        throw AssertionError("condition not met within ${timeoutMs}ms")
    }

    private suspend fun records(harness: Harness): List<ExecutionRecord> =
        harness.history.getExecutionHistory().first()

    @Test
    fun enablingTaskWhoseAirplaneConditionAlreadyHoldsFiresImmediately() = runBlocking {
        val id = "airplane-fire-task"
        val harness = harness(id)
        try {
            setAirplaneMode(true)
            assertTrue("precondition: airplane mode must read ON", airplaneModeOn())

            // No device broadcast involved: the enable-time reconcile samples
            // the current state and must fire the task immediately.
            harness.monitor.reconcileAutomations()
            waitUntil {
                records(harness).any { it.automationId == id }
            }
            assertEquals(
                "durable trigger mark must exist after the immediate fire",
                1,
                harness.store.activeKeys(SOURCE_AIRPLANE).size
            )

            // The condition ends (airplane turned off): the exit must run.
            setAirplaneMode(false)
            harness.monitor.reconcileAutomations()
            waitUntil {
                records(harness).any { it.automationId == id && it.message == EXIT_NOOP_MARKER }
            }
            waitUntil {
                harness.store.activeKeys(SOURCE_AIRPLANE).isEmpty()
            }
        } finally {
            harness.database.close()
        }
    }

    @Test
    fun enablingTaskWhileConditionAbsentRunsWhenConditionHoldsThenExits() = runBlocking {
        val id = "airplane-transition-task"
        val harness = harness(id)
        try {
            setAirplaneMode(false)

            // Enabled while the condition is absent: nothing may fire yet.
            harness.monitor.reconcileAutomations()
            delay(400)
            assertTrue(
                "no run while the condition does not hold",
                records(harness).none { it.automationId == id }
            )

            // The condition turns true: the task runs, then the exit on the end.
            setAirplaneMode(true)
            harness.monitor.reconcileAutomations()
            waitUntil { records(harness).any { it.automationId == id } }

            setAirplaneMode(false)
            harness.monitor.reconcileAutomations()
            waitUntil {
                records(harness).any { it.automationId == id && it.message == EXIT_NOOP_MARKER }
            }
        } finally {
            harness.database.close()
        }
    }

    @Test
    fun disablingTaskWhileConditionHoldsPrunesWithoutStaleExit() = runBlocking {
        val id = "airplane-disable-task"
        val harness = harness(id)
        try {
            setAirplaneMode(true)
            harness.monitor.reconcileAutomations()
            waitUntil { records(harness).any { it.automationId == id } }
            assertTrue(
                "durable mark must exist while the condition holds",
                harness.store.activeKeys(SOURCE_AIRPLANE).isNotEmpty()
            )

            // The user disables the task while its condition still holds.
            harness.repository.updateAutomationStatus(id, false)
            harness.monitor.reconcileAutomations()
            waitUntil { harness.store.activeKeys(SOURCE_AIRPLANE).isEmpty() }

            // App-wide contract: a deliberate disable is an abandonment — no
            // stale exit may fire for it.
            delay(400)
            assertTrue(
                "disabling must not fire a stale exit",
                records(harness).none { it.automationId == id && it.message == EXIT_NOOP_MARKER }
            )
        } finally {
            harness.database.close()
        }
    }

    private companion object {
        const val EXIT_NOOP_MARKER = "No exit behavior configured"
        /** ActiveTriggerStore source key used by [AirplaneModeMonitor]. */
        const val SOURCE_AIRPLANE = "airplane"

        /**
         * NexaFlowApplication starts the REAL MonitoringService when the test
         * process boots. Its monitors share the on-device ActiveTriggerStore
         * ledger but query the real (empty) repository, so on an airplane-mode
         * toggle they reconcile and PRUNE this test's marks as unknown
         * automations — the source of the intermittent `activeKeys == 0`.
         * Stop the real service once so only the test harness monitors run.
         */
        @BeforeClass
        @JvmStatic
        fun stopRealMonitoringService() {
            MonitoringService.stop(ApplicationProvider.getApplicationContext<Context>())
        }
    }
}
