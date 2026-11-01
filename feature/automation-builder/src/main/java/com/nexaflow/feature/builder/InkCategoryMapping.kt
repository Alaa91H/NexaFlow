package com.nexaflow.feature.builder

import com.nexaflow.core.ui.theme.NexaFlowCategory

/**
 * The builder's catalogue categories predate Ink. These two functions are the
 * single place they are translated into the seven Ink hues, so the picker, the
 * builder nodes and the dashboard agree on what a category means.
 */
internal fun TriggerCategory.toInkCategory(): NexaFlowCategory = when (this) {
    TriggerCategory.SCHEDULE -> NexaFlowCategory.TIME
    TriggerCategory.POWER -> NexaFlowCategory.POWER
    TriggerCategory.SOUND -> NexaFlowCategory.SOUND
    TriggerCategory.DISPLAY -> NexaFlowCategory.TIME
    TriggerCategory.DEVICE -> NexaFlowCategory.LOCATION
    TriggerCategory.CONNECTIVITY -> NexaFlowCategory.CONNECTIVITY
    TriggerCategory.LOCATION -> NexaFlowCategory.LOCATION
    TriggerCategory.APPS -> NexaFlowCategory.NOTIFICATIONS
    TriggerCategory.COMMUNICATION -> NexaFlowCategory.NOTIFICATIONS
}

internal fun ActionCategory.toInkCategory(): NexaFlowCategory = when (this) {
    ActionCategory.DISPLAY -> NexaFlowCategory.TIME
    ActionCategory.SOUND -> NexaFlowCategory.SOUND
    ActionCategory.CONNECTIVITY -> NexaFlowCategory.CONNECTIVITY
    ActionCategory.MEDIA -> NexaFlowCategory.SOUND
    ActionCategory.NOTIFICATIONS -> NexaFlowCategory.NOTIFICATIONS
    ActionCategory.APPS -> NexaFlowCategory.NOTIFICATIONS
    ActionCategory.SYSTEM -> NexaFlowCategory.ADVANCED
    ActionCategory.BATTERY -> NexaFlowCategory.POWER
    ActionCategory.PLUGINS -> NexaFlowCategory.ADVANCED
}
