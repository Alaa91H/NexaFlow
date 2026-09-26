package com.nexaflow.core.engine

import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationMonitorMultiTriggerLifecycleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun location(
        latitude: Double,
        longitude: Double,
    ): Location = Location("test").apply {
        this.latitude = latitude
        this.longitude = longitude
        accuracy = 5f
    }

    private fun locationTrigger(radius: Int): Trigger = Trigger(
        TriggerType.LOCATION,
        mapOf(
            "lat" to "52.5200",
            "lng" to "13.4050",
            "radius" to radius.toString(),
            "event" to "ENTER",
            "source" to "selected",
        ),
    )

    private suspend fun fixture(
        id: String,
        mode: TriggerMatchMode,
    ): Triple<LocationMonitor, AutomationRuntimeStore, ActiveTriggerStore> {
        val automation = testAutomation(
            id = id,
            triggers = listOf(
                locationTrigger(radius = 100),
                locationTrigger(radius = 1_000),
            ),
        ).copy(
            triggerMatch = mode,
            cooldownSeconds = 0,
            actions = emptyList(),
            exitActions = emptyList(),
        )
        val repository = FakeRepository(listOf(automation))
        val history = RecordingHistory()
        val runtimeStore = AutomationRuntimeStore(context)
        val activeStore = ActiveTriggerStore(context)
        runtimeStore.clear(id)
        activeStore.clearAutomation("location", id)
        ActiveExecutionStore(context).clear(id)

        val engine = ExecutionEngine(
            context = context,
            historyRepository = history,
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(emptyList()),
            automationRuntimeStore = runtimeStore,
        )
        val exitCoordinator = ExitCoordinator(
            runtimeStore = runtimeStore,
            executionEngine = engine,
            automationRepository = repository,
            historyRepository = history,
        )
        val monitor = LocationMonitor(
            context = context,
            repository = repository,
            executionEngine = engine,
            exitCoordinator = exitCoordinator,
            runtimeStore = runtimeStore,
            activeStore = activeStore,
            scope = CoroutineScope(Dispatchers.Default),
        )
        return Triple(monitor, runtimeStore, activeStore)
    }

    @Test
    fun orphanedLocationLifecycleIsPreservedForRecoveryReview() = runBlocking {
        val id = "location-orphan"
        val repository = FakeRepository(emptyList())
        val history = RecordingHistory()
        val runtimeStore = AutomationRuntimeStore(context)
        val activeStore = ActiveTriggerStore(context)
        runtimeStore.clear(id)
        activeStore.clearAutomation("location", id)
        runtimeStore.activateStrict(
            AutomationRuntimeState(
                automationId = id,
                occurrenceId = "location-orphan-occurrence",
                source = "location",
                sourceKey = "location-indices:0",
                lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                activatedAt = 1L,
            )
        )
        activeStore.markActive("location", id)

        val engine = testEngine(context, history)
        val monitor = LocationMonitor(
            context = context,
            repository = repository,
            executionEngine = engine,
            exitCoordinator = ExitCoordinator(runtimeStore, engine, repository, history),
            runtimeStore = runtimeStore,
            activeStore = activeStore,
            scope = CoroutineScope(Dispatchers.Default),
        )

        monitor.rearmFromLedger()

        assertEquals(
            AutomationRuntimeLifecycleState.ACTIVE,
            runtimeStore.current(id)?.lifecycleState
        )
        assertEquals("location-orphan-occurrence", runtimeStore.current(id)?.occurrenceId)
        assertTrue(activeStore.activeKeys("location").isEmpty())

        runtimeStore.clear(id)
        Unit
    }

    @Test
    fun disabledLocationLifecycleRunsExitBeforeOwnershipClears() = runBlocking {
        val id = "location-disabled"
        val automation = testAutomation(
            id = id,
            triggers = listOf(locationTrigger(radius = 100)),
        ).copy(enabled = false, actions = emptyList(), exitActions = emptyList())
        val repository = FakeRepository(listOf(automation))
        val history = RecordingHistory()
        val runtimeStore = AutomationRuntimeStore(context)
        val activeStore = ActiveTriggerStore(context)
        runtimeStore.clear(id)
        activeStore.clearAutomation("location", id)
        runtimeStore.activateStrict(
            AutomationRuntimeState(
                automationId = id,
                occurrenceId = "location-disabled-occurrence",
                source = "location",
                sourceKey = "location-indices:0",
                lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                activatedAt = 1L,
            )
        )
        activeStore.markActive("location", id)

        val engine = testEngine(context, history)
        val monitor = LocationMonitor(
            context = context,
            repository = repository,
            executionEngine = engine,
            exitCoordinator = ExitCoordinator(runtimeStore, engine, repository, history),
            runtimeStore = runtimeStore,
            activeStore = activeStore,
            scope = CoroutineScope(Dispatchers.Default),
        )

        monitor.rearmFromLedger()

        assertNull(runtimeStore.current(id))
        assertTrue(activeStore.activeKeys("location").isEmpty())
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun allOwnsBothMatchingLocationTriggersAndExitsWhenEitherTargetStateEnds() = runBlocking {
        val id = "location-all-owned-indices"
        val (monitor, runtimeStore, activeStore) = fixture(id, TriggerMatchMode.ALL)

        // Selected locations initialise without emitting on the first fix.
        monitor.checkLocation(location(52.5400, 13.4050))
        kotlinx.coroutines.delay(100)
        assertNull(runtimeStore.current(id))

        // One physical fix enters both radii, so both trigger indices prove ALL.
        monitor.checkLocation(location(52.5200, 13.4050))
        waitUntil { runtimeStore.current(id) != null }
        val active = runtimeStore.current(id)
        assertNotNull(active)
        assertEquals("location-indices:0,1", active!!.sourceKey)

        // ~445 m away: outside the 100 m trigger but still inside 1 km.
        // ALL is now definitively false, so the location lifecycle must end.
        monitor.checkLocation(location(52.5240, 13.4050))
        waitUntil { runtimeStore.current(id) == null }

        activeStore.clearAutomation("location", id)
        ActiveExecutionStore(context).clear(id)
    }

    @Test
    fun anyKeepsLifecycleWhileOneOwnedLocationTargetStateRemainsTrue() = runBlocking {
        val id = "location-any-owned-indices"
        val (monitor, runtimeStore, activeStore) = fixture(id, TriggerMatchMode.ANY)

        monitor.checkLocation(location(52.5400, 13.4050))
        kotlinx.coroutines.delay(100)
        monitor.checkLocation(location(52.5200, 13.4050))
        waitUntil { runtimeStore.current(id) != null }

        // Leaving only the smaller geofence keeps ANY true.
        monitor.checkLocation(location(52.5240, 13.4050))
        kotlinx.coroutines.delay(150)
        assertNotNull(runtimeStore.current(id))

        // Leaving both owned geofences makes ANY definitively false.
        monitor.checkLocation(location(52.5400, 13.4050))
        waitUntil { runtimeStore.current(id) == null }

        activeStore.clearAutomation("location", id)
        ActiveExecutionStore(context).clear(id)
    }
}
