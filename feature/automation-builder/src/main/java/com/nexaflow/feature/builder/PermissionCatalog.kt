package com.nexaflow.feature.builder

import android.annotation.SuppressLint
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

    /** Runtime (system-dialog) permissions required by an action. */
    // ACCESS_LOCAL_NETWORK is an API 37 constant; requesting it on older
    // devices is a safe no-op, so the InlinedApi warning is suppressed here.
    @SuppressLint("InlinedApi")
    fun runtimePermissionsFor(actionType: ActionType): List<String> = when (actionType) {
        ActionType.SYSTEM_SEND_SMS -> listOf(android.Manifest.permission.SEND_SMS)
        ActionType.SYSTEM_FLASHLIGHT -> listOf(android.Manifest.permission.CAMERA)
        ActionType.SYSTEM_SEND_NOTIFICATION,
        ActionType.SYSTEM_SEND_REMINDER,
        ActionType.BATTERY_ALERTS,
        ActionType.BATTERY_CHARGING_NOTIFICATIONS -> listOf(android.Manifest.permission.POST_NOTIFICATIONS)
        ActionType.SYSTEM_LOCATION -> listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        // Android 17 (API 37) makes ACCESS_LOCAL_NETWORK mandatory to reach
        // LAN devices (home-assistant hubs, NAS, smart plugs). HTTP requests
        // to private IPs / mDNS names need it; public URLs do not, but the
        // permission is harmless to request up-front for the HTTP action.
        ActionType.SYSTEM_HTTP_REQUEST -> listOf(android.Manifest.permission.ACCESS_LOCAL_NETWORK)
        else -> emptyList()
    }

    /** Special (settings-screen) permission an action needs, if any. */
    fun specialPermissionFor(actionType: ActionType): SpecialPermission? = when (actionType) {
        ActionType.SYSTEM_BRIGHTNESS,
        ActionType.SYSTEM_SCREEN_ROTATION,
        ActionType.SYSTEM_SCREEN_TIMEOUT,
        ActionType.SYSTEM_STAY_AWAKE,
        ActionType.SYSTEM_AUTO_BRIGHTNESS,
        ActionType.SYSTEM_DARK_MODE,
        ActionType.SYSTEM_ANIMATIONS -> SpecialPermission.WRITE_SETTINGS
        ActionType.SYSTEM_DND,
        ActionType.SYSTEM_RINGER_MODE -> SpecialPermission.DND_ACCESS
        ActionType.ADVANCED_SHIZUKU -> SpecialPermission.SHIZUKU
        ActionType.ADVANCED_ROOT -> SpecialPermission.ROOT
        ActionType.APPLICATION_CLOSE_APP,
        ActionType.SYSTEM_MOBILE_DATA,
        ActionType.SYSTEM_NETWORK_MODE,
        ActionType.SYSTEM_HOTSPOT,
        ActionType.SYSTEM_NFC,
        ActionType.SYSTEM_POWER_SAVER,
        ActionType.SYSTEM_LOCK_SCREEN,
        ActionType.SYSTEM_OPEN_RECENTS,
        ActionType.SYSTEM_GO_HOME -> SpecialPermission.ELEVATED
        ActionType.SYSTEM_SET_RINGTONE -> SpecialPermission.WRITE_SETTINGS
        ActionType.SYSTEM_BLOCK_NOTIFICATION,
        ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS -> SpecialPermission.NOTIFICATION_ACCESS
        else -> null
    }

    /** Runtime permissions required by a trigger. */
    fun runtimePermissionsFor(triggerType: TriggerType): List<String> = when (triggerType) {
        TriggerType.SMS -> listOf(android.Manifest.permission.RECEIVE_SMS)
        TriggerType.LOCATION -> listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        TriggerType.CALENDAR -> listOf(android.Manifest.permission.READ_CALENDAR)
        TriggerType.NOTIFICATION -> listOf(android.Manifest.permission.POST_NOTIFICATIONS)
        TriggerType.BLUETOOTH_DEVICE -> listOf(android.Manifest.permission.BLUETOOTH_CONNECT)
        TriggerType.SENSOR -> listOf(android.Manifest.permission.ACTIVITY_RECOGNITION)
        else -> emptyList()
    }

    /** Special permission required by a trigger, if any. */
    fun specialPermissionFor(triggerType: TriggerType): SpecialPermission? = when (triggerType) {
        TriggerType.NOTIFICATION -> SpecialPermission.NOTIFICATION_ACCESS
        TriggerType.APPLICATION -> SpecialPermission.ACCESSIBILITY
        TriggerType.BLUETOOTH_DEVICE -> SpecialPermission.BLUETOOTH
        else -> null
    }

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
            val runtime = runtimePermissionsFor(trigger.type)
            val special = specialPermissionFor(trigger.type)
            if (runtime.isNotEmpty() || special != null) {
                result += PermissionRequirement(
                    owner = "trigger:${trigger.type.name}",
                    runtimePermissions = runtime,
                    special = special?.name
                )
            }
        }
        (actions + exitActions).forEach { action ->
            val runtime = runtimePermissionsFor(action.type)
            val special = specialPermissionFor(action.type)
            if (runtime.isNotEmpty() || special != null) {
                result += PermissionRequirement(
                    owner = "action:${action.type.name}",
                    runtimePermissions = runtime,
                    special = special?.name
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
