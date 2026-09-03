package com.nexaflow.core.engine

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import com.nexaflow.core.common.CellularNetworkReader
import com.nexaflow.core.common.DefaultNetworkSnapshot
import com.nexaflow.core.common.DefaultNetworkStateReader
import com.nexaflow.core.common.HotspotStateReader
import com.nexaflow.core.common.NetworkTransportState
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.UUID
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val exitCoordinator: ExitCoordinator,
    private val runtimeStore: AutomationRuntimeStore,
    private val activeStore: ActiveTriggerStore,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var initialized = false

    private val lastRunAt = ConcurrentHashMap<String, Long>()
    /** Automations currently in their triggered state, mapped to the active state string. */
    private val activeStates = ConcurrentHashMap<String, String>()
    private var callback: ConnectivityManager.NetworkCallback? = null

    // Telephony callbacks are delivered off the main thread. Their callbacks
    // only schedule a full condition read on the application scope.
    private var telephonyExecutor: ExecutorService = newTelephonyExecutor()
    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: TelephonyCallback? = null
    private var hotspotRegistration: AutoCloseable? = null
    private var legacyTelephonyListener: PhoneStateListener? = null

    @Volatile
    private var latestDisplayInfo: TelephonyDisplayInfo? = null

    fun initialize() {
        if (initialized) return
        initialized = true
        if (telephonyExecutor.isShutdown) {
            telephonyExecutor = newTelephonyExecutor()
        }
        scope.launch {
            // Re-arm the in-memory active set from the durable ledger BEFORE
            // the network callback is registered: registering delivers the
            // current network immediately, and that first handleChange must
            // see the restored active states so a condition that already ended
            // while the process was down fires its exit behavior right away.
            rearmFromLedger()
            if (!initialized) return@launch

            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) {
                initialized = false
                return@launch
            }
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // Queue a guarded snapshot for compatibility, but never read
                    // capabilities synchronously on the callback thread. Android
                    // supplies the authoritative payload immediately afterward
                    // through onCapabilitiesChanged on supported releases.
                    handleChange()
                }

                override fun onLost(network: Network) {
                    // A default-network handover can report a replacement before
                    // the old network is lost. Read after this callback returns so
                    // the fresh default state, if any, decides the trigger.
                    handleChange()
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    // Consume the callback payload directly instead of calling
                    // getNetworkCapabilities from the callback, which Android
                    // documents as race-prone. Telephony callbacks separately
                    // refresh NETWORK_MODE for 4G/5G transitions.
                    handleChange(DefaultNetworkSnapshot.Available(caps))
                }
            }
            callback = networkCallback
            runCatching {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
            }
            registerTelephonyCallbacks()
            registerHotspotCallback()
            handleChange()
        }
    }

    /**
     * Restores the durable active keys into the in-memory map before the first
     * network callback. Keys carry the desired state (`id|CONNECTED`), so the
     * restored entry matches the monitor's exit check exactly. Stale keys for
     * deleted/disabled automations are pruned so they can never fire a stale
     * exit.
     */
    private suspend fun rearmFromLedger() {
        val automations = repository.getAutomations().first().associateBy { it.id }
        // The occurrence ledger is authoritative. This closes the process-death
        // gap between durable lifecycle activation and writing the older active
        // trigger key used for backwards-compatible monitor bookkeeping.
        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                val automation = automations[state.automationId]
                if (automation?.enabled == true) {
                    activeStates[state.automationId] = state.sourceKey.substringAfter('|', "CONNECTED")
                    activeStore.markActive(SOURCE, state.sourceKey)
                } else if (automation != null) {
                    when (exitCoordinator.requestExit(
                        automation,
                        ExitReason.AUTOMATION_DISABLED,
                        state.occurrenceId
                    )) {
                        is ExitCoordinatorResult.Executed,
                        ExitCoordinatorResult.NotActive,
                        ExitCoordinatorResult.StaleOccurrence -> {
                            activeStates.remove(state.automationId)
                            activeStore.clearAutomation(SOURCE, state.automationId)
                        }
                        ExitCoordinatorResult.AlreadyInProgress,
                        is ExitCoordinatorResult.RecoveryRequired -> Unit
                    }
                }
            }
        // Migrate a pre-runtime-ledger active key once, preserving configured
        // exit actions after an upgrade. No snapshot existed in that old format,
        // so only normal exit actions (not state restoration) can be recovered.
        activeStore.activeKeys(SOURCE).forEach { sourceKey ->
            val automationId = sourceKey.substringBefore('|')
            val automation = automations[automationId]
            if (automation?.enabled != true) {
                activeStore.clearAutomation(SOURCE, automationId)
                return@forEach
            }
            val current = runtimeStore.current(automationId)
            if (current == null) {
                runtimeStore.activate(
                    AutomationRuntimeState(
                        automationId = automationId,
                        occurrenceId = "legacy:$SOURCE:$automationId:${UUID.randomUUID()}",
                        source = SOURCE,
                        sourceKey = sourceKey,
                        lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                        activatedAt = System.currentTimeMillis()
                    )
                )
            }
            runtimeStore.current(automationId)
                ?.takeIf { it.source == SOURCE }
                ?.let { state ->
                    activeStates[automationId] = state.sourceKey.substringAfter('|', "CONNECTED")
                }
        }
    }

    private companion object {
        const val SOURCE = "connectivity"
    }

    fun stop() {
        if (!initialized) return
        initialized = false
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        callback?.let { c ->
            runCatching { connectivityManager?.unregisterNetworkCallback(c) }
        }
        callback = null
        hotspotRegistration?.close()
        hotspotRegistration = null
        unregisterTelephonyCallbacks()
        activeStates.clear()
        lastRunAt.clear()
        telephonyExecutor.shutdownNow()
    }

    private fun newTelephonyExecutor(): ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "NexaFlow-telephony").apply { isDaemon = true }
        }

    /**
     * Android 16/API 36 and Android 17/API 37 expose an app-facing tethering
     * callback. It is the authoritative event for the HOTSPOT trigger; default
     * network callbacks do not report the device's own Soft AP state.
     */
    private fun registerHotspotCallback() {
        hotspotRegistration?.close()
        hotspotRegistration = HotspotStateReader.observe(context, telephonyExecutor) {
            handleChange()
        }
    }

    /**
     * Uses TelephonyCallback on Android 12+ and the public PhoneStateListener
     * callbacks on older releases. Registration failures are expected on
     * devices without phone permission or telephony hardware and are ignored;
     * the synchronous reader still degrades to null.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun registerTelephonyCallbacks() {
        if (!initialized) return
        unregisterTelephonyCallbacks()
        val manager = CellularNetworkReader.telephonyForDefaultDataSubscription(context)
            ?: return
        telephonyManager = manager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(),
                TelephonyCallback.DisplayInfoListener,
                TelephonyCallback.ServiceStateListener,
                TelephonyCallback.ActiveDataSubscriptionIdListener {
                override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                    latestDisplayInfo = telephonyDisplayInfo
                    handleChange()
                }

                override fun onServiceStateChanged(serviceState: ServiceState) {
                    handleChange()
                }

                override fun onActiveDataSubscriptionIdChanged(subId: Int) {
                    latestDisplayInfo = null
                    registerTelephonyCallbacks()
                    handleChange()
                }
            }
            if (runCatching {
                    manager.registerTelephonyCallback(telephonyExecutor, callback)
                }.isSuccess
            ) {
                telephonyCallback = callback
                return
            }
            // Fall through to the legacy listener when the modern registration
            // is refused (typically the missing runtime READ_PHONE_STATE grant;
            // sometimes an OEM restriction). Registration is marshalled to the
            // main thread below so the no-arg constructor's internal
            // Looper.myLooper() read can never NPE on this scope's Looper-less
            // background thread — which previously aborted the whole monitor
            // with a swallowed 'Background coroutine failed'.
        }

        // The deprecated listener is the only telephony signal path on Android
        // 11 and below, and the fallback on newer releases. Its no-arg
        // constructor resolves Looper.myLooper() for an internal Handler, so it
        // must be constructed on a thread with a Looper. This registration can
        // run from the application scope (no Looper), so marshal construction
        // and registration to the main thread.
        registerLegacyListenerOnMain(manager)
    }

    /**
     * Registers the deprecated PhoneStateListener for pre-Android-12 devices.
     * Runs on the main thread, where Looper.myLooper() is non-null; posting
     * from the main thread executes inline is not guaranteed, so the queued
     * runnable re-checks that registration is still wanted before it acts.
     */
    @Suppress("DEPRECATION")
    private fun registerLegacyListenerOnMain(manager: TelephonyManager) {
        val registration = Runnable {
            // Guard against stop()/re-registration clearing state while this
            // message sat in the queue.
            if (!initialized || telephonyManager !== manager) return@Runnable
            val listener = object : PhoneStateListener() {
                override fun onServiceStateChanged(serviceState: ServiceState) {
                    handleChange()
                }

                override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                    handleChange()
                }

                override fun onActiveDataSubscriptionIdChanged(subId: Int) {
                    latestDisplayInfo = null
                    registerTelephonyCallbacks()
                    handleChange()
                }
            }
            var listenFlags = PhoneStateListener.LISTEN_SERVICE_STATE or
                PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                listenFlags = listenFlags or PhoneStateListener.LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE
            }
            if (runCatching { manager.listen(listener, listenFlags) }.isSuccess) {
                legacyTelephonyListener = listener
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            registration.run()
        } else {
            Handler(Looper.getMainLooper()).post(registration)
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun unregisterTelephonyCallbacks() {
        val manager = telephonyManager
        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { callback ->
                    runCatching { manager.unregisterTelephonyCallback(callback) }
                }
            }
            legacyTelephonyListener?.let { listener ->
                runCatching { manager.listen(listener, PhoneStateListener.LISTEN_NONE) }
            }
        }
        telephonyCallback = null
        legacyTelephonyListener = null
        telephonyManager = null
        latestDisplayInfo = null
    }

    private fun handleChange(callbackSnapshot: DefaultNetworkSnapshot? = null) {
        if (!initialized) return
        scope.launch {
            if (!initialized) return@launch
            // Callback-delivered capabilities are authoritative for that change.
            // Outside a callback, take one guarded snapshot that preserves the
            // distinction between no default network and a transient unreadable
            // capability read.
            val networkSnapshot = callbackSnapshot ?: DefaultNetworkStateReader.read(context)
            val automations = repository.getAutomations().first()
            val now = System.currentTimeMillis()
            automations
                .filter { automation ->
                    automation.enabled && automation.triggers.any {
                        it.type == TriggerType.CONNECTIVITY ||
                            it.type == TriggerType.HOTSPOT ||
                            it.type == TriggerType.NETWORK_MODE
                    }
                }
                .forEach { automation ->
                    // Saved tasks can keep the legacy combined CONNECTIVITY type;
                    // newly created hotspot tasks use the dedicated HOTSPOT type.
                    val trigger = automation.triggers.firstOrNull {
                        it.type == TriggerType.CONNECTIVITY ||
                            it.type == TriggerType.HOTSPOT ||
                            it.type == TriggerType.NETWORK_MODE
                    } ?: return@forEach
                    val network = trigger.config["network"] ?: when (trigger.type) {
                        TriggerType.HOTSPOT -> "HOTSPOT"
                        TriggerType.NETWORK_MODE -> "NETWORK_MODE"
                        else -> "WIFI"
                    }
                    val desiredState = trigger.config["state"]
                        ?: if (network == "HOTSPOT") "ON" else "CONNECTED"
                    val current = currentNetworkValue(network, networkSnapshot)
                    val matched = if (network == "NETWORK_MODE") {
                        CellularNetworkReader.matchesNetworkMode(desiredState, current)
                    } else {
                        current == desiredState
                    }
                    if (matched && activeStates[automation.id] != desiredState) {
                        val last = lastRunAt[automation.id] ?: 0L
                        if (now - last > automation.cooldownMillis) {
                            lastRunAt[automation.id] = now
                            val sourceKey = "${automation.id}|$desiredState"
                            val occurrenceId = "connectivity:${automation.id}:${UUID.randomUUID()}"
                            executionEngine.runAutomation(
                                automation = automation,
                                lifecycleContext = AutomationLifecycleContext(
                                    occurrenceId = occurrenceId,
                                    source = SOURCE,
                                    sourceKey = sourceKey
                                )
                            )
                            val accepted = runtimeStore.current(automation.id)?.let { state ->
                                state.occurrenceId == occurrenceId && state.source == SOURCE
                            } == true
                            if (accepted) {
                                activeStore.markActive(SOURCE, sourceKey)
                                activeStates[automation.id] = desiredState
                                // The condition can flip while runAutomation
                                // is awaiting actions. Re-read now that a
                                // durable owner exists, so no later callback
                                // is required to close the occurrence.
                                handleChange()
                            }
                        }
                    } else if (current != null && activeStates[automation.id] == desiredState) {
                        // A known non-matching value ends the condition. An
                        // unreadable cellular generation is deliberately not an
                        // exit event, otherwise a transient permission/OEM read
                        // failure could run exit actions incorrectly.
                        when (exitCoordinator.requestExit(automation, ExitReason.TRIGGER_FALSE)) {
                            is ExitCoordinatorResult.Executed,
                            ExitCoordinatorResult.NotActive,
                            ExitCoordinatorResult.StaleOccurrence -> {
                                activeStates.remove(automation.id)
                                activeStore.clearAutomation(SOURCE, automation.id)
                            }
                            ExitCoordinatorResult.AlreadyInProgress,
                            is ExitCoordinatorResult.RecoveryRequired -> Unit
                        }
                    }
                }
        }
    }

    /**
     * The current value of the monitored connectivity condition, in the same
     * vocabulary the trigger config uses: CONNECTED/DISCONNECTED for WIFI and
     * MOBILE, ON/OFF for the hotspot, and the cellular generation (2G/3G/4G/5G)
     * for NETWORK_MODE. Null means the condition is not applicable or could not
     * be read; it never becomes AUTO implicitly.
     */
    private fun currentNetworkValue(
        network: String,
        snapshot: DefaultNetworkSnapshot
    ): String? = when (network) {
        "WIFI" -> defaultTransportValue(snapshot, NetworkCapabilities.TRANSPORT_WIFI)
        "MOBILE" -> defaultTransportValue(snapshot, NetworkCapabilities.TRANSPORT_CELLULAR)
        "HOTSPOT" -> HotspotStateReader.currentState(context)
            ?.let { enabled -> if (enabled) "ON" else "OFF" }
        "NETWORK_MODE" -> {
            // The cellular registration can remain active while Wi-Fi is the
            // default route; do not gate the telephony read on the currently
            // active transport or a 4G/5G transition will be missed.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                CellularNetworkReader.read(context, latestDisplayInfo)
            } else {
                CellularNetworkReader.read(context)
            }
        }
        else -> null
    }

    private fun defaultTransportValue(
        snapshot: DefaultNetworkSnapshot,
        transport: Int
    ): String? = when (DefaultNetworkStateReader.transportState(snapshot, transport)) {
        NetworkTransportState.CONNECTED -> "CONNECTED"
        NetworkTransportState.DISCONNECTED -> "DISCONNECTED"
        NetworkTransportState.UNKNOWN -> null
    }
}

/**
 * Compatibility entry point for callers that only need a one-shot read. The
 * actual implementation lives in core:common so the monitor, manual gate and
 * editor share exactly the same semantics.
 */
fun currentCellularGeneration(context: Context): String? =
    CellularNetworkReader.read(context)

/** Maps a TelephonyManager network type to the trigger vocabulary. */
internal fun cellularGenerationOf(networkType: Int): String? =
    CellularNetworkReader.generationOf(networkType)
