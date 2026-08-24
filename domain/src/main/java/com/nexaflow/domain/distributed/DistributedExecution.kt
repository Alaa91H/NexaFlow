package com.nexaflow.domain.distributed

import com.nexaflow.domain.capability.CapabilityResult
import kotlinx.serialization.Serializable

/**
 * Identifies a remote device in the user's NexaFlow mesh network.
 * Allows executing a workflow node on a tablet from a phone, or via a desktop client.
 */
@Serializable
data class RemoteNodeEndpoint(
    val deviceId: String,
    val deviceName: String,
    val endpointUrl: String,
    val connectionType: ConnectionType
) {
    enum class ConnectionType {
        LOCAL_LAN,
        BLUETOOTH,
        CLOUD_RELAY
    }
}

/**
 * Request payload sent to a remote device to execute an action.
 */
@Serializable
data class RemoteExecutionRequest(
    val runId: String,
    val actionType: String,
    val parameters: Map<String, String>,
    val requiresCapabilities: List<String>
)

/**
 * Response payload received from a remote device.
 */
@Serializable
data class RemoteExecutionResponse(
    val success: Boolean,
    val message: String,
    val metadata: Map<String, String>
)

/**
 * Routes capability execution requests to remote devices if a node is configured
 * to run distributedly.
 */
interface DistributedExecutionRouter {
    /** List known, authenticated devices in the mesh. */
    suspend fun getAvailableEndpoints(): List<RemoteNodeEndpoint>
    
    /** Route an execution request to a specific endpoint. */
    suspend fun route(
        endpoint: RemoteNodeEndpoint,
        request: RemoteExecutionRequest
    ): RemoteExecutionResponse
}
