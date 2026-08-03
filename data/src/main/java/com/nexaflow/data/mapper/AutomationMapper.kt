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
        conditions = converters.toConditionList(conditionsJson),
        actions = converters.toActionList(actionsJson),
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
        conditionsJson = converters.fromConditionList(conditions),
        actionsJson = converters.fromActionList(actions),
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
