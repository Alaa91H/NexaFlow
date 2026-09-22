package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.CapabilityIdempotency
import com.nexaflow.domain.capability.CapabilityParameterSpec
import com.nexaflow.domain.capability.CapabilityParameterType
import com.nexaflow.domain.capability.CapabilityRetrySafety
import com.nexaflow.domain.capability.CapabilityRiskLevel
import com.nexaflow.domain.capability.CapabilityCompensationSupport
import com.nexaflow.domain.capability.VerificationMode
import com.nexaflow.domain.capability.operation.DeviceFeature
import com.nexaflow.domain.capability.operation.OperationSpec
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId

/**
 * The single source of truth for semantic operation contracts. Duplicate
 * registrations fail fast: two specs for one operation would make routing
 * ambiguous. Register only operations with real strategies — no aspirational
 * entries.
 */
class OperationRegistry private constructor(
    private val specs: Map<SemanticOperationId, OperationSpec>
) {
    fun specFor(id: SemanticOperationId): OperationSpec? = specs[id]

    fun operations(): List<OperationSpec> = specs.values.sortedBy { it.id.name }

    fun requiresOperation(id: SemanticOperationId): OperationSpec =
        specs[id] ?: error("Operation ${id.name} is not registered")

    companion object {
        fun of(specs: List<OperationSpec>): OperationRegistry {
            val byId = specs.associateBy { it.id }
            require(byId.size == specs.size) { "An OperationSpec was registered twice" }
            return OperationRegistry(byId)
        }

        /**
         * Phase-A catalog: connectivity, display and audio-interruption state
         * operations with their actually implemented strategies. Every entry
         * is declarative; see [spec] for the builder contract.
         */
        fun default(): OperationRegistry = of(
            listOf(
                spec(SemanticOperationId.WIFI_GET_STATE, "Read Wi-Fi state",
                    features = setOf(DeviceFeature.WIFI_HARDWARE), write = false,
                    strategies = listOf(StrategyId.ANDROID_PUBLIC_API, StrategyId.ROOT_SHELL)),
                spec(SemanticOperationId.WIFI_SET_STATE, "Enable or disable Wi-Fi",
                    features = setOf(DeviceFeature.WIFI_HARDWARE),
                    strategies = listOf(
                        StrategyId.ANDROID_PUBLIC_API,
                        StrategyId.SHIZUKU_USER_SERVICE,
                        StrategyId.ROOT_SHELL,
                        StrategyId.SETTINGS_USER_ACTION
                    )),
                spec(SemanticOperationId.BLUETOOTH_GET_STATE, "Read Bluetooth state",
                    features = setOf(DeviceFeature.BLUETOOTH_HARDWARE), write = false,
                    strategies = listOf(StrategyId.ANDROID_PUBLIC_API, StrategyId.ROOT_SHELL)),
                spec(SemanticOperationId.BLUETOOTH_SET_STATE, "Enable or disable Bluetooth",
                    features = setOf(DeviceFeature.BLUETOOTH_HARDWARE),
                    strategies = listOf(
                        StrategyId.ANDROID_PUBLIC_API,
                        StrategyId.SHIZUKU_USER_SERVICE,
                        StrategyId.ROOT_SHELL,
                        StrategyId.SETTINGS_USER_ACTION
                    )),
                spec(SemanticOperationId.LOCATION_GET_STATE, "Read location state",
                    features = setOf(DeviceFeature.LOCATION_HARDWARE), write = false,
                    strategies = listOf(StrategyId.ANDROID_PUBLIC_API)),
                spec(SemanticOperationId.LOCATION_SET_STATE, "Enable or disable location",
                    features = setOf(DeviceFeature.LOCATION_HARDWARE),
                    risk = CapabilityRiskLevel.MODERATE,
                    strategies = listOf(StrategyId.WRITE_SETTINGS, StrategyId.SETTINGS_USER_ACTION)),
                spec(SemanticOperationId.AIRPLANE_MODE_GET_STATE, "Read airplane mode state",
                    write = false,
                    strategies = listOf(StrategyId.ANDROID_PUBLIC_API, StrategyId.ROOT_SHELL)),
                spec(SemanticOperationId.AIRPLANE_MODE_SET_STATE, "Enable or disable airplane mode",
                    risk = CapabilityRiskLevel.MODERATE,
                    strategies = listOf(StrategyId.WRITE_SETTINGS, StrategyId.ROOT_SHELL, StrategyId.SETTINGS_USER_ACTION)),
                spec(SemanticOperationId.ROTATION_GET_STATE, "Read auto-rotate state",
                    write = false,
                    strategies = listOf(StrategyId.ANDROID_PUBLIC_API, StrategyId.WRITE_SETTINGS)),
                spec(SemanticOperationId.ROTATION_SET_STATE, "Enable or disable auto-rotate",
                    // Settings.System.putInt is the public API; it additionally
                    // needs the WRITE_SETTINGS appop, checked by the strategy.
                    strategies = listOf(StrategyId.ANDROID_PUBLIC_API)),
                spec(SemanticOperationId.BRIGHTNESS_GET, "Read screen brightness",
                    write = false,
                    strategies = listOf(StrategyId.ANDROID_PUBLIC_API, StrategyId.WRITE_SETTINGS)),
                spec(SemanticOperationId.BRIGHTNESS_SET, "Set screen brightness",
                    parameters = listOf(
                        CapabilityParameterSpec(
                            "value", CapabilityParameterType.INTEGER, required = true,
                            minimumInteger = 0, maximumInteger = 255
                        )
                    ),
                    strategies = listOf(StrategyId.WRITE_SETTINGS, StrategyId.SETTINGS_USER_ACTION)),
                spec(SemanticOperationId.SCREEN_TIMEOUT_GET, "Read screen timeout",
                    write = false,
                    strategies = listOf(StrategyId.ANDROID_PUBLIC_API, StrategyId.WRITE_SETTINGS)),
                spec(SemanticOperationId.SCREEN_TIMEOUT_SET, "Set screen timeout",
                    parameters = listOf(
                        CapabilityParameterSpec(
                            "seconds", CapabilityParameterType.INTEGER, required = true,
                            minimumInteger = 1, maximumInteger = 86_400
                        )
                    ),
                    strategies = listOf(StrategyId.WRITE_SETTINGS, StrategyId.SETTINGS_USER_ACTION)),
                spec(SemanticOperationId.DND_GET_STATE, "Read Do-Not-Disturb state",
                    write = false,
                    strategies = listOf(StrategyId.ANDROID_PUBLIC_API, StrategyId.ROOT_SHELL)),
                spec(SemanticOperationId.DND_SET_STATE, "Enable or disable Do-Not-Disturb",
                    strategies = listOf(
                        StrategyId.ANDROID_PUBLIC_API,
                        StrategyId.ROOT_SHELL,
                        StrategyId.SETTINGS_USER_ACTION
                    )),
                spec(SemanticOperationId.NFC_GET_STATE, "Read NFC state",
                    features = setOf(DeviceFeature.NFC_HARDWARE), write = false,
                    strategies = listOf(StrategyId.ANDROID_PUBLIC_API)),
                spec(SemanticOperationId.NFC_SET_STATE, "Enable or disable NFC",
                    features = setOf(DeviceFeature.NFC_HARDWARE),
                    strategies = listOf(StrategyId.ROOT_SHELL, StrategyId.SETTINGS_USER_ACTION)),
                spec(SemanticOperationId.HOTSPOT_GET_STATE, "Read hotspot state",
                    write = false,
                    strategies = listOf(StrategyId.ANDROID_PUBLIC_API, StrategyId.ROOT_SHELL)),
                spec(SemanticOperationId.HOTSPOT_SET_STATE, "Start or stop the Wi-Fi hotspot",
                    risk = CapabilityRiskLevel.MODERATE,
                    strategies = listOf(
                        StrategyId.SHIZUKU_USER_SERVICE,
                        StrategyId.ROOT_SHELL,
                        StrategyId.SETTINGS_USER_ACTION
                    )),
                spec(SemanticOperationId.MOBILE_DATA_GET_STATE, "Read mobile data state",
                    features = setOf(DeviceFeature.TELEPHONY), write = false,
                    strategies = listOf(StrategyId.ANDROID_PUBLIC_API, StrategyId.ROOT_SHELL)),
                spec(SemanticOperationId.MOBILE_DATA_SET_STATE, "Enable or disable mobile data",
                    features = setOf(DeviceFeature.TELEPHONY),
                    risk = CapabilityRiskLevel.MODERATE,
                    strategies = listOf(
                        StrategyId.SHIZUKU_USER_SERVICE,
                        StrategyId.ROOT_SHELL,
                        StrategyId.SETTINGS_USER_ACTION
                    )),
                spec(SemanticOperationId.DATA_SAVER_GET_STATE, "Read Data Saver state",
                    write = false,
                    strategies = listOf(StrategyId.ANDROID_PUBLIC_API, StrategyId.ROOT_SHELL)),
                spec(SemanticOperationId.DATA_SAVER_SET_STATE, "Enable or disable Data Saver",
                    strategies = listOf(StrategyId.WRITE_SETTINGS, StrategyId.SETTINGS_USER_ACTION)),
                spec(SemanticOperationId.PACKAGE_FORCE_STOP, "Force-stop a package",
                    parameters = listOf(
                        CapabilityParameterSpec(
                            "packageName", CapabilityParameterType.PACKAGE_NAME, required = true
                        )
                    ),
                    risk = CapabilityRiskLevel.HIGH,
                    // No reliable observable post-condition (a killed process
                    // may be restarted instantly by a sync job), so REQUIRED
                    // would fabricate verdicts; BEST_EFFORT documents that.
                    verificationMode = VerificationMode.BEST_EFFORT,
                    strategies = listOf(
                        StrategyId.SHIZUKU_USER_SERVICE,
                        StrategyId.ROOT_SHELL
                    )),
                spec(SemanticOperationId.PACKAGE_CLEAR_DATA, "Clear a package's data",
                    parameters = listOf(
                        CapabilityParameterSpec(
                            "packageName", CapabilityParameterType.PACKAGE_NAME, required = true
                        )
                    ),
                    risk = CapabilityRiskLevel.HIGH,
                    // Same honesty as force-stop: the enabled-state probe says
                    // nothing about whether data was cleared.
                    verificationMode = VerificationMode.BEST_EFFORT,
                    strategies = listOf(
                        StrategyId.SHIZUKU_USER_SERVICE,
                        StrategyId.ROOT_SHELL
                    )),
                spec(SemanticOperationId.PACKAGE_SET_ENABLED_STATE, "Enable or disable a package",
                    parameters = listOf(
                        CapabilityParameterSpec(
                            "packageName", CapabilityParameterType.PACKAGE_NAME, required = true
                        ),
                        CapabilityParameterSpec(
                            "enabled", CapabilityParameterType.BOOLEAN, required = true
                        )
                    ),
                    risk = CapabilityRiskLevel.HIGH,
                    strategies = listOf(
                        StrategyId.SHIZUKU_USER_SERVICE,
                        StrategyId.ROOT_SHELL
                    )),
                spec(SemanticOperationId.PACKAGE_GET_ENABLED_STATE, "Read a package's enabled state",
                    parameters = listOf(
                        CapabilityParameterSpec(
                            "packageName", CapabilityParameterType.PACKAGE_NAME, required = true
                        )
                    ),
                    write = false,
                    strategies = listOf(
                        StrategyId.ANDROID_PUBLIC_API,
                        StrategyId.SHIZUKU_USER_SERVICE,
                        StrategyId.ROOT_SHELL
                    ))
            )
        )

        /**
         * Declarative spec builder. Reads are idempotent, safe and never
         * verified; writes default to an enabled boolean parameter, REQUIRED
         * verification and reversible side effects. Risk defaults LOW.
         */
        private fun spec(
            id: SemanticOperationId,
            displayName: String,
            parameters: List<CapabilityParameterSpec> =
                if (id.isReadOnly || id.name.startsWith("PACKAGE_")) emptyList() else listOf(
                    CapabilityParameterSpec("enabled", CapabilityParameterType.BOOLEAN, required = true)
                ),
            features: Set<DeviceFeature> = emptySet(),
            risk: CapabilityRiskLevel = CapabilityRiskLevel.LOW,
            write: Boolean = true,
            verificationMode: VerificationMode? = null,
            strategies: List<StrategyId>
        ) = OperationSpec(
            id = id,
            displayName = displayName,
            parameters = parameters,
            requiredFeatures = features,
            risk = risk,
            idempotency = CapabilityIdempotency.IDEMPOTENT,
            retrySafety = CapabilityRetrySafety.SAFE,
            verificationMode = verificationMode ?:
                if (write) VerificationMode.REQUIRED else VerificationMode.NONE,
            // PACKAGE_CLEAR_DATA destroys user data irreversibly; force-stop
            // and enable/disable are reversible in practice. Compensation
            // honesty feeds the recovery coordinator, so it must match reality.
            compensation = if (write && id != SemanticOperationId.PACKAGE_CLEAR_DATA)
                CapabilityCompensationSupport.SUPPORTED
            else CapabilityCompensationSupport.UNSUPPORTED,
            strategies = strategies
        )
    }
}
