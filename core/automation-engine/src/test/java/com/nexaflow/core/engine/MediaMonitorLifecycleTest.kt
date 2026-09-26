package com.nexaflow.core.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
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

/**
 * Regression coverage for the media lifecycle migration. Playback state is fed
 * directly into the monitor's deterministic reconcile boundary so the tests do
 * not depend on a device AudioManager implementation.
 */
@RunWith(RobolectricTestRunner::class)
class MediaMonitorLifecycleTest {

    private lateinit var context: Context
    private lateinit var runtimeStore: AutomationRuntimeStore
    private lateinit var activeStore: ActiveTriggerStore
    private val ids = listOf("media-active", "media-exit-failed")

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            runtimeStore = AutomationRuntimeStore(context)
            activeStore = ActiveTriggerStore(context)
            ids.forEach { runtimeStore.clear(it) }
            activeStore.clearSource("media")
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            ids.forEach { runtimeStore.clear(it) }
            activeStore.clearSource("media")
        }
    }

    private fun monitorFor(
        automation: com.nexaflow.domain.models.Automation,
        history: RecordingHistory
    ): MediaMonitor {
        val repository = FakeRepository(listOf(automation))
        val engine = testEngine(context, history)
        return MediaMonitor(
            context = context,
            repository = repository,
            executionEngine = engine,
            exitCoordinator = ExitCoordinator(runtimeStore, engine, repository, history),
            runtimeStore = runtimeStore,
            activeStore = activeStore,
            scope = CoroutineScope(Dispatchers.Default)
        )
    }

    private fun startedAutomation(id: String) = testAutomation(
        id = id,
        triggers = listOf(
            Trigger(TriggerType.MEDIA_PLAYING, mapOf("event" to "STARTED"))
        )
    ).copy(actions = emptyList())

    @Test
    fun `playback activation is durable and opposite state exits exactly once`() = runBlocking {
        val history = RecordingHistory()
        val automation = startedAutomation("media-active")
        val monitor = monitorFor(automation, history)

        monitor.reconcilePlaybackState(playing = true)

        val active = runtimeStore.current(automation.id)
        assertEquals("media", active?.source)
        assertEquals(AutomationRuntimeLifecycleState.ACTIVE, active?.lifecycleState)
        assertTrue(activeStore.activeKeys("media").contains(automation.id))

        monitor.reconcilePlaybackState(playing = false)

        assertNull(runtimeStore.current(automation.id))
        assertTrue(activeStore.activeKeys("media").none { it == automation.id })
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `failed exit retains durable ownership and compatibility marker`() = runBlocking {
        val history = RecordingHistory()
        val automation = startedAutomation("media-exit-failed").copy(
            exitActions = listOf(
                Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "exit"))
            )
        )
        val monitor = monitorFor(automation, history)

        monitor.reconcilePlaybackState(playing = true)
        monitor.reconcilePlaybackState(playing = false)

        val failed = runtimeStore.current(automation.id)
        assertEquals(AutomationRuntimeLifecycleState.EXIT_FAILED, failed?.lifecycleState)
        assertTrue(activeStore.activeKeys("media").contains(automation.id))
    }
}
