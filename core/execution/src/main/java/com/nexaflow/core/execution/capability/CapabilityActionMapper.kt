package com.nexaflow.core.execution.capability

import com.nexaflow.core.rom.PrivilegedOperation
import com.nexaflow.core.rom.PrivilegedRunner
import com.nexaflow.core.rom.ShizukuShellBridge
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.ExecutionPolicy
import com.nexaflow.domain.capability.VerificationMode
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType

/**
 * Maps actions that have a semantics-preserving typed capability implementation.
 * Actions with arbitrary command semantics intentionally return null and continue
 * through their existing reviewed handlers.
 */
object CapabilityActionMapper {
    var privilegedBackendResolver: ((Action) -> CapabilityBackendId)? = null

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

        ActionType.SYSTEM_OPEN_WIFI_SETTINGS -> settingsRequest("WIFI", workflowId, executionId, action.type.name)
        ActionType.SYSTEM_OPEN_BLUETOOTH_SETTINGS -> settingsRequest("BLUETOOTH", workflowId, executionId, action.type.name)
        ActionType.SYSTEM_OPEN_LOCATION_SETTINGS -> settingsRequest("LOCATION", workflowId, executionId, action.type.name)
        ActionType.SYSTEM_OPEN_SOUND_SETTINGS -> settingsRequest("SOUND", workflowId, executionId, action.type.name)
        ActionType.SYSTEM_OPEN_DISPLAY_SETTINGS -> settingsRequest("DISPLAY", workflowId, executionId, action.type.name)
        ActionType.SYSTEM_OPEN_BATTERY_SETTINGS -> settingsRequest("BATTERY", workflowId, executionId, action.type.name)

        ActionType.SYSTEM_FORCE_STOP_APP,
        ActionType.APPLICATION_CLOSE_APP -> {
            val pkg = action.config["package"] ?: action.config["packageName"]
            if (pkg.isNullOrBlank()) {
                null
            } else {
                val backend = resolvePrivilegedBackend(action)
                CapabilityRequest(
                    capability = CapabilityId.PACKAGE_FORCE_STOP,
                    parameters = mapOf("packageName" to pkg),
                    policy = privilegedPolicy(backend),
                    verification = VerificationMode.BEST_EFFORT,
                    workflowId = workflowId,
                    executionId = executionId,
                    actionId = action.type.name
                )
            }
        }

        ActionType.SYSTEM_CLEAR_APP_DATA -> {
            val pkg = action.config["package"] ?: action.config["packageName"]
            if (pkg.isNullOrBlank()) {
                null
            } else {
                val backend = resolvePrivilegedBackend(action)
                CapabilityRequest(
                    capability = CapabilityId.PACKAGE_CLEAR_DATA,
                    parameters = mapOf("packageName" to pkg),
                    policy = privilegedPolicy(backend),
                    verification = VerificationMode.BEST_EFFORT,
                    workflowId = workflowId,
                    executionId = executionId,
                    actionId = action.type.name
                )
            }
        }

        ActionType.SYSTEM_SET_SETTING -> {
            val ns = (action.config["namespace"] ?: "GLOBAL").uppercase()
            val key = action.config["key"].orEmpty()
            val value = action.config["value"]
            if (key.isBlank() || value == null || key !in PrivilegedOperation.ALLOWED_SETTING_KEYS) {
                // Non-allowlisted or incomplete settings continue through the legacy handler.
                null
            } else {
                val backend = resolvePrivilegedBackend(action)
                CapabilityRequest(
                    capability = CapabilityId.SYSTEM_SETTING_WRITE,
                    parameters = mapOf(
                        "namespace" to ns,
                        "key" to key,
                        "value" to value
                    ),
                    policy = privilegedPolicy(backend),
                    verification = VerificationMode.REQUIRED,
                    workflowId = workflowId,
                    executionId = executionId,
                    actionId = action.type.name
                )
            }
        }

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

    private fun settingsRequest(
        page: String,
        workflowId: String?,
        executionId: String?,
        actionId: String
    ) = CapabilityRequest(
        capability = CapabilityId.SETTINGS_LAUNCH,
        parameters = mapOf("page" to page),
        verification = VerificationMode.NONE,
        workflowId = workflowId,
        executionId = executionId,
        actionId = actionId
    )

    private fun resolvePrivilegedBackend(action: Action): CapabilityBackendId {
        privilegedBackendResolver?.invoke(action)?.let { return it }
        val configured = action.config["backend"] ?: action.config["channel"]
        if (!configured.isNullOrBlank()) {
            val parsed = CapabilityBackendId.entries.firstOrNull { it.name.equals(configured, ignoreCase = true) }
            if (parsed != null) return parsed
        }
        return when {
            PrivilegedRunner.isShizukuGranted() && ShizukuShellBridge.isUserServiceBound -> CapabilityBackendId.SHIZUKU
            PrivilegedRunner.isRootAvailable() -> CapabilityBackendId.ROOT
            else -> CapabilityBackendId.SHIZUKU
        }
    }

    private fun privilegedPolicy(backend: CapabilityBackendId): ExecutionPolicy = ExecutionPolicy(
        allowedBackends = listOf(backend),
        preferredBackends = listOf(backend),
        allowPrivilegedBackends = true
    )
}
