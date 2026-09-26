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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VolumeMonitorLifecycleTest {

    private lateinit var context: Context
    private lateinit var runtimeStore: AutomationRuntimeStore
    private lateinit var activeStore: ActiveTriggerStore

    private val ids = listOf(
        "volume-active",
        "volume-unknown",
        "volume-legacy",
        "volume-disabled",
        "volume-orphan"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runtimeStore = AutomationRuntimeStore(context)
        activeStore = ActiveTriggerStore(context)
        runBlocking {
            ids.forEach { runtimeStore.clear(it) }
            activeStore.clearSource("volume")
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            ids.forEach { runtimeStore.clear(it) }
            activeStore.clearSource("volume")
        }
    }

    private fun automation(
        id: String,
        threshold: Int = 50,
        direction: String = "ABOVE",
        enabled: Boolean = true
    ) = testAutomation(
        id = id,
        triggers = listOf(
            Trigger(
                TriggerType.VOLUME_CHANGED,
                mapOf(
                    "stream" to "MUSIC",
                    "threshold" to threshold.toString(),
                    "direction" to direction
                )
            )
        )
    ).copy(
        enabled = enabled,
        actions = emptyList()
    )

    private fun monitorFor(
        repository: FakeRepository,
        history: RecordingHistory
    ): VolumeMonitor {
        val engine = testEngine(context, history)
        return VolumeMonitor(
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
    fun `raw stream indexes normalize to the configured percentage scale`() {
        assertEquals(0, normalizedVolumePercent(level = 2, minimum = 2, maximum = 12))
        assertEquals(50, normalizedVolumePercent(level = 7, minimum = 2, maximum = 12))
        assertEquals(100, normalizedVolumePercent(level = 12, minimum = 2, maximum = 12))
        assertNull(normalizedVolumePercent(level = 5, minimum = 5, maximum = 5))
    }

    @Test
    fun `matching percentage owns a durable occurrence and opposite side exits once`() = runBlocking {
        val task = automation("volume-active", threshold = 50)
        val repository = FakeRepository(listOf(task))
        val history = RecordingHistory()
        val monitor = monitorFor(repository, history)

        monitor.reconcileSnapshot(mapOf("MUSIC" to 60))

        val active = runtimeStore.current(task.id)
        assertEquals("volume", active?.source)
        assertEquals(AutomationRuntimeLifecycleState.ACTIVE, active?.lifecycleState)
        assertTrue(activeStore.activeKeys("volume").any { it.startsWith(task.id) })

        monitor.reconcileSnapshot(mapOf("MUSIC" to 40))

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("volume").none { it.startsWith(task.id) })
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `unreadable stream state never ends an active occurrence`() = runBlocking {
        val task = automation("volume-unknown", threshold = 50)
        val repository = FakeRepository(listOf(task))
        val history = RecordingHistory()
        val monitor = monitorFor(repository, history)

        monitor.reconcileSnapshot(mapOf("MUSIC" to 75))
        monitor.reconcileSnapshot(mapOf("MUSIC" to null))

        val active = runtimeStore.current(task.id)
        assertEquals(AutomationRuntimeLifecycleState.ACTIVE, active?.lifecycleState)
        assertTrue(activeStore.activeKeys("volume").any { it.startsWith(task.id) })
        assertTrue(history.exits.none { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `legacy marker is promoted before restart reconciliation can exit it`() = runBlocking {
        val task = automation("volume-legacy", threshold = 50)
        val repository = FakeRepository(listOf(task))
        val history = RecordingHistory()
        val monitor = monitorFor(repository, history)
        activeStore.markActive("volume", task.id)

        monitor.rearmFromLedger()

        val promoted = runtimeStore.current(task.id)
        assertEquals("volume", promoted?.source)
        assertEquals(AutomationRuntimeLifecycleState.ACTIVE, promoted?.lifecycleState)

        monitor.reconcileSnapshot(mapOf("MUSIC" to 10))

        assertNull(runtimeStore.current(task.id))
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `disabled legacy routine closes through ExitCoordinator before mirror cleanup`() = runBlocking {
        val task = automation("volume-disabled", enabled = false)
        val repository = FakeRepository(listOf(task))
        val history = RecordingHistory()
        val monitor = monitorFor(repository, history)
        activeStore.markActive("volume", task.id)

        monitor.rearmFromLedger()

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("volume").none { it.startsWith(task.id) })
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `missing definition preserves orphaned durable evidence`() = runBlocking {
        val repository = FakeRepository(emptyList())
        val history = RecordingHistory()
        val monitor = monitorFor(repository, history)
        runtimeStore.activateStrict(
            AutomationRuntimeState(
                automationId = "volume-orphan",
                occurrenceId = "volume:orphan:1",
                source = "volume",
                sourceKey = "volume-orphan|MUSIC",
                lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                activatedAt = 1L
            )
        )
        activeStore.markActive("volume", "volume-orphan|MUSIC")

        monitor.rearmFromLedger()

        assertEquals(
            AutomationRuntimeLifecycleState.ACTIVE,
            runtimeStore.current("volume-orphan")?.lifecycleState
        )
        assertTrue(activeStore.activeKeys("volume").none { it.startsWith("volume-orphan") })
        assertTrue(history.exits.isEmpty())
    }
}
