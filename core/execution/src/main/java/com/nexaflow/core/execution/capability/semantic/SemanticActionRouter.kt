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
        ActionType.SYSTEM_DATA_SAVER
    )

    fun isRouted(action: Action): Boolean = action.type in ROUTED_TYPES

    /**
     * Builds a typed request, or null when the action must remain on its
     * legacy handler (unsupported type here or invalid configuration that the
     * legacy handler already reports in its own terms).
     */
    fun requestFor(
        action: Action,
        workflowId: String?,
        executionId: String?,
        allowPrivilegedStrategies: Boolean
    ): TypedOperationRequest? {
        if (!isRouted(action)) return null
        val operation = when (action.type) {
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
            else -> return null
        }
        // Legacy toggle configs stored either "enabled" or omitted; the omit
        // case predates the parameter and was displayed to users as a toggle
        // defaulting to ON. Absent value is a *parse error* for new workflows
        // only when no marker exists; marker "configVersion" >= 2 means the
        // builder always persisted an explicit value.
        val enabled = parseEnabled(action.config["enabled"], action.config["configVersion"])
            ?: return null
        val parameters = when (operation) {
            SemanticOperationId.BRIGHTNESS_SET -> mapOf(
                "value" to (action.config["value"] ?: return null)
            )
            SemanticOperationId.SCREEN_TIMEOUT_SET -> mapOf(
                "seconds" to (action.config["seconds"] ?: return null)
            )
            else -> mapOf("enabled" to enabled)
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
     * Executes when the action maps to a semantic operation; returns null so
     * callers fall back to the legacy handler untouched.
     */
    suspend fun routeIfSupported(action: Action, workflowId: String?, executionId: String?): SystemControlResult? {
        val request = SemanticActionMapper.requestFor(
            action, workflowId, executionId, privilegedPolicyEnabled()
        ) ?: return null
        val outcome = router.execute(request)
        return when (outcome.status) {
            OperationOutcomeStatus.SUCCESS,
            OperationOutcomeStatus.PENDING_USER_ACTION -> SystemControlResult.ok(outcome.message)
            OperationOutcomeStatus.PARTIAL -> SystemControlResult.ok(outcome.message)
            else -> SystemControlResult.fail(outcome.message)
        }
    }
}
