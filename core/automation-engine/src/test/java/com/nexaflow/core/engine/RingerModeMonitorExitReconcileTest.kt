package com.nexaflow.core.engine

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ringer-mode exit-reliability contract: a task triggered by entering a sound
 * mode BEFORE a process/service restart must still fire its exit behavior when
 * the mode changes away. The monitor's in-memory active set dies with the
 * process, so it re-arms from the durable [ActiveTriggerStore] ledger on start
 * and then reads the CURRENT sound mode — so a mode already left while the
 * process was down fires the missed exit on that first reconcile (RINGER_MODE
 * broadcasts only fire on changes, so the exit would otherwise wait until the
 * mode flips again, possibly never).
 *
 * Scenario: a VIBRATE task fired, then the process was killed while still on
 * Vibrate. The user switches to Normal during downtime, then the app restarts.
 * The fresh monitor must see the durable mark, read the current mode (Normal),
 * and run the exit.
 */
@RunWith(RobolectricTestRunner::class)
class RingerModeMonitorExitReconcileTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Robolectric shares one Application (and its DataStore cache) across
        // test methods, so reset the ringer source for isolation.
        runBlocking { ActiveTriggerStore(context).clearSource("ringer") }
    }

    private fun ringerAutomation(id: String, mode: String = "VIBRATE"): com.nexaflow.domain.models.Automation =
        testAutomation(
            id = id,
            triggers = listOf(
                Trigger(TriggerType.RINGER_MODE, mapOf("mode" to mode))
            )
        )

    private fun audioManager(): AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun setRingerMode(mode: Int) {
        // ShadowAudioManager's setRingerMode helper is protected; calling the
        // real AudioManager.setRingerMode is intercepted by the shadow and
        // updates the same state the monitor reads back.
        audioManager().setRingerMode(mode)
    }

    private fun monitorFor(
        repository: FakeRepository,
        engine: com.nexaflow.core.execution.ExecutionEngine,
        store: ActiveTriggerStore
    ): RingerModeMonitor = RingerModeMonitor(
        context = context,
        repository = repository,
        executionEngine = engine,
        activeStore = store,
        scope = CoroutineScope(Dispatchers.Default)
    )

    @Test
    fun `restart with ringer already left fires the missed exit on init`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(listOf(ringerAutomation("ring-task", "VIBRATE")))
        val store = ActiveTriggerStore(context)
        // The task fired on Vibrate, then the process died while still on Vibrate.
        store.markActive("ringer", "ring-task|VIBRATE")
        // The sound mode is now Normal — the VIBRATE condition ended during downtime.
        setRingerMode(AudioManager.RINGER_MODE_NORMAL)

        val monitor = monitorFor(repository, engine, store)
        monitor.initialize()

        // reconcileWithCurrentMode reads Normal and fires the missed exit.
        waitUntil { history.exits.any { it == EXIT_NOOP_MARKER } }
        waitUntil { store.activeKeys("ringer").isEmpty() }
        monitor.stop()
    }

    @Test
    fun `restart while ringer still in the triggered mode keeps the task active`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(listOf(ringerAutomation("ring-task", "VIBRATE")))
        val store = ActiveTriggerStore(context)
        store.markActive("ringer", "ring-task|VIBRATE")
        // Still on Vibrate — the condition still holds after the restart.
        setRingerMode(AudioManager.RINGER_MODE_VIBRATE)

        val monitor = monitorFor(repository, engine, store)
        monitor.initialize()

        // Give the async reconcile a moment, then assert no exit ran.
        Thread.sleep(300)
        assertTrue(
            "no exit while the ringer mode still matches",
            history.exits.none { it == EXIT_NOOP_MARKER }
        )
        assertTrue(
            "active mark survives while the mode matches",
            store.activeKeys("ringer").isNotEmpty()
        )
        monitor.stop()
    }

    @Test
    fun `stale mark for a disabled automation is pruned on restart`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(
            listOf(ringerAutomation("ring-task", "VIBRATE").copy(enabled = false))
        )
        val store = ActiveTriggerStore(context)
        store.markActive("ringer", "ring-task|VIBRATE")
        setRingerMode(AudioManager.RINGER_MODE_NORMAL)

        val monitor = monitorFor(repository, engine, store)
        monitor.initialize()

        // Give the async prune a moment, then assert nothing fired and the
        // stale mark is gone.
        Thread.sleep(300)
        assertTrue(
            "disabled task must not fire a stale exit",
            history.exits.none { it == EXIT_NOOP_MARKER }
        )
        waitUntil { store.activeKeys("ringer").isEmpty() }
        monitor.stop()
    }
}
