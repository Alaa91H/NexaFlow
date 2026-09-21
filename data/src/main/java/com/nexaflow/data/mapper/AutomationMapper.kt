package com.nexaflow.data.mapper

import com.nexaflow.core.database.AutomationEntity
import com.nexaflow.core.database.Converters
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerMatchMode

fun AutomationEntity.toDomain(): Automation {
    val converters = Converters()
    return Automation(
        id = id,
        name = name,
        description = description,
        icon = icon,
        iconColor = iconColor,
        backgroundColor = backgroundColor,
        category = category,
        priority = priority,
        enabled = enabled,
        showToastOnToggle = showToastOnToggle,
        triggers = converters.toTriggerList(triggersJson),
        actions = converters.toActionList(actionsJson),
        constraints = converters.toConstraintList(constraintsJson),
        exitActions = converters.toActionList(exitActionsJson),
        revertOnExit = revertOnExit,
        cooldownSeconds = cooldownSeconds,
        createdAt = createdAt,
        updatedAt = updatedAt,
        workflowVersion = workflowVersion,
        maintenanceProfile = converters.toMaintenanceProfile(maintenanceJson),
        deepLinkToken = deepLinkToken,
        triggerMatch = runCatching { TriggerMatchMode.valueOf(triggerMatch) }
            .getOrDefault(TriggerMatchMode.ANY)
    )
}

fun Automation.toEntity(): AutomationEntity {
    val converters = Converters()
    return AutomationEntity(
        id = id,
        name = name,
        description = description,
        icon = icon,
        iconColor = iconColor,
        backgroundColor = backgroundColor,
        category = category,
        priority = priority,
        enabled = enabled,
        showToastOnToggle = showToastOnToggle,
        triggersJson = converters.fromTriggerList(triggers),
        actionsJson = converters.fromActionList(actions),
        constraintsJson = converters.fromConstraintList(constraints),
        exitActionsJson = converters.fromActionList(exitActions),
        revertOnExit = revertOnExit,
        cooldownSeconds = cooldownSeconds,
        workflowVersion = workflowVersion,
        maintenanceJson = converters.fromMaintenanceProfile(maintenanceProfile),
        deepLinkToken = deepLinkToken,
        triggerMatch = triggerMatch.name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
