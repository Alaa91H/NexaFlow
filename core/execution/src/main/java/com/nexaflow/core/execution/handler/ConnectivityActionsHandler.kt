package com.nexaflow.core.execution.handler

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType

/** Wi-Fi, Bluetooth, mobile data, hotspot, NFC, airplane mode, location. */
class ConnectivityActionsHandler : ActionHandler {
    override val supportedTypes: Set<ActionType> = setOf(
        ActionType.SYSTEM_WIFI,
        ActionType.SYSTEM_BLUETOOTH,
        ActionType.SYSTEM_MOBILE_DATA,
        ActionType.SYSTEM_NETWORK_MODE,
        ActionType.SYSTEM_HOTSPOT,
        ActionType.SYSTEM_NFC,
        ActionType.SYSTEM_AIRPLANE_MODE,
        ActionType.SYSTEM_LOCATION
    )

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        val enabled = action.config["enabled"]?.toBoolean() ?: true
        return when (action.type) {
            ActionType.SYSTEM_WIFI -> ctx.controller.setWifi(enabled)
            ActionType.SYSTEM_BLUETOOTH -> ctx.controller.setBluetooth(enabled)
            ActionType.SYSTEM_MOBILE_DATA -> ctx.controller.setMobileData(enabled)
            ActionType.SYSTEM_NETWORK_MODE -> ctx.controller.setNetworkMode(
                mode = action.config["mode"] ?: "AUTO",
                requestedMask = action.config["network_mask"]?.toLongOrNull(),
                subscriptionId = action.config["network_subscription_id"]?.toIntOrNull()
            )
            ActionType.SYSTEM_HOTSPOT -> ctx.controller.setHotspot(enabled)
            ActionType.SYSTEM_NFC -> ctx.controller.setNfc(enabled)
            ActionType.SYSTEM_AIRPLANE_MODE -> ctx.controller.setAirplaneMode(enabled)
            ActionType.SYSTEM_LOCATION -> ctx.controller.setLocationEnabled(enabled)
            else -> SystemControlResult.fail("Unsupported action ${action.type}")
        }
    }
}
