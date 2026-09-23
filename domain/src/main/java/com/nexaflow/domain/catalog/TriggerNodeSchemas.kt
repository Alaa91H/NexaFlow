package com.nexaflow.domain.catalog

import com.nexaflow.domain.models.TriggerType

/** Typed configuration contracts for persisted trigger kinds. */
internal object TriggerNodeSchemas {
    fun schemaFor(type: TriggerType): NodeConfigurationSchema = when (type) {
        TriggerType.TIME -> schema(
            timeField("time", default = "08:00"),
            stringField("timeMode"),
            timeField("rangeStart"),
            timeField("rangeEnd"),
            stringField("repeat"),
            integerField("interval", min = 1.0, max = 99.0),
            enumField("intervalUnit", "DAY", "WEEK", "MONTH", "YEAR"),
            stringField("days"),
            dateField("startDate"),
            dateField("endDate"),
            stringField("endMode"),
            integerField("endCount", min = 1.0, max = 999.0)
        )
        TriggerType.BATTERY -> schema(
            enumField("direction", "ABOVE", "BELOW", default = "ABOVE"),
            integerField("above", default = "80", min = 0.0, max = 100.0),
            integerField("below", min = 0.0, max = 100.0),
            stringField("chargerType", default = "ANY")
        )
        TriggerType.APPLICATION -> schema(
            stringField("packages", expressionCapable = true)
        )
        TriggerType.DEVICE -> schema(
            stringField("event", default = "SCREEN_ON")
        )
        TriggerType.CONNECTIVITY -> schema(
            enumField("network", "WIFI", "MOBILE", default = "WIFI"),
            enumField("state", "CONNECTED", "DISCONNECTED", default = "CONNECTED")
        )
        TriggerType.WIFI_CONNECTED,
        TriggerType.MOBILE_DATA_CONNECTED -> schema(
            enumField("state", "CONNECTED", "DISCONNECTED", default = "CONNECTED")
        )
        TriggerType.HOTSPOT,
        TriggerType.AIRPLANE_MODE,
        TriggerType.POWER_SAVER,
        TriggerType.BLUETOOTH_STATE,
        TriggerType.AUTO_ROTATE,
        TriggerType.DATA_SAVER_STATE,
        TriggerType.WIFI_STATE,
        TriggerType.NFC_STATE,
        TriggerType.USB_CONNECTED,
        TriggerType.HDMI_CONNECTED,
        TriggerType.ETHERNET_CONNECTED,
        TriggerType.VPN_CONNECTED,
        TriggerType.DND_STATE,
        TriggerType.STAY_AWAKE_STATE,
        TriggerType.AUTO_BRIGHTNESS_STATE,
        TriggerType.DATA_ROAMING_STATE -> schema(
            enumField("state", "ON", "OFF", default = "ON")
        )
        TriggerType.LOCATION -> schema(
            coordinateField("lat", required = true),
            coordinateField("lng", required = true),
            integerField("radius", default = "100", min = 50.0, max = 2000.0),
            enumField("event", "ENTER", "EXIT", default = "ENTER")
        )
        TriggerType.SMS -> schema(
            stringField("from", expressionCapable = true),
            stringField("contains", expressionCapable = true)
        )
        TriggerType.BLUETOOTH_DEVICE -> schema(
            stringField("deviceName"),
            stringField("deviceAddress"),
            enumField("event", "CONNECTED", "DISCONNECTED", default = "CONNECTED")
        )
        TriggerType.RINGER_MODE -> schema(
            enumField("mode", "NORMAL", "VIBRATE", "SILENT", default = "NORMAL")
        )
        TriggerType.NETWORK_MODE -> schema(
            enumField("state", "AUTO", "2G", "3G", "4G", "5G", default = "4G")
        )
        TriggerType.NOTIFICATION -> schema(
            stringField("packages"),
            stringField("contains", expressionCapable = true),
            enumField("event", "POSTED", "REMOVED", default = "POSTED")
        )
        TriggerType.CALENDAR -> schema(
            stringField("calendar"),
            stringField("contains", expressionCapable = true),
            stringField("event", default = "EVENT_START"),
            integerField("beforeMinutes", default = "0", min = 0.0)
        )
        TriggerType.SENSOR -> schema(
            enumField("sensor", "PROXIMITY", "SHAKE", "LIGHT", "STEP", default = "PROXIMITY"),
            stringField("event", default = "COVERED"),
            decimalField("threshold", default = "200"),
            decimalField("sensitivity", default = "14", min = 0.0)
        )
        TriggerType.WEBHOOK -> schema(
            stringField("path", required = true, default = "/nexaflow"),
            enumField("method", "GET", "POST", "PUT", "PATCH", "DELETE", default = "POST"),
            secretField("token", required = true)
        )
        TriggerType.ROM_SETTING -> schema(
            enumField("namespace", "SYSTEM", "SECURE", "GLOBAL", default = "SYSTEM"),
            stringField("key", required = true),
            enumField("operator", "EQUALS", "NOT_EQUALS", default = "EQUALS"),
            stringField("value", expressionCapable = true)
        )
        TriggerType.HEADPHONE,
        TriggerType.CHARGER -> schema(
            enumField("event", "CONNECTED", "DISCONNECTED", default = "CONNECTED")
        )
        TriggerType.DARK_MODE -> schema(
            enumField("state", "ON", "OFF", default = "ON")
        )
        TriggerType.CALL_STATE -> schema(
            enumField("event", "INCOMING", "OUTGOING", "ENDED", default = "INCOMING")
        )
        TriggerType.INCOMING_CALL -> schema(
            stringField("from"),
            enumField("matchMode", "CONTAINS", "EXACT", "ANY"),
            enumField("category", "ANY", "UNKNOWN", "PRIVATE")
        )
        TriggerType.APP_INSTALLED -> schema(
            enumField("event", "INSTALLED", "REMOVED", "UPDATED", default = "INSTALLED"),
            packageField("package")
        )
        TriggerType.MEDIA_PLAYING -> schema(
            enumField("event", "STARTED", "STOPPED", default = "STARTED")
        )
        TriggerType.VOLUME_CHANGED -> schema(
            enumField("stream", "MUSIC", "RING", "ALARM", "NOTIFICATION", default = "MUSIC"),
            integerField("threshold", default = "50", min = 0.0, max = 100.0),
            enumField("direction", "ABOVE", "BELOW", default = "ABOVE")
        )
        TriggerType.BRIGHTNESS_LEVEL -> thresholdSchema(default = "128", min = 0.0, max = 255.0)
        TriggerType.STORAGE_LOW -> thresholdSchema(default = "1024", min = 0.0, direction = "BELOW")
        TriggerType.DEVICE_LOCKED -> schema(
            enumField("state", "LOCKED", "UNLOCKED", default = "LOCKED")
        )
        TriggerType.LOCATION_STATE -> schema(
            stringField("mode", default = "ON")
        )
        TriggerType.SCREEN_ROTATION_STATE -> schema(
            enumField("state", "PORTRAIT", "LANDSCAPE", default = "PORTRAIT")
        )
        TriggerType.WIFI_SIGNAL_STRENGTH,
        TriggerType.CELL_SIGNAL_STRENGTH -> thresholdSchema(default = "3", min = 0.0, max = 100.0)
        TriggerType.BATTERY_TEMPERATURE -> thresholdSchema(default = "40", min = -50.0, max = 100.0)
        TriggerType.CLIPBOARD_CHANGED -> schema(
            stringField("contains", expressionCapable = true)
        )
        TriggerType.SCREEN_TIMEOUT_CHANGED -> schema(
            durationField("seconds", min = 0.0)
        )
        TriggerType.TIMEZONE_CHANGED -> schema(
            stringField("zone")
        )
        TriggerType.NFC_TAG_SCANNED -> schema(
            stringField("contains")
        )
        TriggerType.ALARM_SET_CHANGED -> schema(
            enumField("event", "SET", "CLEARED")
        )
        TriggerType.WEAR_EVENT -> schema(
            stringField("watchInstallId"),
            enumField("state", "CONNECTED", "DISCONNECTED", default = "CONNECTED")
        )
        TriggerType.BOOT_COMPLETED,
        TriggerType.PLUGIN_EVENT -> NodeConfigurationSchema()
    }


}
