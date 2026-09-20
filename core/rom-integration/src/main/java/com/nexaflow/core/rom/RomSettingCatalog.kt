package com.nexaflow.core.rom

import com.nexaflow.core.rom.model.RomFamily

/**
 * Professional catalog of ROM custom settings across the whole landscape:
 * Privileged community builds and vendor skins alike.
 * Provides structured, categorized metadata for every ROM-custom setting
 * prefix so the builder can offer typed, searchable options instead of raw
 * SYSTEM_SET_SETTING key/value. Works on any ROM — the picker shows only
 * the keys that exist on the current device, grouped by category.
 *
 * Pure Kotlin — no Android dependencies, fully unit-testable.
 * This is the single source of truth for professional system tweaks on any ROM.
 */
object RomSettingCatalog {

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
        OTHER("Other vendor custom setting", "Miscellaneous vendor customizations", "other")
    }

    data class RomSettingMeta(
        val keyPattern: String,
        val namespace: CustomSettingsBridge.Namespace,
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
     * Curated metadata for known custom-ROM settings — covers every vendor
     * settings section. This is not exhaustive (the ROM adds keys per device), but it
     * provides typed editors for the most common 60+ settings. Unknown keys
     * discovered via listCustomKeys() fall back to Category.OTHER with STRING type.
     */
    val knownSettings: List<RomSettingMeta> = listOf(
        // Quick Settings
        RomSettingMeta("qs_tile_*", CustomSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.BOOLEAN, "1", "Individual QS tile enable/disable"),
        RomSettingMeta("evo_qs_tiles", CustomSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.CSV, "", "Custom QS tiles list (csv)"),
        RomSettingMeta("sysui_qs_tiles", CustomSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.CSV, "wifi,bt,cell,dnd", "System QS tiles (standard)"),
        RomSettingMeta("qs_show_brightness_slider", CustomSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.BOOLEAN, "1", "Show brightness slider in QS"),
        RomSettingMeta("qs_tile_tint", CustomSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.BOOLEAN, "1", "Tint QS tiles with accent"),
        RomSettingMeta("qs_footer_text", CustomSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.STRING, "", "Custom QS footer text"),
        RomSettingMeta("qs_compact_media_player_mode", CustomSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.ENUM, "0", "Compact media player mode", listOf("0", "1", "2")),
        RomSettingMeta("qs_show_data_usage", CustomSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.BOOLEAN, "1", "Show data usage in QS"),
        RomSettingMeta("qs_header_clock_style", CustomSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.INTEGER, "0", "QS header clock style"),

        // Status Bar
        RomSettingMeta("evo_status_bar_clock_*", CustomSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.ENUM, "1", "Status bar clock position/style"),
        RomSettingMeta("evo_clock_seconds", CustomSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.BOOLEAN, "0", "Show seconds in clock"),
        RomSettingMeta("evo_status_bar_battery_style", CustomSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.ENUM, "0", "Battery icon style", listOf("0", "1", "2", "3")),
        RomSettingMeta("evo_status_bar_show_battery_percent", CustomSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.BOOLEAN, "0", "Show battery percent"),
        RomSettingMeta("status_bar_show_vpn_icon", CustomSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.BOOLEAN, "1", "Show VPN icon"),
        RomSettingMeta("statusbar_clock_chip_gradient_*", CustomSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.COLOR, "-16777216", "Clock chip gradient colors"),
        RomSettingMeta("status_bar_logo", CustomSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.BOOLEAN, "0", "Show status bar logo"),
        RomSettingMeta("status_bar_brightness_control", CustomSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.BOOLEAN, "0", "Brightness control via status bar swipe"),
        RomSettingMeta("evo_status_bar_show_battery_percent_inside", CustomSettingsBridge.Namespace.SECURE, Category.STATUS_BAR, ValueType.BOOLEAN, "0", "Battery percent inside icon"),

        // Lockscreen
        RomSettingMeta("lockscreen_clock_style", CustomSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.ENUM, "0", "Lockscreen clock style", listOf("0", "1", "2", "3", "4")),
        RomSettingMeta("lockscreen_weather_enabled", CustomSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.BOOLEAN, "0", "Show weather on lockscreen"),
        RomSettingMeta("lockscreen_shortcuts", CustomSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.CSV, "", "Lockscreen shortcuts (csv)"),
        RomSettingMeta("lockscreen_media_art", CustomSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.BOOLEAN, "1", "Show media art on lockscreen"),
        RomSettingMeta("evo_lockscreen_*", CustomSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.BOOLEAN, "1", "vendor custom setting lockscreen tweak"),
        RomSettingMeta("lockscreen_show_carrier", CustomSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.BOOLEAN, "1", "Show carrier on lockscreen"),
        RomSettingMeta("lockscreen_battery_info", CustomSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.BOOLEAN, "1", "Show battery info on lockscreen"),
        RomSettingMeta("lockscreen_udfps_*", CustomSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.BOOLEAN, "1", "UDFPS customization"),

        // Notifications
        RomSettingMeta("notification_heads_up", CustomSettingsBridge.Namespace.SECURE, Category.NOTIFICATIONS, ValueType.BOOLEAN, "1", "Heads-up notifications"),
        RomSettingMeta("evo_notification_*", CustomSettingsBridge.Namespace.SECURE, Category.NOTIFICATIONS, ValueType.BOOLEAN, "1", "vendor custom setting notification tweak"),
        RomSettingMeta("heads_up_timeout", CustomSettingsBridge.Namespace.SECURE, Category.NOTIFICATIONS, ValueType.INTEGER, "5", "Heads-up timeout (seconds)"),
        RomSettingMeta("less_boring_heads_up", CustomSettingsBridge.Namespace.SECURE, Category.NOTIFICATIONS, ValueType.BOOLEAN, "0", "Less boring heads-up"),
        RomSettingMeta("notification_light_pulse", CustomSettingsBridge.Namespace.SECURE, Category.NOTIFICATIONS, ValueType.BOOLEAN, "1", "Notification light pulse"),

        // Navigation
        RomSettingMeta("evo_navigation_mode", CustomSettingsBridge.Namespace.SECURE, Category.NAVIGATION, ValueType.ENUM, "2", "Navigation mode (0=3-button,1=2-button,2=gesture)", listOf("0", "1", "2")),
        RomSettingMeta("evo_swipe_gestures", CustomSettingsBridge.Namespace.SECURE, Category.NAVIGATION, ValueType.BOOLEAN, "1", "Swipe gestures enabled"),
        RomSettingMeta("navigation_bar_height", CustomSettingsBridge.Namespace.SECURE, Category.NAVIGATION, ValueType.INTEGER, "48", "Navigation bar height (dp)"),
        RomSettingMeta("back_gesture_height", CustomSettingsBridge.Namespace.SECURE, Category.NAVIGATION, ValueType.INTEGER, "0", "Back gesture height"),
        RomSettingMeta("evo_button_*", CustomSettingsBridge.Namespace.SECURE, Category.NAVIGATION, ValueType.BOOLEAN, "1", "vendor custom setting button remap"),

        // Theming
        RomSettingMeta("evo_theme_accent", CustomSettingsBridge.Namespace.SECURE, Category.THEMING, ValueType.COLOR, "#FF4081", "Theme accent color"),
        RomSettingMeta("evolution_monet_*", CustomSettingsBridge.Namespace.SECURE, Category.THEMING, ValueType.BOOLEAN, "1", "Monet theming"),
        RomSettingMeta("themed_icons", CustomSettingsBridge.Namespace.SECURE, Category.THEMING, ValueType.BOOLEAN, "0", "Themed icons"),
        RomSettingMeta("icon_pack", CustomSettingsBridge.Namespace.SECURE, Category.THEMING, ValueType.STRING, "", "Icon pack package"),
        RomSettingMeta("evo_font_*", CustomSettingsBridge.Namespace.SECURE, Category.THEMING, ValueType.STRING, "", "Custom font"),
        RomSettingMeta("monet_engine_color_override", CustomSettingsBridge.Namespace.SECURE, Category.THEMING, ValueType.COLOR, "", "Monet color override"),

        // Ambient / AOD
        RomSettingMeta("evo_aod_schedule", CustomSettingsBridge.Namespace.SECURE, Category.AMBIENT_AOD, ValueType.ENUM, "0", "AOD schedule", listOf("0", "1", "2")),
        RomSettingMeta("always_on_display_enabled", CustomSettingsBridge.Namespace.SECURE, Category.AMBIENT_AOD, ValueType.BOOLEAN, "0", "Always-on display (redundant with SYSTEM_ALWAYS_ON_DISPLAY)"),
        RomSettingMeta("doze_*", CustomSettingsBridge.Namespace.SECURE, Category.AMBIENT_AOD, ValueType.BOOLEAN, "1", "Doze/ambient settings"),
        RomSettingMeta("ambient_tilt_to_wake", CustomSettingsBridge.Namespace.SECURE, Category.AMBIENT_AOD, ValueType.BOOLEAN, "1", "Tilt to wake ambient"),
        RomSettingMeta("evo_ambient_*", CustomSettingsBridge.Namespace.SECURE, Category.AMBIENT_AOD, ValueType.BOOLEAN, "1", "vendor custom setting ambient tweak"),

        // Buttons & Haptics
        RomSettingMeta("haptic_feedback_intensity", CustomSettingsBridge.Namespace.SECURE, Category.BUTTONS, ValueType.INTEGER, "1", "Haptic intensity (0-3)"),
        RomSettingMeta("evo_haptic_*", CustomSettingsBridge.Namespace.SECURE, Category.BUTTONS, ValueType.INTEGER, "1", "vendor custom setting haptics"),
        RomSettingMeta("back_gesture_haptic", CustomSettingsBridge.Namespace.SECURE, Category.BUTTONS, ValueType.BOOLEAN, "1", "Back gesture haptic"),

        // Network & Battery
        RomSettingMeta("evo_smart_charging", CustomSettingsBridge.Namespace.SECURE, Category.NETWORK_BATTERY, ValueType.BOOLEAN, "0", "Smart charging limit"),
        RomSettingMeta("evo_battery_light", CustomSettingsBridge.Namespace.SECURE, Category.NETWORK_BATTERY, ValueType.BOOLEAN, "1", "Battery light"),
        RomSettingMeta("evo_network_traffic", CustomSettingsBridge.Namespace.SECURE, Category.NETWORK_BATTERY, ValueType.BOOLEAN, "0", "Network traffic indicator"),
        RomSettingMeta("network_traffic_*", CustomSettingsBridge.Namespace.SECURE, Category.NETWORK_BATTERY, ValueType.BOOLEAN, "1", "Network traffic options"),

        // System UI
        RomSettingMeta("evo_disable_animation", CustomSettingsBridge.Namespace.SECURE, Category.SYSTEM_UI, ValueType.BOOLEAN, "0", "Disable animations "),
        RomSettingMeta("animation_scales_*", CustomSettingsBridge.Namespace.SECURE, Category.SYSTEM_UI, ValueType.INTEGER, "1", "Window/transition/animator scales"),
        RomSettingMeta("extra_dim_enabled", CustomSettingsBridge.Namespace.SECURE, Category.SYSTEM_UI, ValueType.BOOLEAN, "0", "Extra dim"),
        RomSettingMeta("color_inversion_enabled", CustomSettingsBridge.Namespace.SECURE, Category.SYSTEM_UI, ValueType.BOOLEAN, "0", "Color inversion"),

        // DEX
        RomSettingMeta("dex_*", CustomSettingsBridge.Namespace.SECURE, Category.DEX, ValueType.BOOLEAN, "0", "DEX / desktop mode"),

        // === Generic / All ROMs — common system settings that work everywhere ===
        RomSettingMeta("screen_brightness", CustomSettingsBridge.Namespace.SYSTEM, Category.SYSTEM_UI, ValueType.INTEGER, "128", "Screen brightness (0-255)"),
        RomSettingMeta("screen_brightness_mode", CustomSettingsBridge.Namespace.SYSTEM, Category.SYSTEM_UI, ValueType.BOOLEAN, "0", "Auto brightness"),
        RomSettingMeta("accelerometer_rotation", CustomSettingsBridge.Namespace.SYSTEM, Category.SYSTEM_UI, ValueType.BOOLEAN, "1", "Auto-rotate"),
        RomSettingMeta("haptic_feedback_enabled", CustomSettingsBridge.Namespace.SYSTEM, Category.BUTTONS, ValueType.BOOLEAN, "1", "Haptic feedback"),
        RomSettingMeta("sound_effects_enabled", CustomSettingsBridge.Namespace.SYSTEM, Category.BUTTONS, ValueType.BOOLEAN, "1", "Touch sounds"),
        RomSettingMeta("lock_screen_show_notifications", CustomSettingsBridge.Namespace.SECURE, Category.LOCKSCREEN, ValueType.BOOLEAN, "1", "Show notifications on lockscreen"),
        RomSettingMeta("doze_enabled", CustomSettingsBridge.Namespace.SECURE, Category.AMBIENT_AOD, ValueType.BOOLEAN, "1", "Doze"),
        RomSettingMeta("qs_tiles", CustomSettingsBridge.Namespace.SECURE, Category.QUICK_SETTINGS, ValueType.CSV, "", "QS tiles (generic)"),
        RomSettingMeta("status_bar_show_battery_percent", CustomSettingsBridge.Namespace.SYSTEM, Category.STATUS_BAR, ValueType.BOOLEAN, "0", "Battery percent (AOSP)"),
        RomSettingMeta("notification_light_pulse", CustomSettingsBridge.Namespace.SYSTEM, Category.NOTIFICATIONS, ValueType.BOOLEAN, "1", "Notification LED"),
        // Vendor skin setting keys (protocol prefixes; descriptions stay neutral)
        RomSettingMeta("sec_*", CustomSettingsBridge.Namespace.SYSTEM, Category.OTHER, ValueType.STRING, "", "Vendor skin setting"),
        RomSettingMeta("oneui_*", CustomSettingsBridge.Namespace.SYSTEM, Category.OTHER, ValueType.STRING, "", "Vendor skin setting"),
        RomSettingMeta("miui_*", CustomSettingsBridge.Namespace.SYSTEM, Category.OTHER, ValueType.STRING, "", "Vendor skin setting"),
        RomSettingMeta("hyper_*", CustomSettingsBridge.Namespace.SYSTEM, Category.OTHER, ValueType.STRING, "", "Vendor skin setting"),
        RomSettingMeta("oplus_*", CustomSettingsBridge.Namespace.SYSTEM, Category.OTHER, ValueType.STRING, "", "Vendor skin setting"),
        RomSettingMeta("oppo_*", CustomSettingsBridge.Namespace.SYSTEM, Category.OTHER, ValueType.STRING, "", "Vendor skin setting"),
        RomSettingMeta("oneplus_*", CustomSettingsBridge.Namespace.SYSTEM, Category.OTHER, ValueType.STRING, "", "Vendor skin setting")
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
    fun metaFor(key: String): RomSettingMeta? {
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
    fun grouped(): Map<Category, List<RomSettingMeta>> =
        knownSettings.groupBy { it.category }

    /** Whether a family supports system tweaks (all ROMs — generic). */
    fun supportsCustomSettings(family: RomFamily): Boolean =
        RomSettingSchema.isSupported(family)

    /** Live keys from device, classified into categories. */
    fun classifyLiveKeys(keys: List<CustomSettingsBridge.SettingEntry>): Map<Category, List<CustomSettingsBridge.SettingEntry>> =
        keys.groupBy { categorize(it.key) }
}
