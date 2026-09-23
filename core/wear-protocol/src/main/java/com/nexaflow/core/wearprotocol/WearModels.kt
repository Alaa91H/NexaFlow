package com.nexaflow.core.wearprotocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class WearEndpoint {
    PHONE,
    WATCH,
}

@Serializable
enum class WearMessageKind {
    COMMAND,
    EVENT,
    RESULT,
    CAPABILITIES,
    DEVICE_STATE,
    PING,
    PONG,
}

@Serializable
enum class WearCommandKind {
    RUN_AUTOMATION,
    SET_AUTOMATION_ENABLED,
    REQUEST_SYNC,
    PING,
}

@Serializable
enum class WearEventKind {
    CONNECTION_CHANGED,
    BATTERY_LEVEL_CHANGED,
    CHARGING_CHANGED,
    QUICK_ACTION,
    TILE_ACTION,
    APP_OPENED,
    CUSTOM,
}

@Serializable
enum class WearCommandStatus {
    RECEIVED,
    SUCCESS,
    FAILED,
    UNAVAILABLE,
    TIMEOUT,
    UNSUPPORTED,
    PERMISSION_REQUIRED,
    STALE,
    DUPLICATE,
}

@Serializable
enum class WearCapability {
    PROTOCOL_V1,
    AUTOMATION_SYNC,
    RUN_AUTOMATION,
    TOGGLE_AUTOMATION,
    COMMAND_ACK,
    DEVICE_STATE,
    TRIGGER_EVENTS,
    WATCH_ACTIONS,
}

@Serializable
data class WearEnvelope(
    val protocolVersion: Int = WearProtocol.CURRENT_VERSION,
    val messageId: String,
    val timestampEpochMs: Long,
    val source: WearEndpoint,
    val target: WearEndpoint,
    val kind: WearMessageKind,
    val requestId: String? = null,
    val ttlMs: Long = WearProtocol.DEFAULT_MESSAGE_TTL_MS,
    val payload: Map<String, String> = emptyMap(),
) {
    fun isExpired(nowEpochMs: Long): Boolean {
        if (ttlMs <= 0L) return true
        if (nowEpochMs <= timestampEpochMs) return false
        return nowEpochMs - timestampEpochMs > ttlMs
    }
}

@Serializable
data class WearCommandRequest(
    val requestId: String,
    val command: WearCommandKind,
    val automationId: String? = null,
    val enabled: Boolean? = null,
    val arguments: Map<String, String> = emptyMap(),
)

@Serializable
data class WearCommandResult(
    val requestId: String,
    val status: WearCommandStatus,
    val executedAtEpochMs: Long,
    val message: String? = null,
    val data: Map<String, String> = emptyMap(),
)

@Serializable
data class WearEvent(
    val eventId: String,
    val kind: WearEventKind,
    val occurredAtEpochMs: Long,
    val state: String? = null,
    val data: Map<String, String> = emptyMap(),
)

@Serializable
data class WearDeviceDescriptor(
    val watchInstallId: String,
    val nodeId: String? = null,
    val displayName: String? = null,
    val protocolVersion: Int = WearProtocol.CURRENT_VERSION,
    val appVersionName: String? = null,
    val appVersionCode: Long? = null,
    val wearOsSdk: Int? = null,
    val capabilities: Set<WearCapability> = emptySet(),
    val lastSeenEpochMs: Long? = null,
)

@Serializable
data class WearCapabilitySnapshot(
    val watchInstallId: String,
    val protocolVersion: Int = WearProtocol.CURRENT_VERSION,
    val capabilities: Set<String>,
    val deviceName: String? = null,
    val appVersionName: String? = null,
    val appVersionCode: Long? = null,
    val wearOsSdk: Int? = null,
    val permissions: Map<String, Boolean> = emptyMap(),
    val updatedAtEpochMs: Long,
)

object WearProtocolJson {
    val format: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}