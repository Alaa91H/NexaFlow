package com.nexaflow.core.engine

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
class DeviceStateMonitor28LifecycleTest {

    private lateinit var context: Context
    private lateinit var activeStore: ActiveTriggerStore
    private lateinit var runtimeStore: AutomationRuntimeStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        activeStore = ActiveTriggerStore(context)
        runtimeStore = AutomationRuntimeStore(context)
        runBlocking {
            activeStore.clearSource("device_state-28")
            listOf("dnd-task", "hdmi-task", "disabled-task").forEach {
                runtimeStore.clear(it)
            }
        }
        Settings.Global.putInt(context.contentResolver, "zen_mode", 0)
    }

    private fun automation(
        id: String,
        type: TriggerType,
        enabled: Boolean = true
    ) = testAutomation(
        id = id,
        triggers = listOf(Trigger(type, mapOf("state" to "ON")))
    ).copy(
        enabled = enabled,
        actions = emptyList()
    )

    private fun monitorFor(
        repository: FakeRepository,
        history: RecordingHistory
    ): DeviceStateMonitor28 {
        val engine = testEngine(context, history)
        return DeviceStateMonitor28(
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
    fun `known matching DND state activates once and opposite state exits`() = runBlocking {
        val task = automation("dnd-task", TriggerType.DND_STATE)
        val repository = FakeRepository(listOf(task))
        val history = RecordingHistory()
        val monitor = monitorFor(repository, history)

        Settings.Global.putInt(context.contentResolver, "zen_mode", 1)
        monitor.reconcileStateTriggers()

        val active = runtimeStore.current(task.id)
        assertEquals(AutomationRuntimeLifecycleState.ACTIVE, active?.lifecycleState)
        assertEquals("device_state-28", active?.source)
        assertTrue(activeStore.activeKeys("device_state-28").any { it.startsWith(task.id) })

        Settings.Global.putInt(context.contentResolver, "zen_mode", 0)
        monitor.reconcileStateTriggers()

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("device_state-28").none { it.startsWith(task.id) })
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `unknown HDMI state preserves promoted legacy ownership`() = runBlocking {
        val task = automation("hdmi-task", TriggerType.HDMI_CONNECTED)
        val repository = FakeRepository(listOf(task))
        val history = RecordingHistory()
        val monitor = monitorFor(repository, history)
        activeStore.markActive("device_state-28", task.id)

        monitor.reconcileStateTriggers()

        val active = runtimeStore.current(task.id)
        assertEquals(AutomationRuntimeLifecycleState.ACTIVE, active?.lifecycleState)
        assertEquals("device_state-28", active?.source)
        assertTrue(activeStore.activeKeys("device_state-28").isNotEmpty())
        assertTrue(history.exits.isEmpty())
    }

    @Test
    fun `disabled legacy state closes through exit coordinator`() = runBlocking {
        val task = automation(
            id = "disabled-task",
            type = TriggerType.DND_STATE,
            enabled = false
        )
        val repository = FakeRepository(listOf(task))
        val history = RecordingHistory()
        val monitor = monitorFor(repository, history)
        activeStore.markActive("device_state-28", task.id)

        monitor.reconcileStateTriggers()

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("device_state-28").none { it.startsWith(task.id) })
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }
}
