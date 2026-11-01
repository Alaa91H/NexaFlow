package com.nexaflow.feature.builder

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Presentation-only vocabulary for the canonical schema renderer.
 *
 * The canonical editor used to print storage keys (`event`, `mode`, `threshold`)
 * and enum tokens (`NORMAL`, `POSTED`, `WIFI_CONNECTED`) straight into the UI.
 * Every user-visible label and every unit a value carries is resolved here, so
 * the renderer never invents copy and all node kinds are described with one
 * vocabulary. Pure Kotlin: the mapping is unit-tested without Compose.
 */
internal enum class SchemaValueUnit {
    NONE,
    PERCENT,
    /** 0..255 hardware brightness, shown to the user as a percentage. */
    BRIGHTNESS,
    CELSIUS,
    MEGABYTES,
    SECONDS,
    MINUTES,
    BARS,
}

internal data class SchemaFieldView(
    val labelRes: Int?,
    val unit: SchemaValueUnit = SchemaValueUnit.NONE,
)

internal fun schemaFieldView(fieldId: String): SchemaFieldView = when (fieldId) {
    "event" -> SchemaFieldView(R.string.event)
    "state" -> SchemaFieldView(R.string.state)
    "mode" -> SchemaFieldView(R.string.schema_field_mode)
    "direction" -> SchemaFieldView(R.string.volume_direction)
    "threshold" -> SchemaFieldView(R.string.volume_threshold, SchemaValueUnit.NONE)
    "stream" -> SchemaFieldView(R.string.volume_stream)
    "streams" -> SchemaFieldView(R.string.schema_field_streams)
    "contains" -> SchemaFieldView(R.string.notification_contains)
    "package", "packages" -> SchemaFieldView(R.string.schema_field_apps)
    "seconds" -> SchemaFieldView(R.string.action_screen_timeout, SchemaValueUnit.SECONDS)
    "minutes" -> SchemaFieldView(R.string.schema_field_minutes, SchemaValueUnit.MINUTES)
    "beforeMinutes" -> SchemaFieldView(R.string.schema_field_before_minutes, SchemaValueUnit.MINUTES)
    "radius" -> SchemaFieldView(R.string.schema_field_radius)
    "zone", "zoneId" -> SchemaFieldView(R.string.schema_field_timezone)
    "deviceName" -> SchemaFieldView(R.string.schema_field_device_name)
    "deviceAddress" -> SchemaFieldView(R.string.schema_field_device_address)
    "from" -> SchemaFieldView(R.string.schema_field_sender)
    "category" -> SchemaFieldView(R.string.schema_field_category)
    "matchMode" -> SchemaFieldView(R.string.schema_field_match_mode)
    "calendar" -> SchemaFieldView(R.string.schema_field_calendar)
    "key" -> SchemaFieldView(R.string.rom_custom_key_label)
    "value" -> SchemaFieldView(R.string.rom_setting_operator_value)
    "namespace" -> SchemaFieldView(R.string.rom_setting_namespace)
    "operator" -> SchemaFieldView(R.string.rom_setting_operator)
    "sensor" -> SchemaFieldView(R.string.sensor_kind)
    "level" -> SchemaFieldView(R.string.schema_field_level)
    "percent", "percentage" -> SchemaFieldView(R.string.volume_threshold, SchemaValueUnit.PERCENT)
    "brightness" -> SchemaFieldView(R.string.action_brightness, SchemaValueUnit.BRIGHTNESS)
    "temperature" -> SchemaFieldView(R.string.schema_field_temperature, SchemaValueUnit.CELSIUS)
    else -> SchemaFieldView(null)
}

/**
 * Value units inferred from the field itself when the schema only carries an id:
 * a threshold on a brightness trigger is a 0..255 level, a storage threshold is
 * megabytes, a battery-temperature threshold is Celsius.
 */
internal fun schemaValueUnit(fieldId: String, schemaTarget: String?): SchemaValueUnit {
    val declared = schemaFieldView(fieldId).unit
    if (declared != SchemaValueUnit.NONE) return declared
    if (fieldId != "threshold") return SchemaValueUnit.NONE
    return when {
        schemaTarget?.contains("brightness", ignoreCase = true) == true -> SchemaValueUnit.BRIGHTNESS
        schemaTarget?.contains("storage", ignoreCase = true) == true -> SchemaValueUnit.MEGABYTES
        schemaTarget?.contains("temperature", ignoreCase = true) == true -> SchemaValueUnit.CELSIUS
        schemaTarget?.contains("volume", ignoreCase = true) == true -> SchemaValueUnit.PERCENT
        schemaTarget?.contains("battery", ignoreCase = true) == true -> SchemaValueUnit.PERCENT
        schemaTarget?.contains("signal", ignoreCase = true) == true -> SchemaValueUnit.BARS
        else -> SchemaValueUnit.NONE
    }
}

/** Translated label for an enum token; null when the token has no resource. */
internal fun schemaTokenLabel(token: String): Int? = when (token.uppercase(Locale.ROOT)) {
    "ON" -> R.string.builder_state_on
    "OFF" -> R.string.builder_state_off
    "CONNECTED" -> R.string.state_connected
    "DISCONNECTED" -> R.string.state_disconnected
    "ABOVE" -> R.string.constraint_above
    "BELOW" -> R.string.constraint_below
    "NORMAL" -> R.string.schema_token_normal
    "VIBRATE" -> R.string.ringer_vibrate
    "SILENT" -> R.string.ringer_silent
    "LOCKED" -> R.string.device_locked
    "UNLOCKED" -> R.string.schema_token_unlocked
    "PORTRAIT" -> R.string.rotation_portrait
    "LANDSCAPE" -> R.string.rotation_landscape
    "POSTED" -> R.string.schema_token_posted
    "REMOVED" -> R.string.app_removed
    "STARTED" -> R.string.schema_token_started
    "STOPPED" -> R.string.schema_token_stopped
    "INSTALLED" -> R.string.app_installed
    "UPDATED" -> R.string.app_updated
    "ANY" -> R.string.schema_token_any
    "YES" -> R.string.schema_token_yes
    "NO" -> R.string.schema_token_no
    "MUSIC" -> R.string.volume_stream_music
    "RING" -> R.string.volume_stream_ring
    "ALARM" -> R.string.volume_stream_alarm
    "NOTIFICATION" -> R.string.volume_stream_notification
    "INCOMING" -> R.string.call_incoming
    "OUTGOING" -> R.string.call_outgoing
    "ENDED" -> R.string.call_ended
    "CONTAINS" -> R.string.schema_token_contains
    "EXACT" -> R.string.schema_token_exact
    "SYSTEM" -> R.string.rom_setting_namespace_system
    "SECURE" -> R.string.rom_setting_namespace_secure
    "GLOBAL" -> R.string.rom_setting_namespace_global
    "EQUALS" -> R.string.rom_setting_equals
    "NOT_EQUALS" -> R.string.rom_setting_not_equals
    "AUTO", "AUTOMATIC" -> R.string.network_mode_auto
    "SET" -> R.string.schema_token_alarm_set
    "CLEARED" -> R.string.schema_token_alarm_cleared
    "SCREEN_ON" -> R.string.device_screen_on
    "SCREEN_OFF" -> R.string.device_screen_off
    "CHARGING" -> R.string.charging_yes
    "NOT_CHARGING" -> R.string.charging_no
    "AC" -> R.string.charger_ac
    "USB" -> R.string.charger_usb
    "WIRELESS" -> R.string.charger_wireless
    "WIFI" -> R.string.network_wifi
    "MOBILE" -> R.string.network_mobile
    "CONTACT" -> R.string.call_category_contact
    "PRIVATE" -> R.string.call_category_private
    "UNKNOWN" -> R.string.call_category_unknown
    "EVENT_START" -> R.string.calendar_event_start
    "EVENT_END" -> R.string.calendar_event_end
    else -> null
}

/**
 * Humanises an unmapped token (`FIRST_DAY` → "First day") so no screen ever
 * shows an upper-case storage constant. Mapped tokens always win.
 */
internal fun humanizeSchemaToken(token: String): String =
    token.lowercase(Locale.ROOT)
        .split('_', '-', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.getDefault()) } }


/**
 * Converts a stored raw value into the text a slider row shows, applying the
 * field's unit (brightness 0..255 → percent, storage MB → GB, …).
 */
internal fun schemaValueText(
    raw: String,
    fieldId: String,
    schemaTarget: String?,
    percentPattern: String,
    celsiusPattern: String,
    megabytePattern: String,
    gigabytePattern: String,
    secondsPattern: String,
    minutesPattern: String,
    barsPattern: String,
): String {
    val number = raw.trim().replace(',', '.').toDoubleOrNull() ?: return raw
    return when (schemaValueUnit(fieldId, schemaTarget)) {
        SchemaValueUnit.PERCENT -> percentPattern.format(number.roundToLong().toInt())
        SchemaValueUnit.BRIGHTNESS ->
            percentPattern.format((number / 255.0 * 100).roundToLong().toInt().coerceIn(0, 100))
        SchemaValueUnit.CELSIUS -> celsiusPattern.format(trimDecimal(number))
        SchemaValueUnit.MEGABYTES -> if (number >= 1024) {
            gigabytePattern.format(trimDecimal(number / 1024.0))
        } else {
            megabytePattern.format(trimDecimal(number))
        }
        SchemaValueUnit.SECONDS -> secondsPattern.format(number.roundToLong())
        SchemaValueUnit.MINUTES -> minutesPattern.format(number.roundToLong())
        SchemaValueUnit.BARS -> barsPattern.format(number.roundToLong().toInt())
        SchemaValueUnit.NONE -> trimDecimal(number)
    }
}

private fun trimDecimal(value: Double): String =
    if (value == value.roundToLong().toDouble()) value.roundToLong().toString()
    else String.format(Locale.US, "%.1f", value)

/**
 * Slider bounds for a numeric schema field. Falls back to the schema's declared
 * minimum/maximum, then to a unit-specific default so a bounded control is
 * always available (the redesign never asks for a typed number).
 */
internal fun schemaSliderBounds(
    fieldId: String,
    schemaTarget: String?,
    minimum: Long?,
    maximum: Long?,
): ClosedFloatingPointRange<Float> {
    if (minimum != null && maximum != null && minimum < maximum) {
        return minimum.toFloat()..maximum.toFloat()
    }
    val unit = schemaValueUnit(fieldId, schemaTarget)
    return when (unit) {
        SchemaValueUnit.BRIGHTNESS -> 0f..255f
        SchemaValueUnit.PERCENT -> 0f..100f
        SchemaValueUnit.CELSIUS -> 0f..80f
        SchemaValueUnit.MEGABYTES -> 0f..8192f
        SchemaValueUnit.BARS -> 0f..4f
        SchemaValueUnit.SECONDS -> 0f..3600f
        SchemaValueUnit.MINUTES -> 0f..180f
        SchemaValueUnit.NONE -> 0f..100f
    }
}

/** Step size for one −/+ press on a numeric field. */
internal fun schemaStepSize(fieldId: String, schemaTarget: String?): Float =
    when (schemaValueUnit(fieldId, schemaTarget)) {
        SchemaValueUnit.BRIGHTNESS -> 5f
        SchemaValueUnit.CELSIUS -> 0.5f
        SchemaValueUnit.MEGABYTES -> 100f
        else -> 1f
    }

/**
 * ROM-defined screen-timeout choices, in seconds. The device's own current
 * value is prepended by the editor, so the list always reflects what the ROM
 * actually uses instead of a theoretical number typed by hand.
 */
internal val screenTimeoutPresetsSeconds: List<Long> = listOf(
    15, 30, 60, 120, 300, 600, 1800, 3600
)

internal fun formatScreenTimeout(seconds: Long, minutesPattern: String, secondsPattern: String): String =
    if (seconds >= 60 && seconds % 60L == 0L) minutesPattern.format(seconds / 60) else secondsPattern.format(seconds)
