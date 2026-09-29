package com.nexaflow.domain.catalog

import com.nexaflow.domain.models.ActionType

/** Typed configuration contracts for persisted action kinds. */
internal object ActionNodeSchemas {
    fun schemaFor(type: ActionType): NodeConfigurationSchema = when (type) {
        in toggleActions -> schema(
            booleanField("enabled", default = "true")
        )
        ActionType.SYSTEM_BRIGHTNESS -> schema(
            integerField("value", default = "128", min = 0.0, max = 255.0, expressionCapable = true)
        )
        ActionType.SYSTEM_VOLUME,
        ActionType.SYSTEM_RING_VOLUME -> schema(
            integerField("value", default = "50", min = 0.0, max = 100.0, expressionCapable = true)
        )
        ActionType.SYSTEM_STREAM_VOLUME -> schema(
            stringField("stream", default = "MUSIC"),
            integerField("value", default = "50", min = 0.0, max = 100.0, expressionCapable = true)
        )
        ActionType.SYSTEM_SCREEN_ROTATION -> schema(
            booleanField("autoRotate", default = "true")
        )
        ActionType.SYSTEM_SCREEN_TIMEOUT -> schema(
            durationField("seconds", default = "60", min = 10.0, max = 1_800.0)
        )
        ActionType.SYSTEM_RINGER_MODE -> schema(
            enumField("mode", "NORMAL", "VIBRATE", "SILENT", default = "NORMAL")
        )
        ActionType.SYSTEM_OPEN_URL -> schema(
            urlField("url", required = true, expressionCapable = true)
        )
        ActionType.SYSTEM_OPEN_APP,
        ActionType.APPLICATION_LAUNCH_APP -> schema(
            stringField("packages"),
            packageField("package")
        )
        ActionType.SYSTEM_SEND_NOTIFICATION -> schema(
            stringField("title", default = "NexaFlow", expressionCapable = true),
            stringField("text", default = "Automation executed", expressionCapable = true),
            enumField("sound", "DEFAULT", "RINGTONE", "NOTIFICATION", "BEEP", "SILENT", default = "DEFAULT"),
            jsonField("action_buttons")
        )
        ActionType.SYSTEM_BLOCK_NOTIFICATION -> schema(
            stringField("packages"),
            packageField("package"),
            booleanField("enabled", default = "true")
        )
        ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS -> schema(
            stringField("packages"),
            packageField("package")
        )
        ActionType.APPLICATION_OPEN_APP_SETTINGS,
        ActionType.APPLICATION_CLOSE_APP,
        ActionType.SYSTEM_FORCE_STOP_APP,
        ActionType.SYSTEM_CLEAR_APP_DATA,
        ActionType.SYSTEM_UNINSTALL_APP,
        ActionType.SYSTEM_DISABLE_APP,
        ActionType.SYSTEM_ENABLE_APP,
        ActionType.SYSTEM_OPEN_PLAY_STORE_APP -> schema(
            packageField("package", required = true)
        )
        ActionType.SYSTEM_NETWORK_MODE -> schema(
            stringField("mode", default = "AUTO"),
            stringField("network_mask"),
            stringField("network_mask_schema"),
            stringField("network_subscription_id")
        )
        ActionType.SYSTEM_PRIVATE_DNS -> schema(
            enumField("mode", "OFF", "AUTOMATIC", "HOSTNAME", default = "AUTOMATIC"),
            stringField("hostname", expressionCapable = true)
        )
        ActionType.SYSTEM_SET_ALARM -> schema(
            integerField("hour", default = "7", min = 0.0, max = 23.0),
            integerField("minute", default = "0", min = 0.0, max = 59.0)
        )
        ActionType.SYSTEM_SET_TIMER -> schema(
            durationField("seconds", default = "300", min = 1.0, max = 86_400.0, expressionCapable = true),
            stringField("message", default = "NexaFlow timer", expressionCapable = true),
            booleanField("skipUi", default = "false")
        )
        ActionType.SYSTEM_SET_RINGTONE -> schema(
            stringField("uri", required = true)
        )
        ActionType.SYSTEM_SEND_SMS -> schema(
            stringField("number", required = true, expressionCapable = true),
            stringField("text", required = true, expressionCapable = true)
        )
        ActionType.SYSTEM_SEND_REMINDER -> schema(
            stringField("title", default = "Reminder", expressionCapable = true),
            stringField("text", expressionCapable = true),
            integerField("hour", default = "9", min = 0.0, max = 23.0),
            integerField("minute", default = "0", min = 0.0, max = 59.0)
        )
        ActionType.SYSTEM_OPEN_SETTINGS -> schema(
            enumField(
                "page",
                "SETTINGS",
                "ABOUT_PHONE",
                "ACCESSIBILITY",
                "AIRPLANE_MODE",
                "APP_SETTINGS_LIST",
                "BATTERY",
                "BLUETOOTH",
                "CAST",
                "DATA_SAVER",
                "DATA_USAGE",
                "DATE",
                "DEFAULT_APPS",
                "DEVELOPER",
                "DEVICE_ADMIN",
                "DISPLAY",
                "INPUT_METHOD",
                "LOCATION",
                "NETWORK",
                "NFC",
                "NOTIFICATION",
                "PRINT",
                "PRIVACY",
                "SECURITY",
                "SOUND",
                "STORAGE",
                "SYSTEM_UPDATE",
                "USAGE_ACCESS",
                "VPN",
                "WIFI",
                default = "WIFI"
            )
        )
        ActionType.SYSTEM_WAIT -> schema(
            durationField("seconds", default = "5", min = 0.0, expressionCapable = true)
        )
        ActionType.BATTERY_ALERTS -> schema(
            integerField("below", default = "20", min = 5.0, max = 100.0),
            stringField("message", default = "Battery alert triggered", expressionCapable = true),
            enumField("sound", "DEFAULT", "RINGTONE", "NOTIFICATION", "BEEP", "SILENT", default = "DEFAULT")
        )
        ActionType.BATTERY_CHARGING_NOTIFICATIONS -> schema(
            stringField("message", default = "Battery alert triggered", expressionCapable = true),
            enumField("sound", "DEFAULT", "RINGTONE", "NOTIFICATION", "BEEP", "SILENT", default = "DEFAULT")
        )
        ActionType.ADVANCED_SHIZUKU,
        ActionType.ADVANCED_ROOT -> schema(
            stringField("command", required = true, expressionCapable = true)
        )
        ActionType.SYSTEM_HTTP_REQUEST -> schema(
            urlField("url", required = true, expressionCapable = true),
            enumField("method", "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", default = "GET"),
            stringField("body", expressionCapable = true),
            stringField("headers", expressionCapable = true),
            booleanField("allowPrivateNetwork", default = "false"),
            integerField("timeoutMs", default = "10000", min = 1_000.0, max = 60_000.0),
            integerField("retryAttempts", default = "0", min = 0.0, max = 5.0),
            integerField("retryBaseDelayMs", default = "1000", min = 0.0, max = 60_000.0),
            integerField("retryCapMs", default = "60000", min = 0.0, max = 60_000.0),
            stringField("outputPath")
        )
        ActionType.PLUGIN_FIRE -> schema(
            packageField("package", required = true),
            stringField("receiver", required = true),
            stringField("pluginInstance"),
            enumField("pluginApproval", "approved"),
            enumField("pluginHighRiskApproval", "approved"),
            stringField("editActivity"),
            jsonField("bundleJson"),
            stringField("blurb")
        )
        ActionType.SYSTEM_VIBRATE -> schema(
            durationField("seconds", default = "1", min = 0.0, expressionCapable = true)
        )
        ActionType.SYSTEM_CLIPBOARD_SET,
        ActionType.SYSTEM_INPUT_TEXT,
        ActionType.SYSTEM_TOAST -> schema(
            stringField("text", required = true, expressionCapable = true)
        )
        ActionType.SYSTEM_SET_SETTING -> schema(
            enumField("namespace", "SYSTEM", "SECURE", "GLOBAL", default = "GLOBAL"),
            stringField("key", required = true),
            stringField("value", expressionCapable = true)
        )
        ActionType.ROM_CUSTOM_SETTING -> schema(
            enumField("namespace", "SYSTEM", "SECURE", "GLOBAL", default = "SECURE"),
            stringField("key", required = true),
            stringField("value", expressionCapable = true)
        )
        ActionType.SYSTEM_SCREENSHOT -> schema(
            stringField("filename", expressionCapable = true)
        )
        ActionType.SYSTEM_KEY_EVENT -> schema(
            stringField("key", required = true, expressionCapable = true)
        )
        ActionType.SYSTEM_INPUT_TAP -> schema(
            integerField("x", required = true, min = 0.0, expressionCapable = true),
            integerField("y", required = true, min = 0.0, expressionCapable = true)
        )
        ActionType.SYSTEM_INPUT_SWIPE -> schema(
            integerField("x1", required = true, min = 0.0, expressionCapable = true),
            integerField("y1", required = true, min = 0.0, expressionCapable = true),
            integerField("x2", required = true, min = 0.0, expressionCapable = true),
            integerField("y2", required = true, min = 0.0, expressionCapable = true),
            integerField("durationMs", default = "300", min = 0.0, max = 60_000.0)
        )
        ActionType.SYSTEM_LOCATION_MODE -> schema(
            enumField("mode", "OFF", "SENSORS", "BATTERY", "HIGH", default = "HIGH")
        )
        ActionType.SYSTEM_FONT_SCALE -> schema(
            decimalField("scale", default = "1.0", min = 0.5, max = 2.0, expressionCapable = true)
        )
        ActionType.SYSTEM_DISPLAY_DENSITY -> schema(
            integerField("dpi", default = "440", min = 72.0, expressionCapable = true),
            // Import compatibility for older agents/schemas; the runtime prefers dpi.
            integerField("density", min = 72.0, expressionCapable = true)
        )
        ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD -> schema(
            integerField("percent", default = "20", min = 0.0, max = 100.0, expressionCapable = true),
            // Legacy alias still read by ActionStateReader.
            integerField("level", min = 0.0, max = 100.0, expressionCapable = true)
        )
        ActionType.SYSTEM_CHARGING_LIMIT -> schema(
            integerField("percent", default = "80", min = 50.0, max = 100.0, expressionCapable = true)
        )
        ActionType.SYSTEM_CHARGING_FEEDBACK -> schema(
            booleanField("sound", default = "true"),
            booleanField("vibration", default = "true")
        )
        ActionType.SYSTEM_WIFI_SLEEP_POLICY -> schema(
            enumField("policy", "ALWAYS", "PLUGGED", "NEVER", default = "ALWAYS")
        )
        ActionType.SYSTEM_BLUETOOTH_DISCOVERABILITY -> schema(
            integerField("timeoutSeconds", default = "300", min = 0.0, max = 3_600.0)
        )
        ActionType.SYSTEM_HAPTIC_INTENSITY -> schema(
            integerField("level", default = "255", min = 0.0, max = 255.0, expressionCapable = true)
        )
        ActionType.SYSTEM_MEDIA_PLAY_FROM_SEARCH -> schema(
            stringField("query", required = true, expressionCapable = true),
            packageField("package")
        )
        ActionType.SYSTEM_REBOOT -> schema(
            enumField("mode", "NORMAL", "RECOVERY", "BOOTLOADER", default = "NORMAL")
        )
        ActionType.SYSTEM_ALERT -> schema(
            stringField("title", expressionCapable = true),
            stringField("text", required = true, expressionCapable = true)
        )
        ActionType.SYSTEM_VIBRATE_PATTERN -> schema(
            stringField("pattern", required = true, default = "0,200,100,200", expressionCapable = true)
        )
        ActionType.SYSTEM_WIFI_CONNECT -> schema(
            stringField("ssid", required = true, expressionCapable = true),
            NodeConfigField(
                key = "password",
                valueType = NodeConfigValueType.SECRET,
                sensitive = true,
                expressionCapable = true
            )
        )
        ActionType.SYSTEM_WIFI_FORGET -> schema(
            stringField("ssid", required = true, expressionCapable = true)
        )
        ActionType.SYSTEM_SCREENSAVER_TIMEOUT -> schema(
            integerField("minutes", default = "10", min = 0.0, expressionCapable = true)
        )
        ActionType.SYSTEM_POINTER_SPEED -> schema(
            integerField("speed", default = "0", min = -7.0, max = 7.0, expressionCapable = true)
        )
        ActionType.SYSTEM_INSTALL_APK -> schema(
            stringField("path", required = true, expressionCapable = true)
        )
        ActionType.SYSTEM_DIAL_NUMBER -> schema(
            stringField("number", required = true, expressionCapable = true)
        )
        ActionType.SYSTEM_OPEN_MAPS -> schema(
            coordinateField("lat", required = true, expressionCapable = true),
            coordinateField("lng", required = true, expressionCapable = true)
        )
        ActionType.SYSTEM_SEND_EMAIL -> schema(
            stringField("to", required = true, expressionCapable = true),
            stringField("subject", expressionCapable = true),
            stringField("body", expressionCapable = true)
        )
        ActionType.SYSTEM_SET_NOTIFICATION_TONE -> schema(
            stringField("tone")
        )
        ActionType.SYSTEM_SET_TIMEZONE -> schema(
            stringField("zone", required = true, default = "GMT", expressionCapable = true)
        )
        ActionType.ROM_QS_TILES -> schema(
            stringField("tiles"),
            integerField("columns", default = "4", min = 3.0, max = 5.0),
            enumField("brightness_slider", "0", "1", default = "1"),
            stringField("footer_text")
        )
        ActionType.ROM_STATUS_BAR -> schema(
            enumField("clock_position", "left", "center", "right", default = "right"),
            enumField("battery_style", "0", "1", "2", "3", default = "0"),
            enumField("battery_percent", "0", "1", default = "0"),
            enumField("clock_seconds", "0", "1", default = "0"),
            jsonField("config_json")
        )
        ActionType.ROM_LOCKSCREEN -> schema(
            enumField("clock_style", "0", "1", "2", "3", default = "0"),
            enumField("weather", "0", "1", default = "0"),
            enumField("shortcuts", "0", "1", default = "0"),
            enumField("media_art", "0", "1", default = "1"),
            jsonField("config_json")
        )
        ActionType.ROM_NAVIGATION -> schema(
            enumField("mode", "0", "1", "2", default = "2"),
            integerField("back_height", min = 0.0, max = 200.0)
        )
        ActionType.ROM_THEME -> schema(
            enumField("monet", "0", "1", default = "1"),
            stringField("accent", default = "#FF4081"),
            enumField("themed_icons", "0", "1", default = "0"),
            jsonField("config_json")
        )
        ActionType.ROM_AMBIENT_AOD -> schema(
            enumField("enabled", "0", "1", default = "0"),
            enumField("schedule", "0", "1", "2", default = "0")
        )
        ActionType.ROM_NOTIFICATIONS -> schema(
            enumField("heads_up", "0", "1", default = "1"),
            integerField("timeout", default = "5", min = 1.0, max = 30.0),
            enumField("less_boring", "0", "1", default = "0")
        )
        ActionType.DATA_TEXT,
        ActionType.DATA_ENCODING,
        ActionType.DATA_HASH,
        ActionType.DATA_RANDOM,
        ActionType.DATA_MATH,
        ActionType.DATA_DATE_TIME,
        ActionType.DATA_JSON,
        ActionType.DATA_ARRAY -> dataActionSchema(type)
        ActionType.ROM_BATCH -> schema(
            jsonField("batch_json", required = true)
        )
        else -> NodeConfigurationSchema()
    }

    private fun dataActionSchema(type: ActionType): NodeConfigurationSchema {
        val operations = com.nexaflow.domain.workflow.DataTransforms.operations.getValue(type)
        return schema(
            enumField("operation", *operations.toTypedArray(), default = operations.first()),
            stringField("input", expressionCapable = true),
            stringField("inputPath"),
            stringField("outputPath", default = "$.data.result"),
            stringField("argument", expressionCapable = true),
            stringField("replacement", expressionCapable = true),
            integerField("start", min = 0.0, expressionCapable = true),
            integerField("end", min = 0.0, expressionCapable = true),
            integerField("min", expressionCapable = true),
            integerField("max", expressionCapable = true),
            stringField("zone", default = "UTC")
        )
    }

    private val toggleActions: Set<ActionType> by lazy { setOf(
        ActionType.SYSTEM_LOCATION,
        ActionType.SYSTEM_DND,
        ActionType.SYSTEM_WIFI,
        ActionType.SYSTEM_BLUETOOTH,
        ActionType.SYSTEM_FLASHLIGHT,
        ActionType.SYSTEM_AIRPLANE_MODE,
        ActionType.SYSTEM_STAY_AWAKE,
        ActionType.SYSTEM_AUTO_BRIGHTNESS,
        ActionType.SYSTEM_MOBILE_DATA,
        ActionType.SYSTEM_HOTSPOT,
        ActionType.SYSTEM_NFC,
        ActionType.SYSTEM_POWER_SAVER,
        ActionType.SYSTEM_ANIMATIONS,
        ActionType.SYSTEM_DARK_MODE,
        ActionType.SYSTEM_COLOR_INVERSION,
        ActionType.SYSTEM_GRAYSCALE,
        ActionType.SYSTEM_EXTRA_DIM,
        ActionType.SYSTEM_NIGHT_LIGHT,
        ActionType.SYSTEM_HAPTIC_FEEDBACK,
        ActionType.SYSTEM_SOUND_EFFECTS,
        ActionType.SYSTEM_DATA_SAVER,
        ActionType.SYSTEM_SCREENSAVER,
        ActionType.SYSTEM_ALWAYS_ON_DISPLAY,
        ActionType.SYSTEM_SHOW_TAPS,
        ActionType.SYSTEM_POINTER_LOCATION,
        ActionType.SYSTEM_ADAPTIVE_BATTERY,
        ActionType.SYSTEM_AUTO_TIME,
        ActionType.SYSTEM_AUTO_TIMEZONE,
        ActionType.SYSTEM_CAMERA_SHUTTER_SOUND,
        ActionType.SYSTEM_WIFI_SCANNING,
        ActionType.SYSTEM_DATA_ROAMING,
        ActionType.SYSTEM_CALL_VIBRATION,
        ActionType.SYSTEM_STATUS_BAR_TOGGLE
    ) }


}
