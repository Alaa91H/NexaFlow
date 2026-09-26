package com.nexaflow.core.automationcontrol.api

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.MaintenanceKind
import com.nexaflow.domain.models.MaintenanceNotificationPolicy
import com.nexaflow.domain.models.MaintenanceProfile
import com.nexaflow.domain.models.MaintenanceRecoveryPolicy
import com.nexaflow.domain.models.MaintenanceRetryPolicy
import com.nexaflow.domain.models.MaintenanceWindow
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode

data class AgentTaskMappingError(
    val code: String,
    val path: String
)

class AgentTaskMappingException(
    val error: AgentTaskMappingError
) : IllegalArgumentException("${error.code} at ${error.path}")

object AgentTaskMapper {

    fun toAutomation(
        draft: AgentTaskDraftV1,
        id: String,
        nowMillis: Long,
        existing: Automation? = null
    ): Automation {
        if (draft.schemaVersion != AgentTaskDraftV1.CURRENT_SCHEMA_VERSION) {
            throw AgentTaskMappingException(
                AgentTaskMappingError("unsupported_schema_version", "schemaVersion")
            )
        }

        return Automation(
            id = id,
            name = draft.name,
            description = draft.description,
            icon = draft.icon,
            iconColor = draft.iconColor,
            backgroundColor = draft.backgroundColor,
            category = draft.category,
            priority = draft.priority,
            enabled = draft.enabled,
            showToastOnToggle = draft.showToastOnToggle,
            triggers = draft.triggers.mapIndexed { index, trigger ->
                Trigger(
                    type = enumValue(trigger.type, "triggers[$index].type"),
                    config = trigger.config.toMap()
                )
            },
            triggerMatch = when (draft.triggerMatch) {
                AgentTriggerMatchV1.ANY -> TriggerMatchMode.ANY
                AgentTriggerMatchV1.ALL -> TriggerMatchMode.ALL
            },
            actions = draft.actions.mapIndexed { index, action ->
                action.toAction("actions[$index]")
            },
            constraints = draft.constraints.mapIndexed { index, constraint ->
                Constraint(
                    type = enumValue(constraint.type, "constraints[$index].type"),
                    config = constraint.config.toMap()
                )
            },
            exitActions = draft.exitActions.mapIndexed { index, action ->
                action.toAction("exitActions[$index]")
            },
            revertOnExit = draft.revertOnExit,
            cooldownSeconds = draft.cooldownSeconds,
            createdAt = existing?.createdAt ?: nowMillis,
            updatedAt = nowMillis,
            workflowVersion = Automation.CURRENT_WORKFLOW_VERSION,
            maintenanceProfile = draft.maintenance?.toMaintenanceProfile(),
            deepLinkToken = existing?.deepLinkToken
        )
    }

    private fun AgentActionDraftV1.toAction(path: String): Action = Action(
        type = enumValue(type, "$path.type"),
        config = config.toMap(),
        endBehavior = endBehavior?.let { behavior ->
            EndBehavior(
                mode = enumValue<EndMode>(behavior.mode.name, "$path.endBehavior.mode"),
                config = behavior.config.toMap()
            )
        }
    )

    private fun AgentMaintenanceDraftV1.toMaintenanceProfile(): MaintenanceProfile =
        MaintenanceProfile(
            kind = enumValue<MaintenanceKind>(kind, "maintenance.kind"),
            window = window?.let { value ->
                MaintenanceWindow(
                    startTime = value.startTime,
                    endTime = value.endTime,
                    allowedDays = value.allowedDays.toSet(),
                    minimumBatteryPercent = value.minimumBatteryPercent,
                    chargingRequired = value.chargingRequired,
                    unmeteredWifiRequired = value.unmeteredWifiRequired,
                    screenOffRequired = value.screenOffRequired,
                    deviceIdleRequired = value.deviceIdleRequired,
                    maximumThermalStatus = value.maximumThermalStatus,
                    minimumFreeStorageBytes = value.minimumFreeStorageBytes
                )
            },
            retryPolicy = MaintenanceRetryPolicy(
                maxAttempts = retryPolicy.maxAttempts,
                initialDelayMs = retryPolicy.initialDelayMs,
                backoffMultiplier = retryPolicy.backoffMultiplier,
                maxDelayMs = retryPolicy.maxDelayMs
            ),
            notificationPolicy = enumValue<MaintenanceNotificationPolicy>(
                notificationPolicy,
                "maintenance.notificationPolicy"
            ),
            dependencyAutomationIds = dependencyAutomationIds.toList(),
            recoveryPolicy = enumValue<MaintenanceRecoveryPolicy>(
                recoveryPolicy,
                "maintenance.recoveryPolicy"
            )
        )

    private inline fun <reified T : Enum<T>> enumValue(
        value: String,
        path: String
    ): T = runCatching { enumValueOf<T>(value) }.getOrElse {
        throw AgentTaskMappingException(
            AgentTaskMappingError("unknown_enum_value", path)
        )
    }
}
