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
 * A request must explicitly select ROOT or SHIZUKU in its execution policy;
 * leaving backend selection empty never creates an automatic privilege fallback.
 */
object PrivilegedCapabilityCatalog {
    fun descriptors(): List<CapabilityDescriptor> = listOf(
        CapabilityDescriptor(
            id = CapabilityId.PACKAGE_FORCE_STOP,
            displayName = "Force stop installed package",
            description = "Stops one validated package through a user-selected elevated backend",
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
            description = "Changes enabled state for one validated package through a user-selected elevated backend",
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
            description = "Copies a file only under /sdcard/NexaFlow/ through a user-selected elevated backend",
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
        !explicitlySelected(request) -> BackendAvailability(id, CapabilityAvailability.PERMISSION_REQUIRED, EXPLICIT_SELECTION_REQUIRED)
        !running() -> BackendAvailability(id, CapabilityAvailability.UNAVAILABLE, "Shizuku server is not running")
        !granted() -> BackendAvailability(id, CapabilityAvailability.PERMISSION_REQUIRED, "Shizuku access was not granted")
        !userServiceBound() -> BackendAvailability(id, CapabilityAvailability.UNAVAILABLE, "Shizuku UserService is not connected")
        else -> BackendAvailability(id, CapabilityAvailability.AVAILABLE)
    }

    override suspend fun execute(request: CapabilityRequest): CapabilityResult {
        val operation = runCatching { PrivilegedOperationRequestMapper.map(request) }.getOrElse { error ->
            return CapabilityResult.failed(CapabilityErrorCode.INVALID_CONFIGURATION, error.message ?: "Invalid privileged request", id)
        }
        if (!running()) return unavailable(CapabilityErrorCode.SHIZUKU_UNAVAILABLE, "Shizuku server is not running")
        if (!granted()) return unavailable(CapabilityErrorCode.SHIZUKU_DENIED, "Shizuku access was not granted")
        if (!userServiceBound()) return unavailable(CapabilityErrorCode.SHIZUKU_UNAVAILABLE, "Shizuku UserService is not connected")
        return executeOperation(operation).toCapabilityResult(id, operation)
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
        !explicitlySelected(request) -> BackendAvailability(id, CapabilityAvailability.PERMISSION_REQUIRED, EXPLICIT_SELECTION_REQUIRED)
        !rootAvailable() -> BackendAvailability(id, CapabilityAvailability.UNAVAILABLE, "Root access is not available")
        else -> BackendAvailability(id, CapabilityAvailability.AVAILABLE)
    }

    override suspend fun execute(request: CapabilityRequest): CapabilityResult {
        val operation = runCatching { PrivilegedOperationRequestMapper.map(request) }.getOrElse { error ->
            return CapabilityResult.failed(CapabilityErrorCode.INVALID_CONFIGURATION, error.message ?: "Invalid privileged request", id)
        }
        if (!rootAvailable()) return unavailable(CapabilityErrorCode.ROOT_UNAVAILABLE, "Root access is not available")
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
private const val EXPLICIT_SELECTION_REQUIRED = "Select exactly one privileged backend in execution policy"

private fun explicitlySelected(request: CapabilityRequest, backend: CapabilityBackendId? = null): Boolean {
    val selected = request.policy.allowedBackends
    return selected.isNotEmpty() && (backend == null || selected.size == 1 && selected.single() == backend)
}

private fun CapabilityBackend.explicitlySelected(request: CapabilityRequest): Boolean =
    explicitlySelected(request, id)

private fun unavailable(code: CapabilityErrorCode, message: String): CapabilityResult = CapabilityResult.failed(code, message)

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
        message.contains("denied", ignoreCase = true) -> denialCode
        else -> CapabilityErrorCode.UNKNOWN_ERROR
    }
    CapabilityResult.failed(code, message, backend).copy(metadata = mapOf("operation" to operation.wireId.wireValue))
}
