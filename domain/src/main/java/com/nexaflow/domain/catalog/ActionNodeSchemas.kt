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
        ActionType.SYSTEM_OPEN_APP,
        ActionType.APPLICATION_LAUNCH_APP -> schema(
            stringField("packages"),
            packageField("package")
        )
        ActionType.SYSTEM_SEND_NOTIFICATION -> schema(
            stringField("title", expressionCapable = true),
            stringField("text", expressionCapable = true),
            stringField("sound"),
            jsonField("action_buttons")
        )
        ActionType.SYSTEM_BLOCK_NOTIFICATION,
        ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS,
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
            durationField("seconds", default = "300", min = 1.0, expressionCapable = true),
            stringField("message", expressionCapable = true),
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
            stringField("title", expressionCapable = true),
            stringField("text", expressionCapable = true),
            integerField("hour", min = 0.0, max = 23.0),
            integerField("minute", min = 0.0, max = 59.0)
        )
        ActionType.SYSTEM_OPEN_SETTINGS -> schema(
            stringField("page")
        )
        ActionType.SYSTEM_WAIT -> schema(
            durationField("seconds", default = "5", min = 0.0, expressionCapable = true)
        )
        ActionType.BATTERY_ALERTS -> schema(
            integerField("below", default = "20", min = 0.0, max = 100.0)
        )
        ActionType.ADVANCED_SHIZUKU,
        ActionType.ADVANCED_ROOT -> schema(
            stringField("command", required = true, expressionCapable = true)
        )
        ActionType.SYSTEM_HTTP_REQUEST -> schema(
            urlField("url", required = true, expressionCapable = true),
            enumField("method", "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", default = "GET"),
            stringField("body", expressionCapable = true),
            stringField("headers", expressionCapable = true),
            integerField("timeoutSeconds", min = 1.0)
        )
        ActionType.PLUGIN_FIRE -> schema(
            packageField("package", required = true),
            stringField("receiver", required = true),
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
        ActionType.SYSTEM_SET_SETTING,
        ActionType.ROM_CUSTOM_SETTING -> schema(
            enumField("namespace", "SYSTEM", "SECURE", "GLOBAL", default = "SYSTEM"),
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
            integerField("durationMs", min = 0.0)
        )
        ActionType.SYSTEM_FONT_SCALE -> schema(
            decimalField("scale", min = 0.5, max = 2.0, expressionCapable = true)
        )
        ActionType.SYSTEM_DISPLAY_DENSITY -> schema(
            integerField("density", min = 72.0, expressionCapable = true)
        )
        ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD -> schema(
            integerField("level", min = 0.0, max = 100.0, expressionCapable = true)
        )
        ActionType.SYSTEM_CHARGING_LIMIT -> schema(
            integerField("percent", min = 50.0, max = 100.0, expressionCapable = true)
        )
        ActionType.SYSTEM_CHARGING_FEEDBACK -> schema(
            booleanField("sound"),
            booleanField("vibration")
        )
        ActionType.SYSTEM_WIFI_SLEEP_POLICY -> schema(
            integerField("policy", min = 0.0, max = 2.0)
        )
        ActionType.SYSTEM_BLUETOOTH_DISCOVERABILITY -> schema(
            integerField("mode", min = 0.0, max = 2.0)
        )
        ActionType.SYSTEM_HAPTIC_INTENSITY -> schema(
            integerField("level", min = 0.0, max = 255.0, expressionCapable = true)
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
            stringField("pattern", required = true, expressionCapable = true)
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
            integerField("minutes", min = 0.0, expressionCapable = true)
        )
        ActionType.SYSTEM_POINTER_SPEED -> schema(
            integerField("speed", min = -7.0, max = 7.0, expressionCapable = true)
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
        ActionType.SYSTEM_SET_TIMEZONE -> schema(
            stringField("zone", required = true, expressionCapable = true)
        )
        ActionType.ROM_BATCH -> schema(
            jsonField("batch_json", required = true)
        )
        else -> NodeConfigurationSchema()
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
