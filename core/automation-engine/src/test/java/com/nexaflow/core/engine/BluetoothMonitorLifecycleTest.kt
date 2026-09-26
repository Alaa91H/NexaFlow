package com.nexaflow.core.engine

import android.content.Context
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BluetoothMonitorLifecycleTest {

    private lateinit var context: Context
    private lateinit var runtimeStore: AutomationRuntimeStore
    private lateinit var activeStore: ActiveTriggerStore

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        runtimeStore = AutomationRuntimeStore(context)
        activeStore = ActiveTriggerStore(context)
        listOf("bt-orphan", "bt-disabled").forEach { runtimeStore.clear(it) }
        activeStore.clearSource("bluetooth")
    }

    private fun automation(id: String, enabled: Boolean = true) = testAutomation(
        id = id,
        triggers = listOf(
            Trigger(
                TriggerType.BLUETOOTH_DEVICE,
                mapOf(
                    "deviceName" to "Headphones",
                    "deviceAddress" to "AA:BB:CC:DD:EE:FF",
                    "event" to "CONNECTED"
                )
            )
        )
    ).copy(enabled = enabled, actions = emptyList(), exitActions = emptyList())

    private fun monitor(
        repository: FakeRepository,
        history: RecordingHistory
    ): BluetoothMonitor {
        val engine = testEngine(context, history)
        return BluetoothMonitor(
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
    fun `missing definition preserves durable bluetooth ownership`() = runBlocking {
        runtimeStore.activateStrict(
            AutomationRuntimeState(
                automationId = "bt-orphan",
                occurrenceId = "bt-orphan-occurrence",
                source = "bluetooth",
                sourceKey = "bt-orphan|AA:BB:CC:DD:EE:FF",
                lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                activatedAt = 1L
            )
        )
        activeStore.markActive("bluetooth", "bt-orphan|AA:BB:CC:DD:EE:FF")
        val history = RecordingHistory()

        monitor(FakeRepository(emptyList()), history).rearmFromLedger()

        assertEquals(
            AutomationRuntimeLifecycleState.ACTIVE,
            runtimeStore.current("bt-orphan")?.lifecycleState
        )
        assertEquals(
            "bt-orphan-occurrence",
            runtimeStore.current("bt-orphan")?.occurrenceId
        )
        assertTrue(activeStore.activeKeys("bluetooth").isEmpty())
        assertTrue(history.exits.isEmpty())

        runtimeStore.clear("bt-orphan")
        Unit
    }

    @Test
    fun `disabled bluetooth ownership exits before mirrors clear`() = runBlocking {
        val task = automation("bt-disabled", enabled = false)
        runtimeStore.activateStrict(
            AutomationRuntimeState(
                automationId = task.id,
                occurrenceId = "bt-disabled-occurrence",
                source = "bluetooth",
                sourceKey = "bt-disabled|AA:BB:CC:DD:EE:FF",
                lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                activatedAt = 1L
            )
        )
        activeStore.markActive("bluetooth", "bt-disabled|AA:BB:CC:DD:EE:FF")
        val history = RecordingHistory()

        monitor(FakeRepository(listOf(task)), history).rearmFromLedger()

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("bluetooth").isEmpty())
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }
}
