package com.nexaflow.data.mapper

import com.nexaflow.core.database.AutomationEntity
import com.nexaflow.core.database.Converters
import com.nexaflow.domain.canonical.CanonicalV3WriteState
import com.nexaflow.domain.canonical.CanonicalWorkflowV3Codec
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerMatchMode

fun AutomationEntity.toDomain(): Automation {
    val converters = Converters()
    val legacy = Automation(
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

    val payload = canonicalWorkflowJson ?: return legacy
    val state = runCatching { CanonicalV3WriteState.valueOf(canonicalWriteState) }
        .getOrNull()
        ?: return legacy
    if (state == CanonicalV3WriteState.LEGACY_ONLY_DEGRADED) return legacy

    return runCatching {
        CanonicalWorkflowV3ReadMapper.toAutomation(
            CanonicalWorkflowV3Codec.decode(payload),
            legacy,
        )
    }.getOrDefault(legacy)
}

fun Automation.toEntity(): AutomationEntity {
    val converters = Converters()
    val canonicalWrite = CanonicalWorkflowV3Codec.prepareWrite(this)
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
        // T27 product wiring: every production save records both the payload
        // (when valid) and an explicit write state. A null payload can no
        // longer masquerade as an unexplained/pre-V3 row.
        canonicalWorkflowJson = canonicalWrite.payload,
        canonicalWriteState = canonicalWrite.state.name,
        canonicalWriteErrorCode = canonicalWrite.errorCode,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
