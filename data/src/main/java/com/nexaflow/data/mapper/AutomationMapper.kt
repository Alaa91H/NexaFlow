package com.nexaflow.data.mapper

import com.nexaflow.core.database.AutomationEntity
import com.nexaflow.core.database.Converters
import com.nexaflow.domain.models.Automation

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
        triggers = converters.toTriggerList(triggersJson),
        actions = converters.toActionList(actionsJson),
        exitActions = converters.toActionList(exitActionsJson),
        revertOnExit = revertOnExit,
        createdAt = createdAt,
        updatedAt = updatedAt
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
        triggersJson = converters.fromTriggerList(triggers),
        actionsJson = converters.fromActionList(actions),
        exitActionsJson = converters.fromActionList(exitActions),
        revertOnExit = revertOnExit,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
