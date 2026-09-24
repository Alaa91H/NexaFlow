package com.nexaflow.feature.builder

import com.nexaflow.core.execution.compat.WorkflowRequirementCatalog
import com.nexaflow.core.execution.compat.WorkflowSpecialPermission
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.PermissionRequirement
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType

/**
 * Maps every trigger and action to the permissions it needs, so the builder can
 * collect all missing permissions of a task and request them immediately (the
 * "aggressive permission flow") instead of waiting for the user to tap a hint.
 *
 * Runtime permissions are requested through the system dialog; special
 * permissions (write settings, DND, notification listener, accessibility,
 * Shizuku/root, bluetooth) open their dedicated settings screen.
 */
object PermissionCatalog {
    fun runtimePermissionsFor(action: Action): List<String> =
        WorkflowRequirementCatalog.runtimePermissionsFor(action)

    /** Runtime (system-dialog) permissions required by an action. */
    fun runtimePermissionsFor(actionType: ActionType): List<String> =
        WorkflowRequirementCatalog.runtimePermissionsFor(actionType)

    /** Special (settings-screen) permission an action needs, if any. */
    fun specialPermissionFor(actionType: ActionType): SpecialPermission? =
        WorkflowRequirementCatalog.specialPermissionFor(actionType)?.toUiSpecialPermission()

    /** Runtime permissions required by a trigger type. */
    fun runtimePermissionsFor(triggerType: TriggerType): List<String> =
        WorkflowRequirementCatalog.runtimePermissionsFor(triggerType)

    /** Runtime permissions required by a trigger, including config-specific reads. */
    fun runtimePermissionsFor(trigger: Trigger): List<String> =
        WorkflowRequirementCatalog.runtimePermissionsFor(trigger)

    /** Special permission required by a trigger, if any. */
    fun specialPermissionFor(triggerType: TriggerType): SpecialPermission? =
        WorkflowRequirementCatalog.specialPermissionFor(triggerType)?.toUiSpecialPermission()

    /**
     * Aggregates every permission the task needs — runtime permissions first
     * (they can be requested right now with one system dialog), then special
     * permissions that need their settings screen.
     */
    fun requirementsFor(
        triggers: List<Trigger>,
        actions: List<Action>,
        exitActions: List<Action> = emptyList()
    ): List<PermissionRequirement> {
        val result = mutableListOf<PermissionRequirement>()
        triggers.forEach { trigger ->
            val requirement = WorkflowRequirementCatalog.permissionRequirementFor(trigger)
            if (requirement.runtimePermissions.isNotEmpty() || requirement.special != null) {
                result += PermissionRequirement(
                    owner = "trigger:${trigger.type.name}",
                    runtimePermissions = requirement.runtimePermissions,
                    special = requirement.special?.toUiSpecialPermission()?.name
                )
            }
        }
        (actions + exitActions + (actions + exitActions).mapNotNull { action ->
            action.endBehavior?.takeIf { it.mode == com.nexaflow.domain.models.EndMode.SET_VALUE }
                ?.let { action.withConfig(it.config) }
        }).forEach { action ->
            val requirement = WorkflowRequirementCatalog.permissionRequirementFor(action)
            if (requirement.runtimePermissions.isNotEmpty() || requirement.special != null) {
                result += PermissionRequirement(
                    owner = "action:${action.type.name}",
                    runtimePermissions = requirement.runtimePermissions,
                    special = requirement.special?.toUiSpecialPermission()?.name
                )
            }
        }
        return result
    }

    /** All distinct runtime permissions a task needs (for one dialog launch). */
    fun allRuntimePermissions(
        triggers: List<Trigger>,
        actions: List<Action>,
        exitActions: List<Action> = emptyList()
    ): Array<String> = requirementsFor(triggers, actions, exitActions)
        .flatMap { it.runtimePermissions }
        .distinct()
        .toTypedArray()

    /** All distinct special permissions a task needs (for the explain flow). */
    fun allSpecialPermissions(
        triggers: List<Trigger>,
        actions: List<Action>,
        exitActions: List<Action> = emptyList()
    ): List<SpecialPermission> = requirementsFor(triggers, actions, exitActions)
        .mapNotNull { it.special }
        .distinct()
        .mapNotNull { name -> SpecialPermission.entries.firstOrNull { it.name == name } }
}

private fun WorkflowSpecialPermission.toUiSpecialPermission(): SpecialPermission = when (this) {
    WorkflowSpecialPermission.WRITE_SETTINGS -> SpecialPermission.WRITE_SETTINGS
    WorkflowSpecialPermission.DND_ACCESS -> SpecialPermission.DND_ACCESS
    WorkflowSpecialPermission.NOTIFICATION_ACCESS -> SpecialPermission.NOTIFICATION_ACCESS
    WorkflowSpecialPermission.ACCESSIBILITY -> SpecialPermission.ACCESSIBILITY
    WorkflowSpecialPermission.SHIZUKU -> SpecialPermission.SHIZUKU
    WorkflowSpecialPermission.ROOT -> SpecialPermission.ROOT
    WorkflowSpecialPermission.ELEVATED -> SpecialPermission.ELEVATED
    WorkflowSpecialPermission.EXACT_ALARM -> SpecialPermission.EXACT_ALARM
}
