package com.nexaflow.core.execution.capability

import com.nexaflow.core.rom.PrivilegedOperation
import com.nexaflow.core.rom.PrivilegedRunner
import com.nexaflow.core.rom.ShizukuShellBridge
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.capability.BackendAvailability
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityDescriptor
import com.nexaflow.domain.capability.CapabilityErrorCode
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityParameterSpec
import com.nexaflow.domain.capability.CapabilityParameterType
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilityResult
import com.nexaflow.domain.capability.CapabilityRiskLevel
import com.nexaflow.domain.capability.CapabilityStatus
import com.nexaflow.domain.capability.PrivilegeLevel

/**
 * Static declarations for the small reviewed subset of elevated operations.
 * Privileged execution still requires [com.nexaflow.domain.capability.ExecutionPolicy.allowPrivilegedBackends]
 * to be explicitly enabled. Once enabled, the resolver may adaptively choose
 * Shizuku first and Root as a fallback unless the request narrows or reorders
 * those channels through allowedBackends/preferredBackends.
 */
object PrivilegedCapabilityCatalog {
    fun descriptors(): List<CapabilityDescriptor> = listOf(
        CapabilityDescriptor(
            id = CapabilityId.PACKAGE_FORCE_STOP,
            displayName = "Force stop installed package",
            description = "Stops one validated package through the best authorized elevated backend",
            risk = CapabilityRiskLevel.HIGH,
            minimumPrivilege = PrivilegeLevel.NONE,
            supportedBackends = TYPED_BACKENDS,
            parameters = listOf(
                CapabilityParameterSpec("packageName", CapabilityParameterType.PACKAGE_NAME, required = true)
            )
        ),
        CapabilityDescriptor(
            id = CapabilityId.PACKAGE_SET_ENABLED,
            displayName = "Enable or disable package",
            description = "Changes enabled state for one validated package through the best authorized elevated backend",
            risk = CapabilityRiskLevel.HIGH,
            minimumPrivilege = PrivilegeLevel.NONE,
            supportedBackends = TYPED_BACKENDS,
            parameters = listOf(
                CapabilityParameterSpec("packageName", CapabilityParameterType.PACKAGE_NAME, required = true),
                CapabilityParameterSpec("enabled", CapabilityParameterType.BOOLEAN, required = true)
            )
        ),
        CapabilityDescriptor(
            id = CapabilityId.SYSTEM_SETTING_WRITE,
            displayName = "Write allowlisted system setting",
            description = "Writes one reviewed settings key; arbitrary namespace/key commands are not supported",
            risk = CapabilityRiskLevel.HIGH,
            minimumPrivilege = PrivilegeLevel.NONE,
            supportedBackends = TYPED_BACKENDS,
            parameters = listOf(
                CapabilityParameterSpec(
                    "namespace", CapabilityParameterType.STRING, required = true,
                    allowedValues = PrivilegedOperation.SettingNamespace.entries.map { it.name }
                ),
                CapabilityParameterSpec(
                    "key", CapabilityParameterType.STRING, required = true,
                    allowedValues = PrivilegedOperation.ALLOWED_SETTING_KEYS.sorted()
                ),
                CapabilityParameterSpec("value", CapabilityParameterType.STRING, required = true, maximumLength = 512)
            )
        ),
        CapabilityDescriptor(
            id = CapabilityId.FILE_COPY,
            displayName = "Copy controlled NexaFlow file",
            description = "Copies a file only under /sdcard/NexaFlow/ through the best authorized elevated backend",
            risk = CapabilityRiskLevel.HIGH,
            minimumPrivilege = PrivilegeLevel.NONE,
            supportedBackends = TYPED_BACKENDS,
            parameters = listOf(
                CapabilityParameterSpec("source", CapabilityParameterType.STRING, required = true, maximumLength = 1_024),
                CapabilityParameterSpec("destination", CapabilityParameterType.STRING, required = true, maximumLength = 1_024)
            )
        )
    )

    private val TYPED_BACKENDS = listOf(CapabilityBackendId.SHIZUKU, CapabilityBackendId.ROOT)
}

/** Shared typed request mapper; it contains no shell command representation. */
internal object PrivilegedOperationRequestMapper {
    fun map(request: CapabilityRequest): PrivilegedOperation = when (request.capability) {
        CapabilityId.PACKAGE_FORCE_STOP -> PrivilegedOperation.ForceStopPackage(
            checkNotNull(request.parameters["packageName"])
        )
        CapabilityId.PACKAGE_SET_ENABLED -> PrivilegedOperation.SetPackageEnabled(
            packageName = checkNotNull(request.parameters["packageName"]),
            enabled = checkNotNull(request.parameters["enabled"]).toBooleanStrict()
        )
        CapabilityId.SYSTEM_SETTING_WRITE -> PrivilegedOperation.WriteSetting(
            namespace = PrivilegedOperation.SettingNamespace.parse(checkNotNull(request.parameters["namespace"]))
                ?: error("Unsupported settings namespace"),
            key = checkNotNull(request.parameters["key"]),
            value = checkNotNull(request.parameters["value"])
        )
        CapabilityId.FILE_COPY -> PrivilegedOperation.CopyControlledFile(
            source = checkNotNull(request.parameters["source"]),
            destination = checkNotNull(request.parameters["destination"])
        )
        else -> error("Capability ${request.capability} has no typed privileged operation")
    }
}

class ShizukuCapabilityBackend(
    private val running: () -> Boolean = PrivilegedRunner::isShizukuRunning,
    private val granted: () -> Boolean = PrivilegedRunner::isShizukuGranted,
    private val userServiceBound: () -> Boolean = { ShizukuShellBridge.isUserServiceBound },
    private val executeOperation: (PrivilegedOperation) -> SystemControlResult = PrivilegedRunner::runShizukuOperation
) : CapabilityBackend {
    override val id: CapabilityBackendId = CapabilityBackendId.SHIZUKU
    override val supportedCapabilities: Set<CapabilityId> = TYPED_CAPABILITIES

    override suspend fun availability(request: CapabilityRequest): BackendAvailability = when {
        request.capability !in supportedCapabilities -> unsupportedAvailability()
        !request.policy.allowPrivilegedBackends -> BackendAvailability(
            id,
            CapabilityAvailability.PERMISSION_REQUIRED,
            PRIVILEGED_OPT_IN_REQUIRED
        )
        !running() -> BackendAvailability(id, CapabilityAvailability.UNAVAILABLE, "Shizuku server is not running")
        !granted() -> BackendAvailability(id, CapabilityAvailability.PERMISSION_REQUIRED, "Shizuku access was not granted")
        !userServiceBound() -> BackendAvailability(id, CapabilityAvailability.UNAVAILABLE, "Shizuku UserService is not connected")
        else -> BackendAvailability(id, CapabilityAvailability.AVAILABLE)
    }

    override suspend fun execute(request: CapabilityRequest): CapabilityResult {
        if (!request.policy.allowPrivilegedBackends) {
            return policyDenied(id)
        }
        val operation = runCatching { PrivilegedOperationRequestMapper.map(request) }.getOrElse { error ->
            return CapabilityResult.failed(CapabilityErrorCode.INVALID_CONFIGURATION, error.message ?: "Invalid privileged request", id)
        }
        if (!running()) return unavailable(CapabilityErrorCode.SHIZUKU_UNAVAILABLE, "Shizuku server is not running", id)
        if (!granted()) return unavailable(CapabilityErrorCode.SHIZUKU_DENIED, "Shizuku access was not granted", id)
        if (!userServiceBound()) return unavailable(CapabilityErrorCode.SHIZUKU_UNAVAILABLE, "Shizuku UserService is not connected", id)
        return executeOperation(operation).toCapabilityResult(
            backend = id,
            operation = operation,
            denialCode = CapabilityErrorCode.SHIZUKU_DENIED
        )
    }

    private fun unsupportedAvailability() = BackendAvailability(id, CapabilityAvailability.UNSUPPORTED, "Capability is not implemented by Shizuku backend")
}

class RootCapabilityBackend(
    private val rootAvailable: () -> Boolean = PrivilegedRunner::isRootAvailable,
    private val executeOperation: (PrivilegedOperation) -> SystemControlResult = PrivilegedRunner::runRootOperation
) : CapabilityBackend {
    override val id: CapabilityBackendId = CapabilityBackendId.ROOT
    override val supportedCapabilities: Set<CapabilityId> = TYPED_CAPABILITIES

    override suspend fun availability(request: CapabilityRequest): BackendAvailability = when {
        request.capability !in supportedCapabilities -> BackendAvailability(id, CapabilityAvailability.UNSUPPORTED, "Capability is not implemented by Root backend")
        !request.policy.allowPrivilegedBackends -> BackendAvailability(
            id,
            CapabilityAvailability.PERMISSION_REQUIRED,
            PRIVILEGED_OPT_IN_REQUIRED
        )
        !rootAvailable() -> BackendAvailability(id, CapabilityAvailability.UNAVAILABLE, "Root access is not available")
        else -> BackendAvailability(id, CapabilityAvailability.AVAILABLE)
    }

    override suspend fun execute(request: CapabilityRequest): CapabilityResult {
        if (!request.policy.allowPrivilegedBackends) {
            return policyDenied(id)
        }
        val operation = runCatching { PrivilegedOperationRequestMapper.map(request) }.getOrElse { error ->
            return CapabilityResult.failed(CapabilityErrorCode.INVALID_CONFIGURATION, error.message ?: "Invalid privileged request", id)
        }
        if (!rootAvailable()) return unavailable(CapabilityErrorCode.ROOT_UNAVAILABLE, "Root access is not available", id)
        return executeOperation(operation).toCapabilityResult(id, operation, CapabilityErrorCode.ROOT_DENIED)
    }
}

/**
 * Normal Android applications have no authenticated interactive ADB transport.
 * This backend is deliberately not registered in the production catalog until a
 * real managed channel exists; its explicit response prevents false support.
 */
class AdbCapabilityBackend : CapabilityBackend {
    override val id: CapabilityBackendId = CapabilityBackendId.ADB
    override val supportedCapabilities: Set<CapabilityId> = TYPED_CAPABILITIES

    override suspend fun availability(request: CapabilityRequest): BackendAvailability = BackendAvailability(
        id,
        if (request.capability in supportedCapabilities) CapabilityAvailability.UNAVAILABLE else CapabilityAvailability.UNSUPPORTED,
        "ADB shell is unavailable to a normal Android application"
    )

    override suspend fun execute(request: CapabilityRequest): CapabilityResult = CapabilityResult.failed(
        CapabilityErrorCode.ADB_UNAVAILABLE,
        "ADB shell is unavailable to a normal Android application",
        id
    )
}

private val TYPED_CAPABILITIES = setOf(
    CapabilityId.PACKAGE_FORCE_STOP,
    CapabilityId.PACKAGE_SET_ENABLED,
    CapabilityId.SYSTEM_SETTING_WRITE,
    CapabilityId.FILE_COPY
)
private const val PRIVILEGED_OPT_IN_REQUIRED = "Privileged backend use requires explicit execution-policy opt-in"

private fun policyDenied(backend: CapabilityBackendId): CapabilityResult = CapabilityResult.failed(
    CapabilityErrorCode.POLICY_NOT_SATISFIED,
    PRIVILEGED_OPT_IN_REQUIRED,
    backend
)

private fun unavailable(
    code: CapabilityErrorCode,
    message: String,
    backend: CapabilityBackendId? = null
): CapabilityResult = CapabilityResult.failed(code, message, backend)

private fun SystemControlResult.toCapabilityResult(
    backend: CapabilityBackendId,
    operation: PrivilegedOperation,
    denialCode: CapabilityErrorCode = CapabilityErrorCode.UNKNOWN_ERROR
): CapabilityResult = if (success) {
    CapabilityResult(
        status = CapabilityStatus.SUCCESS,
        backend = backend,
        message = message,
        metadata = mapOf("operation" to operation.wireId.wireValue)
    )
} else {
    val code = when {
        message.contains("exit 124") || message.contains("timed out", ignoreCase = true) -> CapabilityErrorCode.TIMEOUT
        backend == CapabilityBackendId.SHIZUKU &&
            (message.contains("not connected", ignoreCase = true) ||
                message.contains("unavailable", ignoreCase = true)) -> CapabilityErrorCode.SHIZUKU_UNAVAILABLE
        backend == CapabilityBackendId.ROOT && message.contains("not available", ignoreCase = true) ->
            CapabilityErrorCode.ROOT_UNAVAILABLE
        message.contains("denied", ignoreCase = true) -> denialCode
        else -> CapabilityErrorCode.UNKNOWN_ERROR
    }
    CapabilityResult.failed(code, message, backend).copy(metadata = mapOf("operation" to operation.wireId.wireValue))
}
