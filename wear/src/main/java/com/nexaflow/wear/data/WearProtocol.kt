package com.nexaflow.wear.data

import com.nexaflow.core.wearprotocol.WearProtocol as SharedWearProtocol

/**
 * Watch-side compatibility facade.
 *
 * Existing watch code keeps its stable import while every literal contract is
 * sourced from :core:wear-protocol. New transport code should import the
 * shared protocol/models directly.
 */
object WearProtocol {
    const val CURRENT_VERSION: Int = SharedWearProtocol.CURRENT_VERSION

    const val PATH_AUTOMATIONS: String = SharedWearProtocol.PATH_AUTOMATIONS
    const val PATH_RUN_COMMAND: String = SharedWearProtocol.PATH_RUN_COMMAND
    const val PATH_TOGGLE_COMMAND: String = SharedWearProtocol.PATH_TOGGLE_COMMAND
    const val PATH_SYNC_REQUEST: String = SharedWearProtocol.PATH_SYNC_REQUEST

    const val PATH_COMMAND_V1: String = SharedWearProtocol.PATH_COMMAND_V1
    const val PATH_EVENT_V1: String = SharedWearProtocol.PATH_EVENT_V1
    const val PATH_RESULT_V1: String = SharedWearProtocol.PATH_RESULT_V1
    const val PATH_DEVICE_STATE_V1: String = SharedWearProtocol.PATH_DEVICE_STATE_V1
    const val PATH_CAPABILITIES_V1: String = SharedWearProtocol.PATH_CAPABILITIES_V1

    const val KEY_PAYLOAD: String = SharedWearProtocol.KEY_PAYLOAD
    const val KEY_UPDATED_AT: String = SharedWearProtocol.KEY_UPDATED_AT
    const val KEY_PROTOCOL_VERSION: String = SharedWearProtocol.KEY_PROTOCOL_VERSION
    const val KEY_MESSAGE_ID: String = SharedWearProtocol.KEY_MESSAGE_ID

    const val TOGGLE_SEPARATOR: String = SharedWearProtocol.TOGGLE_SEPARATOR

    const val CAPABILITY_PHONE_APP: String = SharedWearProtocol.CAPABILITY_PHONE_APP
    const val CAPABILITY_WATCH_APP: String = SharedWearProtocol.CAPABILITY_WATCH_APP

    fun isVersionSupported(version: Int): Boolean =
        SharedWearProtocol.isVersionSupported(version)
}
