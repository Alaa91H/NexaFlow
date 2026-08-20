package com.nexaflow.core.execution.capability

import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.VerificationMode
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType

/**
 * Maps only legacy actions that have a semantics-preserving public capability
 * implementation. Actions with system, Root, Shizuku, Accessibility or arbitrary
 * command semantics intentionally return null and continue through their existing
 * reviewed handlers.
 */
object CapabilityActionMapper {
    fun requestFor(
        action: Action,
        workflowId: String?,
        executionId: String?
    ): CapabilityRequest? = when (action.type) {
        ActionType.SYSTEM_OPEN_URL -> CapabilityRequest(
            capability = CapabilityId.INTENT_LAUNCH,
            parameters = mapOf("url" to action.config["url"].orEmpty()),
            // Android intent delivery is a user-visible handoff. Its start is the
            // observable boundary; completion belongs to the receiving app.
            verification = VerificationMode.NONE,
            workflowId = workflowId,
            executionId = executionId,
            actionId = action.type.name
        )

        ActionType.SYSTEM_OPEN_SETTINGS -> CapabilityRequest(
            capability = CapabilityId.SETTINGS_LAUNCH,
            parameters = mapOf("page" to action.config["page"].orEmpty()),
            verification = VerificationMode.NONE,
            workflowId = workflowId,
            executionId = executionId,
            actionId = action.type.name
        )

        ActionType.PLUGIN_FIRE -> {
            val instance = action.config["pluginInstance"]
            val approved = action.config["pluginApproval"] == "approved"
            if (instance.isNullOrBlank() || !approved) {
                // Existing saved automations predate the opaque reference. Keep
                // their reviewed handler path until the user reconfigures them.
                null
            } else {
                CapabilityRequest(
                    capability = CapabilityId.PLUGIN_ACTION,
                    // The protocol Bundle remains in persisted action config.
                    // Only a validated opaque reference crosses this boundary.
                    parameters = mapOf("pluginInstance" to instance),
                    verification = VerificationMode.BEST_EFFORT,
                    workflowId = workflowId,
                    executionId = executionId,
                    actionId = action.type.name
                )
            }
        }

        else -> null
    }
}
