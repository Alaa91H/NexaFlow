package com.nexaflow.domain.capability

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * A device operation requested by the workflow. The identifier deliberately
 * describes *what* is needed, never an implementation detail such as `su`.
 */
@Serializable
enum class CapabilityId {
    PACKAGE_READ,
    PACKAGE_INSTALL,
    PACKAGE_UNINSTALL,
    PACKAGE_FORCE_STOP,
    PACKAGE_SET_ENABLED,
    PACKAGE_CLEAR_DATA,
    UPDATE_APPS,
    INTENT_LAUNCH,
    NETWORK_HTTP_REQUEST,
    DEVICE_STATE_READ
}

/** A concrete execution channel that may implement one or more capabilities. */
@Serializable
enum class CapabilityBackendId {
    ANDROID_API,
    INTENT,
    PACKAGE_MANAGER,
    PACKAGE_INSTALLER,
    ACCESSIBILITY,
    SHIZUKU,
    ROOT,
    ADB,
    OEM,
    NATIVE,
    NETWORK
}

/** The highest trust boundary a capability backend must cross. */
@Serializable
enum class PrivilegeLevel {
    NORMAL,
    SYSTEM,
    ACCESSIBILITY,
    SHIZUKU,
    ROOT,
    NETWORK,
    NATIVE
}

/** Human-review and policy signal; it is not a substitute for permission checks. */
@Serializable
enum class CapabilityRiskLevel {
    LOW,
    MODERATE,
    HIGH,
    DESTRUCTIVE
}

/** Device-visible capability state for diagnostics and user interface. */
@Serializable
enum class CapabilityAvailability {
    AVAILABLE,
    PARTIAL,
    PERMISSION_REQUIRED,
    UNAVAILABLE,
    UNSUPPORTED
}

/** Terminal or externally-actionable lifecycle state of a capability invocation. */
@Serializable
enum class CapabilityStatus {
    SUCCESS,
    PARTIAL,
    PENDING_USER_ACTION,
    UNSUPPORTED,
    FAILED,
    CANCELLED
}

/** Machine-readable failure categories. Messages are strictly human-facing summaries. */
@Serializable
enum class CapabilityErrorCode {
    UNSUPPORTED_CAPABILITY,
    BACKEND_UNAVAILABLE,
    PERMISSION_DENIED,
    POLICY_NOT_SATISFIED,
    ROOT_UNAVAILABLE,
    SHIZUKU_UNAVAILABLE,
    ACCESSIBILITY_UNAVAILABLE,
    ADB_UNAVAILABLE,
    OEM_UNSUPPORTED,
    ANDROID_VERSION_UNSUPPORTED,
    SECURITY_EXCEPTION,
    INSTALL_FAILED,
    UPDATE_FAILED,
    UPDATE_NOT_FOUND,
    UPDATE_NOT_VERIFIED,
    VERIFICATION_FAILED,
    TIMEOUT,
    CANCELLED,
    NETWORK_ERROR,
    INVALID_CONFIGURATION,
    UNKNOWN_ERROR
}

/** Policy network requirement, independent from any transport implementation. */
@Serializable
enum class NetworkRequirement {
    ANY,
    WIFI_ONLY
}

/** Thermal severity exposed to policies without coupling the domain to Android APIs. */
@Serializable
enum class ThermalState {
    UNKNOWN,
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL
}

/**
 * One coherent, best-effort device state read for policy evaluation. Unknown
 * values remain explicit instead of being silently coerced to `false`.
 */
@Immutable
@Serializable
data class CapabilityDeviceState(
    val capturedAt: Long,
    val wifiConnected: Boolean? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val screenInteractive: Boolean? = null,
    val thermalState: ThermalState = ThermalState.UNKNOWN
) {
    init {
        require(batteryPercent == null || batteryPercent in 0..100) {
            "batteryPercent must be in 0..100 when supplied"
        }
    }
}

/** Why a policy blocked a request before any backend was invoked. */
@Serializable
enum class PolicyBlockReason {
    NETWORK_REQUIREMENT_NOT_MET,
    BATTERY_REQUIREMENT_NOT_MET,
    CHARGING_REQUIREMENT_NOT_MET,
    SCREEN_REQUIREMENT_NOT_MET,
    THERMAL_REQUIREMENT_NOT_MET,
    PRIVILEGE_NOT_ALLOWED,
    BACKEND_NOT_ALLOWED
}

/** Pure result of evaluating an [ExecutionPolicy] against a device snapshot. */
@Immutable
data class PolicyEvaluation(
    val allowed: Boolean,
    val reasons: List<PolicyBlockReason> = emptyList()
)

@Serializable
enum class CapabilityParameterType {
    STRING,
    BOOLEAN,
    INTEGER,
    PACKAGE_NAME,
    HTTPS_URL,
    CONTENT_URI
}

/** Declarative allowlist for one request parameter; arbitrary command fields are never valid. */
@Immutable
@Serializable
data class CapabilityParameterSpec(
    val name: String,
    val type: CapabilityParameterType,
    val required: Boolean = false,
    val maximumLength: Int = 512,
    val minimumInteger: Long? = null,
    val maximumInteger: Long? = null,
    val allowedValues: List<String> = emptyList()
) {
    init {
        require(name.matches(PARAMETER_NAME)) { "Invalid capability parameter name" }
        require(maximumLength in 1..4_096) { "maximumLength must be in 1..4096" }
        require(minimumInteger == null || maximumInteger == null || minimumInteger <= maximumInteger) {
            "minimumInteger must not exceed maximumInteger"
        }
    }

    private companion object {
        val PARAMETER_NAME = Regex("[a-z][A-Za-z0-9_]{0,63}")
    }
}

/**
 * Static declaration used by the registry. Runtime availability belongs to a
 * backend, because a device can have the same capability but different usable
 * channels at different moments.
 */
@Immutable
@Serializable
data class CapabilityDescriptor(
    val id: CapabilityId,
    val displayName: String,
    val description: String,
    val minAndroidApi: Int = 26,
    val requiredPermissions: List<String> = emptyList(),
    val minimumPrivilege: PrivilegeLevel = PrivilegeLevel.NORMAL,
    val risk: CapabilityRiskLevel = CapabilityRiskLevel.LOW,
    val supportedBackends: List<CapabilityBackendId> = emptyList(),
    /** Complete typed allowlist. Empty means this capability accepts no parameters. */
    val parameters: List<CapabilityParameterSpec> = emptyList()
) {
    init {
        require(parameters.map { it.name }.distinct().size == parameters.size) {
            "Capability parameter names must be unique"
        }
    }
}

/**
 * User/workflow policy evaluated before backend resolution. An empty
 * [allowedBackends] allows every backend declared by the descriptor; an empty
 * [preferredBackends] delegates safe ordering to the resolver.
 */
@Immutable
@Serializable
data class ExecutionPolicy(
    val allowedBackends: List<CapabilityBackendId> = emptyList(),
    val preferredBackends: List<CapabilityBackendId> = emptyList(),
    val network: NetworkRequirement = NetworkRequirement.ANY,
    val minimumBatteryPercent: Int = 0,
    val chargingRequired: Boolean = false,
    val screenOffRequired: Boolean = false,
    val rejectCriticalThermalState: Boolean = true,
    /** Explicit opt-in required before the resolver can choose Shizuku, Root or ADB. */
    val allowPrivilegedBackends: Boolean = false,
    val timeoutMs: Long = 30_000L,
    val retry: CapabilityRetryPolicy = CapabilityRetryPolicy()
) {
    init {
        require(minimumBatteryPercent in 0..100) {
            "minimumBatteryPercent must be in 0..100"
        }
        require(timeoutMs in 1_000L..300_000L) {
            "timeoutMs must be in 1,000..300,000ms"
        }
    }
}

/**
 * Serializable retry settings for a capability request. A runtime adapter maps
 * this to the existing workflow retry executor, keeping delay semantics shared
 * while allowing capability policies to be persisted independently.
 */
@Immutable
@Serializable
data class CapabilityRetryPolicy(
    val maxAttempts: Int = 1,
    val baseDelayMs: Long = 1_000L,
    val capDelayMs: Long = 60_000L,
    val retryableErrors: List<CapabilityErrorCode> = listOf(
        CapabilityErrorCode.BACKEND_UNAVAILABLE,
        CapabilityErrorCode.NETWORK_ERROR,
        CapabilityErrorCode.TIMEOUT
    )
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(baseDelayMs >= 0) { "baseDelayMs must be non-negative" }
        require(capDelayMs >= baseDelayMs) { "capDelayMs must be at least baseDelayMs" }
    }
}

/** Strategy requested after a side-effecting operation returns. */
@Serializable
enum class VerificationMode {
    NONE,
    BEST_EFFORT,
    REQUIRED
}

/**
 * A validated capability request. Parameters are constrained by an action
 * mapper/backend schema; they never contain an arbitrary shell command.
 */
@Immutable
@Serializable
data class CapabilityRequest(
    val capability: CapabilityId,
    val parameters: Map<String, String> = emptyMap(),
    val policy: ExecutionPolicy = ExecutionPolicy(),
    val verification: VerificationMode = VerificationMode.REQUIRED,
    val workflowId: String? = null,
    val executionId: String? = null,
    val actionId: String? = null
)

/** Result of one verification pass, retained separately from transport success. */
@Immutable
@Serializable
data class VerificationResult(
    val attempted: Boolean,
    val verified: Boolean,
    val message: String,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * The normalized outcome written by all capability backends. Metadata is
 * strictly non-sensitive: no bearer tokens, raw shell commands, or APK paths.
 */
@Immutable
@Serializable
data class CapabilityResult(
    val status: CapabilityStatus,
    val backend: CapabilityBackendId? = null,
    val errorCode: CapabilityErrorCode? = null,
    val message: String,
    val durationMs: Long = 0L,
    val verification: VerificationResult? = null,
    val packageName: String? = null,
    val previousVersion: String? = null,
    val newVersion: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    val isSuccess: Boolean
        get() = status == CapabilityStatus.SUCCESS

    companion object {
        fun unsupported(
            message: String,
            errorCode: CapabilityErrorCode = CapabilityErrorCode.UNSUPPORTED_CAPABILITY
        ): CapabilityResult = CapabilityResult(
            status = CapabilityStatus.UNSUPPORTED,
            errorCode = errorCode,
            message = message
        )

        fun failed(
            errorCode: CapabilityErrorCode,
            message: String,
            backend: CapabilityBackendId? = null,
            durationMs: Long = 0L
        ): CapabilityResult = CapabilityResult(
            status = CapabilityStatus.FAILED,
            backend = backend,
            errorCode = errorCode,
            message = message,
            durationMs = durationMs
        )
    }
}

/** One backend's live availability statement for diagnostics and resolver input. */
@Immutable
data class BackendAvailability(
    val backend: CapabilityBackendId,
    val availability: CapabilityAvailability,
    val reason: String? = null,
    val grantedPrivileges: List<PrivilegeLevel> = emptyList()
)

/** Capability availability aggregated from all registered backends. */
@Immutable
data class CapabilityAvailabilityReport(
    val capability: CapabilityId,
    val availability: CapabilityAvailability,
    val backends: List<BackendAvailability>,
    val reason: String? = null
)
