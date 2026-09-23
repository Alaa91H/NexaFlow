package com.nexaflow.core.wearprotocol

/**
 * Single source of truth for every phone <-> Wear OS Data Layer path, key,
 * capability name and compatibility rule used by NexaFlow.
 *
 * Legacy paths stay frozen so already-installed phone/watch versions continue
 * to interoperate while the versioned protocol is rolled out incrementally.
 */
object WearProtocol {
    const val CURRENT_VERSION: Int = 1
    const val MIN_SUPPORTED_VERSION: Int = 1
    const val DEFAULT_MESSAGE_TTL_MS: Long = 30_000L

    // Legacy production paths. Do not rename or repurpose.
    const val PATH_AUTOMATIONS: String = "/nexaflow/automations"
    const val PATH_RUN_COMMAND: String = "/nexaflow/run"
    const val PATH_TOGGLE_COMMAND: String = "/nexaflow/toggle"
    const val PATH_SYNC_REQUEST: String = "/nexaflow/sync-request"

    // Versioned protocol paths used by first-class watch triggers/actions.
    const val PATH_COMMAND_V1: String = "/nexaflow/v1/command"
    const val PATH_EVENT_V1: String = "/nexaflow/v1/event"
    const val PATH_RESULT_V1: String = "/nexaflow/v1/result"
    const val PATH_DEVICE_STATE_V1: String = "/nexaflow/v1/device-state"
    const val PATH_CAPABILITIES_V1: String = "/nexaflow/v1/capabilities"

    const val KEY_PAYLOAD: String = "payload"
    const val KEY_UPDATED_AT: String = "updatedAt"
    const val KEY_PROTOCOL_VERSION: String = "protocolVersion"
    const val KEY_MESSAGE_ID: String = "messageId"

    const val TOGGLE_SEPARATOR: String = ":"

    const val CAPABILITY_PHONE_APP: String = "nexaflow_phone_companion"
    const val CAPABILITY_WATCH_APP: String = "nexaflow_watch_companion"

    fun isVersionSupported(version: Int): Boolean =
        version in MIN_SUPPORTED_VERSION..CURRENT_VERSION
}
