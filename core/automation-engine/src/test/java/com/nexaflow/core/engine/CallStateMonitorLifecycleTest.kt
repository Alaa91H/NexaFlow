package com.nexaflow.core.engine

import android.content.Context
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
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

@RunWith(RobolectricTestRunner::class)
class CallStateMonitorLifecycleTest {

    private lateinit var context: Context
    private lateinit var activeStore: ActiveTriggerStore
    private lateinit var runtimeStore: AutomationRuntimeStore

    private val ids = listOf(
        "call-incoming",
        "call-outgoing",
        "call-legacy",
        "call-disabled",
        "call-foreign"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        activeStore = ActiveTriggerStore(context)
        runtimeStore = AutomationRuntimeStore(context)
        runBlocking {
            activeStore.clearSource("call")
            ids.forEach {
                runtimeStore.clear(it)
                ActiveExecutionStore(context).clear(it)
            }
        }
    }

    private fun automation(
        id: String,
        event: String,
        enabled: Boolean = true
    ) = testAutomation(
        id = id,
        triggers = listOf(
            Trigger(TriggerType.CALL_STATE, mapOf("event" to event))
        )
    ).copy(enabled = enabled)

    private fun monitorFor(
        repository: FakeRepository,
        history: RecordingHistory
    ): CallStateMonitor {
        val engine = testEngine(context, history)
        return CallStateMonitor(
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
    fun `incoming call survives offhook and exits only when the call ends`() = runBlocking {
        val task = automation("call-incoming", "INCOMING")
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)

        monitor.reconcileState(TelephonyManager.CALL_STATE_RINGING)
        val ringing = runtimeStore.current(task.id)
        assertEquals(AutomationRuntimeLifecycleState.ACTIVE, ringing?.lifecycleState)
        assertTrue(activeStore.activeKeys("call").any { it.startsWith(task.id) })

        // Answering the incoming call changes the platform state to OFFHOOK.
        // It must not close the INCOMING occurrence; only IDLE ends the call.
        monitor.reconcileState(TelephonyManager.CALL_STATE_OFFHOOK)
        val answered = runtimeStore.current(task.id)
        assertEquals(ringing?.occurrenceId, answered?.occurrenceId)
        assertTrue(history.exits.none { it == EXIT_NOOP_MARKER })

        monitor.reconcileState(TelephonyManager.CALL_STATE_IDLE)
        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("call").none { it.startsWith(task.id) })
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `outgoing call activates on offhook`() = runBlocking {
        val task = automation("call-outgoing", "OUTGOING")
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)

        monitor.reconcileState(TelephonyManager.CALL_STATE_OFFHOOK)

        val active = runtimeStore.current(task.id)
        assertEquals(AutomationRuntimeLifecycleState.ACTIVE, active?.lifecycleState)
        assertTrue(active?.source == "call")
        assertTrue(activeStore.activeKeys("call").any { it.startsWith(task.id) })
    }

    @Test
    fun `legacy call marker is promoted before a missed idle exit`() = runBlocking {
        val task = automation("call-legacy", "INCOMING")
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)
        activeStore.markActive("call", task.id)

        monitor.reconcileState(TelephonyManager.CALL_STATE_IDLE)

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("call").none { it.startsWith(task.id) })
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `disabled call occurrence exits before compatibility state clears`() = runBlocking {
        val task = automation("call-disabled", "INCOMING", enabled = false)
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)
        activeStore.markActive("call", task.id)

        monitor.reconcileState(TelephonyManager.CALL_STATE_RINGING)

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("call").none { it.startsWith(task.id) })
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `stale call marker cannot take ownership from another stateful source`() = runBlocking {
        val task = automation("call-foreign", "INCOMING")
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)
        runtimeStore.activateStrict(
            AutomationRuntimeState(
                automationId = task.id,
                occurrenceId = "settings-owner",
                source = "settings",
                sourceKey = "${task.id}|foreign",
                lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                activatedAt = 1L
            )
        )
        activeStore.markActive("call", task.id)

        monitor.reconcileState(TelephonyManager.CALL_STATE_RINGING)

        assertEquals("settings", runtimeStore.current(task.id)?.source)
        assertTrue(activeStore.activeKeys("call").none { it.startsWith(task.id) })
        assertTrue(history.exits.none { it == EXIT_NOOP_MARKER })
    }
}
