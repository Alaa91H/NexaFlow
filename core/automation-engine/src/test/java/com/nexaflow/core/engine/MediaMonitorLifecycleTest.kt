package com.nexaflow.core.engine

import android.content.Context
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

/**
 * Playback lifecycle contract: media state owns a durable occurrence before
 * its main chain can be considered active, and the opposite playback state
 * exits only through [ExitCoordinator].
 */
@RunWith(RobolectricTestRunner::class)
class MediaMonitorLifecycleTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            ActiveTriggerStore(context).clearSource("media")
            ActiveExecutionStore(context).clear("media-task")
            AutomationRuntimeStore(context).clear("media-task")
        }
    }

    private fun automation(event: String = "STARTED") =
        testAutomation(
            id = "media-task",
            triggers = listOf(
                Trigger(
                    TriggerType.MEDIA_PLAYING,
                    mapOf("event" to event)
                )
            )
        )

    private fun monitor(
        repository: FakeRepository,
        history: RecordingHistory,
        runtimeStore: AutomationRuntimeStore,
        activeStore: ActiveTriggerStore
    ): MediaMonitor {
        val engine = testEngine(context, history)
        return MediaMonitor(
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
    fun `matching playback state establishes durable ownership before active marker`() = runBlocking {
        val task = automation()
        val repository = FakeRepository(listOf(task))
        val history = RecordingHistory()
        val runtimeStore = AutomationRuntimeStore(context)
        val activeStore = ActiveTriggerStore(context)
        val monitor = monitor(repository, history, runtimeStore, activeStore)

        monitor.reconcileState(playing = true)

        val runtime = runtimeStore.current(task.id)
        assertEquals(AutomationRuntimeLifecycleState.ACTIVE, runtime?.lifecycleState)
        assertEquals("media", runtime?.source)
        assertTrue(activeStore.activeKeys("media").contains(task.id))
    }

    @Test
    fun `opposite playback state exits once and clears durable ownership`() = runBlocking {
        val task = automation()
        val repository = FakeRepository(listOf(task))
        val history = RecordingHistory()
        val runtimeStore = AutomationRuntimeStore(context)
        val activeStore = ActiveTriggerStore(context)
        val monitor = monitor(repository, history, runtimeStore, activeStore)

        monitor.reconcileState(playing = true)
        monitor.reconcileState(playing = false)

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("media").isEmpty())
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `restart rearm preserves occurrence and opposite state fires missed exit`() = runBlocking {
        val task = automation()
        val repository = FakeRepository(listOf(task))
        val history = RecordingHistory()
        val runtimeStore = AutomationRuntimeStore(context)
        val activeStore = ActiveTriggerStore(context)
        runtimeStore.activate(
            AutomationRuntimeState(
                automationId = task.id,
                occurrenceId = "media:media-task:restarted",
                source = "media",
                sourceKey = "STARTED",
                lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                activatedAt = 1L
            )
        )

        val monitor = monitor(repository, history, runtimeStore, activeStore)
        monitor.rearmFromLedger()
        monitor.reconcileState(playing = false)

        assertNull(runtimeStore.current(task.id))
        assertTrue(activeStore.activeKeys("media").isEmpty())
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `legacy marker without durable occurrence never authorizes an exit`() = runBlocking {
        val task = automation()
        val repository = FakeRepository(listOf(task))
        val history = RecordingHistory()
        val runtimeStore = AutomationRuntimeStore(context)
        val activeStore = ActiveTriggerStore(context)
        activeStore.markActive("media", task.id)

        val monitor = monitor(repository, history, runtimeStore, activeStore)
        monitor.rearmFromLedger()
        monitor.reconcileState(playing = false)

        assertTrue(history.exits.none { it == EXIT_NOOP_MARKER })
        assertTrue(activeStore.activeKeys("media").isEmpty())
        assertNull(runtimeStore.current(task.id))
    }
}
