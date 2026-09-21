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
 * Each command is sent to the nearest connected phone node; if no node is
 * found, the call returns silently — the user will see no change on screen
 * and the phone's [WearSyncManager] will push a fresh state when connection
 * is restored.
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
     * disconnected. Targets phone nodes advertising the companion capability
     * first; falls back to any connected node for older phone builds.
     */
    suspend fun requestSync(): Boolean {
        val capabilityNodes = runCatching {
            Wearable.getCapabilityClient(context)
                .getCapability(WearProtocol.CAPABILITY_PHONE_APP, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        }.getOrDefault(emptySet())
        val nodeId = capabilityNodes
            .filter { it.isNearby }
            .firstOrNull()?.id
            ?: capabilityNodes.firstOrNull()?.id
            ?: nearbyNodeId()
            ?: return false
        return runCatching {
            messageClient.sendMessage(
                nodeId,
                WearProtocol.PATH_SYNC_REQUEST,
                ByteArray(0)
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
            val nodeId = nearbyNodeId() ?: return@withContext
            runCatching {
                messageClient.sendMessage(nodeId, path, data).await()
            }.onFailure {
                Log.w(TAG, "Failed to send Wear message on path $path", it)
            }
        }
    }

    private suspend fun nearbyNodeId(): String? = runCatching {
        nodeClient.connectedNodes.await()
            .firstOrNull { it.isNearby }
            ?.id
    }.getOrNull()

    private companion object {
        const val TAG = "WearDataLayerClient"
    }
}
