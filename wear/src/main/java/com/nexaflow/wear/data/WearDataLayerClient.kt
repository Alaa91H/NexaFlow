package com.nexaflow.wear.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
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
 * Sends command messages from the watch to the connected phone via the
 * Wearable [MessageClient].
 *
 * The phone capability is preferred so commands cannot be routed to the wrong
 * wearable node. If a capability has not propagated yet (for example while
 * upgrading from an older build), the client falls back to any connected node,
 * preferring a nearby node but allowing the Data Layer's Wi-Fi/cloud route.
 */
@Singleton
class WearDataLayerClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageClient: MessageClient,
    private val nodeClient: NodeClient,
) {

    /**
     * Asks the phone to re-push the automation list now. Called when the watch
     * UI starts: the phone only pushes on data changes, so without this pull
     * the watch could sit on its "Connecting" spinner forever whenever the
     * phone process started (or its data last changed) while the watch was
     * disconnected.
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

    private companion object {
        const val TAG = "WearDataLayerClient"
    }
}
