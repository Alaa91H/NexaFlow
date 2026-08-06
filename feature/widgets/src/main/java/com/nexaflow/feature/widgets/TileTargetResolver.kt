package com.nexaflow.feature.widgets

import com.nexaflow.domain.models.Automation

/**
 * Pure resolution logic for the quick-settings tiles. Each of the four tiles
 * controls one task (routine): an explicit user binding wins, otherwise the
 * slot picks the Nth enabled task so tiles stay useful out of the box.
 */
object TileTargetResolver {

    /**
     * Resolves the automation a tile slot should control.
     *
     * @param automations all tasks, ordered as stored.
     * @param slot        1-based tile slot (1..4).
     * @param boundId     optional automation id the user pinned to this slot.
     * @return the automation to control, or null when there are no tasks.
     */
    fun resolveTarget(
        automations: List<Automation>,
        slot: Int,
        boundId: String?
    ): Automation? {
        if (!boundId.isNullOrBlank()) {
            automations.firstOrNull { it.id == boundId }?.let { return it }
        }
        val enabled = automations.filter { it.enabled }
        return enabled.getOrNull(slot - 1) ?: automations.firstOrNull()
    }
}
