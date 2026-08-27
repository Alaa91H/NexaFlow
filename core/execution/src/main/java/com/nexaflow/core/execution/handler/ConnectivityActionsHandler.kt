package com.nexaflow.core.execution.handler

import com.nexaflow.core.rom.NetworkModePolicy
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            // Root/Shizuku paths can wait for a process and framework calls can
            // block on a remote telephony binder. Keep this explicit at the
            // handler boundary because workflow executors are intentionally
            // dispatcher-agnostic.
            ActionType.SYSTEM_NETWORK_MODE -> withContext(Dispatchers.IO) {
                val requestedMask = action.config["network_mask"]?.toLongOrNull()
                if (requestedMask != null &&
                    action.config["network_mask_schema"] != NetworkModePolicy.NETWORK_MASK_SCHEMA_AOSP_V1
                ) {
                    // v3.50 and earlier persisted a one-bit-shifted dynamic mask.
                    // It is unsafe to reinterpret it after correcting the AOSP
                    // constants: a saved LTE mask could become LTE+NR. The user
                    // must reselect a profile, which writes the explicit schema.
                    SystemControlResult.fail(
                        "Saved dynamic network profile uses an obsolete mask mapping; open the task and reselect the network profile"
                    )
                } else {
                    ctx.controller.setNetworkMode(
                        mode = action.config["mode"] ?: "AUTO",
                        requestedMask = requestedMask,
                        subscriptionId = action.config["network_subscription_id"]?.toIntOrNull()
                    )
                }
            }
            ActionType.SYSTEM_HOTSPOT -> ctx.controller.setHotspot(enabled)
            ActionType.SYSTEM_NFC -> ctx.controller.setNfc(enabled)
            ActionType.SYSTEM_AIRPLANE_MODE -> ctx.controller.setAirplaneMode(enabled)
            ActionType.SYSTEM_LOCATION -> ctx.controller.setLocationEnabled(enabled)
            else -> SystemControlResult.fail("Unsupported action ${action.type}")
        }
    }
}
