package com.nexaflow.core.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * Connectivity exit-reliability contract: a task triggered by a network
 * condition BEFORE a process/service restart must still fire its exit behavior
 * when the condition ends. The monitor's in-memory active set dies with the
 * process, so it re-arms from the durable occurrence lifecycle ledger on start
 * — and the first network callback re-evaluates the CURRENT network, so a
 * condition that already ended while the process was down (e.g. WiFi dropped)
 * fires its missed exit on that first callback instead of waiting for the
 * network to flip again (possibly never).
 *
 * Scenario: a WiFi-CONNECTED task fired, then the process was killed while
 * still on WiFi. WiFi drops during downtime, then the app restarts. The fresh
 * monitor must see the durable mark, read the current network (disconnected),
 * and run the exit.
 */
@RunWith(RobolectricTestRunner::class)
class ConnectivityMonitorExitReconcileTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Robolectric shares one Application (and its DataStore cache) across
        // test methods, so reset the connectivity source for isolation.
        runBlocking {
            ActiveTriggerStore(context).clearSource("connectivity")
            ActiveExecutionStore(context).clear("conn-task")
            AutomationRuntimeStore(context).clear("conn-task")
        }
    }

    private fun connectivityAutomation(id: String): com.nexaflow.domain.models.Automation =
        testAutomation(
            id = id,
            triggers = listOf(
                Trigger(
                    TriggerType.CONNECTIVITY,
                    mapOf("network" to "WIFI", "state" to "CONNECTED")
                )
            )
        )

    private fun connectivityManager(): ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun shadowCm(): ShadowConnectivityManager = shadowOf(connectivityManager())

    /** Simulates the device being on WiFi with internet. */
    private fun setWifiConnected() {
        shadowCm().setDefaultNetworkActive(true)
        shadowCm().setActiveNetworkInfo(
            ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI,
                0,
                true,
                true
            )
        )
        val active = connectivityManager().activeNetwork
        shadowCm().setNetworkCapabilities(active, wifiCapabilities())
    }

    /**
     * The compile-time android.jar for SDK 37 strips `NetworkCapabilities.Builder`
     * (the class exists in the Robolectric runtime), so build the capabilities
     * via reflection to keep the test compiling against the stub while
     * exercising the real class at runtime.
     */
    private fun wifiCapabilities(): NetworkCapabilities {
        val builder = Class.forName("android.net.NetworkCapabilities\$Builder")
            .getConstructor()
            .newInstance()
        val builderClass = builder.javaClass
        builderClass.getMethod("addTransportType", Int::class.javaPrimitiveType)
            .invoke(builder, NetworkCapabilities.TRANSPORT_WIFI)
        builderClass.getMethod("addCapability", Int::class.javaPrimitiveType)
            .invoke(builder, NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return builderClass.getMethod("build").invoke(builder) as NetworkCapabilities
    }

    /** Simulates WiFi being gone entirely. */
    private fun setWifiDisconnected() {
        shadowCm().setDefaultNetworkActive(true)
        shadowCm().setActiveNetworkInfo(null)
    }

    private fun monitorFor(
        repository: FakeRepository,
        engine: com.nexaflow.core.execution.ExecutionEngine,
        exitCoordinator: ExitCoordinator,
        runtimeStore: AutomationRuntimeStore,
        store: ActiveTriggerStore
    ): ConnectivityMonitor = ConnectivityMonitor(
        context = context,
        repository = repository,
        executionEngine = engine,
        exitCoordinator = exitCoordinator,
        runtimeStore = runtimeStore,
        activeStore = store,
        scope = CoroutineScope(Dispatchers.Default)
    )

    /**
     * The monitor registers its default-network callback asynchronously (after
     * re-arming from the ledger). Drive the first registered callback so
     * handleChange re-reads the CURRENT network state.
     */
    private suspend fun driveFirstNetworkCallback() {
        waitUntil { shadowCm().getNetworkCallbacks().isNotEmpty() }
        // The monitor ignores the callback's network parameter and re-reads
        // the live state, so any network object works here. ShadowNetwork's
        // factory bypasses the real handle validation.
        shadowCm().getNetworkCallbacks().first().onAvailable(ShadowNetwork.newInstance(1))
    }

    @Test
    fun `restart with wifi already lost fires the missed exit on first callback`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(listOf(connectivityAutomation("conn-task")))
        val store = ActiveTriggerStore(context)
        // The task fired on WiFi, then the process died while still connected.
        ActiveExecutionStore(context).markStarted("conn-task")
        val runtimeStore = AutomationRuntimeStore(context)
        runtimeStore.activate(
            AutomationRuntimeState(
                automationId = "conn-task",
                occurrenceId = "connectivity:conn-task:restarted",
                source = "connectivity",
                sourceKey = "conn-task|CONNECTED",
                lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                activatedAt = 1L
            )
        )
        // WiFi is now gone — the CONNECTED condition ended during downtime.
        setWifiDisconnected()

        val exitCoordinator = ExitCoordinator(runtimeStore, engine, repository, history)
        val monitor = monitorFor(repository, engine, exitCoordinator, runtimeStore, store)
        monitor.initialize()

        driveFirstNetworkCallback()
        waitUntil { history.exits.any { it == EXIT_NOOP_MARKER } }
        waitUntil { store.activeKeys("connectivity").isEmpty() }
        monitor.stop()
    }

    @Test
    fun `restart while wifi still connected keeps the task active`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(listOf(connectivityAutomation("conn-task")))
        val store = ActiveTriggerStore(context)
        store.markActive("connectivity", "conn-task|CONNECTED")
        ActiveExecutionStore(context).markStarted("conn-task")
        val runtimeStore = AutomationRuntimeStore(context)
        runtimeStore.activate(
            AutomationRuntimeState(
                automationId = "conn-task",
                occurrenceId = "connectivity:conn-task:restarted",
                source = "connectivity",
                sourceKey = "conn-task|CONNECTED",
                lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                activatedAt = 1L
            )
        )
        // WiFi is still up — the condition still holds after the restart.
        setWifiConnected()

        val exitCoordinator = ExitCoordinator(runtimeStore, engine, repository, history)
        val monitor = monitorFor(repository, engine, exitCoordinator, runtimeStore, store)
        monitor.initialize()

        driveFirstNetworkCallback()
        assertTrue(
            "no exit while the network condition still holds",
            history.exits.none { it == EXIT_NOOP_MARKER }
        )
        assertTrue(
            "active mark survives while the condition holds",
            store.activeKeys("connectivity").isNotEmpty()
        )
        monitor.stop()
    }

    @Test
    fun `stale mark for a disabled automation is pruned on restart`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(
            listOf(connectivityAutomation("conn-task").copy(enabled = false))
        )
        val store = ActiveTriggerStore(context)
        store.markActive("connectivity", "conn-task|CONNECTED")
        setWifiDisconnected()

        val runtimeStore = AutomationRuntimeStore(context)
        val exitCoordinator = ExitCoordinator(runtimeStore, engine, repository, history)
        val monitor = monitorFor(repository, engine, exitCoordinator, runtimeStore, store)
        monitor.initialize()

        driveFirstNetworkCallback()
        assertTrue(
            "disabled task must not fire a stale exit",
            history.exits.none { it == EXIT_NOOP_MARKER }
        )
        waitUntil { store.activeKeys("connectivity").isEmpty() }
        monitor.stop()
    }
}
