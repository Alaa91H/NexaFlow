package com.nexaflow.core.execution.handler

import com.nexaflow.core.rom.CustomSettingsBridge
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import org.json.JSONObject

/**
 * Professional Custom ROM settings handler — covers every custom-ROM settings section
 * (QS, Status Bar, Lockscreen, Navigation, Theming, Ambient/AOD, Notifications)
 * with typed, picker-driven actions. Unlike the generic SYSTEM_SET_SETTING,
 * these actions are custom-rom-settings-aware: they validate against RomSettingCatalog,
 * use the correct namespace (secure), and provide read-back verification.
 */
class RomSettingsActionHandler : ActionHandler {
    override val supportedTypes: Set<ActionType> = setOf(
        ActionType.ROM_CUSTOM_SETTING,
        ActionType.ROM_QS_TILES,
        ActionType.ROM_STATUS_BAR,
        ActionType.ROM_LOCKSCREEN,
        ActionType.ROM_NAVIGATION,
        ActionType.ROM_THEME,
        ActionType.ROM_AMBIENT_AOD,
        ActionType.ROM_NOTIFICATIONS,
        ActionType.ROM_BATCH
    )

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        return when (action.type) {
            ActionType.ROM_CUSTOM_SETTING -> {
                val namespace = parseNamespace(action.config["namespace"] ?: "SECURE")
                val key = action.config["key"]?.trim() ?: ""
                val value = action.config["value"] ?: ""
                if (key.isBlank()) return SystemControlResult.fail("Custom setting key is empty")
                CustomSettingsBridge.write(ctx.appContext, namespace, key, value)
            }

            ActionType.ROM_QS_TILES -> {
                val tiles = action.config["tiles"]?.trim()
                val columns = action.config["columns"]?.trim()
                val brightnessSlider = action.config["brightness_slider"]?.trim()
                val footerText = action.config["footer_text"]?.trim()
                val results = mutableListOf<SystemControlResult>()
                if (!tiles.isNullOrBlank()) {
                    results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "sysui_qs_tiles", tiles)
                }
                if (!columns.isNullOrBlank()) {
                    results += ctx.controller.writeSetting("SECURE", "qs_tiles_columns", columns)
                }
                if (!brightnessSlider.isNullOrBlank()) {
                    results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "qs_show_brightness_slider", brightnessSlider)
                }
                if (!footerText.isNullOrBlank()) {
                    results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "qs_footer_text", footerText)
                }
                combine(results, "QS tiles")
            }

            ActionType.ROM_STATUS_BAR -> {
                // Config is a JSON map of key->value for status bar custom keys
                val json = action.config["config_json"]?.trim().orEmpty()
                if (json.isBlank()) {
                    // Fallback to individual keys for backward compat
                    val clockPos = action.config["clock_position"]
                    val batteryStyle = action.config["battery_style"]
                    val clockSeconds = action.config["clock_seconds"]
                    val batteryPercent = action.config["battery_percent"]
                    val results = mutableListOf<SystemControlResult>()
                    if (!clockPos.isNullOrBlank()) results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "evo_status_bar_clock_position", clockPos)
                    if (!batteryStyle.isNullOrBlank()) results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "evo_status_bar_battery_style", batteryStyle)
                    if (!clockSeconds.isNullOrBlank()) results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "evo_clock_seconds", clockSeconds)
                    if (!batteryPercent.isNullOrBlank()) results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "evo_status_bar_show_battery_percent", batteryPercent)
                    return combine(results, "Status bar")
                }
                batchWrite(ctx, json, "Status bar")
            }

            ActionType.ROM_LOCKSCREEN -> {
                val json = action.config["config_json"]?.trim().orEmpty()
                if (json.isBlank()) {
                    val clockStyle = action.config["clock_style"]
                    val weather = action.config["weather"]
                    val shortcuts = action.config["shortcuts"]
                    val mediaArt = action.config["media_art"]
                    val results = mutableListOf<SystemControlResult>()
                    if (!clockStyle.isNullOrBlank()) results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "lockscreen_clock_style", clockStyle)
                    if (!weather.isNullOrBlank()) results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "lockscreen_weather_enabled", weather)
                    if (!shortcuts.isNullOrBlank()) results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "lockscreen_shortcuts", shortcuts)
                    if (!mediaArt.isNullOrBlank()) results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "lockscreen_media_art", mediaArt)
                    return combine(results, "Lockscreen")
                }
                batchWrite(ctx, json, "Lockscreen")
            }

            ActionType.ROM_NAVIGATION -> {
                val mode = action.config["mode"]?.trim()
                val results = mutableListOf<SystemControlResult>()
                if (!mode.isNullOrBlank()) {
                    // rom_navigation_mode: 0=3-button, 1=2-button, 2=gesture
                    results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "rom_navigation_mode", mode)
                }
                val backHeight = action.config["back_height"]?.trim()
                if (!backHeight.isNullOrBlank()) {
                    results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "back_gesture_height", backHeight)
                }
                combine(results, "Navigation")
            }

            ActionType.ROM_THEME -> {
                val json = action.config["config_json"]?.trim().orEmpty()
                if (json.isBlank()) {
                    val accent = action.config["accent"]?.trim()
                    val monet = action.config["monet"]?.trim()
                    val themedIcons = action.config["themed_icons"]?.trim()
                    val results = mutableListOf<SystemControlResult>()
                    if (!accent.isNullOrBlank()) results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "rom_theme_accent", accent)
                    if (!monet.isNullOrBlank()) results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "evolution_monet_enabled", monet)
                    if (!themedIcons.isNullOrBlank()) results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "themed_icons", themedIcons)
                    return combine(results, "Theme")
                }
                batchWrite(ctx, json, "Theme")
            }

            ActionType.ROM_AMBIENT_AOD -> {
                val enabled = action.config["enabled"]?.trim()
                val schedule = action.config["schedule"]?.trim()
                val results = mutableListOf<SystemControlResult>()
                if (!enabled.isNullOrBlank()) {
                    results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "always_on_display_enabled", if (enabled == "1" || enabled.equals("true", true)) "1" else "0")
                }
                if (!schedule.isNullOrBlank()) {
                    results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "rom_aod_schedule", schedule)
                }
                combine(results, "Ambient/AOD")
            }

            ActionType.ROM_NOTIFICATIONS -> {
                val headsUp = action.config["heads_up"]?.trim()
                val timeout = action.config["timeout"]?.trim()
                val lessBoring = action.config["less_boring"]?.trim()
                val results = mutableListOf<SystemControlResult>()
                if (!headsUp.isNullOrBlank()) results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "notification_heads_up", headsUp)
                if (!timeout.isNullOrBlank()) results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "heads_up_timeout", timeout)
                if (!lessBoring.isNullOrBlank()) results += CustomSettingsBridge.write(ctx.appContext, CustomSettingsBridge.Namespace.SECURE, "less_boring_heads_up", lessBoring)
                combine(results, "Notifications")
            }

            ActionType.ROM_BATCH -> {
                val batchJson = action.config["batch_json"]?.trim().orEmpty()
                if (batchJson.isBlank()) return SystemControlResult.fail("Batch JSON is empty")
                batchWrite(ctx, batchJson, "Batch")
            }

            else -> SystemControlResult.fail("Unsupported custom setting action ${action.type}")
        }
    }

    private fun parseNamespace(raw: String): CustomSettingsBridge.Namespace =
        when (raw.uppercase()) {
            "SYSTEM" -> CustomSettingsBridge.Namespace.SYSTEM
            "GLOBAL" -> CustomSettingsBridge.Namespace.GLOBAL
            else -> CustomSettingsBridge.Namespace.SECURE
        }

    private fun batchWrite(ctx: ActionExecutionContext, json: String, label: String): SystemControlResult {
        return try {
            val obj = JSONObject(json)
            val results = mutableListOf<SystemControlResult>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.optString(key, "")
                // Heuristic: keys starting with rom_/evolution_/sysui_/qs_/lockscreen_ go to SECURE
                val namespace = when {
                    key.startsWith("rom_") || key.startsWith("evolution_") || key.startsWith("sysui_") ||
                        key.startsWith("qs_") || key.startsWith("lockscreen_") || key.startsWith("status_bar") -> CustomSettingsBridge.Namespace.SECURE
                    else -> CustomSettingsBridge.Namespace.SECURE
                }
                results += CustomSettingsBridge.write(ctx.appContext, namespace, key, value)
            }
            combine(results, label)
        } catch (e: Exception) {
            SystemControlResult.fail("Invalid batch JSON: ${e.message}")
        }
    }

    private fun combine(results: List<SystemControlResult>, label: String): SystemControlResult {
        if (results.isEmpty()) return SystemControlResult.fail("No $label settings to apply")
        val failed = results.filter { !it.success }
        return if (failed.isEmpty()) {
            SystemControlResult.ok("$label applied (${results.size} keys)")
        } else {
            SystemControlResult.fail("$label partially failed: ${failed.joinToString { it.message }}")
        }
    }
}
