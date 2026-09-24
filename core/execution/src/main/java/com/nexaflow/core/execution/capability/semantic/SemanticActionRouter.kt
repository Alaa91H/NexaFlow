package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType

/**
 * Bridges legacy serialized [Action]s to the semantic operation layer.
 *
 * Parsing rules (contract, requirement 7):
 * - A missing *required* boolean is an error, never a silent dangerous
 *   default (`toBoolean() ?: true` is forbidden here).
 * - Unknown/unsupported action types return null so the reviewed legacy
 *   handler path keeps serving them; migration stays incremental.
 */
object SemanticActionMapper {

    /** The action types migrated to the semantic router in this phase. */
    private val ROUTED_TYPES: Set<ActionType> = setOf(
        ActionType.SYSTEM_WIFI,
        ActionType.SYSTEM_BLUETOOTH,
        ActionType.SYSTEM_LOCATION,
        ActionType.SYSTEM_AIRPLANE_MODE,
        ActionType.SYSTEM_SCREEN_ROTATION,
        ActionType.SYSTEM_BRIGHTNESS,
        ActionType.SYSTEM_SCREEN_TIMEOUT,
        ActionType.SYSTEM_DND,
        ActionType.SYSTEM_NFC,
        ActionType.SYSTEM_HOTSPOT,
        ActionType.SYSTEM_MOBILE_DATA,
        ActionType.SYSTEM_DATA_SAVER,
        ActionType.APPLICATION_CLOSE_APP,
        ActionType.SYSTEM_FORCE_STOP_APP,
        ActionType.SYSTEM_CLEAR_APP_DATA,
        ActionType.SYSTEM_DISABLE_APP,
        ActionType.SYSTEM_ENABLE_APP
    )

    fun operationFor(actionType: ActionType): SemanticOperationId? = when (actionType) {
        ActionType.SYSTEM_WIFI -> SemanticOperationId.WIFI_SET_STATE
        ActionType.SYSTEM_BLUETOOTH -> SemanticOperationId.BLUETOOTH_SET_STATE
        ActionType.SYSTEM_LOCATION -> SemanticOperationId.LOCATION_SET_STATE
        ActionType.SYSTEM_AIRPLANE_MODE -> SemanticOperationId.AIRPLANE_MODE_SET_STATE
        ActionType.SYSTEM_SCREEN_ROTATION -> SemanticOperationId.ROTATION_SET_STATE
        ActionType.SYSTEM_BRIGHTNESS -> SemanticOperationId.BRIGHTNESS_SET
        ActionType.SYSTEM_SCREEN_TIMEOUT -> SemanticOperationId.SCREEN_TIMEOUT_SET
        ActionType.SYSTEM_DND -> SemanticOperationId.DND_SET_STATE
        ActionType.SYSTEM_NFC -> SemanticOperationId.NFC_SET_STATE
        ActionType.SYSTEM_HOTSPOT -> SemanticOperationId.HOTSPOT_SET_STATE
        ActionType.SYSTEM_MOBILE_DATA -> SemanticOperationId.MOBILE_DATA_SET_STATE
        ActionType.SYSTEM_DATA_SAVER -> SemanticOperationId.DATA_SAVER_SET_STATE
        ActionType.APPLICATION_CLOSE_APP -> SemanticOperationId.PACKAGE_FORCE_STOP
        ActionType.SYSTEM_FORCE_STOP_APP -> SemanticOperationId.PACKAGE_FORCE_STOP
        ActionType.SYSTEM_CLEAR_APP_DATA -> SemanticOperationId.PACKAGE_CLEAR_DATA
        ActionType.SYSTEM_DISABLE_APP -> SemanticOperationId.PACKAGE_SET_ENABLED_STATE
        ActionType.SYSTEM_ENABLE_APP -> SemanticOperationId.PACKAGE_SET_ENABLED_STATE
        else -> null
    }

    fun isRouted(action: Action): Boolean = operationFor(action.type) != null

    /**
     * Builds a typed request, or null when the action is not routed or its
     * configuration cannot be represented by the typed semantic contract.
     */
    fun requestFor(
        action: Action,
        workflowId: String?,
        executionId: String?,
        allowPrivilegedStrategies: Boolean
    ): TypedOperationRequest? {
        val operation = operationFor(action.type) ?: return null
        // Package operations: a validated package name is the primary
        // parameter. Historical configs used `package`/`packageName`; the
        // alias resolution is explicit and a missing name fails the mapping
        // so the legacy handler keeps reporting in its own terms.
        if (operation == SemanticOperationId.PACKAGE_FORCE_STOP ||
            operation == SemanticOperationId.PACKAGE_CLEAR_DATA ||
            operation == SemanticOperationId.PACKAGE_SET_ENABLED_STATE
        ) {
            val pkg = action.config["package"] ?: action.config["packageName"]
            if (pkg.isNullOrBlank()) return null
            val parameters = if (operation == SemanticOperationId.PACKAGE_SET_ENABLED_STATE) {
                val enabled = parseEnabled(action.config["enabled"], action.config["configVersion"])
                    ?: return null
                mapOf("packageName" to pkg, "enabled" to enabled)
            } else {
                mapOf("packageName" to pkg)
            }
            // Enable/disable aliases carry their intent in the action type
            // itself for every pre-configVersion automation.
            val resolvedParameters = if (
                operation == SemanticOperationId.PACKAGE_SET_ENABLED_STATE &&
                action.config["enabled"] == null
            ) {
                parameters + ("enabled" to if (action.type == ActionType.SYSTEM_ENABLE_APP) "true" else "false")
            } else {
                parameters
            }
            return TypedOperationRequest(
                operation = operation,
                parameters = resolvedParameters,
                allowPrivilegedStrategies = allowPrivilegedStrategies,
                workflowId = workflowId,
                executionId = executionId
            )
        }
        val parameters = when (operation) {
            SemanticOperationId.BRIGHTNESS_SET -> mapOf(
                "value" to (action.config["value"] ?: return null)
            )
            SemanticOperationId.SCREEN_TIMEOUT_SET -> mapOf(
                "seconds" to (action.config["seconds"] ?: return null)
            )
            else -> {
                // Boolean compatibility rules apply only to toggle operations.
                // Scalar writes use their own required value and must not be
                // rejected merely because they do not have an "enabled" key.
                val enabled = parseEnabled(
                    action.config["enabled"],
                    action.config["configVersion"]
                ) ?: return null
                mapOf("enabled" to enabled)
            }
        }
        return TypedOperationRequest(
            operation = operation,
            parameters = parameters,
            allowPrivilegedStrategies = allowPrivilegedStrategies,
            workflowId = workflowId,
            executionId = executionId
        )
    }

    /**
     * Strict boolean parsing. Returns null when the value cannot be trusted:
     * absent for legacy automation is accepted as the historical ON default
     * only when the automation predates explicit serialization (no
     * configVersion marker); any present-but-unparseable value is rejected.
     */
    fun parseEnabled(raw: String?, configVersion: String?): String? = when {
        raw == null && configVersion == null -> "true" // documented legacy default
        raw == null -> null
        raw.equals("true", ignoreCase = true) || raw == "1" -> "true"
        raw.equals("false", ignoreCase = true) || raw == "0" -> "false"
        else -> null
    }
}

/**
 * Execution façade for handlers: routes a legacy action through the semantic
 * layer and normalizes the outcome back to the result type every handler and
 * the history timeline already understand.
 */
class SemanticActionRouter(
    private val router: CapabilityRouter,
    private val privilegedPolicyEnabled: () -> Boolean = { false }
) {
    /**
     * Side-effect-free planning through the same router used by execution.
     * A routed action with invalid typed configuration returns an explicit
     * INVALID_CONFIGURATION plan instead of silently falling back to legacy.
     */
    suspend fun planIfSupported(
        action: Action,
        workflowId: String?,
        executionId: String?
    ): OperationExecutionPlan? {
        val operation = SemanticActionMapper.operationFor(action.type) ?: return null
        val request = SemanticActionMapper.requestFor(
            action,
            workflowId,
            executionId,
            // Planning is read-only, so include privileged candidates even
            // before a grant exists. Their live availability still decides
            // readiness, and execution re-applies the real policy immediately
            // before the side effect.
            allowPrivilegedStrategies = true
        ) ?: return OperationExecutionPlan(
            operation = operation,
            status = OperationPlanStatus.INVALID_CONFIGURATION,
            message = "Action configuration cannot be mapped to the typed operation contract",
            errorCode = com.nexaflow.domain.capability.CapabilityErrorCode.INVALID_CONFIGURATION
        )
        return router.plan(request)
    }

    /**
     * Executes when the action maps to a semantic operation; returns null so
     * callers fall back to the legacy handler untouched.
     */
    suspend fun routeIfSupported(action: Action, workflowId: String?, executionId: String?): SystemControlResult? {
        val request = SemanticActionMapper.requestFor(
            action, workflowId, executionId, privilegedPolicyEnabled()
        ) ?: return null
        return router.execute(request).toSystemControlResult()
    }
}

/**
 * Adapts the rich semantic lifecycle to the legacy boolean action contract.
 * Only a completed SUCCESS is successful. In particular, a Settings hand-off
 * offered as a fallback for a requested state change, or a missing
 * Shizuku/Root grant, remains pending/non-successful instead of being recorded
 * as if the requested device state had already changed.
 */
internal fun OperationOutcome.toSystemControlResult(): SystemControlResult =
    SystemControlResult(
        success = status == OperationOutcomeStatus.SUCCESS,
        message = message,
        executionChannel = strategy?.name,
        errorCode = errorCode?.name,
        verificationAttempted = verification?.attempted == true,
        verified = verification?.takeIf { it.attempted }?.verified
    )
