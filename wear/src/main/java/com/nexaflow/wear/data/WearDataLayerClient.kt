package com.nexaflow.wear.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends commands from the watch to the connected phone and bootstraps the
 * watch from the latest DataItem already cached by the Wearable Data Layer.
 *
 * The phone capability is preferred so commands cannot be routed to the wrong
 * wearable node. If a capability has not propagated yet (for example while
 * upgrading from an older build), the client falls back to any connected node,
 * preferring a nearby node but allowing the Data Layer's Wi-Fi/cloud route.
 */
@Singleton
class WearDataLayerClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataClient: DataClient,
    private val messageClient: MessageClient,
    private val nodeClient: NodeClient,
) {

    /**
     * Reads the newest locally available automation DataItem, if any.
     *
     * DataItems are durable Data Layer state: unlike MessageClient commands,
     * they remain available while devices are temporarily disconnected. The
     * wildcard URI covers the phone node that originally created the item and
     * also handles node-id changes after re-pairing. If more than one creator
     * exists, the payload with the greatest updatedAt value wins.
     */
    suspend fun readCachedAutomationPayload(): String? = withContext(Dispatchers.IO) {
        val uri = Uri.parse("wear://*${WearProtocol.PATH_AUTOMATIONS}")
        val buffer = runCatching {
            dataClient.getDataItems(uri, DataClient.FILTER_LITERAL).await()
        }.getOrElse { error ->
            Log.w(TAG, "Failed to read cached Wear automation DataItem", error)
            return@withContext null
        }

        try {
            buffer.mapNotNull { item ->
                runCatching {
                    val map = DataMapItem.fromDataItem(item).dataMap
                    val payload = map.getString(WearProtocol.KEY_PAYLOAD)
                        ?: return@runCatching null
                    CachedPayload(
                        payload = payload,
                        updatedAt = map.getLong(WearProtocol.KEY_UPDATED_AT),
                    )
                }.getOrNull()
            }.maxByOrNull { it.updatedAt }?.payload
        } finally {
            buffer.release()
        }
    }

    /**
     * Asks the phone to re-push the automation list now.
     *
     * A true return value means only that MessageClient accepted/delivered the
     * request to the target node. The caller must still wait for a new DataItem
     * revision before treating synchronization as complete.
     */
    suspend fun requestSync(): Boolean {
        val nodeId = resolvePhoneNodeId() ?: return false
        return runCatching {
            messageClient.sendMessage(
                nodeId,
                WearProtocol.PATH_SYNC_REQUEST,
                ByteArray(0),
            ).await()
            true
        }.getOrElse { error ->
            Log.w(TAG, "Sync request to node $nodeId failed", error)
            false
        }
    }

    /**
     * Asks the phone to manually force-run the automation with [automationId].
     * Equivalent to the phone-side "Run now" button with force-run semantics.
     */
    suspend fun sendRunCommand(automationId: String) {
        sendMessage(WearProtocol.PATH_RUN_COMMAND, automationId.toByteArray(Charsets.UTF_8))
    }

    /**
     * Asks the phone to toggle the enabled state of [automationId] to [enabled].
     * The toggle payload is encoded as "automationId:true" or "automationId:false".
     */
    suspend fun sendToggleCommand(automationId: String, enabled: Boolean) {
        val payload = "$automationId${WearProtocol.TOGGLE_SEPARATOR}$enabled"
        sendMessage(WearProtocol.PATH_TOGGLE_COMMAND, payload.toByteArray(Charsets.UTF_8))
    }

    private suspend fun sendMessage(path: String, data: ByteArray) {
        withContext(Dispatchers.IO) {
            val nodeId = resolvePhoneNodeId()
            if (nodeId == null) {
                Log.w(TAG, "No reachable phone node for Wear message on path $path")
                return@withContext
            }
            runCatching {
                messageClient.sendMessage(nodeId, path, data).await()
            }.onFailure {
                Log.w(TAG, "Failed to send Wear message on path $path", it)
            }
        }
    }

    /**
     * Resolves the actual phone companion first by its advertised capability.
     * Nearby is preferred for latency, but reachable non-nearby nodes remain
     * valid because the Wearable Data Layer can route via Wi-Fi/cloud.
     */
    private suspend fun resolvePhoneNodeId(): String? {
        val capabilityNodes = runCatching {
            Wearable.getCapabilityClient(context)
                .getCapability(
                    WearProtocol.CAPABILITY_PHONE_APP,
                    CapabilityClient.FILTER_REACHABLE,
                )
                .await()
                .nodes
        }.getOrDefault(emptySet())

        capabilityNodes.firstOrNull { it.isNearby }?.let { return it.id }
        capabilityNodes.firstOrNull()?.let { return it.id }

        return runCatching {
            val nodes = nodeClient.connectedNodes.await()
            nodes.firstOrNull { it.isNearby }?.id ?: nodes.firstOrNull()?.id
        }.getOrNull()
    }

    private data class CachedPayload(
        val payload: String,
        val updatedAt: Long,
    )

    private companion object {
        const val TAG = "WearDataLayerClient"
    }
}
