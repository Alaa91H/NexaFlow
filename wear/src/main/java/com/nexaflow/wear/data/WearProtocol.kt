package com.nexaflow.wear.data

/**
 * Wearable Data Layer paths and DataMap keys shared by the watch app and the
 * phone-side WearCommandListenerService / WearSyncManager.
 *
 * These must stay in sync with the constants in AutomationIntents.kt on the
 * phone side. Changes here require a matching change there.
 */
object WearProtocol {
    /** DataItem path: phone pushes the serialized automation list here. */
    const val PATH_AUTOMATIONS: String = "/nexaflow/automations"

    /** MessageClient path: watch sends a manual-run command for one automation. */
    const val PATH_RUN_COMMAND: String = "/nexaflow/run"

    /** MessageClient path: watch sends an enable/disable toggle command. */
    const val PATH_TOGGLE_COMMAND: String = "/nexaflow/toggle"

    /** DataMap key carrying the JSON automation-list payload. */
    const val KEY_PAYLOAD: String = "payload"

    /**
     * DataMap key: monotonic timestamp added to every DataItem push so the
     * Data Layer treats each update as a new item even when the payload is
     * identical. Without this, the platform may de-duplicate the push and the
     * watch receives no DATA_CHANGED callback.
     */
    const val KEY_UPDATED_AT: String = "updatedAt"

    /** Separator used to encode "automationId:enabled" in a toggle message. */
    const val TOGGLE_SEPARATOR: String = ":"

    /** MessageClient path: watch requests an immediate automation-list push. */
    const val PATH_SYNC_REQUEST: String = "/nexaflow/sync-request"

    /** Capability advertised by the phone companion app. */
    const val CAPABILITY_PHONE_APP: String = "nexaflow_phone_companion"
}
