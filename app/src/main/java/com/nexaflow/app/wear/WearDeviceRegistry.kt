package com.nexaflow.app.wear

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.compat.TriggerSource
import com.nexaflow.core.wearprotocol.WearCapability
import com.nexaflow.core.wearprotocol.WearCapabilitySnapshot
import com.nexaflow.core.wearprotocol.WearDeviceDescriptor
import com.nexaflow.core.wearprotocol.WearProtocol
import com.nexaflow.core.wearprotocol.WearProtocolJson
import com.nexaflow.core.wearprotocol.WearRuntimeState
import com.nexaflow.domain.events.NexaFlowEvent
import com.nexaflow.domain.events.NexaFlowEventBus
import com.nexaflow.domain.events.NexaFlowEventType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Phone-side registry of watches that have advertised NexaFlow capabilities.
 *
 * Durable identity is [WearDeviceDescriptor.watchInstallId]; Data Layer node id
 * is retained only as the current transport address and may change after
 * re-pairing. Reachability is observed from the watch capability, not inferred
 * from stale DataItems.
 */
@Singleton
class WearDeviceRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val eventBus: NexaFlowEventBus,
) {
    private val started = AtomicBoolean(false)
    private val _devices = MutableStateFlow<List<WearDeviceDescriptor>>(emptyList())

    @Volatile
    private var reachableNodeIds: Set<String> = emptySet()

    private val capabilityListener =
        CapabilityClient.OnCapabilityChangedListener { info ->
            scope.launch(Dispatchers.IO) {
                applyReachableNodes(info.nodes.mapTo(linkedSetOf()) { it.id })
            }
        }

    val devices: StateFlow<List<WearDeviceDescriptor>> = _devices.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            val capabilityClient = Wearable.getCapabilityClient(context)
            runCatching {
                capabilityClient.addListener(
                    capabilityListener,
                    WearProtocol.CAPABILITY_WATCH_APP,
                ).await()
            }.onFailure { error ->
                Log.w(TAG, "Failed to register Wear reachability listener", error)
            }

            val reachable = runCatching {
                capabilityClient.getCapability(
                    WearProtocol.CAPABILITY_WATCH_APP,
                    CapabilityClient.FILTER_REACHABLE,
                ).await().nodes.mapTo(linkedSetOf()) { it.id }
            }.getOrElse { error ->
                Log.w(TAG, "Failed to read initial Wear reachability", error)
                emptySet()
            }
            applyReachableNodes(reachable)
            refreshCachedAdvertisements()
        }
    }

    suspend fun refreshCachedAdvertisements() = withContext(Dispatchers.IO) {
        val uri = Uri.parse("wear://*${WearProtocol.PATH_CAPABILITIES_V1}")
        val buffer = runCatching {
            Wearable.getDataClient(context)
                .getDataItems(uri, DataClient.FILTER_LITERAL)
                .await()
        }.getOrElse { error ->
            Log.w(TAG, "Failed to read cached Wear capability snapshots", error)
            return@withContext
        }

        try {
            buffer.forEach { item -> acceptDataItem(item) }
        } finally {
            buffer.release()
        }
    }

    fun acceptDataItem(dataItem: DataItem): Boolean {
        if (dataItem.uri.path != WearProtocol.PATH_CAPABILITIES_V1) return false

        val payload = runCatching {
            DataMapItem.fromDataItem(dataItem).dataMap.getString(WearProtocol.KEY_PAYLOAD)
        }.getOrNull() ?: return false

        val snapshot = runCatching {
            WearProtocolJson.format.decodeFromString<WearCapabilitySnapshot>(payload)
        }.getOrElse { error ->
            Log.w(TAG, "Ignoring invalid Wear capability snapshot", error)
            return false
        }

        return acceptSnapshot(dataItem.uri.host, snapshot)
    }

    internal fun acceptSnapshot(
        nodeId: String?,
        snapshot: WearCapabilitySnapshot,
    ): Boolean {
        if (!WearProtocol.isVersionSupported(snapshot.protocolVersion)) {
            Log.w(TAG, "Ignoring unsupported Wear protocol ${snapshot.protocolVersion}")
            return false
        }

        val accepted = upsert(
            WearDeviceDescriptor(
                watchInstallId = snapshot.watchInstallId,
                nodeId = nodeId,
                displayName = snapshot.deviceName,
                protocolVersion = snapshot.protocolVersion,
                appVersionName = snapshot.appVersionName,
                appVersionCode = snapshot.appVersionCode,
                wearOsSdk = snapshot.wearOsSdk,
                capabilities = snapshot.capabilities.mapNotNull { capabilityName ->
                    WearCapability.entries.firstOrNull { it.name == capabilityName }
                }.toSet(),
                lastSeenEpochMs = snapshot.updatedAtEpochMs,
            )
        )
        if (!accepted) return false

        val reachable = nodeId != null && nodeId in reachableNodeIds
        WearRuntimeState.upsertKnownDevice(
            watchInstallId = snapshot.watchInstallId,
            nodeId = nodeId,
            reachable = reachable,
            observedAtEpochMs = snapshot.updatedAtEpochMs,
        )
        publishConnectionObservation(
            watchInstallId = snapshot.watchInstallId,
            nodeId = nodeId,
            connected = reachable,
            observedAtEpochMs = maxOf(snapshot.updatedAtEpochMs, System.currentTimeMillis()),
        )
        return true
    }

    fun findByInstallId(watchInstallId: String): WearDeviceDescriptor? =
        _devices.value.firstOrNull { it.watchInstallId == watchInstallId }

    private fun applyReachableNodes(nodeIds: Set<String>) {
        reachableNodeIds = nodeIds
        val observedAt = System.currentTimeMillis()
        WearRuntimeState.updateReachableNodeIds(nodeIds, observedAt)
            .forEach { transition ->
                publishConnectionObservation(
                    watchInstallId = transition.watchInstallId,
                    nodeId = transition.nodeId,
                    connected = transition.connected,
                    observedAtEpochMs = transition.observedAtEpochMs,
                )
            }
    }

    private fun publishConnectionObservation(
        watchInstallId: String,
        nodeId: String?,
        connected: Boolean,
        observedAtEpochMs: Long,
    ) {
        val state = if (connected) STATE_CONNECTED else STATE_DISCONNECTED
        val eventId = "wear:$watchInstallId:$state:$observedAtEpochMs"
        scope.launch {
            eventBus.publish(
                NexaFlowEvent(
                    eventId = eventId,
                    type = NexaFlowEventType.WEAR_CONNECTION_CHANGED,
                    source = TriggerSource.WEAR.sourceId,
                    occurredAt = observedAtEpochMs,
                    payload = JsonObject(
                        buildMap {
                            put(KEY_WATCH_INSTALL_ID, JsonPrimitive(watchInstallId))
                            put(KEY_STATE, JsonPrimitive(state))
                            nodeId?.let { put(KEY_NODE_ID, JsonPrimitive(it)) }
                        }
                    ),
                    deduplicationKey = eventId,
                )
            )
        }
    }

    @Synchronized
    private fun upsert(device: WearDeviceDescriptor): Boolean {
        val current = _devices.value
        val existing = current.firstOrNull { it.watchInstallId == device.watchInstallId }
        val existingSeenAt = existing?.lastSeenEpochMs
        val incomingSeenAt = device.lastSeenEpochMs
        if (
            existingSeenAt != null &&
            incomingSeenAt != null &&
            incomingSeenAt < existingSeenAt
        ) {
            return false
        }

        _devices.value =
            (current.filterNot { it.watchInstallId == device.watchInstallId } + device)
                .sortedBy { it.displayName ?: it.watchInstallId }
        return true
    }

    private companion object {
        const val TAG = "WearDeviceRegistry"
        const val KEY_WATCH_INSTALL_ID = "watchInstallId"
        const val KEY_NODE_ID = "nodeId"
        const val KEY_STATE = "state"
        const val STATE_CONNECTED = "CONNECTED"
        const val STATE_DISCONNECTED = "DISCONNECTED"
    }
}
