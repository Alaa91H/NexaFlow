package com.nexaflow.core.execution.capability

import android.content.Context
import com.nexaflow.core.execution.plugin.PluginFireClient
import com.nexaflow.core.pluginsdk.LocaleContract
import com.nexaflow.core.pluginsdk.PluginCompatibilityStatus
import com.nexaflow.core.pluginsdk.PluginConfigParser
import com.nexaflow.core.pluginsdk.PluginDiscoveryRegistry
import com.nexaflow.core.pluginsdk.PluginType
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
import com.nexaflow.domain.capability.VerificationResult
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.repositories.AutomationRepository

/** Static descriptor additions for external components. */
object PluginCapabilityCatalog {
    fun descriptors(): List<CapabilityDescriptor> = listOf(
        CapabilityDescriptor(
            id = CapabilityId.PLUGIN_ACTION,
            displayName = "Run configured external plugin action",
            description = "Invokes one user-configured Locale-compatible setting plugin through an explicit adapter",
            risk = CapabilityRiskLevel.MODERATE,
            minimumPrivilege = PrivilegeLevel.NONE,
            supportedBackends = listOf(CapabilityBackendId.PLUGIN),
            parameters = listOf(
                CapabilityParameterSpec(
                    name = "pluginInstance",
                    type = CapabilityParameterType.OPAQUE_REFERENCE,
                    required = true,
                    maximumLength = 192
                )
            )
        )
    )
}

/**
 * Single external-plugin execution boundary. The request carries a non-secret
 * instance id only; all persisted protocol data is resolved from the owning
 * automation after validating workflow/action identity and user approval.
 */
class PluginCapabilityBackend(
    context: Context,
    private val automationRepository: AutomationRepository,
    private val discoveryRegistry: PluginDiscoveryRegistry,
    private val fireClient: PluginFireClient = PluginFireClient()
) : CapabilityBackend {
    override val id: CapabilityBackendId = CapabilityBackendId.PLUGIN
    override val supportedCapabilities: Set<CapabilityId> = setOf(
        CapabilityId.PLUGIN_ACTION,
        CapabilityId.PLUGIN_CONDITION_READ
    )

    private val appContext = context.applicationContext
    private val conditionBackend = PluginConditionBackend(
        context = appContext,
        automationRepository = automationRepository,
        discoveryRegistry = discoveryRegistry
    )

    override suspend fun availability(request: CapabilityRequest): BackendAvailability = when (request.capability) {
        CapabilityId.PLUGIN_ACTION -> {
            val resolved = resolveAction(request)
            when {
                resolved.error != null -> BackendAvailability(id, CapabilityAvailability.UNAVAILABLE, resolved.error)
                resolved.action == null -> BackendAvailability(id, CapabilityAvailability.UNAVAILABLE, "Plugin instance is unavailable")
                else -> BackendAvailability(id, CapabilityAvailability.AVAILABLE)
            }
        }
        CapabilityId.PLUGIN_CONDITION_READ -> conditionBackend.availability(request)
        else -> BackendAvailability(id, CapabilityAvailability.UNSUPPORTED, "Plugin backend does not implement this capability")
    }

    override suspend fun execute(request: CapabilityRequest): CapabilityResult = when (request.capability) {
        CapabilityId.PLUGIN_ACTION -> executeAction(request)
        CapabilityId.PLUGIN_CONDITION_READ -> conditionBackend.execute(request)
        else -> CapabilityResult.unsupported("Plugin backend does not implement this capability")
    }

    override suspend fun verify(request: CapabilityRequest, result: CapabilityResult): VerificationResult {
        // Locale's base protocol gives only a receiver acknowledgement. It is
        // not an independently observable post-condition, so BEST_EFFORT stays
        // transparent and REQUIRED never gets a fabricated proof.
        return VerificationResult(
            attempted = false,
            verified = false,
            message = "External plugin action has no independent verification strategy"
        )
    }

    private suspend fun executeAction(request: CapabilityRequest): CapabilityResult {
        val resolved = resolveAction(request)
        val action = resolved.action ?: return resolved.failureResult()
        val config = action.config
        val pluginBundle = runCatching {
            val rawJson = config[KEY_BUNDLE_JSON].orEmpty()
            require(rawJson.isNotBlank()) { "Plugin configuration is missing" }
            PluginConfigParser.toBundle(PluginConfigParser.parseJsonStrict(rawJson))
        }.getOrElse {
            return CapabilityResult.failed(
                CapabilityErrorCode.INVALID_CONFIGURATION,
                "Plugin configuration is invalid",
                id
            )
        }
        val result = fireClient.fire(
            context = appContext,
            packageName = checkNotNull(config[KEY_PACKAGE]),
            receiverClass = checkNotNull(config[KEY_RECEIVER]),
            bundle = pluginBundle
        )
        val metadata = buildMap {
            put("pluginPackage", checkNotNull(config[KEY_PACKAGE]))
            put("pluginReceiver", checkNotNull(config[KEY_RECEIVER]))
            request.executionId?.let { put("correlationId", it) }
            put("pluginInstance", checkNotNull(config[KEY_INSTANCE]))
        }
        return when {
            result.timedOut -> CapabilityResult.failed(
                CapabilityErrorCode.TIMEOUT,
                "Plugin did not respond before the configured timeout",
                id
            ).copy(metadata = metadata)
            result.resultCode == LocaleContract.RESULT_CODE_OK -> CapabilityResult(
                status = CapabilityStatus.SUCCESS,
                backend = id,
                message = "Plugin reported successful completion",
                metadata = metadata
            )
            result.resultCode == LocaleContract.RESULT_CODE_PENDING -> CapabilityResult(
                status = CapabilityStatus.PARTIAL,
                backend = id,
                errorCode = CapabilityErrorCode.PLUGIN_INVALID_RESULT,
                message = "Plugin reported asynchronous work without a registered completion adapter",
                metadata = metadata
            )
            result.resultCode == LocaleContract.RESULT_CODE_CANCELED -> CapabilityResult(
                status = CapabilityStatus.CANCELLED,
                backend = id,
                errorCode = CapabilityErrorCode.CANCELLED,
                message = result.message?.takeIf { it.isNotBlank() } ?: "Plugin canceled or is no longer available",
                metadata = metadata
            )
            result.resultCode == LocaleContract.RESULT_CODE_FAILED -> CapabilityResult.failed(
                CapabilityErrorCode.UNKNOWN_ERROR,
                result.message?.takeIf { it.isNotBlank() } ?: "Plugin reported failure",
                id
            ).copy(metadata = metadata)
            else -> CapabilityResult.failed(
                CapabilityErrorCode.PLUGIN_INVALID_RESULT,
                "Plugin returned an unsupported result code",
                id
            ).copy(metadata = metadata)
        }
    }

    private suspend fun resolveAction(request: CapabilityRequest): ResolvedPluginAction {
        val workflowId = request.workflowId
        if (workflowId.isNullOrBlank() || request.actionId != ActionType.PLUGIN_FIRE.name) {
            return ResolvedPluginAction(error = "Plugin request is not bound to a persisted plugin action")
        }
        val expectedInstance = request.parameters[PARAM_INSTANCE].orEmpty()
        val automation = automationRepository.getAutomationById(workflowId)
            ?: return ResolvedPluginAction(error = "Owning workflow is no longer available")
        val action = automation.actions.singleOrNull { it.type == ActionType.PLUGIN_FIRE }
            ?: return ResolvedPluginAction(error = "Configured plugin action is no longer available")
        val config = action.config
        if (config[KEY_INSTANCE] != expectedInstance) {
            return ResolvedPluginAction(error = "Plugin instance reference does not match the saved workflow")
        }
        if (config[KEY_APPROVAL] != APPROVAL_VALUE) {
            return ResolvedPluginAction(error = "Plugin action has not been approved by the user")
        }
        val packageName = config[KEY_PACKAGE]
        val receiver = config[KEY_RECEIVER]
        if (packageName.isNullOrBlank() || receiver.isNullOrBlank()) {
            return ResolvedPluginAction(error = "Plugin component configuration is missing")
        }
        val snapshot = discoveryRegistry.snapshot().takeIf { it.refreshedAtMs > 0L }
            ?: discoveryRegistry.refresh()
        val descriptor = snapshot.descriptors.firstOrNull {
            it.type == PluginType.SETTING &&
                it.packageName == packageName &&
                it.receiver?.className == receiver
        } ?: return ResolvedPluginAction(error = "Configured plugin is not installed or visible")
        if (descriptor.compatibility != PluginCompatibilityStatus.COMPATIBLE) {
            return ResolvedPluginAction(error = "Configured plugin is not currently compatible")
        }
        return ResolvedPluginAction(action = action)
    }

    private data class ResolvedPluginAction(
        val action: com.nexaflow.domain.models.Action? = null,
        val error: String? = null
    ) {
        fun failureResult(): CapabilityResult = CapabilityResult.failed(
            errorCode = when {
                error?.contains("approved") == true -> CapabilityErrorCode.PLUGIN_NOT_APPROVED
                error?.contains("installed") == true || error?.contains("visible") == true -> CapabilityErrorCode.PLUGIN_MISSING_DEPENDENCY
                else -> CapabilityErrorCode.PLUGIN_UNAVAILABLE
            },
            message = error ?: "Plugin action is unavailable",
            backend = CapabilityBackendId.PLUGIN
        )
    }

    private companion object {
        const val PARAM_INSTANCE = "pluginInstance"
        const val KEY_INSTANCE = "pluginInstance"
        const val KEY_APPROVAL = "pluginApproval"
        const val APPROVAL_VALUE = "approved"
        const val KEY_PACKAGE = "package"
        const val KEY_RECEIVER = "receiver"
        const val KEY_BUNDLE_JSON = "bundleJson"
    }
}
