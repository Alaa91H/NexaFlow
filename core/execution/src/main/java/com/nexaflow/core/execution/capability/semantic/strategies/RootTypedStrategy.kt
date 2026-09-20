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
        SemanticOperationId.AIRPLANE_MODE_GET_STATE,
        SemanticOperationId.AIRPLANE_MODE_SET_STATE,
        SemanticOperationId.DND_GET_STATE,
        SemanticOperationId.NFC_SET_STATE,
        SemanticOperationId.HOTSPOT_SET_STATE,
        SemanticOperationId.HOTSPOT_GET_STATE,
        SemanticOperationId.MOBILE_DATA_GET_STATE,
        SemanticOperationId.MOBILE_DATA_SET_STATE
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
            SemanticOperationId.AIRPLANE_MODE_SET_STATE ->
                PrivilegedOperation.WriteSetting(
                    namespace = PrivilegedOperation.SettingNamespace.GLOBAL,
                    key = "airplane_mode_on",
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
        SemanticOperationId.AIRPLANE_MODE_GET_STATE ->
            readSettingInt(PrivilegedOperation.SettingNamespace.GLOBAL, "airplane_mode_on")
        SemanticOperationId.DND_GET_STATE ->
            readSettingInt(PrivilegedOperation.SettingNamespace.GLOBAL, "zen_mode")
        SemanticOperationId.MOBILE_DATA_GET_STATE ->
            readSettingInt(PrivilegedOperation.SettingNamespace.GLOBAL, "mobile_data")
        SemanticOperationId.HOTSPOT_GET_STATE -> null // no reliable single read; honest null
        else -> null
    }

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

    private fun toOutcome(
        operation: SemanticOperationId,
        result: SystemControlResult,
        requestedEnabled: Boolean
    ): OperationOutcome = if (result.success) {
        OperationOutcome(
            operation = operation,
            status = OperationOutcomeStatus.SUCCESS,
            strategy = id,
            message = result.message,
            metadata = mapOf("requestedEnabled" to requestedEnabled.toString())
        )
    } else {
        val transport = result.message.contains("timed out", ignoreCase = true) ||
            result.message.contains("not available", ignoreCase = true)
        val status = if (transport && transportIsUncertain(operation)) {
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
            transportFailure = transport && !transportIsUncertain(operation),
            metadata = mapOf("requestedEnabled" to requestedEnabled.toString())
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
        SemanticOperationId.NFC_SET_STATE,
        SemanticOperationId.MOBILE_DATA_SET_STATE,
        SemanticOperationId.HOTSPOT_SET_STATE -> true
        else -> false
    }

    private companion object {
        // Service names live in PrivilegedOperation.Companion.ServiceName.
    }
}
