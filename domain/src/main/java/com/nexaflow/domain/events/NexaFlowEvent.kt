package com.nexaflow.domain.events

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Canonical event kinds emitted by NexaFlow's existing Android monitors. */
@Serializable
enum class NexaFlowEventType {
    // System Events
    BOOT_COMPLETED,
    SHUTDOWN,
    SCREEN_ON,
    SCREEN_OFF,
    UNLOCK,
    LOCK,
    DEVICE_IDLE_CHANGED,
    CHARGING_CHANGED,
    DISCHARGING,
    BATTERY_CHANGED,
    BATTERY_LOW,
    BATTERY_FULL,
    POWER_CONNECTED,
    POWER_DISCONNECTED,
    USB_CONNECTED,
    USB_DISCONNECTED,
    
    // Application Events
    APP_INSTALLED,
    APP_UPDATED,
    APP_REMOVED,
    APP_ENABLED,
    APP_DISABLED,
    APP_STARTED,
    APP_CLOSED,
    APPLICATION_FOREGROUND_CHANGED,
    APPLICATION_BACKGROUND_CHANGED,
    PACKAGE_CHANGED,
    
    // Connectivity & Network
    CONNECTIVITY_CHANGED,
    WIFI_CONNECTED,
    WIFI_DISCONNECTED,
    SSID_CHANGED,
    SIGNAL_CHANGED,
    BLUETOOTH_ENABLED,
    BLUETOOTH_DISABLED,
    BLUETOOTH_CONNECTED,
    BLUETOOTH_DISCONNECTED,
    MOBILE_NETWORK_CHANGED,
    VPN_CHANGED,
    INTERNET_AVAILABLE,
    INTERNET_LOST,
    
    // Original Generic Types (retain for compat if needed)
    TIME_FIRED,
    LOCATION_CHANGED,
    SCREEN_CHANGED,
    NOTIFICATION_RECEIVED,
    SETTINGS_CHANGED,
    MEDIA_CHANGED,
    VOLUME_CHANGED,
    SENSOR_CHANGED,
    WEBHOOK_RECEIVED,
    SYSTEM_EVENT,
    CUSTOM
}

/**
 * A canonical, JSON-safe event produced by an adapter over an existing source.
 * Payload is constrained to JSON so Android framework objects, binder handles,
 * and secrets cannot leak across the monitor/runtime boundary.
 */
@Immutable
@Serializable
data class NexaFlowEvent(
    val eventId: String,
    val type: NexaFlowEventType,
    val source: String,
    val occurredAt: Long,
    val payload: JsonObject = JsonObject(emptyMap()),
    val correlationId: String? = null,
    val deduplicationKey: String? = null
) {
    init {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(source.isNotBlank()) { "source must not be blank" }
        require(occurredAt >= 0L) { "occurredAt must be non-negative" }
        require(payload.toString().toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) {
            "Event payload exceeds ${MAX_PAYLOAD_BYTES / 1024}KB limit"
        }
    }

    companion object {
        const val MAX_PAYLOAD_BYTES = 64 * 1024
    }
}

/** A deterministic predicate that can be evaluated before workflow matching. */
@Immutable
@Serializable
data class EventFilter(
    val types: Set<NexaFlowEventType> = emptySet(),
    val sources: Set<String> = emptySet(),
    val correlationId: String? = null
) {
    fun matches(event: NexaFlowEvent): Boolean =
        (types.isEmpty() || event.type in types) &&
            (sources.isEmpty() || event.source in sources) &&
            (correlationId == null || correlationId == event.correlationId)
}

/** Handle returned by a subscription; closing it is idempotent. */
interface EventSubscription : AutoCloseable {
    val id: String
    override fun close()
}

/** Outcome of accepting an event into the internal bus. */
@Immutable
data class EventPublishResult(
    val accepted: Boolean,
    val deduplicated: Boolean,
    val matchedSubscriptions: Int,
    val reason: String? = null
)

/**
 * The internal event bus boundary. Android monitors publish one canonical event;
 * subscribers then route/match it without subscribing to platform broadcasts.
 */
interface NexaFlowEventBus : AutoCloseable {
    suspend fun subscribe(
        filter: EventFilter = EventFilter(),
        onEvent: suspend (NexaFlowEvent) -> Unit
    ): EventSubscription

    suspend fun publish(event: NexaFlowEvent): EventPublishResult

    /** Removes a subscription atomically; returns false when it was already absent. */
    suspend fun unsubscribe(subscriptionId: String): Boolean

    override fun close()
}
