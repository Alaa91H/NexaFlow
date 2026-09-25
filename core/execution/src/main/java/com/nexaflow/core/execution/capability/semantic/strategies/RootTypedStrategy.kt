package com.nexaflow.core.execution.capability.semantic.strategies

import com.nexaflow.core.rom.PrivilegedOperation
import com.nexaflow.core.rom.PrivilegedRunner
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.core.execution.capability.semantic.CapabilityStrategy
import com.nexaflow.core.execution.capability.semantic.OperationOutcome
import com.nexaflow.core.execution.capability.semantic.OperationOutcomeStatus
import com.nexaflow.core.execution.capability.semantic.StrategyAvailability
import com.nexaflow.core.execution.capability.semantic.TypedOperationRequest
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId

/**
 * Root-backed strategy built exclusively on the closed [PrivilegedOperation]
 * algebra. Two new typed operations extend that algebra below (state reads
 * via `settings get` and radio toggles via `svc`), so the workflow can never
 * inject a command: only this file may add argv shapes, and each is bounded.
 */
class RootTypedStrategy(
    private val rootAvailable: () -> Boolean = PrivilegedRunner::isRootAvailable,
    private val execute: (PrivilegedOperation) -> SystemControlResult = PrivilegedRunner::runRootOperation
) : CapabilityStrategy {

    override val id: StrategyId = StrategyId.ROOT_SHELL

    override val supportedOperations: Set<SemanticOperationId> = setOf(
        SemanticOperationId.WIFI_GET_STATE,
        SemanticOperationId.WIFI_SET_STATE,
        SemanticOperationId.BLUETOOTH_GET_STATE,
        SemanticOperationId.BLUETOOTH_SET_STATE,
        SemanticOperationId.LOCATION_SET_STATE,
        SemanticOperationId.AIRPLANE_MODE_GET_STATE,
        SemanticOperationId.ROTATION_SET_STATE,
        SemanticOperationId.BRIGHTNESS_SET,
        SemanticOperationId.SCREEN_TIMEOUT_SET,
        SemanticOperationId.AIRPLANE_MODE_SET_STATE,
        SemanticOperationId.DND_GET_STATE,
        SemanticOperationId.DND_SET_STATE,
        SemanticOperationId.NFC_SET_STATE,
        SemanticOperationId.MOBILE_DATA_GET_STATE,
        SemanticOperationId.MOBILE_DATA_SET_STATE,
        SemanticOperationId.DATA_SAVER_SET_STATE,
        SemanticOperationId.PACKAGE_FORCE_STOP,
        SemanticOperationId.PACKAGE_CLEAR_DATA,
        SemanticOperationId.PACKAGE_SET_ENABLED_STATE,
        SemanticOperationId.PACKAGE_GET_ENABLED_STATE
    )

    override suspend fun availability(
        request: TypedOperationRequest,
        operation: SemanticOperationId
    ): StrategyAvailability {
        if (!rootAvailable()) {
            return StrategyAvailability(false, "Root access is not available")
        }
        return StrategyAvailability(true)
    }

    override suspend fun execute(
        request: TypedOperationRequest,
        operation: SemanticOperationId
    ): OperationOutcome {
        // Package operations carry a validated package name instead of the
        // generic enabled boolean; parsed before the toggle branch so their
        // contract stays explicit.
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
                        com.nexaflow.domain.capability.CapabilityErrorCode.INVALID_CONFIGURATION,
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
                com.nexaflow.domain.capability.CapabilityErrorCode.INVALID_CONFIGURATION,
                "The 'enabled' parameter is missing or not a boolean",
                strategy = id
            )
        val privileged = when (operation) {
            SemanticOperationId.WIFI_SET_STATE ->
                PrivilegedOperation.SetServiceState(
                    PrivilegedOperation.Companion.ServiceName.WIFI, enable
                )
            SemanticOperationId.BLUETOOTH_SET_STATE ->
                PrivilegedOperation.SetServiceState(
                    PrivilegedOperation.Companion.ServiceName.BLUETOOTH, enable
                )
            SemanticOperationId.LOCATION_SET_STATE ->
                PrivilegedOperation.SetLocationEnabled(enable)
            SemanticOperationId.AIRPLANE_MODE_SET_STATE ->
                PrivilegedOperation.SetAirplaneMode(enable)
            SemanticOperationId.ROTATION_SET_STATE ->
                PrivilegedOperation.WriteSetting(
                    namespace = PrivilegedOperation.SettingNamespace.SYSTEM,
                    key = "accelerometer_rotation",
                    value = if (enable) "1" else "0"
                )
            SemanticOperationId.DND_SET_STATE ->
                PrivilegedOperation.WriteSetting(
                    namespace = PrivilegedOperation.SettingNamespace.GLOBAL,
                    key = "zen_mode",
                    value = if (enable) "2" else "0"
                )
            SemanticOperationId.NFC_SET_STATE ->
                PrivilegedOperation.SetServiceState(
                    PrivilegedOperation.Companion.ServiceName.NFC, enable
                )
            SemanticOperationId.MOBILE_DATA_SET_STATE ->
                PrivilegedOperation.SetServiceState(
                    PrivilegedOperation.Companion.ServiceName.DATA, enable
                )
            SemanticOperationId.DATA_SAVER_SET_STATE ->
                PrivilegedOperation.SetDataSaver(enable)
            else -> return OperationOutcome.unsupported(
                operation, "Write is not implemented by the Root strategy"
            )
        }
        val result = execute(privileged)
        return toOutcome(operation, result, enable)
    }

    override suspend fun readState(
        request: TypedOperationRequest,
        operation: SemanticOperationId
    ): Boolean? = when (operation) {
        SemanticOperationId.WIFI_GET_STATE ->
            readSettingInt(PrivilegedOperation.SettingNamespace.GLOBAL, "wifi_on")
        SemanticOperationId.BLUETOOTH_GET_STATE ->
            readSettingInt(PrivilegedOperation.SettingNamespace.GLOBAL, "bluetooth_on")
        SemanticOperationId.LOCATION_GET_STATE ->
            readBooleanCommand(execute(PrivilegedOperation.ReadLocationEnabled))
        SemanticOperationId.AIRPLANE_MODE_GET_STATE ->
            readSettingInt(PrivilegedOperation.SettingNamespace.GLOBAL, "airplane_mode_on")
        SemanticOperationId.ROTATION_GET_STATE ->
            readSettingInt(PrivilegedOperation.SettingNamespace.SYSTEM, "accelerometer_rotation")
        SemanticOperationId.DND_GET_STATE ->
            readNonZeroSetting(PrivilegedOperation.SettingNamespace.GLOBAL, "zen_mode")
        SemanticOperationId.MOBILE_DATA_GET_STATE ->
            readSettingInt(PrivilegedOperation.SettingNamespace.GLOBAL, "mobile_data")
        SemanticOperationId.DATA_SAVER_GET_STATE ->
            readBooleanCommand(execute(PrivilegedOperation.ReadDataSaver))
        SemanticOperationId.PACKAGE_GET_ENABLED_STATE -> {
            val pkg = request.parameters["packageName"]
            if (pkg == null) null else readPackageEnabled(pkg)
        }
        else -> null
    }

    override suspend fun readStateValue(
        request: TypedOperationRequest,
        operation: SemanticOperationId
    ): String? = when (operation) {
        SemanticOperationId.BRIGHTNESS_GET ->
            readSettingRaw(PrivilegedOperation.SettingNamespace.SYSTEM, "screen_brightness")
        SemanticOperationId.SCREEN_TIMEOUT_GET ->
            readSettingRaw(PrivilegedOperation.SettingNamespace.SYSTEM, "screen_off_timeout")
                ?.let { it.toLongOrNull()?.div(1000)?.toString() }
        else -> null
    }

    private fun readSettingRaw(
        namespace: PrivilegedOperation.SettingNamespace,
        key: String
    ): String? {
        val result = execute(PrivilegedOperation.ReadSettingState(namespace, key))
        if (!result.success) return null
        val value = result.message.trim()
        return value.takeIf { it.isNotEmpty() && it != "null" && it.toLongOrNull() != null }
    }

    /**
     * Same bounded probe as the Shizuku strategy: `pm list packages -d <pkg>`
     * prints exactly one line when the package is disabled, nothing when it
     * is enabled. Unexpected output shapes stay honest-null, never guesses.
     */
    private fun readPackageEnabled(packageName: String): Boolean? {
        val result = execute(PrivilegedOperation.ReadPackageEnabledState(packageName))
        if (!result.success) return null
        val output = result.message.trim()
        return when {
            output.isEmpty() -> true
            output == "package:$packageName" -> false
            else -> null
        }
    }

    private fun invalidParameter(
        operation: SemanticOperationId,
        message: String
    ): OperationOutcome = OperationOutcome.failed(
        operation,
        com.nexaflow.domain.capability.CapabilityErrorCode.INVALID_CONFIGURATION,
        message,
        strategy = id
    )

    private fun missingPackage(operation: SemanticOperationId): OperationOutcome =
        OperationOutcome.failed(
            operation,
            com.nexaflow.domain.capability.CapabilityErrorCode.INVALID_CONFIGURATION,
            "A validated package name is required for this operation",
            strategy = id
        )

    private fun readSettingBool(
        namespace: PrivilegedOperation.SettingNamespace,
        key: String
    ): Boolean? {
        val operation = PrivilegedOperation.ReadSettingState(namespace, key)
        val result = execute(operation)
        if (!result.success) return null
        val value = result.message.trim()
        return when (value) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    private fun readSettingInt(
        namespace: PrivilegedOperation.SettingNamespace,
        key: String
    ): Boolean? = readSettingBool(namespace, key)

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

    /** DND uses 0 for off and multiple non-zero zen modes for active states. */
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
        val timedOut = result.message.contains("timed out", ignoreCase = true)
        val unavailable = result.message.contains("not available", ignoreCase = true)
        val transport = timedOut || unavailable
        val status = if (timedOut && transportIsUncertain(operation)) {
            OperationOutcomeStatus.UNKNOWN
        } else {
            OperationOutcomeStatus.FAILED
        }
        OperationOutcome(
            operation = operation,
            status = status,
            strategy = id,
            errorCode = if (transport) {
                com.nexaflow.domain.capability.CapabilityErrorCode.TIMEOUT
            } else {
                com.nexaflow.domain.capability.CapabilityErrorCode.ROOT_DENIED
            },
            message = result.message,
            transportFailure = unavailable ||
                (timedOut && !transportIsUncertain(operation)),
            metadata = requestedEnabled?.let { mapOf("requestedEnabled" to it.toString()) } ?: emptyMap()
        )
    }

    /**
     * For radio toggles (`svc wifi enable`), a timeout does not prove the
     * radio did not change: the side effect may have landed. Those outcomes
     * must surface as UNKNOWN so the router reconciles instead of failing
     * definitively or re-executing.
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
        // A dispatched-but-unconfirmed package operation may have landed:
        // reconcile by reading the actual package state, never blind-retry.
        SemanticOperationId.PACKAGE_FORCE_STOP,
        SemanticOperationId.PACKAGE_CLEAR_DATA,
        SemanticOperationId.PACKAGE_SET_ENABLED_STATE -> true
        else -> false
    }

    private companion object {
        // Service names live in PrivilegedOperation.Companion.ServiceName.
    }
}
