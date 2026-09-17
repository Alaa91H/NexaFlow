package com.nexaflow.core.rom

import com.nexaflow.core.rom.model.RomFamily

/**
 * Professional catalog of ALL ROM custom settings — Evolution X, LineageOS
 * forks, and OEM skins (One UI, HyperOS, ColorOS, OxygenOS, etc.).
 * Provides structured, categorized metadata for every ROM-custom setting
 * prefix so the builder can offer typed, searchable options instead of raw
 * SYSTEM_SET_SETTING key/value. Works on any ROM — the picker shows only
 * the keys that exist on the current device, grouped by category.
 *
 * Pure Kotlin — no Android dependencies, fully unit-testable.
 * This is the single source of truth for professional system tweaks on any ROM.
 */
object EvolverCatalog {

    enum class Category(
        val displayName: String,
        val description: String,
        val icon: String
    ) {
        QUICK_SETTINGS("Quick Settings", "QS tiles, panel, brightness slider, footer", "qs"),
        STATUS_BAR("Status Bar", "Clock, battery, icons, network indicators", "status_bar"),
        LOCKSCREEN("Lockscreen", "Clock, shortcuts, weather, media art, UDFPS", "lockscreen"),
        NOTIFICATIONS("Notifications", "Heads-up, vibration, less boring, ticker", "notification"),
        NAVIGATION("Navigation", "Gesture, 3-button, navbar, swipe actions", "navigation"),
        THEMING("Theming", "Monet, accent, themed icons, fonts, shapes", "theming"),
        AMBIENT_AOD("Ambient & AOD", "Always-on display, ambient ticker, pulse", "ambient"),
        BUTTONS("Buttons & Haptics", "Button remap, haptics, back gesture", "buttons"),
        NETWORK_BATTERY("Network & Battery", "Smart charging, traffic, battery light", "network"),
        SYSTEM_UI("System UI", "Animations, blur, extra dim, display tweaks", "system_ui"),
        DEX("DEX & Windowing", "Desktop mode, freeform, multi-window", "dex"),
        OTHER("Other Evolver", "Misc Evolution X customizations", "other")
    }

    data class EvolverSettingMeta(
        val keyPattern: String,
        val namespace: EvolutionXSettingsBridge.Namespace,
        val category: Category,
        val valueType: ValueType,
        val defaultValue: String,
        val description: String,
        val options: List<String> = emptyList()
    )

    enum class ValueType {
        BOOLEAN,      // 0/1 or true/false
        INTEGER,      // numeric range
        STRING,       // free text
        ENUM,         // fixed set of values
        COLOR,        // hex color
        CSV           // comma-separated list (e.g., QS tiles)
    }

    /**
     * Curated metadata for known Evolution X settings — covers every Evolver
     * section. This is not exhaustive (the ROM adds keys per device), but it
     * provides typed editors for the most common 60+ settings. Unknown keys
     * discovered via listCustomKeys() fall back to Category.OTHER with STRING type.
     */
    val knownSettings: List<EvolverSettingMeta> = listOf(
        // Quick Settings
        EvolverSettingMeta("qs_tile_*", EvolutionXSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.BOOLEAN, "1", "Individual QS tile enable/disable"),
        EvolverSettingMeta("evo_qs_tiles", EvolutionXSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.CSV, "", "Custom QS tiles list (csv)"),
        EvolverSettingMeta("sysui_qs_tiles", EvolutionXSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.CSV, "wifi,bt,cell,dnd", "System QS tiles (standard)"),
        EvolverSettingMeta("qs_show_brightness_slider", EvolutionXSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.BOOLEAN, "1", "Show brightness slider in QS"),
        EvolverSettingMeta("qs_tile_tint", EvolutionXSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.BOOLEAN, "1", "Tint QS tiles with accent"),
        EvolverSettingMeta("qs_footer_text", EvolutionXSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.STRING, "", "Custom QS footer text"),
        EvolverSettingMeta("qs_compact_media_player_mode", EvolutionXSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.ENUM, "0", "Compact media player mode", listOf("0", "1", "2")),
        EvolverSettingMeta("qs_show_data_usage", EvolutionXSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.BOOLEAN, "1", "Show data usage in QS"),
        EvolverSettingMeta("qs_header_clock_style", EvolutionXSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.INTEGER, "0", "QS header clock style"),

        // Status Bar
        EvolverSettingMeta("evo_status_bar_clock_*", EvolutionXSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.ENUM, "1", "Status bar clock position/style"),
        EvolverSettingMeta("evo_clock_seconds", EvolutionXSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.BOOLEAN, "0", "Show seconds in clock"),
        EvolverSettingMeta("evo_status_bar_battery_style", EvolutionXSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.ENUM, "0", "Battery icon style", listOf("0", "1", "2", "3")),
        EvolverSettingMeta("evo_status_bar_show_battery_percent", EvolutionXSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.BOOLEAN, "0", "Show battery percent"),
        EvolverSettingMeta("status_bar_show_vpn_icon", EvolutionXSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.BOOLEAN, "1", "Show VPN icon"),
        EvolverSettingMeta("statusbar_clock_chip_gradient_*", EvolutionXSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.COLOR, "-16777216", "Clock chip gradient colors"),
        EvolverSettingMeta("status_bar_logo", EvolutionXSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.BOOLEAN, "0", "Show status bar logo (Evolution X)"),
        EvolverSettingMeta("status_bar_brightness_control", EvolutionXSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.BOOLEAN, "0", "Brightness control via status bar swipe"),
        EvolverSettingMeta("evo_status_bar_show_battery_percent_inside", EvolutionXSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.BOOLEAN, "0", "Battery percent inside icon"),

        // Lockscreen
        EvolverSettingMeta("lockscreen_clock_style", EvolutionXSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.ENUM, "0", "Lockscreen clock style", listOf("0", "1", "2", "3", "4")),
        EvolverSettingMeta("lockscreen_weather_enabled", EvolutionXSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.BOOLEAN, "0", "Show weather on lockscreen"),
        EvolverSettingMeta("lockscreen_shortcuts", EvolutionXSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.CSV, "", "Lockscreen shortcuts (csv)"),
        EvolverSettingMeta("lockscreen_media_art", EvolutionXSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.BOOLEAN, "1", "Show media art on lockscreen"),
        EvolverSettingMeta("evo_lockscreen_*", EvolutionXSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.BOOLEAN, "1", "Evolver lockscreen tweak"),
        EvolverSettingMeta("lockscreen_show_carrier", EvolutionXSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.BOOLEAN, "1", "Show carrier on lockscreen"),
        EvolverSettingMeta("lockscreen_battery_info", EvolutionXSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.BOOLEAN, "1", "Show battery info on lockscreen"),
        EvolverSettingMeta("lockscreen_udfps_*", EvolutionXSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.BOOLEAN, "1", "UDFPS customization"),

        // Notifications
        EvolverSettingMeta("notification_heads_up", EvolutionXSettingsBridge.Namespace.SECURE, Category.NOTIFICATIONS, ValueType.BOOLEAN, "1", "Heads-up notifications"),
        EvolverSettingMeta("evo_notification_*", EvolutionXSettingsBridge.Namespace.SECURE, Category.NOTIFICATIONS, ValueType.BOOLEAN, "1", "Evolver notification tweak"),
        EvolverSettingMeta("heads_up_timeout", EvolutionXSettingsBridge.Namespace.SECURE, Category.NOTIFICATIONS, ValueType.INTEGER, "5", "Heads-up timeout (seconds)"),
        EvolverSettingMeta("less_boring_heads_up", EvolutionXSettingsBridge.Namespace.SECURE, Category.NOTIFICATIONS, ValueType.BOOLEAN, "0", "Less boring heads-up"),
        EvolverSettingMeta("notification_light_pulse", EvolutionXSettingsBridge.Namespace.SECURE, Category.NOTIFICATIONS, ValueType.BOOLEAN, "1", "Notification light pulse"),

        // Navigation
        EvolverSettingMeta("evo_navigation_mode", EvolutionXSettingsBridge.Namespace.SECURE, Category.NAVIGATION, ValueType.ENUM, "2", "Navigation mode (0=3-button,1=2-button,2=gesture)", listOf("0", "1", "2")),
        EvolverSettingMeta("evo_swipe_gestures", EvolutionXSettingsBridge.Namespace.SECURE, Category.NAVIGATION, ValueType.BOOLEAN, "1", "Swipe gestures enabled"),
        EvolverSettingMeta("navigation_bar_height", EvolutionXSettingsBridge.Namespace.SECURE, Category.NAVIGATION, ValueType.INTEGER, "48", "Navigation bar height (dp)"),
        EvolverSettingMeta("back_gesture_height", EvolutionXSettingsBridge.Namespace.SECURE, Category.NAVIGATION, ValueType.INTEGER, "0", "Back gesture height"),
        EvolverSettingMeta("evo_button_*", EvolutionXSettingsBridge.Namespace.SECURE, Category.NAVIGATION, ValueType.BOOLEAN, "1", "Evolver button remap"),

        // Theming
        EvolverSettingMeta("evo_theme_accent", EvolutionXSettingsBridge.Namespace.SECURE, Category.THEMING, ValueType.COLOR, "#FF4081", "Theme accent color"),
        EvolverSettingMeta("evolution_monet_*", EvolutionXSettingsBridge.Namespace.SECURE, Category.THEMING, ValueType.BOOLEAN, "1", "Monet theming"),
        EvolverSettingMeta("themed_icons", EvolutionXSettingsBridge.Namespace.SECURE, Category.THEMING, ValueType.BOOLEAN, "0", "Themed icons"),
        EvolverSettingMeta("icon_pack", EvolutionXSettingsBridge.Namespace.SECURE, Category.THEMING, ValueType.STRING, "", "Icon pack package"),
        EvolverSettingMeta("evo_font_*", EvolutionXSettingsBridge.Namespace.SECURE, Category.THEMING, ValueType.STRING, "", "Custom font"),
        EvolverSettingMeta("monet_engine_color_override", EvolutionXSettingsBridge.Namespace.SECURE, Category.THEMING, ValueType.COLOR, "", "Monet color override"),

        // Ambient / AOD
        EvolverSettingMeta("evo_aod_schedule", EvolutionXSettingsBridge.Namespace.SECURE, Category.AMBIENT_AOD, ValueType.ENUM, "0", "AOD schedule", listOf("0", "1", "2")),
        EvolverSettingMeta("always_on_display_enabled", EvolutionXSettingsBridge.Namespace.SECURE, Category.AMBIENT_AOD, ValueType.BOOLEAN, "0", "Always-on display (redundant with SYSTEM_ALWAYS_ON_DISPLAY)"),
        EvolverSettingMeta("doze_*", EvolutionXSettingsBridge.Namespace.SECURE, Category.AMBIENT_AOD, ValueType.BOOLEAN, "1", "Doze/ambient settings"),
        EvolverSettingMeta("ambient_tilt_to_wake", EvolutionXSettingsBridge.Namespace.SECURE, Category.AMBIENT_AOD, ValueType.BOOLEAN, "1", "Tilt to wake ambient"),
        EvolverSettingMeta("evo_ambient_*", EvolutionXSettingsBridge.Namespace.SECURE, Category.AMBIENT_AOD, ValueType.BOOLEAN, "1", "Evolver ambient tweak"),

        // Buttons & Haptics
        EvolverSettingMeta("haptic_feedback_intensity", EvolutionXSettingsBridge.Namespace.SECURE, Category.BUTTONS, ValueType.INTEGER, "1", "Haptic intensity (0-3)"),
        EvolverSettingMeta("evo_haptic_*", EvolutionXSettingsBridge.Namespace.SECURE, Category.BUTTONS, ValueType.INTEGER, "1", "Evolver haptics"),
        EvolverSettingMeta("back_gesture_haptic", EvolutionXSettingsBridge.Namespace.SECURE, Category.BUTTONS, ValueType.BOOLEAN, "1", "Back gesture haptic"),

        // Network & Battery
        EvolverSettingMeta("evo_smart_charging", EvolutionXSettingsBridge.Namespace.SECURE, Category.NETWORK_BATTERY, ValueType.BOOLEAN, "0", "Smart charging limit"),
        EvolverSettingMeta("evo_battery_light", EvolutionXSettingsBridge.Namespace.SECURE, Category.NETWORK_BATTERY, ValueType.BOOLEAN, "1", "Battery light"),
        EvolverSettingMeta("evo_network_traffic", EvolutionXSettingsBridge.Namespace.SECURE, Category.NETWORK_BATTERY, ValueType.BOOLEAN, "0", "Network traffic indicator"),
        EvolverSettingMeta("network_traffic_*", EvolutionXSettingsBridge.Namespace.SECURE, Category.NETWORK_BATTERY, ValueType.BOOLEAN, "1", "Network traffic options"),

        // System UI
        EvolverSettingMeta("evo_disable_animation", EvolutionXSettingsBridge.Namespace.SECURE, Category.SYSTEM_UI, ValueType.BOOLEAN, "0", "Disable animations (Evolver)"),
        EvolverSettingMeta("animation_scales_*", EvolutionXSettingsBridge.Namespace.SECURE, Category.SYSTEM_UI, ValueType.INTEGER, "1", "Window/transition/animator scales"),
        EvolverSettingMeta("extra_dim_enabled", EvolutionXSettingsBridge.Namespace.SECURE, Category.SYSTEM_UI, ValueType.BOOLEAN, "0", "Extra dim"),
        EvolverSettingMeta("color_inversion_enabled", EvolutionXSettingsBridge.Namespace.SECURE, Category.SYSTEM_UI, ValueType.BOOLEAN, "0", "Color inversion"),

        // DEX
        EvolverSettingMeta("dex_*", EvolutionXSettingsBridge.Namespace.SECURE, Category.DEX, ValueType.BOOLEAN, "0", "DEX / desktop mode"),

        // === Generic / All ROMs — common system settings that work everywhere ===
        EvolverSettingMeta("screen_brightness", EvolutionXSettingsBridge.Namespace.SYSTEM, Category.SYSTEM_UI, ValueType.INTEGER, "128", "Screen brightness (0-255)"),
        EvolverSettingMeta("screen_brightness_mode", EvolutionXSettingsBridge.Namespace.SYSTEM, Category.SYSTEM_UI, ValueType.BOOLEAN, "0", "Auto brightness"),
        EvolverSettingMeta("accelerometer_rotation", EvolutionXSettingsBridge.Namespace.SYSTEM, Category.SYSTEM_UI, ValueType.BOOLEAN, "1", "Auto-rotate"),
        EvolverSettingMeta("haptic_feedback_enabled", EvolutionXSettingsBridge.Namespace.SYSTEM, Category.BUTTONS, ValueType.BOOLEAN, "1", "Haptic feedback"),
        EvolverSettingMeta("sound_effects_enabled", EvolutionXSettingsBridge.Namespace.SYSTEM, Category.BUTTONS, ValueType.BOOLEAN, "1", "Touch sounds"),
        EvolverSettingMeta("lock_screen_show_notifications", EvolutionXSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.BOOLEAN, "1", "Show notifications on lockscreen"),
        EvolverSettingMeta("doze_enabled", EvolutionXSettingsBridge.Namespace.SECURE, Category.AMBIENT_AOD, ValueType.BOOLEAN, "1", "Doze"),
        EvolverSettingMeta("qs_tiles", EvolutionXSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.CSV, "", "QS tiles (generic)"),
        EvolverSettingMeta("status_bar_show_battery_percent", EvolutionXSettingsBridge.Namespace.SYSTEM, Category.STATUS_BAR, ValueType.BOOLEAN, "0", "Battery percent (AOSP)"),
        EvolverSettingMeta("notification_light_pulse", EvolutionXSettingsBridge.Namespace.SYSTEM, Category.NOTIFICATIONS, ValueType.BOOLEAN, "1", "Notification LED"),
        // One UI (Samsung)
        EvolverSettingMeta("sec_*", EvolutionXSettingsBridge.Namespace.SYSTEM, Category.OTHER, ValueType.STRING, "", "One UI setting"),
        EvolverSettingMeta("oneui_*", EvolutionXSettingsBridge.Namespace.SYSTEM, Category.OTHER, ValueType.STRING, "", "One UI setting"),
        // HyperOS / MIUI
        EvolverSettingMeta("miui_*", EvolutionXSettingsBridge.Namespace.SYSTEM, Category.OTHER, ValueType.STRING, "", "MIUI/HyperOS setting"),
        EvolverSettingMeta("hyper_*", EvolutionXSettingsBridge.Namespace.SYSTEM, Category.OTHER, ValueType.STRING, "", "HyperOS setting"),
        // ColorOS / OxygenOS / Realme
        EvolverSettingMeta("oplus_*", EvolutionXSettingsBridge.Namespace.SYSTEM, Category.OTHER, ValueType.STRING, "", "ColorOS/OxygenOS setting"),
        EvolverSettingMeta("oppo_*", EvolutionXSettingsBridge.Namespace.SYSTEM, Category.OTHER, ValueType.STRING, "", "ColorOS setting"),
        EvolverSettingMeta("oneplus_*", EvolutionXSettingsBridge.Namespace.SYSTEM, Category.OTHER, ValueType.STRING, "", "OxygenOS setting")
    )

    /**
     * Classifies a live key (from settings list) into a Category by matching
     * its prefix against knownSettings, with fallback to prefix heuristics.
     */
    fun categorize(key: String): Category {
        val lower = key.lowercase()
        // Exact or pattern match first
        knownSettings.firstOrNull { meta ->
            when {
                meta.keyPattern.endsWith("*") -> lower.startsWith(meta.keyPattern.removeSuffix("*").lowercase())
                meta.keyPattern.contains("*") -> lower.contains(meta.keyPattern.replace("*", "").lowercase())
                else -> lower == meta.keyPattern.lowercase()
            }
        }?.let { return it.category }

        // Prefix heuristics fallback — generic for all ROMs
        return when {
            lower.startsWith("qs_") || lower.startsWith("evo_qs") || lower.startsWith("sec_qs") || lower.startsWith("miui_qs") -> Category.QUICK_SETTINGS
            lower.startsWith("status_bar") || lower.startsWith("statusbar") || lower.startsWith("evo_status") || lower.startsWith("sec_status") -> Category.STATUS_BAR
            lower.startsWith("lockscreen") || lower.startsWith("evo_lockscreen") || lower.startsWith("sec_lockscreen") -> Category.LOCKSCREEN
            lower.startsWith("notification") || lower.startsWith("heads_up") || lower.startsWith("less_boring") || lower.startsWith("sec_notification") -> Category.NOTIFICATIONS
            lower.startsWith("evo_navigation") || lower.startsWith("navigation_") || lower.startsWith("back_gesture") || lower.startsWith("sec_nav") -> Category.NAVIGATION
            lower.startsWith("evo_theme") || lower.startsWith("evolution_monet") || lower.startsWith("themed_icons") || lower.startsWith("icon_pack") || lower.startsWith("monet_") || lower.startsWith("sec_theme") || lower.startsWith("miui_theme") -> Category.THEMING
            lower.startsWith("evo_aod") || lower.startsWith("always_on") || lower.startsWith("doze") || lower.startsWith("ambient_") || lower.startsWith("sec_aod") -> Category.AMBIENT_AOD
            lower.startsWith("haptic") || lower.startsWith("evo_haptic") || lower.startsWith("evo_button") || lower.startsWith("sec_haptic") -> Category.BUTTONS
            lower.startsWith("evo_smart") || lower.startsWith("evo_battery") || lower.startsWith("evo_network") || lower.startsWith("network_traffic") || lower.startsWith("sec_battery") -> Category.NETWORK_BATTERY
            lower.startsWith("dex_") || lower.startsWith("sec_dex") -> Category.DEX
            lower.startsWith("sec_") || lower.startsWith("oneui_") -> Category.OTHER
            lower.startsWith("miui_") || lower.startsWith("hyper_") -> Category.OTHER
            lower.startsWith("oplus_") || lower.startsWith("oppo_") || lower.startsWith("oneplus_") -> Category.OTHER
            else -> Category.OTHER
        }
    }

    /** Returns metadata for a key, or null if unknown (caller uses fallback). */
    fun metaFor(key: String): EvolverSettingMeta? {
        val lower = key.lowercase()
        return knownSettings.firstOrNull { meta ->
            when {
                meta.keyPattern.endsWith("*") -> lower.startsWith(meta.keyPattern.removeSuffix("*").lowercase())
                meta.keyPattern.contains("*") -> lower.contains(meta.keyPattern.replace("*", "").lowercase())
                else -> lower == meta.keyPattern.lowercase()
            }
        }
    }

    /** All categories that have at least one known setting. */
    fun categories(): List<Category> = Category.entries.toList()

    /** Settings grouped by category for the picker UI. */
    fun grouped(): Map<Category, List<EvolverSettingMeta>> =
        knownSettings.groupBy { it.category }

    /** Whether a family supports system tweaks (all ROMs — generic). */
    fun isEvolverSupported(family: RomFamily): Boolean =
        RomSettingSchema.isSupported(family)

    /** Live keys from device, classified into categories. */
    fun classifyLiveKeys(keys: List<EvolutionXSettingsBridge.SettingEntry>): Map<Category, List<EvolutionXSettingsBridge.SettingEntry>> =
        keys.groupBy { categorize(it.key) }
}
