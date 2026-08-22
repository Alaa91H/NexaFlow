package com.nexaflow.core.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
 * process, so it re-arms from the durable [ActiveTriggerStore] ledger on start
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
            ActiveExecutionStore(context).clear("hotspot-task")
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

    private fun hotspotAutomation(id: String): com.nexaflow.domain.models.Automation =
        testAutomation(
            id = id,
            triggers = listOf(
                Trigger(
                    TriggerType.CONNECTIVITY,
                    mapOf("network" to "HOTSPOT", "state" to "ON")
                )
            )
        )

    private fun connectivityManager(): ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun shadowCm(): ShadowConnectivityManager = shadowOf(connectivityManager())

    /**
     * Simulates the device being on WiFi with internet.
     * Robolectric's active-network fixture still accepts NetworkInfo only.
     */
    @Suppress("DEPRECATION")
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
        store: ActiveTriggerStore
    ): ConnectivityMonitor = ConnectivityMonitor(
        context = context,
        repository = repository,
        executionEngine = engine,
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
    fun `matching connectivity callbacks run once and a later loss runs exit once`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(listOf(connectivityAutomation("conn-task")))
        val monitor = monitorFor(repository, engine, ActiveTriggerStore(context))
        setWifiConnected()
        monitor.initialize()

        driveFirstNetworkCallback()
        waitUntil(timeoutMs = 15_000) { history.exits.size == 1 }
        shadowCm().getNetworkCallbacks().first().onAvailable(ShadowNetwork.newInstance(2))
        Thread.sleep(150)
        assertEquals("state callbacks must not repeat the main action", 1, history.exits.size)

        setWifiDisconnected()
        shadowCm().getNetworkCallbacks().first().onLost(ShadowNetwork.newInstance(1))
        waitUntil(timeoutMs = 15_000) { history.exits.any { it == EXIT_NOOP_MARKER } }
        val afterExit = history.exits.size
        shadowCm().getNetworkCallbacks().first().onLost(ShadowNetwork.newInstance(3))
        Thread.sleep(150)
        assertEquals("state loss must not repeat the end behavior", afterExit, history.exits.size)
        monitor.stop()
    }

    @Test
    fun `hotspot state broadcast closes the task lifecycle`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(listOf(hotspotAutomation("hotspot-task")))
        val monitor = monitorFor(repository, engine, ActiveTriggerStore(context))
        monitor.initialize()
        waitUntil { shadowCm().getNetworkCallbacks().isNotEmpty() }

        monitor.onHotspotStateChanged(13)
        waitUntil(timeoutMs = 15_000) { history.exits.size == 1 }

        monitor.onHotspotStateChanged(11)
        waitUntil(timeoutMs = 15_000) { history.exits.any { it == EXIT_NOOP_MARKER } }
        monitor.stop()
    }

    @Test
    fun `restart with wifi already lost fires the missed exit on first callback`() = runBlocking {
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val repository = FakeRepository(listOf(connectivityAutomation("conn-task")))
        val store = ActiveTriggerStore(context)
        // The task fired on WiFi, then the process died while still connected.
        store.markActive("connectivity", "conn-task|CONNECTED")
        ActiveExecutionStore(context).markStarted("conn-task")
        // WiFi is now gone — the CONNECTED condition ended during downtime.
        setWifiDisconnected()

        val monitor = monitorFor(repository, engine, store)
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
        // WiFi is still up — the condition still holds after the restart.
        setWifiConnected()

        val monitor = monitorFor(repository, engine, store)
        monitor.initialize()

        driveFirstNetworkCallback()
        // Give the async evaluation a moment, then assert no exit ran.
        Thread.sleep(300)
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

        val monitor = monitorFor(repository, engine, store)
        monitor.initialize()

        driveFirstNetworkCallback()
        Thread.sleep(300)
        assertTrue(
            "disabled task must not fire a stale exit",
            history.exits.none { it == EXIT_NOOP_MARKER }
        )
        waitUntil { store.activeKeys("connectivity").isEmpty() }
        monitor.stop()
    }
}
