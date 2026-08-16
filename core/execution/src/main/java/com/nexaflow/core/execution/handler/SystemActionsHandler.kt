package com.nexaflow.core.execution.handler

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.delay

private fun Boolean.bool(): String = if (this) "1" else "0"

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
        ActionType.SYSTEM_OPEN_QUICK_SETTINGS,
        ActionType.SYSTEM_SET_SETTING,
        ActionType.SYSTEM_SCREENSHOT,
        ActionType.SYSTEM_INPUT_TEXT,
        ActionType.SYSTEM_KEY_EVENT,
        ActionType.SYSTEM_INPUT_TAP,
        ActionType.SYSTEM_INPUT_SWIPE,
        ActionType.SYSTEM_COLOR_INVERSION,
        ActionType.SYSTEM_GRAYSCALE,
        ActionType.SYSTEM_EXTRA_DIM,
        ActionType.SYSTEM_NIGHT_LIGHT,
        ActionType.SYSTEM_HAPTIC_FEEDBACK,
        ActionType.SYSTEM_SOUND_EFFECTS,
        ActionType.SYSTEM_FORCE_STOP_APP,
        ActionType.SYSTEM_CLEAR_APP_DATA
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
            ActionType.SYSTEM_SET_SETTING ->
                ctx.controller.writeSetting(
                    action.config["namespace"] ?: "GLOBAL",
                    action.config["key"] ?: "",
                    action.config["value"] ?: ""
                )
            ActionType.SYSTEM_SCREENSHOT ->
                ctx.controller.screenshot(action.config["filename"] ?: "")
            ActionType.SYSTEM_INPUT_TEXT ->
                ctx.controller.inputText(action.config["text"] ?: "")
            ActionType.SYSTEM_KEY_EVENT ->
                ctx.controller.keyEvent(action.config["key"] ?: "")
            ActionType.SYSTEM_INPUT_TAP ->
                ctx.controller.inputTap(
                    action.config["x"]?.toIntOrNull() ?: 0,
                    action.config["y"]?.toIntOrNull() ?: 0
                )
            ActionType.SYSTEM_INPUT_SWIPE ->
                ctx.controller.inputSwipe(
                    action.config["x1"]?.toIntOrNull() ?: 0,
                    action.config["y1"]?.toIntOrNull() ?: 0,
                    action.config["x2"]?.toIntOrNull() ?: 0,
                    action.config["y2"]?.toIntOrNull() ?: 0,
                    action.config["durationMs"]?.toIntOrNull() ?: 300
                )
            ActionType.SYSTEM_COLOR_INVERSION ->
                ctx.controller.writeSetting(
                    "SECURE", "accessibility_display_inversion_enabled",
                    (action.config["enabled"]?.toBoolean() ?: true).bool()
                )
            ActionType.SYSTEM_GRAYSCALE -> {
                val on = action.config["enabled"]?.toBoolean() ?: true
                // Grayscale = daltonizer monochromacy (mode 12) + enable flag.
                ctx.controller.writeSetting(
                    "SECURE", "accessibility_display_daltonizer_enabled", on.bool()
                )
                if (on) {
                    ctx.controller.writeSetting("SECURE", "accessibility_display_daltonizer", "12")
                }
            }
            ActionType.SYSTEM_EXTRA_DIM ->
                ctx.controller.writeSetting(
                    "SECURE", "reduce_bright_colors_activated",
                    (action.config["enabled"]?.toBoolean() ?: true).bool()
                )
            ActionType.SYSTEM_NIGHT_LIGHT ->
                ctx.controller.writeSetting(
                    "SECURE", "night_display_activated",
                    (action.config["enabled"]?.toBoolean() ?: true).bool()
                )
            ActionType.SYSTEM_HAPTIC_FEEDBACK ->
                ctx.controller.writeSetting(
                    "SYSTEM", "haptic_feedback_enabled",
                    (action.config["enabled"]?.toBoolean() ?: true).bool()
                )
            ActionType.SYSTEM_SOUND_EFFECTS ->
                ctx.controller.writeSetting(
                    "SYSTEM", "sound_effects_enabled",
                    (action.config["enabled"]?.toBoolean() ?: true).bool()
                )
            ActionType.SYSTEM_FORCE_STOP_APP ->
                ctx.controller.forceStopApp(action.config["package"] ?: "")
            ActionType.SYSTEM_CLEAR_APP_DATA ->
                ctx.controller.clearAppData(action.config["package"] ?: "")
            else -> SystemControlResult.fail("Unsupported action ${action.type}")
        }
    }
}
