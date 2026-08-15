package com.nexaflow.core.execution.handler

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.delay

/** Flashlight, URL, status bar, lock screen, alarm, recents/home, stores, SMS, wait. */
class SystemActionsHandler : ActionHandler {
    override val supportedTypes: Set<ActionType> = setOf(
        ActionType.SYSTEM_FLASHLIGHT,
        ActionType.SYSTEM_OPEN_URL,
        ActionType.SYSTEM_EXPAND_STATUS_BAR,
        ActionType.SYSTEM_COLLAPSE_STATUS_BAR,
        ActionType.SYSTEM_LOCK_SCREEN,
        ActionType.SYSTEM_SET_ALARM,
        ActionType.SYSTEM_OPEN_RECENTS,
        ActionType.SYSTEM_GO_HOME,
        ActionType.SYSTEM_OPEN_PLAY_UPDATES,
        ActionType.SYSTEM_OPEN_GALAXY_STORE,
        ActionType.SYSTEM_OPEN_SETTINGS,
        ActionType.SYSTEM_SEND_SMS,
        ActionType.SYSTEM_WAIT,
        ActionType.SYSTEM_POWER_SAVER,
        ActionType.SYSTEM_VIBRATE,
        ActionType.SYSTEM_WAKE_SCREEN,
        ActionType.SYSTEM_CLIPBOARD_SET,
        ActionType.SYSTEM_OPEN_NOTIFICATIONS,
        ActionType.SYSTEM_OPEN_QUICK_SETTINGS
    )

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        return when (action.type) {
            ActionType.SYSTEM_FLASHLIGHT ->
                ctx.controller.setFlashlight(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_OPEN_URL ->
                ctx.controller.openUrl(action.config["url"] ?: "")
            ActionType.SYSTEM_EXPAND_STATUS_BAR ->
                ctx.controller.expandStatusBar()
            ActionType.SYSTEM_COLLAPSE_STATUS_BAR ->
                ctx.controller.collapseStatusBar()
            ActionType.SYSTEM_LOCK_SCREEN ->
                ctx.controller.lockScreenNow()
            ActionType.SYSTEM_SET_ALARM ->
                ctx.controller.setAlarm(
                    action.config["hour"]?.toIntOrNull() ?: 7,
                    action.config["minute"]?.toIntOrNull() ?: 0
                )
            ActionType.SYSTEM_OPEN_RECENTS ->
                ctx.controller.openRecents()
            ActionType.SYSTEM_GO_HOME ->
                ctx.controller.goHome()
            ActionType.SYSTEM_OPEN_PLAY_UPDATES ->
                ctx.controller.openPlayStoreUpdates()
            ActionType.SYSTEM_OPEN_GALAXY_STORE ->
                ctx.controller.openGalaxyStore()
            ActionType.SYSTEM_OPEN_SETTINGS ->
                ctx.controller.openSystemSettings(action.config["page"] ?: "")
            ActionType.SYSTEM_SEND_SMS ->
                ctx.controller.sendSms(action.config["number"] ?: "", action.config["text"] ?: "")
            ActionType.SYSTEM_WAIT -> {
                val seconds = action.config["seconds"]?.toIntOrNull()?.coerceIn(1, 3600) ?: 5
                delay(seconds * 1000L)
                SystemControlResult.ok("Waited ${seconds}s")
            }
            ActionType.SYSTEM_POWER_SAVER ->
                ctx.controller.setPowerSaver(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_VIBRATE ->
                ctx.controller.vibrate(action.config["seconds"]?.toIntOrNull() ?: 1)
            ActionType.SYSTEM_WAKE_SCREEN ->
                ctx.controller.wakeScreen()
            ActionType.SYSTEM_CLIPBOARD_SET ->
                ctx.controller.setClipboard(action.config["text"] ?: "")
            ActionType.SYSTEM_OPEN_NOTIFICATIONS ->
                ctx.controller.expandNotifications()
            ActionType.SYSTEM_OPEN_QUICK_SETTINGS ->
                ctx.controller.expandQuickSettings()
            else -> SystemControlResult.fail("Unsupported action ${action.type}")
        }
    }
}
