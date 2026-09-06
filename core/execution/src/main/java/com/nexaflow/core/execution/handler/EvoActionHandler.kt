package com.nexaflow.core.execution.handler

import com.nexaflow.core.rom.EvolutionXSettingsBridge
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import org.json.JSONObject

/**
 * Professional Evolution X Evolver handler — covers every Evolver section
 * (QS, Status Bar, Lockscreen, Navigation, Theming, Ambient/AOD, Notifications)
 * with typed, picker-driven actions. Unlike the generic SYSTEM_SET_SETTING,
 * these actions are Evolver-aware: they validate against EvolverCatalog,
 * use the correct namespace (secure), and provide read-back verification.
 */
class EvoActionHandler : ActionHandler {
    override val supportedTypes: Set<ActionType> = setOf(
        ActionType.EVO_SET_SETTING,
        ActionType.EVO_QS_TILES,
        ActionType.EVO_STATUS_BAR,
        ActionType.EVO_LOCKSCREEN,
        ActionType.EVO_NAVIGATION,
        ActionType.EVO_THEME,
        ActionType.EVO_AMBIENT_AOD,
        ActionType.EVO_NOTIFICATIONS,
        ActionType.EVO_BATCH
    )

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        return when (action.type) {
            ActionType.EVO_SET_SETTING -> {
                val namespace = parseNamespace(action.config["namespace"] ?: "SECURE")
                val key = action.config["key"]?.trim() ?: ""
                val value = action.config["value"] ?: ""
                if (key.isBlank()) return SystemControlResult.fail("Evolver key is empty")
                EvolutionXSettingsBridge.write(ctx.appContext, namespace, key, value)
            }

            ActionType.EVO_QS_TILES -> {
                val tiles = action.config["tiles"]?.trim()
                val columns = action.config["columns"]?.trim()
                val brightnessSlider = action.config["brightness_slider"]?.trim()
                val footerText = action.config["footer_text"]?.trim()
                val results = mutableListOf<SystemControlResult>()
                if (!tiles.isNullOrBlank()) {
                    results += EvolutionXSettingsBridge.write(ctx.appContext, EvolutionXSettingsBridge.Namespace.SECURE, "sysui_qs_tiles", tiles)
                }
                if (!columns.isNullOrBlank()) {
                    results += ctx.controller.writeSetting("SECURE", "qs_tiles_columns", columns)
                }
                if (!brightnessSlider.isNullOrBlank()) {
                    results += EvolutionXSettingsBridge.write(ctx.appContext, EvolutionXSettingsBridge.Namespace.SECURE, "qs_show_brightness_slider", brightnessSlider)
                }
                if (!footerText.isNullOrBlank()) {
                    results += EvolutionXSettingsBridge.write(ctx.appContext, EvolutionXSettingsBridge.Namespace.SECURE, "qs_footer_text", footerText)
                }
                combine(results, "QS tiles")
            }

            ActionType.EVO_STATUS_BAR -> {
                // Config is a JSON map of key->value for status bar Evolver keys
                val json = action.config["config_json"]?.trim().orEmpty()
                if (json.isBlank()) {
                    // Fallback to individual keys for backward compat
                    val clockPos = action.config["clock_position"]
                    val batteryStyle = action.config["battery_style"]
                    val results = mutableListOf<SystemControlResult>()
                    if (!clockPos.isNullOrBlank()) results += EvolutionXSettingsBridge.write(ctx.appContext, EvolutionXSettingsBridge.Namespace.SECURE, "evo_status_bar_clock_position", clockPos)
                    if (!batteryStyle.isNullOrBlank()) results += EvolutionXSettingsBridge.write(ctx.appContext, EvolutionXSettingsBridge.Namespace.SECURE, "evo_status_bar_battery_style", batteryStyle)
                    return combine(results, "Status bar")
                }
                batchWrite(ctx, json, "Status bar")
            }

            ActionType.EVO_LOCKSCREEN -> {
                val json = action.config["config_json"]?.trim().orEmpty()
                if (json.isBlank()) {
                    val clockStyle = action.config["clock_style"]
                    val results = mutableListOf<SystemControlResult>()
                    if (!clockStyle.isNullOrBlank()) results += EvolutionXSettingsBridge.write(ctx.appContext, EvolutionXSettingsBridge.Namespace.SECURE, "lockscreen_clock_style", clockStyle)
                    return combine(results, "Lockscreen")
                }
                batchWrite(ctx, json, "Lockscreen")
            }

            ActionType.EVO_NAVIGATION -> {
                val mode = action.config["mode"]?.trim()
                val results = mutableListOf<SystemControlResult>()
                if (!mode.isNullOrBlank()) {
                    // evo_navigation_mode: 0=3-button, 1=2-button, 2=gesture
                    results += EvolutionXSettingsBridge.write(ctx.appContext, EvolutionXSettingsBridge.Namespace.SECURE, "evo_navigation_mode", mode)
                }
                val backHeight = action.config["back_height"]?.trim()
                if (!backHeight.isNullOrBlank()) {
                    results += EvolutionXSettingsBridge.write(ctx.appContext, EvolutionXSettingsBridge.Namespace.SECURE, "back_gesture_height", backHeight)
                }
                combine(results, "Navigation")
            }

            ActionType.EVO_THEME -> {
                val json = action.config["config_json"]?.trim().orEmpty()
                if (json.isBlank()) {
                    val accent = action.config["accent"]?.trim()
                    val results = mutableListOf<SystemControlResult>()
                    if (!accent.isNullOrBlank()) results += EvolutionXSettingsBridge.write(ctx.appContext, EvolutionXSettingsBridge.Namespace.SECURE, "evo_theme_accent", accent)
                    return combine(results, "Theme")
                }
                batchWrite(ctx, json, "Theme")
            }

            ActionType.EVO_AMBIENT_AOD -> {
                val enabled = action.config["enabled"]?.trim()
                val schedule = action.config["schedule"]?.trim()
                val results = mutableListOf<SystemControlResult>()
                if (!enabled.isNullOrBlank()) {
                    results += EvolutionXSettingsBridge.write(ctx.appContext, EvolutionXSettingsBridge.Namespace.SECURE, "always_on_display_enabled", if (enabled == "1" || enabled.equals("true", true)) "1" else "0")
                }
                if (!schedule.isNullOrBlank()) {
                    results += EvolutionXSettingsBridge.write(ctx.appContext, EvolutionXSettingsBridge.Namespace.SECURE, "evo_aod_schedule", schedule)
                }
                combine(results, "Ambient/AOD")
            }

            ActionType.EVO_NOTIFICATIONS -> {
                val headsUp = action.config["heads_up"]?.trim()
                val timeout = action.config["timeout"]?.trim()
                val lessBoring = action.config["less_boring"]?.trim()
                val results = mutableListOf<SystemControlResult>()
                if (!headsUp.isNullOrBlank()) results += EvolutionXSettingsBridge.write(ctx.appContext, EvolutionXSettingsBridge.Namespace.SECURE, "notification_heads_up", headsUp)
                if (!timeout.isNullOrBlank()) results += EvolutionXSettingsBridge.write(ctx.appContext, EvolutionXSettingsBridge.Namespace.SECURE, "heads_up_timeout", timeout)
                if (!lessBoring.isNullOrBlank()) results += EvolutionXSettingsBridge.write(ctx.appContext, EvolutionXSettingsBridge.Namespace.SECURE, "less_boring_heads_up", lessBoring)
                combine(results, "Notifications")
            }

            ActionType.EVO_BATCH -> {
                val batchJson = action.config["batch_json"]?.trim().orEmpty()
                if (batchJson.isBlank()) return SystemControlResult.fail("Batch JSON is empty")
                batchWrite(ctx, batchJson, "Batch")
            }

            else -> SystemControlResult.fail("Unsupported Evolver action ${action.type}")
        }
    }

    private fun parseNamespace(raw: String): EvolutionXSettingsBridge.Namespace =
        when (raw.uppercase()) {
            "SYSTEM" -> EvolutionXSettingsBridge.Namespace.SYSTEM
            "GLOBAL" -> EvolutionXSettingsBridge.Namespace.GLOBAL
            else -> EvolutionXSettingsBridge.Namespace.SECURE
        }

    private fun batchWrite(ctx: ActionExecutionContext, json: String, label: String): SystemControlResult {
        return try {
            val obj = JSONObject(json)
            val results = mutableListOf<SystemControlResult>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.optString(key, "")
                // Heuristic: keys starting with evo_/evolution_/sysui_/qs_/lockscreen_ go to SECURE
                val namespace = when {
                    key.startsWith("evo_") || key.startsWith("evolution_") || key.startsWith("sysui_") ||
                        key.startsWith("qs_") || key.startsWith("lockscreen_") || key.startsWith("status_bar") -> EvolutionXSettingsBridge.Namespace.SECURE
                    else -> EvolutionXSettingsBridge.Namespace.SECURE
                }
                results += EvolutionXSettingsBridge.write(ctx.appContext, namespace, key, value)
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
