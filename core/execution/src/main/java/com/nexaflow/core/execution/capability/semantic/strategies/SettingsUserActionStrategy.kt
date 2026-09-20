package com.nexaflow.core.execution.capability.semantic.strategies

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.nexaflow.core.execution.capability.semantic.CapabilityStrategy
import com.nexaflow.core.execution.capability.semantic.OperationOutcome
import com.nexaflow.core.execution.capability.semantic.OperationOutcomeStatus
import com.nexaflow.core.execution.capability.semantic.StrategyAvailability
import com.nexaflow.core.execution.capability.semantic.TypedOperationRequest
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId

/**
 * Explicit fallback that hands the user the correct Settings page. It is
 * registered only for operations the platform legitimately requires a user
 * action for on modern Android (location, NFC on many builds, data saver on
 * some OEMs). Its outcome is PENDING_USER_ACTION — never SUCCESS — so history
 * and UI remain truthful.
 */
class SettingsUserActionStrategy(private val context: Context) : CapabilityStrategy {
    override val id: StrategyId = StrategyId.SETTINGS_USER_ACTION

    override val supportedOperations: Set<SemanticOperationId> = setOf(
        SemanticOperationId.WIFI_SET_STATE,
        SemanticOperationId.BLUETOOTH_SET_STATE,
        SemanticOperationId.LOCATION_SET_STATE,
        SemanticOperationId.NFC_SET_STATE,
        SemanticOperationId.HOTSPOT_SET_STATE,
        SemanticOperationId.MOBILE_DATA_SET_STATE,
        SemanticOperationId.AIRPLANE_MODE_SET_STATE,
        SemanticOperationId.BRIGHTNESS_SET,
        SemanticOperationId.SCREEN_TIMEOUT_SET,
        SemanticOperationId.DATA_SAVER_SET_STATE,
        SemanticOperationId.DND_SET_STATE
    )

    override suspend fun availability(
        request: TypedOperationRequest,
        operation: SemanticOperationId
    ): StrategyAvailability = settingsPageFor(operation)?.let { page ->
        val resolvable = Intent(page).resolveActivity(context.packageManager) != null
        if (resolvable) {
            StrategyAvailability(true)
        } else {
            StrategyAvailability(false, "The required Settings page is unavailable on this device")
        }
    } ?: StrategyAvailability(false, "No settings page maps to this operation")

    override suspend fun execute(
        request: TypedOperationRequest,
        operation: SemanticOperationId
    ): OperationOutcome {
        val page = settingsPageFor(operation)
            ?: return OperationOutcome.unsupported(operation, "No settings page maps to this operation")
        return runCatching {
            context.startActivity(Intent(page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.fold(
            onSuccess = {
                OperationOutcome(
                    operation = operation,
                    status = OperationOutcomeStatus.PENDING_USER_ACTION,
                    strategy = id,
                    message = "The user must complete the change in Android Settings",
                    metadata = mapOf("requestedEnabled" to (request.parameters["enabled"] ?: "true"))
                )
            },
            onFailure = {
                OperationOutcome.failed(
                    operation,
                    com.nexaflow.domain.capability.CapabilityErrorCode.BACKEND_UNAVAILABLE,
                    "Android Settings could not be opened for this operation",
                    strategy = id
                )
            }
        )
    }

    private fun settingsPageFor(operation: SemanticOperationId) = when (operation) {
        SemanticOperationId.WIFI_SET_STATE -> Settings.ACTION_WIFI_SETTINGS
        SemanticOperationId.BLUETOOTH_SET_STATE -> Settings.ACTION_BLUETOOTH_SETTINGS
        SemanticOperationId.LOCATION_SET_STATE -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
        SemanticOperationId.NFC_SET_STATE -> Settings.ACTION_NFC_SETTINGS
        SemanticOperationId.HOTSPOT_SET_STATE -> Settings.ACTION_WIRELESS_SETTINGS
        SemanticOperationId.MOBILE_DATA_SET_STATE -> Settings.ACTION_DATA_ROAMING_SETTINGS
        SemanticOperationId.AIRPLANE_MODE_SET_STATE -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
        SemanticOperationId.BRIGHTNESS_SET -> Settings.ACTION_DISPLAY_SETTINGS
        SemanticOperationId.SCREEN_TIMEOUT_SET -> Settings.ACTION_DISPLAY_SETTINGS
        // Data Saver has no public Settings action constant across API levels;
        // the network settings page is the documented entry point.
        SemanticOperationId.DATA_SAVER_SET_STATE -> Settings.ACTION_WIRELESS_SETTINGS
        SemanticOperationId.DND_SET_STATE -> Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
        else -> null
    }
}
