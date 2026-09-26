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

@RunWith(RobolectricTestRunner::class)
class SensorMonitorLifecycleTest {

    private lateinit var context: Context
    private lateinit var runtimeStore: AutomationRuntimeStore
    private lateinit var activeStore: ActiveTriggerStore

    private val ids = listOf("sensor-one", "sensor-multi")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runtimeStore = AutomationRuntimeStore(context)
        activeStore = ActiveTriggerStore(context)
        runBlocking {
            activeStore.clearSource("sensor")
            ids.forEach {
                runtimeStore.clear(it)
                ActiveExecutionStore(context).clear(it)
            }
        }
    }

    private fun proximityAutomation(id: String) = testAutomation(
        id = id,
        triggers = listOf(
            Trigger(
                TriggerType.SENSOR,
                mapOf("sensor" to "PROXIMITY", "event" to "COVERED")
            )
        )
    ).copy(actions = emptyList())

    private fun multiSensorAutomation(id: String) = testAutomation(
        id = id,
        triggers = listOf(
            Trigger(
                TriggerType.SENSOR,
                mapOf("sensor" to "PROXIMITY", "event" to "COVERED")
            ),
            Trigger(
                TriggerType.SENSOR,
                mapOf("sensor" to "LIGHT", "event" to "ABOVE", "threshold" to "100")
            )
        )
    ).copy(actions = emptyList())

    private fun monitorFor(
        repository: FakeRepository,
        history: RecordingHistory
    ): SensorMonitor {
        val engine = testEngine(context, history)
        return SensorMonitor(
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
    fun `stateful sensor owns durable lifecycle until opposite reading`() = runBlocking {
        val task = proximityAutomation("sensor-one")
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)

        monitor.reconcileReadingForTest(
            sensor = "PROXIMITY",
            distanceCm = 0f,
            maxRangeCm = 5f,
            elapsedRealtimeMs = 1_000L,
            occurredAtEpochMs = 1_000L
        )

        val active = runtimeStore.current(task.id)
        assertEquals(AutomationRuntimeLifecycleState.ACTIVE, active?.lifecycleState)
        assertEquals(setOf("sensor-one|PROXIMITY"), activeStore.activeKeys("sensor"))

        monitor.reconcileReadingForTest(
            sensor = "PROXIMITY",
            distanceCm = 5f,
            maxRangeCm = 5f,
            elapsedRealtimeMs = 2_000L,
            occurredAtEpochMs = 2_000L
        )

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("sensor").isEmpty())
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `multi sensor lifecycle exits only after final active condition ends`() = runBlocking {
        val task = multiSensorAutomation("sensor-multi")
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)

        monitor.reconcileReadingForTest(
            sensor = "PROXIMITY",
            distanceCm = 0f,
            maxRangeCm = 5f,
            elapsedRealtimeMs = 1_000L,
            occurredAtEpochMs = 1_000L
        )
        monitor.reconcileReadingForTest(
            sensor = "LIGHT",
            lux = 200f,
            elapsedRealtimeMs = 2_000L,
            occurredAtEpochMs = 2_000L
        )

        assertEquals(
            "sensor-multi|LIGHT,PROXIMITY",
            runtimeStore.current(task.id)?.sourceKey
        )

        monitor.reconcileReadingForTest(
            sensor = "PROXIMITY",
            distanceCm = 5f,
            maxRangeCm = 5f,
            elapsedRealtimeMs = 3_000L,
            occurredAtEpochMs = 3_000L
        )

        assertEquals(
            AutomationRuntimeLifecycleState.ACTIVE,
            runtimeStore.current(task.id)?.lifecycleState
        )
        assertEquals("sensor-multi|LIGHT", runtimeStore.current(task.id)?.sourceKey)
        assertTrue(history.exits.none { it == EXIT_NOOP_MARKER })

        monitor.reconcileReadingForTest(
            sensor = "LIGHT",
            lux = 10f,
            elapsedRealtimeMs = 4_000L,
            occurredAtEpochMs = 4_000L
        )

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("sensor").isEmpty())
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }
}
