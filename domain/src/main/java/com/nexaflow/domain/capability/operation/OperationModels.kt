package com.nexaflow.domain.capability.operation

import com.nexaflow.domain.capability.CapabilityIdempotency
import com.nexaflow.domain.capability.CapabilityParameterSpec
import com.nexaflow.domain.capability.CapabilityRiskLevel
import com.nexaflow.domain.capability.CapabilityRetrySafety
import com.nexaflow.domain.capability.CapabilitySideEffectLevel
import com.nexaflow.domain.capability.CapabilityCompensationSupport
import com.nexaflow.domain.capability.VerificationMode
import kotlinx.serialization.Serializable

/**
 * Precise semantic device operations. Each identifier names exactly one
 * observable device state or transition and never an implementation channel.
 * Serialized enum names are part of the persisted-compatibility surface, so
 * this enum is append-only: never rename or reorder existing entries.
 */
@Serializable
enum class SemanticOperationId {
    WIFI_GET_STATE,
    WIFI_SET_STATE,
    BLUETOOTH_GET_STATE,
    BLUETOOTH_SET_STATE,
    MOBILE_DATA_GET_STATE,
    MOBILE_DATA_SET_STATE,
    HOTSPOT_GET_STATE,
    HOTSPOT_SET_STATE,
    NFC_GET_STATE,
    NFC_SET_STATE,
    LOCATION_GET_STATE,
    LOCATION_SET_STATE,
    AIRPLANE_MODE_GET_STATE,
    AIRPLANE_MODE_SET_STATE,
    ROTATION_GET_STATE,
    ROTATION_SET_STATE,
    BRIGHTNESS_GET,
    BRIGHTNESS_SET,
    SCREEN_TIMEOUT_GET,
    SCREEN_TIMEOUT_SET,
    DND_GET_STATE,
    DND_SET_STATE,
    DATA_SAVER_GET_STATE,
    DATA_SAVER_SET_STATE;

    /** True when the operation observes state without changing it. */
    val isReadOnly: Boolean
        get() = name.endsWith("_GET_STATE") || name.endsWith("_GET")

    companion object {
        /** The paired read operation for a write, or the write for a read. */
        fun counterpartOf(id: SemanticOperationId): SemanticOperationId? {
            val prefix = when {
                id.name.endsWith("_GET_STATE") -> id.name.removeSuffix("_GET_STATE")
                id.name.endsWith("_GET") -> id.name.removeSuffix("_GET")
                id.name.endsWith("_SET_STATE") -> id.name.removeSuffix("_SET_STATE")
                id.name.endsWith("_SET") -> id.name.removeSuffix("_SET")
                else -> return null
            }
            val candidateNames = if (id.isReadOnly) {
                listOf("${prefix}_SET_STATE", "${prefix}_SET")
            } else {
                listOf("${prefix}_GET_STATE", "${prefix}_GET")
            }
            return candidateNames.firstNotNullOfOrNull { candidate ->
                entries.firstOrNull { it.name == candidate }
            }
        }
    }
}

/** Hardware or environment prerequisites checked against the live device. */
@Serializable
enum class DeviceFeature {
    WIFI_HARDWARE,
    BLUETOOTH_HARDWARE,
    NFC_HARDWARE,
    TELEPHONY,
    LOCATION_HARDWARE
}

/**
 * One concrete way a strategy can execute an operation. Identifiers are
 * descriptive of the mechanism, not of a privilege tier; the router orders
 * candidates by availability, evidence, health and least privilege — never by
 * the enum position.
 */
@Serializable
enum class StrategyId {
    ANDROID_PUBLIC_API,
    WRITE_SETTINGS,
    DEVICE_OWNER,
    SHIZUKU_USER_SERVICE,
    ROOT_SHELL,
    SETTINGS_USER_ACTION,
    OEM_SPECIFIC
}

/**
 * The typed, complete contract for one semantic operation. Reuses the
 * established risk/idempotency/verification/compensation vocabulary of the
 * capability layer; adds only what a device operation genuinely needs.
 *
 * Parameters reuse [CapabilityParameterSpec] so validation, bounded lengths
 * and allowlists stay identical across the capability and semantic layers.
 */
@Serializable
data class OperationSpec(
    val id: SemanticOperationId,
    val displayName: String,
    /** Typed allowlist; every request parameter must appear here. */
    val parameters: List<CapabilityParameterSpec> = emptyList(),
    val minAndroidApi: Int = 26,
    /** Null when no upper bound has been observed. */
    val maxAndroidApi: Int? = null,
    val requiredFeatures: Set<DeviceFeature> = emptySet(),
    val risk: CapabilityRiskLevel = CapabilityRiskLevel.LOW,
    /** Conservative defaults mirror the capability descriptors. */
    val idempotency: CapabilityIdempotency = CapabilityIdempotency.NON_IDEMPOTENT,
    val retrySafety: CapabilityRetrySafety = CapabilityRetrySafety.UNSAFE,
    val sideEffectLevel: CapabilitySideEffectLevel =
        if (id.isReadOnly) CapabilitySideEffectLevel.NONE else CapabilitySideEffectLevel.REVERSIBLE,
    val verificationMode: VerificationMode =
        if (id.isReadOnly) VerificationMode.NONE else VerificationMode.REQUIRED,
    val compensation: CapabilityCompensationSupport = CapabilityCompensationSupport.UNSUPPORTED,
    /** Only strategies with real, reviewed implementations are listed. */
    val strategies: List<StrategyId> = emptyList()
) {
    init {
        require(parameters.map { it.name }.distinct().size == parameters.size) {
            "Operation parameter names must be unique"
        }
        require(maxAndroidApi == null || maxAndroidApi >= minAndroidApi) {
            "maxAndroidApi must not be below minAndroidApi"
        }
    }

    fun parameterSchema(name: String): CapabilityParameterSpec? =
        parameters.firstOrNull { it.name == name }
}
