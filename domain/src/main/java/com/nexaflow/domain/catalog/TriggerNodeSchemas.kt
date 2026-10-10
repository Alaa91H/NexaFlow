package com.nexaflow.domain.catalog

import com.nexaflow.domain.models.TriggerType

/** Typed configuration contracts for persisted trigger kinds. */
internal object TriggerNodeSchemas {
    fun schemaFor(type: TriggerType): NodeConfigurationSchema {
        val base = when (type) {
        TriggerType.TIME -> schema(
            timeField("time", default = "08:00"),
            stringField("timeMode"),
            timeField("rangeStart"),
            timeField("rangeEnd"),
            stringField("repeat"),
            integerField("interval", min = 1.0, max = 99.0),
            enumField("intervalUnit", "DAY", "WEEK", "MONTH", "YEAR", default = "DAY"),
            stringField("days"),
            dateField("date"),
            dateField("startDate"),
            dateField("endDate"),
            stringField("endMode"),
            integerField("endCount", min = 1.0, max = 999.0),
            enumField("monthlyDayMode", "DAY_OF_MONTH", "FIRST_DAY", "LAST_DAY", default = "DAY_OF_MONTH"),
            integerField("monthDay", min = 1.0, max = 31.0),
            integerField("weekday", min = 1.0, max = 7.0),
            enumField("weekOfMonth", "1", "2", "3", "4", "5", "LAST", default = "1"),
            enumField("zonePolicy", "DEVICE_LOCAL", "FIXED_IANA", default = "DEVICE_LOCAL"),
            stringField("zoneId"),
            stringField("excludedDates")
        )
        TriggerType.BATTERY -> schema(
            enumField("direction", "ABOVE", "BELOW", default = "ABOVE"),
            integerField("threshold", min = 0.0, max = 100.0),
            integerField("above", default = "80", min = 0.0, max = 100.0),
            integerField("below", min = 0.0, max = 100.0),
            enumField("chargerType", "ANY", "AC", "USB", "WIRELESS", default = "ANY"),
            enumField("chargingState", "ANY", "CHARGING", "NOT_CHARGING", default = "ANY")
        )
        TriggerType.APPLICATION -> schema(
            stringField("packages", expressionCapable = true),
            packageField("package")
        )
        TriggerType.DEVICE -> schema(
            enumField(
                "event",
                "SCREEN_ON",
                "SCREEN_OFF",
                "POWER_CONNECTED",
                "POWER_DISCONNECTED",
                "HEADSET_CONNECTED",
                "HEADSET_DISCONNECTED",
                "BLUETOOTH_CONNECTED",
                "BLUETOOTH_DISCONNECTED",
                default = "SCREEN_ON"
            ),
            stringField("deviceName"),
            stringField("deviceAddress")
        )
        TriggerType.CONNECTIVITY -> schema(
            enumField("network", "WIFI", "MOBILE", default = "WIFI"),
            enumField("state", "CONNECTED", "DISCONNECTED", default = "CONNECTED")
        )
        TriggerType.WIFI_CONNECTED -> schema(
            enumField("state", "CONNECTED", "DISCONNECTED", default = "CONNECTED"),
            enumField("validated", "ANY", "YES", "NO", default = "ANY"),
            enumField("captivePortal", "ANY", "YES", "NO", default = "ANY"),
            enumField("metered", "ANY", "YES", "NO", default = "ANY"),
            stringField("ssid"),
            stringField("bssid")
        )
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
            enumField("event", "ENTER", "EXIT", default = "ENTER"),
            enumField("source", "current", "selected", default = "current")
        )
        TriggerType.SMS -> schema(
            stringField("from", expressionCapable = true),
            stringField("contains", expressionCapable = true),
            enumField("matchMode", "CONTAINS", "EXACT", "ANY", default = "CONTAINS")
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
            packageField("package"),
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
            enumField(
                "sensor",
                "PROXIMITY", "SHAKE", "LIGHT", "STEP",
                "PRESSURE", "TEMPERATURE", "HUMIDITY", "MAGNETIC",
                "ACCELERATION", "GYROSCOPE", "GRAVITY", "HINGE",
                default = "PROXIMITY"
            ),
            stringField("event", default = "COVERED"),
            decimalField("threshold", default = "200"),
            decimalField("upperThreshold"),
            decimalField("sensitivity", default = "14", min = 0.0),
            decimalField("calibrationOffset"),
            integerField("samplePeriodUs", default = "200000", min = 20000.0, max = 200000.0)
        )
        TriggerType.WEBHOOK -> schema(
            stringField("path", required = true, default = "/nexaflow"),
            enumField("method", "ANY", "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", default = "POST"),
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
            enumField("matchMode", "CONTAINS", "EXACT", "ANY", default = "ANY"),
            enumField("category", "ANY", "UNKNOWN", "PRIVATE", "CONTACT", default = "ANY")
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
        TriggerType.CELL_SIGNAL_STRENGTH -> thresholdSchema(default = "3", min = 0.0, max = 4.0)
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
            enumField("event", "SET", "CLEARED", default = "SET")
        )
        TriggerType.WEAR_EVENT -> schema(
            stringField("watchInstallId"),
            enumField("state", "CONNECTED", "DISCONNECTED", default = "CONNECTED")
        )
        TriggerType.BOOT_COMPLETED -> NodeConfigurationSchema()
        TriggerType.PLUGIN_EVENT -> schema(
            // Product storage keys are explicit here even though the trigger
            // is created only by the verified plugin configuration path.
            packageField("package"),
            stringField("eventComponent"),
            stringField("pluginInstance"),
            enumField("pluginApproval", "approved"),
            stringField("pluginEventId"),
            // Legacy T31 draft key remains readable during cutover.
            stringField("plugin_id")
        )
        }
        return withTemporalFilters(type, base)
    }

    private fun withTemporalFilters(type: TriggerType, base: NodeConfigurationSchema): NodeConfigurationSchema {
        val eventFilters = when (type) {
            TriggerType.APPLICATION, TriggerType.DEVICE, TriggerType.LOCATION, TriggerType.SMS,
            TriggerType.BLUETOOTH_DEVICE, TriggerType.NOTIFICATION, TriggerType.CALENDAR, TriggerType.WEBHOOK,
            TriggerType.ROM_SETTING, TriggerType.HEADPHONE, TriggerType.CHARGER, TriggerType.CALL_STATE,
            TriggerType.INCOMING_CALL, TriggerType.APP_INSTALLED, TriggerType.MEDIA_PLAYING,
            TriggerType.VOLUME_CHANGED, TriggerType.CLIPBOARD_CHANGED, TriggerType.TIMEZONE_CHANGED,
            TriggerType.NFC_TAG_SCANNED, TriggerType.ALARM_SET_CHANGED, TriggerType.WEAR_EVENT,
            TriggerType.PLUGIN_EVENT, TriggerType.BATTERY, TriggerType.BRIGHTNESS_LEVEL,
            TriggerType.WIFI_SIGNAL_STRENGTH, TriggerType.CELL_SIGNAL_STRENGTH,
            TriggerType.BATTERY_TEMPERATURE -> true
            else -> false
        }
        val fields = buildList {
            addAll(base.fields)
            if (eventFilters) {
                add(integerField("rateLimitCount", min = 1.0, max = 1_000.0))
                add(integerField("rateLimitWindowMs", min = 0.0, max = 604_800_000.0))
            }
            if (eventFilters) {
                add(integerField("minIntervalMs", min = 0.0, max = 604_800_000.0))
                add(integerField("cooldownMs", min = 0.0, max = 604_800_000.0))
            }
            if (type == TriggerType.VOLUME_CHANGED) {
                add(integerField("debounceMs", min = 0.0, max = 604_800_000.0))
            }
            if (type in setOf(
                    TriggerType.BATTERY,
                    TriggerType.VOLUME_CHANGED,
                    TriggerType.BRIGHTNESS_LEVEL,
                    TriggerType.WIFI_SIGNAL_STRENGTH,
                    TriggerType.CELL_SIGNAL_STRENGTH,
                    TriggerType.BATTERY_TEMPERATURE,
                )
            ) {
                add(integerField("stableForMs", min = 0.0, max = 604_800_000.0))
                val hysteresisMax = when (type) {
                    TriggerType.BATTERY, TriggerType.VOLUME_CHANGED -> 100.0
                    TriggerType.BRIGHTNESS_LEVEL -> 255.0
                    TriggerType.WIFI_SIGNAL_STRENGTH, TriggerType.CELL_SIGNAL_STRENGTH -> 4.0
                    else -> 100.0
                }
                add(decimalField("hysteresis", min = 0.0, max = hysteresisMax))
            }
        }
        return NodeConfigurationSchema(fields = fields, acceptsUnknownKeys = base.acceptsUnknownKeys)
    }

}
