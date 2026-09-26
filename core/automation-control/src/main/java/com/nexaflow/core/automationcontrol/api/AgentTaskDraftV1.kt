package com.nexaflow.core.automationcontrol.api

import kotlinx.serialization.Serializable

/**
 * Versioned public task contract for agents and future REST/MCP/A2A adapters.
 *
 * This type intentionally does not expose the persisted Automation model. The
 * adapter boundary is allowed to evolve independently from Room and execution
 * internals while keeping schemaVersion=1 stable.
 */
@Serializable
data class AgentTaskDraftV1(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val name: String,
    val description: String = "",
    val icon: String = "auto_awesome",
    val iconColor: Long = 0xFF0B57D0L,
    val backgroundColor: Long = 0xFFE3EEFAL,
    val category: String = "custom",
    val priority: Int = 1,
    val enabled: Boolean = false,
    val showToastOnToggle: Boolean = true,
    val triggers: List<AgentTriggerDraftV1> = emptyList(),
    val triggerMatch: AgentTriggerMatchV1 = AgentTriggerMatchV1.ANY,
    val actions: List<AgentActionDraftV1> = emptyList(),
    val constraints: List<AgentConstraintDraftV1> = emptyList(),
    val exitActions: List<AgentActionDraftV1> = emptyList(),
    val revertOnExit: Boolean = false,
    val cooldownSeconds: Int = 0,
    val maintenance: AgentMaintenanceDraftV1? = null
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class AgentTriggerDraftV1(
    val type: String,
    val config: Map<String, String> = emptyMap()
)

@Serializable
data class AgentActionDraftV1(
    val type: String,
    val config: Map<String, String> = emptyMap(),
    val endBehavior: AgentEndBehaviorDraftV1? = null
)

@Serializable
data class AgentConstraintDraftV1(
    val type: String,
    val config: Map<String, String> = emptyMap()
)

@Serializable
data class AgentEndBehaviorDraftV1(
    val mode: AgentEndModeV1 = AgentEndModeV1.LEAVE,
    val config: Map<String, String> = emptyMap()
)

@Serializable
enum class AgentEndModeV1 {
    LEAVE,
    REVERT,
    SET_VALUE,
    RERUN
}

@Serializable
enum class AgentTriggerMatchV1 {
    ANY,
    ALL
}

@Serializable
data class AgentMaintenanceDraftV1(
    val kind: String,
    val window: AgentMaintenanceWindowV1? = null,
    val retryPolicy: AgentMaintenanceRetryPolicyV1 = AgentMaintenanceRetryPolicyV1(),
    val notificationPolicy: String = "IMPORTANT_EVENTS",
    val dependencyAutomationIds: List<String> = emptyList(),
    val recoveryPolicy: String = "DEFAULT"
)

@Serializable
data class AgentMaintenanceWindowV1(
    val startTime: String? = null,
    val endTime: String? = null,
    val allowedDays: Set<Int> = emptySet(),
    val minimumBatteryPercent: Int? = null,
    val chargingRequired: Boolean = false,
    val unmeteredWifiRequired: Boolean = false,
    val screenOffRequired: Boolean = false,
    val deviceIdleRequired: Boolean = false,
    val maximumThermalStatus: Int? = null,
    val minimumFreeStorageBytes: Long? = null
)

@Serializable
data class AgentMaintenanceRetryPolicyV1(
    val maxAttempts: Int = 1,
    val initialDelayMs: Long = 15 * 60 * 1000L,
    val backoffMultiplier: Double = 2.0,
    val maxDelayMs: Long = 6 * 60 * 60 * 1000L
)
