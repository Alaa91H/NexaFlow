package com.nexaflow.domain.canonical

import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.operation.SemanticOperationId

/** Explicit compatibility mapping from legacy capability enums to immutable IDs. */
val CapabilityId.stableId: CapabilityStableId
    get() = CapabilityStableId(
        when (this) {
            CapabilityId.PACKAGE_READ -> "core.capability.package_read"
            CapabilityId.PACKAGE_INSTALL -> "core.capability.package_install"
            CapabilityId.PACKAGE_UNINSTALL -> "core.capability.package_uninstall"
            CapabilityId.PACKAGE_FORCE_STOP -> "core.capability.package_force_stop"
            CapabilityId.PACKAGE_SET_ENABLED -> "core.capability.package_set_enabled"
            CapabilityId.PACKAGE_CLEAR_DATA -> "core.capability.package_clear_data"
            CapabilityId.UPDATE_APPS -> "core.capability.update_apps"
            CapabilityId.INTENT_LAUNCH -> "core.capability.intent_launch"
            CapabilityId.SETTINGS_LAUNCH -> "core.capability.settings_launch"
            CapabilityId.NETWORK_HTTP_REQUEST -> "core.capability.network_http_request"
            CapabilityId.DEVICE_STATE_READ -> "core.capability.device_state_read"
            CapabilityId.PLUGIN_ACTION -> "core.capability.plugin_action"
            CapabilityId.PLUGIN_CONDITION_READ -> "core.capability.plugin_condition_read"
            CapabilityId.SYSTEM_SETTING_WRITE -> "core.capability.system_setting_write"
            CapabilityId.FILE_COPY -> "core.capability.file_copy"
            CapabilityId.ACCESSIBILITY_FIND_NODE -> "core.capability.accessibility_find_node"
            CapabilityId.ACCESSIBILITY_CLICK -> "core.capability.accessibility_click"
            CapabilityId.ACCESSIBILITY_SCROLL -> "core.capability.accessibility_scroll"
            CapabilityId.ACCESSIBILITY_INPUT_TEXT -> "core.capability.accessibility_input_text"
            CapabilityId.ACCESSIBILITY_WAIT_FOR_NODE -> "core.capability.accessibility_wait_for_node"
            CapabilityId.ACCESSIBILITY_GESTURE -> "core.capability.accessibility_gesture"
        }
    )

/**
 * Stable semantic identity for the typed operation layer that already exists.
 * The legacy enum remains a compatibility API; new canonical persistence uses
 * this target + operation pair.
 */
data class StableSemanticOperationIdentity(
    val target: TargetId,
    val operation: OperationId,
)

val SemanticOperationId.stableIdentity: StableSemanticOperationIdentity
    get() = StableSemanticOperationIdentity(
        target = stableTargetId,
        operation = stableOperationId,
    )

val SemanticOperationId.stableTargetId: TargetId
    get() = TargetId(
        when (this) {
            SemanticOperationId.WIFI_GET_STATE,
            SemanticOperationId.WIFI_SET_STATE -> "core.connectivity.wifi"

            SemanticOperationId.BLUETOOTH_GET_STATE,
            SemanticOperationId.BLUETOOTH_SET_STATE -> "core.connectivity.bluetooth"

            SemanticOperationId.MOBILE_DATA_GET_STATE,
            SemanticOperationId.MOBILE_DATA_SET_STATE -> "core.connectivity.mobile_data"

            SemanticOperationId.HOTSPOT_GET_STATE,
            SemanticOperationId.HOTSPOT_SET_STATE -> "core.connectivity.hotspot"

            SemanticOperationId.NFC_GET_STATE,
            SemanticOperationId.NFC_SET_STATE -> "core.connectivity.nfc"

            SemanticOperationId.LOCATION_GET_STATE,
            SemanticOperationId.LOCATION_SET_STATE -> "core.location.service"

            SemanticOperationId.AIRPLANE_MODE_GET_STATE,
            SemanticOperationId.AIRPLANE_MODE_SET_STATE -> "core.connectivity.airplane_mode"

            SemanticOperationId.ROTATION_GET_STATE,
            SemanticOperationId.ROTATION_SET_STATE -> "core.display.auto_rotate"

            SemanticOperationId.BRIGHTNESS_GET,
            SemanticOperationId.BRIGHTNESS_SET -> "core.display.brightness"

            SemanticOperationId.SCREEN_TIMEOUT_GET,
            SemanticOperationId.SCREEN_TIMEOUT_SET -> "core.display.screen_timeout"

            SemanticOperationId.DND_GET_STATE,
            SemanticOperationId.DND_SET_STATE -> "core.audio.dnd"

            SemanticOperationId.DATA_SAVER_GET_STATE,
            SemanticOperationId.DATA_SAVER_SET_STATE -> "core.connectivity.data_saver"

            SemanticOperationId.PACKAGE_FORCE_STOP,
            SemanticOperationId.PACKAGE_CLEAR_DATA,
            SemanticOperationId.PACKAGE_SET_ENABLED_STATE,
            SemanticOperationId.PACKAGE_GET_ENABLED_STATE -> "core.application.package"
        }
    )

val SemanticOperationId.stableOperationId: OperationId
    get() = OperationId(
        when (this) {
            SemanticOperationId.WIFI_GET_STATE,
            SemanticOperationId.BLUETOOTH_GET_STATE,
            SemanticOperationId.MOBILE_DATA_GET_STATE,
            SemanticOperationId.HOTSPOT_GET_STATE,
            SemanticOperationId.NFC_GET_STATE,
            SemanticOperationId.LOCATION_GET_STATE,
            SemanticOperationId.AIRPLANE_MODE_GET_STATE,
            SemanticOperationId.ROTATION_GET_STATE,
            SemanticOperationId.DND_GET_STATE,
            SemanticOperationId.DATA_SAVER_GET_STATE -> "core.operation.get_state"

            SemanticOperationId.BRIGHTNESS_GET,
            SemanticOperationId.SCREEN_TIMEOUT_GET -> "core.operation.get_value"

            SemanticOperationId.WIFI_SET_STATE,
            SemanticOperationId.BLUETOOTH_SET_STATE,
            SemanticOperationId.MOBILE_DATA_SET_STATE,
            SemanticOperationId.HOTSPOT_SET_STATE,
            SemanticOperationId.NFC_SET_STATE,
            SemanticOperationId.LOCATION_SET_STATE,
            SemanticOperationId.AIRPLANE_MODE_SET_STATE,
            SemanticOperationId.ROTATION_SET_STATE,
            SemanticOperationId.DND_SET_STATE,
            SemanticOperationId.DATA_SAVER_SET_STATE -> "core.operation.set_state"

            SemanticOperationId.BRIGHTNESS_SET,
            SemanticOperationId.SCREEN_TIMEOUT_SET -> "core.operation.set_value"

            SemanticOperationId.PACKAGE_FORCE_STOP -> "core.operation.force_stop"
            SemanticOperationId.PACKAGE_CLEAR_DATA -> "core.operation.clear_data"
            SemanticOperationId.PACKAGE_SET_ENABLED_STATE -> "core.operation.set_enabled"
            SemanticOperationId.PACKAGE_GET_ENABLED_STATE -> "core.operation.get_enabled"
        }
    )
