package com.nexaflow.domain.schedule

/**
 * Pure matching logic for the battery (charging) trigger.
 *
 * The plugged constants mirror `android.os.BatteryManager` values so this
 * class stays unit-testable on the JVM without an Android runtime:
 * `BATTERY_PLUGGED_AC = 1`, `BATTERY_PLUGGED_USB = 2`,
 * `BATTERY_PLUGGED_WIRELESS = 4`.
 */
object BatteryTriggerMatcher {

    const val PLUGGED_AC = 1
    const val PLUGGED_USB = 2
    const val PLUGGED_WIRELESS = 4

    /** Config values stored in the trigger config map. */
    const val CHARGER_ANY = "ANY"
    const val CHARGER_AC = "AC"
    const val CHARGER_USB = "USB"
    const val CHARGER_WIRELESS = "WIRELESS"

    /** Maps the raw `EXTRA_PLUGGED` bitmask to a canonical type name. */
    fun plugTypeName(plugged: Int): String = when {
        plugged and PLUGGED_WIRELESS != 0 -> CHARGER_WIRELESS
        plugged and PLUGGED_AC != 0 -> CHARGER_AC
        plugged and PLUGGED_USB != 0 -> CHARGER_USB
        else -> "NONE"
    }

    /** The charger type the trigger filters on; defaults to ANY. */
    fun configuredChargerType(config: Map<String, String>): String =
        config["chargerType"] ?: CHARGER_ANY

    /** True when the battery level satisfies the configured direction + threshold. */
    fun levelCrossed(config: Map<String, String>, level: Int): Boolean {
        val threshold = config["above"]?.toIntOrNull() ?: 80
        val direction = config["direction"] ?: "ABOVE"
        return if (direction == "BELOW") level <= threshold else level >= threshold
    }

    /**
     * The battery trigger condition is satisfied when the level condition
     * holds AND (any charger type is accepted OR the current plug type matches
     * the configured one).
     */
    fun isActive(config: Map<String, String>, level: Int, plugged: Int): Boolean {
        if (!levelCrossed(config, level)) return false
        val required = configuredChargerType(config)
        if (required == CHARGER_ANY) return true
        return plugTypeName(plugged) == required
    }
}
