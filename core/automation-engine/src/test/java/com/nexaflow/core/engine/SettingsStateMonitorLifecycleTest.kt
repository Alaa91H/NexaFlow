package com.nexaflow.core.engine

import android.content.Context
import android.provider.Settings
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression tests for the settings-state lifecycle boundary. These tests do
 * not treat a missing hardware adapter as OFF: an active occurrence remains
 * owned until a confirmed NOT_SATISFIED reading reaches ExitCoordinator.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsStateMonitorLifecycleTest {

    private lateinit var context: Context
    private lateinit var runtimeStore: AutomationRuntimeStore
    private lateinit var activeStore: ActiveTriggerStore
    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        runtimeStore = AutomationRuntimeStore(context)
        activeStore = ActiveTriggerStore(context)
        runtimeStore.clear("settings-unknown")
        runtimeStore.clear("settings-false")
        activeStore.clearSource("settings-state")
        Settings.Global.putInt(context.contentResolver, "low_power", 0)
    }

    @After
    fun tearDown() = runBlocking {
        runtimeStore.clear("settings-unknown")
        runtimeStore.clear("settings-false")
        activeStore.clearSource("settings-state")
    }

    private fun monitorFor(automation: com.nexaflow.domain.models.Automation, history: RecordingHistory): SettingsStateMonitor {
        val repository = FakeRepository(listOf(automation))
        val scope = CoroutineScope(Dispatchers.Default)
        scopes += scope
        val engine = testEngine(context, history)
        return SettingsStateMonitor(
            context = context,
            repository = repository,
            executionEngine = engine,
            exitCoordinator = ExitCoordinator(runtimeStore, engine, repository, history),
            runtimeStore = runtimeStore,
            activeStore = activeStore,
            scope = scope
        )
    }

    private fun activeState(id: String, sourceKey: String) = AutomationRuntimeState(
        automationId = id,
        occurrenceId = "occurrence-$id",
        source = "settings-state",
        sourceKey = sourceKey,
        lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
        activatedAt = 1L
    )

    @Test
    fun `unavailable NFC adapter is unknown and retains the active occurrence`() = runBlocking {
        val automation = testAutomation(
            id = "settings-unknown",
            triggers = listOf(Trigger(TriggerType.NFC_STATE, mapOf("state" to "ON")))
        )
        assertTrue(runtimeStore.activate(activeState("settings-unknown", "settings-unknown|NFC_STATE|ON")))
        val history = RecordingHistory()
        val monitor = monitorFor(automation, history)

        monitor.initialize()
        waitUntil { runtimeStore.current("settings-unknown") != null }
        // The Android test environment has no NFC service. Its absence must be
        // UNKNOWN, never a synthetic OFF that runs the configured exit.
        Thread.sleep(250)

        assertNotNull("unknown state must retain durable ownership", runtimeStore.current("settings-unknown"))
        assertTrue("unknown state must not run an exit", history.exits.isEmpty())
        monitor.stop()
    }

    @Test
    fun `confirmed false state performs one coordinated exit`() = runBlocking {
        val automation = testAutomation(
            id = "settings-false",
            triggers = listOf(Trigger(TriggerType.POWER_SAVER, mapOf("state" to "ON")))
        )
        assertTrue(runtimeStore.activate(activeState("settings-false", "settings-false|POWER_SAVER|ON")))
        val history = RecordingHistory()
        val monitor = monitorFor(automation, history)

        monitor.initialize()
        waitUntil { runtimeStore.current("settings-false") == null }

        assertEquals(listOf(EXIT_NOOP_MARKER), history.exits)
        assertTrue("successful exit may now consume the compatibility mark", activeStore.activeKeys("settings-state").isEmpty())
        monitor.stop()
    }
}
