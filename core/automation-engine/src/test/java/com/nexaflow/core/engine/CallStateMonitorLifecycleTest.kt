package com.nexaflow.core.engine

import android.content.Context
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
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
        "incoming-call",
        "outgoing-call",
        "restart-offhook",
        "legacy-call",
        "disabled-call"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        activeStore = ActiveTriggerStore(context)
        runtimeStore = AutomationRuntimeStore(context)
        runBlocking {
            activeStore.clearSource("call")
            ids.forEach { runtimeStore.clear(it) }
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            activeStore.clearSource("call")
            ids.forEach { runtimeStore.clear(it) }
        }
    }

    private fun automation(
        id: String,
        event: String,
        enabled: Boolean = true
    ) = testAutomation(
        id = id,
        triggers = listOf(
            Trigger(
                TriggerType.CALL_STATE,
                mapOf("event" to event)
            )
        )
    ).copy(
        enabled = enabled,
        actions = emptyList()
    )

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
    fun `incoming occurrence survives answer and exits only when call becomes idle`() = runBlocking {
        val task = automation("incoming-call", "INCOMING")
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)

        monitor.reconcileState(
            state = TelephonyManager.CALL_STATE_RINGING,
            previousState = TelephonyManager.CALL_STATE_IDLE,
            transitionKnown = true
        )
        val ringingOccurrence = runtimeStore.current(task.id)
        assertEquals(AutomationRuntimeLifecycleState.ACTIVE, ringingOccurrence?.lifecycleState)

        monitor.reconcileState(
            state = TelephonyManager.CALL_STATE_OFFHOOK,
            previousState = TelephonyManager.CALL_STATE_RINGING,
            transitionKnown = true
        )
        assertEquals(
            ringingOccurrence?.occurrenceId,
            runtimeStore.current(task.id)?.occurrenceId
        )
        assertTrue(history.exits.none { it == EXIT_NOOP_MARKER })

        monitor.reconcileState(
            state = TelephonyManager.CALL_STATE_IDLE,
            previousState = TelephonyManager.CALL_STATE_OFFHOOK,
            transitionKnown = true
        )

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("call").none { it.startsWith(task.id) })
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `idle to offhook transition proves outgoing call`() = runBlocking {
        val task = automation("outgoing-call", "OUTGOING")
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)

        monitor.reconcileState(
            state = TelephonyManager.CALL_STATE_OFFHOOK,
            previousState = TelephonyManager.CALL_STATE_IDLE,
            transitionKnown = true
        )

        assertEquals(
            AutomationRuntimeLifecycleState.ACTIVE,
            runtimeStore.current(task.id)?.lifecycleState
        )
        assertTrue(activeStore.activeKeys("call").any { it.startsWith(task.id) })
    }

    @Test
    fun `answered incoming call never fabricates outgoing activation`() = runBlocking {
        val task = automation("outgoing-call", "OUTGOING")
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)

        monitor.reconcileState(
            state = TelephonyManager.CALL_STATE_RINGING,
            previousState = TelephonyManager.CALL_STATE_IDLE,
            transitionKnown = true
        )
        monitor.reconcileState(
            state = TelephonyManager.CALL_STATE_OFFHOOK,
            previousState = TelephonyManager.CALL_STATE_RINGING,
            transitionKnown = true
        )

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("call").none { it.startsWith(task.id) })
    }

    @Test
    fun `restart into offhook does not guess that call is outgoing`() = runBlocking {
        val task = automation("restart-offhook", "OUTGOING")
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)

        monitor.reconcileState(
            state = TelephonyManager.CALL_STATE_OFFHOOK,
            previousState = null,
            transitionKnown = false
        )

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("call").none { it.startsWith(task.id) })
    }

    @Test
    fun `legacy call marker is promoted before a known idle state exits it`() = runBlocking {
        val task = automation("legacy-call", "INCOMING")
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)
        activeStore.markActive("call", task.id)

        monitor.reconcileState(
            state = TelephonyManager.CALL_STATE_IDLE,
            previousState = null,
            transitionKnown = false
        )

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("call").none { it.startsWith(task.id) })
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `disabled legacy call closes through coordinator`() = runBlocking {
        val task = automation("disabled-call", "INCOMING", enabled = false)
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)
        activeStore.markActive("call", task.id)

        monitor.reconcileState(
            state = TelephonyManager.CALL_STATE_RINGING,
            previousState = null,
            transitionKnown = false
        )

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("call").none { it.startsWith(task.id) })
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }
}
