package com.nexaflow.core.execution.capability.semantic.strategies

import com.nexaflow.core.rom.PrivilegedOperation
import com.nexaflow.core.rom.PrivilegedRunner
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.core.execution.capability.semantic.CapabilityStrategy
import com.nexaflow.core.execution.capability.semantic.OperationOutcome
import com.nexaflow.core.execution.capability.semantic.OperationOutcomeStatus
import com.nexaflow.core.execution.capability.semantic.StrategyAvailability
import com.nexaflow.core.execution.capability.semantic.TypedOperationRequest
import com.nexaflow.domain.capability.CapabilityErrorCode
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId

/**
 * Shizuku-backed strategy built exclusively on the closed
 * [PrivilegedOperation] algebra dispatched through
 * [PrivilegedRunner.runShizukuOperation] (UserService AIDL, direct argv, no
 * `sh -c`). Like the Root strategy, this file is the only place a new argv
 * shape may be added for the Shizuku route: workflow input can never become a
 * shell expression through this path.
 *
 * Availability is honest about the distinction the requirement mandates:
 * a granted permission alone is NOT readiness — typed operations require a
 * bound UserService endpoint, so GRANTED_NOT_BOUND surfaces as
 * unavailable-with-permission-granted, never as executable.
 */
class ShizukuTypedStrategy(
    private val packageName: String,
    private val shizukuGranted: () -> Boolean = PrivilegedRunner::isShizukuGranted,
    private val userServiceReady: () -> Boolean = { com.nexaflow.core.rom.ShizukuShellBridge.isUserServiceBound },
    private val execute: (PrivilegedOperation) -> SystemControlResult =
        PrivilegedRunner::runShizukuOperation
) : CapabilityStrategy {

    override val id: StrategyId = StrategyId.SHIZUKU_USER_SERVICE

    override val supportedOperations: Set<SemanticOperationId> = setOf(
        SemanticOperationId.WIFI_SET_STATE,
        SemanticOperationId.BLUETOOTH_SET_STATE,
        SemanticOperationId.LOCATION_SET_STATE,
        SemanticOperationId.AIRPLANE_MODE_SET_STATE,
        SemanticOperationId.ROTATION_SET_STATE,
        SemanticOperationId.BRIGHTNESS_SET,
        SemanticOperationId.SCREEN_TIMEOUT_SET,
        SemanticOperationId.NFC_SET_STATE,
        SemanticOperationId.MOBILE_DATA_SET_STATE,
        SemanticOperationId.HOTSPOT_SET_STATE,
        SemanticOperationId.DATA_SAVER_SET_STATE,
        SemanticOperationId.DND_GET_STATE,
        SemanticOperationId.DND_SET_STATE,
        SemanticOperationId.PACKAGE_FORCE_STOP,
        SemanticOperationId.PACKAGE_CLEAR_DATA,
        SemanticOperationId.PACKAGE_SET_ENABLED_STATE,
        SemanticOperationId.PACKAGE_GET_ENABLED_STATE
    )

    override suspend fun availability(
        request: TypedOperationRequest,
        operation: SemanticOperationId
    ): StrategyAvailability {
        if (!shizukuGranted()) {
            return StrategyAvailability(
                available = false,
                reason = "Shizuku access is not granted yet",
                permissionRequired = true
            )
        }
        // Permission granted is necessary but not sufficient: the typed AIDL
        // endpoint must be live before any operation can be dispatched.
        if (!userServiceReady()) {
            return StrategyAvailability(
                available = false,
                reason = "Shizuku is granted but its service is not connected; reconnect Shizuku",
                permissionRequired = true
            )
        }
        return StrategyAvailability(true)
    }

    override suspend fun execute(
        request: TypedOperationRequest,
        operation: SemanticOperationId
    ): OperationOutcome {
        // Package operations carry a validated package name instead of the
        // generic enabled boolean; they are parsed first, before the toggle
        // branch, so their contract is explicit.
        val packageParameter = request.parameters["packageName"]
        when (operation) {
            SemanticOperationId.BRIGHTNESS_SET -> {
                val level = request.parameters["value"]?.toIntOrNull()
                    ?.takeIf { it in 0..255 }
                    ?: return invalidParameter(operation, "Brightness value must be in 0..255")
                return toOutcome(
                    operation,
                    execute(
                        PrivilegedOperation.WriteSetting(
                            PrivilegedOperation.SettingNamespace.SYSTEM,
                            "screen_brightness",
                            level.toString()
                        )
                    ),
                    requestedEnabled = null
                )
            }
            SemanticOperationId.SCREEN_TIMEOUT_SET -> {
                val seconds = request.parameters["seconds"]?.toLongOrNull()
                    ?.takeIf { it in 1L..86_400L }
                    ?: return invalidParameter(operation, "Screen timeout must be in 1..86400 seconds")
                return toOutcome(
                    operation,
                    execute(
                        PrivilegedOperation.WriteSetting(
                            PrivilegedOperation.SettingNamespace.SYSTEM,
                            "screen_off_timeout",
                            (seconds * 1_000L).toString()
                        )
                    ),
                    requestedEnabled = null
                )
            }
            SemanticOperationId.PACKAGE_FORCE_STOP -> {
                val pkg = packageParameter
                    ?: return missingPackage(operation)
                return toOutcome(
                    operation,
                    execute(PrivilegedOperation.ForceStopPackage(pkg)),
                    requestedEnabled = null
                )
            }
            SemanticOperationId.PACKAGE_CLEAR_DATA -> {
                val pkg = packageParameter
                    ?: return missingPackage(operation)
                return toOutcome(
                    operation,
                    execute(PrivilegedOperation.ClearPackageData(pkg)),
                    requestedEnabled = null
                )
            }
            SemanticOperationId.PACKAGE_SET_ENABLED_STATE -> {
                val pkg = packageParameter
                    ?: return missingPackage(operation)
                val enable = request.parameters["enabled"]?.toBooleanStrictOrNull()
                    ?: return OperationOutcome.failed(
                        operation,
                        CapabilityErrorCode.INVALID_CONFIGURATION,
                        "The 'enabled' parameter is missing or not a boolean",
                        strategy = id
                    )
                return toOutcome(
                    operation,
                    execute(PrivilegedOperation.SetPackageEnabled(pkg, enable)),
                    requestedEnabled = enable
                )
            }
            else -> { /* toggle operations continue below */ }
        }
        val enable = request.parameters["enabled"]?.toBooleanStrictOrNull()
            ?: return OperationOutcome.failed(
                operation,
                CapabilityErrorCode.INVALID_CONFIGURATION,
                "The 'enabled' parameter is missing or not a boolean",
                strategy = id
            )
        val privileged = when (operation) {
            SemanticOperationId.WIFI_SET_STATE ->
                PrivilegedOperation.WriteSetting(
                    namespace = PrivilegedOperation.SettingNamespace.GLOBAL,
                    key = "wifi_on",
                    value = if (enable) "1" else "0"
                )
            SemanticOperationId.BLUETOOTH_SET_STATE ->
                PrivilegedOperation.SetServiceState(
                    PrivilegedOperation.Companion.ServiceName.BLUETOOTH, enable
                )
            SemanticOperationId.LOCATION_SET_STATE ->
                PrivilegedOperation.SetLocationEnabled(enable)
            SemanticOperationId.AIRPLANE_MODE_SET_STATE ->
                PrivilegedOperation.WriteSetting(
                    namespace = PrivilegedOperation.SettingNamespace.GLOBAL,
                    key = "airplane_mode_on",
                    value = if (enable) "1" else "0"
                )
            SemanticOperationId.ROTATION_SET_STATE ->
                PrivilegedOperation.WriteSetting(
                    namespace = PrivilegedOperation.SettingNamespace.SYSTEM,
                    key = "accelerometer_rotation",
                    value = if (enable) "1" else "0"
                )
            SemanticOperationId.NFC_SET_STATE ->
                PrivilegedOperation.SetServiceState(
                    PrivilegedOperation.Companion.ServiceName.NFC, enable
                )
            SemanticOperationId.MOBILE_DATA_SET_STATE ->
                PrivilegedOperation.SetServiceState(
                    PrivilegedOperation.Companion.ServiceName.DATA, enable
                )
            SemanticOperationId.HOTSPOT_SET_STATE ->
                PrivilegedOperation.SetHotspot(enable)
            SemanticOperationId.DATA_SAVER_SET_STATE ->
                PrivilegedOperation.SetDataSaver(enable)
            SemanticOperationId.DND_SET_STATE ->
                PrivilegedOperation.WriteSetting(
                    namespace = PrivilegedOperation.SettingNamespace.GLOBAL,
                    key = "zen_mode",
                    // Android uses 0 for off and non-zero zen modes for DND.
                    // Use "no interruptions" (2) as the deterministic ON state.
                    value = if (enable) "2" else "0"
                )
            else -> return OperationOutcome.unsupported(
                operation, "Write is not implemented by the Shizuku strategy"
            )
        }
        val result = execute(privileged)
        return toOutcome(operation, result, enable)
    }

    private fun invalidParameter(
        operation: SemanticOperationId,
        message: String
    ): OperationOutcome = OperationOutcome.failed(
        operation,
        CapabilityErrorCode.INVALID_CONFIGURATION,
        message,
        strategy = id
    )

    private fun missingPackage(operation: SemanticOperationId): OperationOutcome =
        OperationOutcome.failed(
            operation,
            CapabilityErrorCode.INVALID_CONFIGURATION,
            "A validated package name is required for this operation",
            strategy = id
        )

    /**
     * Read-back for reconcile/verification through the bounded
     * [PrivilegedOperation.ReadSettingState] and [PrivilegedOperation.ReadPackageEnabledState]
     * allowlists. Returns null — honest "unreadable" — for states with no
     * reliable read (hotspot, NFC on many builds).
     */
    override suspend fun readState(
        request: TypedOperationRequest,
        operation: SemanticOperationId
    ): Boolean? = when (operation) {
        SemanticOperationId.WIFI_GET_STATE ->
            readSettingBool(PrivilegedOperation.SettingNamespace.GLOBAL, "wifi_on")
        SemanticOperationId.BLUETOOTH_GET_STATE ->
            readSettingBool(PrivilegedOperation.SettingNamespace.GLOBAL, "bluetooth_on")
        SemanticOperationId.LOCATION_GET_STATE ->
            readBooleanCommand(execute(PrivilegedOperation.ReadLocationEnabled))
        SemanticOperationId.AIRPLANE_MODE_GET_STATE ->
            readSettingBool(PrivilegedOperation.SettingNamespace.GLOBAL, "airplane_mode_on")
        SemanticOperationId.ROTATION_GET_STATE ->
            readSettingBool(PrivilegedOperation.SettingNamespace.SYSTEM, "accelerometer_rotation")
        SemanticOperationId.DND_GET_STATE ->
            readNonZeroSetting(PrivilegedOperation.SettingNamespace.GLOBAL, "zen_mode")
        SemanticOperationId.MOBILE_DATA_GET_STATE ->
            readSettingBool(PrivilegedOperation.SettingNamespace.GLOBAL, "mobile_data")
        SemanticOperationId.DATA_SAVER_GET_STATE ->
            readBooleanCommand(execute(PrivilegedOperation.ReadDataSaver))
        SemanticOperationId.PACKAGE_GET_ENABLED_STATE -> {
            val pkg = request.parameters["packageName"]
            if (pkg == null) null else readPackageEnabled(pkg)
        }
        else -> null // HOTSPOT/NFC: no reliable single read; stay honest
    }

    /**
     * `pm list packages -d <pkg>` exits 0 and prints exactly one
     * `package:<name>` line when the package IS disabled for the caller's
     * user; it prints nothing when the package is enabled (or uninstalled —
     * a distinguishable special case the exit code cannot express, hence the
     * null fallback for absent packages is documented as an honest gap).
     */
    private fun readPackageEnabled(packageName: String): Boolean? {
        val result = execute(PrivilegedOperation.ReadPackageEnabledState(packageName))
        if (!result.success) return null
        val output = result.message.trim()
        return when {
            output.isEmpty() -> true // not in the disabled list → enabled
            output == "package:$packageName" -> false
            else -> null // unexpected shape: never guess
        }
    }

    private fun readSettingBool(
        namespace: PrivilegedOperation.SettingNamespace,
        key: String
    ): Boolean? {
        val result = execute(PrivilegedOperation.ReadSettingState(namespace, key))
        if (!result.success) return null
        return when (result.message.trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    private fun readBooleanCommand(result: SystemControlResult): Boolean? {
        if (!result.success) return null
        val value = result.message.trim().lowercase()
        return when {
            value == "1" || value == "true" || value == "enabled" ||
                value.endsWith(": true") || value.endsWith(": enabled") -> true
            value == "0" || value == "false" || value == "disabled" ||
                value.endsWith(": false") || value.endsWith(": disabled") -> false
            else -> null
        }
    }

    /** DND has several active zen modes; every non-zero value means enabled. */
    private fun readNonZeroSetting(
        namespace: PrivilegedOperation.SettingNamespace,
        key: String
    ): Boolean? {
        val result = execute(PrivilegedOperation.ReadSettingState(namespace, key))
        if (!result.success) return null
        return result.message.trim().toIntOrNull()?.let { it != 0 }
    }

    private fun toOutcome(
        operation: SemanticOperationId,
        result: SystemControlResult,
        requestedEnabled: Boolean?
    ): OperationOutcome = if (result.success) {
        OperationOutcome(
            operation = operation,
            status = OperationOutcomeStatus.SUCCESS,
            strategy = id,
            message = result.message,
            metadata = requestedEnabled?.let { mapOf("requestedEnabled" to it.toString()) } ?: emptyMap()
        )
    } else {
        val transport = result.message.contains("not granted", ignoreCase = true) ||
            result.message.contains("not available", ignoreCase = true) ||
            result.message.contains("failed", ignoreCase = true) &&
            result.message.contains("UserService", ignoreCase = true)
        val uncertain = transport && transportIsUncertain(operation)
        OperationOutcome(
            operation = operation,
            status = if (uncertain) OperationOutcomeStatus.UNKNOWN else OperationOutcomeStatus.FAILED,
            strategy = id,
            errorCode = when {
                result.message.contains("not granted", ignoreCase = true) ->
                    CapabilityErrorCode.SHIZUKU_DENIED
                transport -> CapabilityErrorCode.SHIZUKU_UNAVAILABLE
                else -> CapabilityErrorCode.POLICY_NOT_SATISFIED
            },
            message = result.message,
            // Definite transport unavailability (not granted / service gone)
            // lets the router fall back safely; an uncertain outcome never does.
            transportFailure = transport && !uncertain,
            metadata = requestedEnabled?.let { mapOf("requestedEnabled" to it.toString()) } ?: emptyMap()
        )
    }

    /**
     * For radio toggles a UserService failure does not prove the radio did
     * not change: the side effect may have landed before the binder dropped.
     * Those outcomes must surface as UNKNOWN so the router reconciles by
     * reading the actual state instead of re-executing or failing definitively.
     */
    private fun transportIsUncertain(operation: SemanticOperationId): Boolean = when (operation) {
        SemanticOperationId.WIFI_SET_STATE,
        SemanticOperationId.BLUETOOTH_SET_STATE,
        SemanticOperationId.LOCATION_SET_STATE,
        SemanticOperationId.ROTATION_SET_STATE,
        SemanticOperationId.BRIGHTNESS_SET,
        SemanticOperationId.SCREEN_TIMEOUT_SET,
        SemanticOperationId.NFC_SET_STATE,
        SemanticOperationId.MOBILE_DATA_SET_STATE,
        SemanticOperationId.HOTSPOT_SET_STATE,
        SemanticOperationId.DATA_SAVER_SET_STATE,
        SemanticOperationId.DND_SET_STATE,
        // A force-stop/clear-data dispatched through the shell that then loses
        // the binder may have completed: process death on the target package
        // is externally observable, so the outcome must be reconciled, never
        // retried or failed definitively.
        SemanticOperationId.PACKAGE_FORCE_STOP,
        SemanticOperationId.PACKAGE_CLEAR_DATA,
        SemanticOperationId.PACKAGE_SET_ENABLED_STATE -> true
        else -> false
    }
}
