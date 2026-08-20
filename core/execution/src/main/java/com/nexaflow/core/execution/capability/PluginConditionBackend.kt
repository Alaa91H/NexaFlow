package com.nexaflow.core.execution.capability

import android.content.Context
import com.nexaflow.core.execution.plugin.PluginConditionClient
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
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.repositories.AutomationRepository

/** Static descriptor for safe, persisted third-party condition queries. */
object PluginConditionCapabilityCatalog {
    fun descriptors(): List<CapabilityDescriptor> = listOf(
        CapabilityDescriptor(
            id = CapabilityId.PLUGIN_CONDITION_READ,
            displayName = "Read configured external plugin condition",
            description = "Queries one user-approved Locale-compatible condition plugin through an explicit adapter",
            risk = CapabilityRiskLevel.LOW,
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
 * Locale condition adapter. `query` exposes the precise typed condition state;
 * `execute` only adapts that state into the one central capability runtime.
 */
class PluginConditionBackend(
    context: Context,
    private val automationRepository: AutomationRepository,
    private val discoveryRegistry: PluginDiscoveryRegistry,
    private val queryClient: PluginConditionClient = PluginConditionClient()
) : CapabilityBackend {
    override val id: CapabilityBackendId = CapabilityBackendId.PLUGIN
    override val supportedCapabilities: Set<CapabilityId> = setOf(CapabilityId.PLUGIN_CONDITION_READ)

    private val appContext = context.applicationContext

    override suspend fun availability(request: CapabilityRequest): BackendAvailability {
        if (request.capability !in supportedCapabilities) {
            return BackendAvailability(id, CapabilityAvailability.UNSUPPORTED, "Plugin backend does not implement this capability")
        }
        val resolved = resolveCondition(request)
        return if (resolved.condition == null) {
            BackendAvailability(id, CapabilityAvailability.UNAVAILABLE, resolved.error ?: "Plugin condition is unavailable")
        } else {
            BackendAvailability(id, CapabilityAvailability.AVAILABLE)
        }
    }

    override suspend fun execute(request: CapabilityRequest): CapabilityResult = query(request)

    /**
     * Evaluates the persisted condition bound to [request] without collapsing
     * `Unknown`/`Unavailable` into `Unsatisfied`.
     */
    suspend fun query(request: CapabilityRequest): CapabilityResult {
        if (request.capability !in supportedCapabilities) {
            return CapabilityResult.unsupported("Plugin backend does not implement this capability")
        }
        val resolved = resolveCondition(request)
        val condition = resolved.condition ?: return resolved.failureResult()
        val bundle = runCatching {
            PluginConfigParser.toBundle(PluginConfigParser.parseJsonStrict(condition.config[KEY_BUNDLE_JSON].orEmpty()))
        }.getOrElse {
            return CapabilityResult.failed(
                CapabilityErrorCode.INVALID_CONFIGURATION,
                "Plugin condition configuration is invalid",
                id
            ).copy(conditionResult = ConditionResult.Error("Plugin configuration is invalid"))
        }
        val result = queryClient.query(
            context = appContext,
            packageName = checkNotNull(condition.config[KEY_PACKAGE]),
            receiverClass = checkNotNull(condition.config[KEY_RECEIVER]),
            bundle = bundle
        )
        val metadata = buildMap {
            put("pluginPackage", checkNotNull(condition.config[KEY_PACKAGE]))
            put("pluginReceiver", checkNotNull(condition.config[KEY_RECEIVER]))
            put("pluginInstance", checkNotNull(condition.config[KEY_INSTANCE]))
            put("conditionState", result.condition.wireState())
            result.resultCode?.let { put("conditionResultCode", it.toString()) }
            if (result.timedOut) put("timedOut", "true")
            request.executionId?.let { put("correlationId", it) }
        }
        return when (val state = result.condition) {
            ConditionResult.Satisfied -> CapabilityResult(
                status = CapabilityStatus.SUCCESS,
                backend = id,
                message = "Plugin condition is satisfied",
                metadata = metadata,
                conditionResult = state
            )
            ConditionResult.Unsatisfied -> CapabilityResult(
                status = CapabilityStatus.SUCCESS,
                backend = id,
                message = "Plugin condition is unsatisfied",
                metadata = metadata,
                conditionResult = state
            )
            ConditionResult.Unknown -> CapabilityResult(
                status = CapabilityStatus.PARTIAL,
                backend = id,
                message = "Plugin condition state is unknown",
                metadata = metadata,
                conditionResult = state
            )
            ConditionResult.Unavailable -> CapabilityResult.failed(
                CapabilityErrorCode.PLUGIN_UNAVAILABLE,
                "Plugin condition receiver is unavailable",
                id
            ).copy(metadata = metadata, conditionResult = state)
            is ConditionResult.Error -> CapabilityResult.failed(
                CapabilityErrorCode.PLUGIN_INVALID_RESULT,
                state.reason,
                id
            ).copy(metadata = metadata, conditionResult = state)
        }
    }

    private suspend fun resolveCondition(request: CapabilityRequest): ResolvedPluginCondition {
        val workflowId = request.workflowId
        if (workflowId.isNullOrBlank() || request.actionId != ACTION_ID_CONSTRAINT) {
            return ResolvedPluginCondition(error = "Plugin condition request is not bound to a persisted plugin constraint")
        }
        val expectedInstance = request.parameters[PARAM_INSTANCE].orEmpty()
        if (expectedInstance.isBlank()) {
            return ResolvedPluginCondition(error = "Plugin condition instance reference is missing")
        }
        val automation = automationRepository.getAutomationById(workflowId)
            ?: return ResolvedPluginCondition(error = "Owning workflow is no longer available")
        val condition = automation.constraints.singleOrNull {
            it.type == ConstraintType.PLUGIN && it.config[KEY_INSTANCE] == expectedInstance
        } ?: return ResolvedPluginCondition(error = "Configured plugin condition is unavailable or ambiguous")
        if (condition.config[KEY_APPROVAL] != APPROVAL_VALUE) {
            return ResolvedPluginCondition(error = "Plugin condition has not been approved by the user")
        }
        val packageName = condition.config[KEY_PACKAGE]
        val receiver = condition.config[KEY_RECEIVER]
        if (packageName.isNullOrBlank() || receiver.isNullOrBlank()) {
            return ResolvedPluginCondition(error = "Plugin condition component configuration is missing")
        }
        val snapshot = discoveryRegistry.snapshot().takeIf { it.refreshedAtMs > 0L }
            ?: discoveryRegistry.refresh()
        val descriptor = snapshot.descriptors.firstOrNull {
            it.type == PluginType.CONDITION &&
                it.packageName == packageName &&
                it.receiver?.className == receiver
        } ?: return ResolvedPluginCondition(error = "Configured condition plugin is not installed or visible")
        if (descriptor.compatibility != PluginCompatibilityStatus.COMPATIBLE) {
            return ResolvedPluginCondition(error = "Configured condition plugin is not currently compatible")
        }
        return ResolvedPluginCondition(condition = condition)
    }

    private data class ResolvedPluginCondition(
        val condition: com.nexaflow.domain.models.Constraint? = null,
        val error: String? = null
    ) {
        fun failureResult(): CapabilityResult {
            val code = when {
                error?.contains("approved") == true -> CapabilityErrorCode.PLUGIN_NOT_APPROVED
                error?.contains("installed") == true || error?.contains("visible") == true ->
                    CapabilityErrorCode.PLUGIN_MISSING_DEPENDENCY
                else -> CapabilityErrorCode.PLUGIN_UNAVAILABLE
            }
            return CapabilityResult.failed(code, error ?: "Plugin condition is unavailable", CapabilityBackendId.PLUGIN)
                .copy(conditionResult = ConditionResult.Unavailable)
        }
    }

    private fun ConditionResult.wireState(): String = when (this) {
        ConditionResult.Satisfied -> "SATISFIED"
        ConditionResult.Unsatisfied -> "UNSATISFIED"
        ConditionResult.Unknown -> "UNKNOWN"
        ConditionResult.Unavailable -> "UNAVAILABLE"
        is ConditionResult.Error -> "ERROR"
    }

    private companion object {
        const val ACTION_ID_CONSTRAINT = "PLUGIN_CONDITION"
        const val PARAM_INSTANCE = "pluginInstance"
        const val KEY_INSTANCE = "pluginInstance"
        const val KEY_APPROVAL = "pluginApproval"
        const val APPROVAL_VALUE = "approved"
        const val KEY_PACKAGE = "package"
        const val KEY_RECEIVER = "receiver"
        const val KEY_BUNDLE_JSON = "bundleJson"
    }
}
