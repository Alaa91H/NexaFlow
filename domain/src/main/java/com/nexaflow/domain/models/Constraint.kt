package com.nexaflow.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * A gate check (MacroDroid-style constraint) that must be satisfied BEFORE a
 * task's actions run. All constraints of a task are AND-ed: every one must
 * pass or the run is skipped.
 *
 * @param type which device condition is checked.
 * @param config type-specific parameters (e.g. `direction` + `level` for
 *   [ConstraintType.BATTERY]). Never mutate in place (Compose @Immutable).
 */
@Immutable
@Serializable
data class Constraint(
    val type: ConstraintType,
    val config: Map<String, String> = emptyMap()
)

@Serializable
enum class ConstraintType {
    /** The device must be connected to a Wi-Fi network. */
    WIFI,
    /** Battery level above or below a threshold (`direction` = ABOVE|BELOW, `level` = 0..100). */
    BATTERY,
    /** The screen must be locked (keyguard showing). */
    SCREEN_LOCKED,
    /** A wired headset must be plugged in. */
    HEADSET,
    /** Bluetooth must be enabled or disabled (`state` = ON|OFF). */
    BLUETOOTH,
    /** Do Not Disturb mode must be active or inactive (`state` = ON|OFF). */
    DND,
    /** Airplane mode must be on or off (`state` = ON|OFF). */
    AIRPLANE,
    /** Device must be charging or not (`state` = CHARGING|NOT_CHARGING). */
    CHARGING,
    /** Location services must be enabled or disabled (`state` = ON|OFF). */
    LOCATION,
    /**
     * Reads one saved, approved Locale-compatible plug-in condition. Its config
     * stores only an opaque `pluginInstance` plus protocol metadata; the Android
     * Bundle is decoded inside the execution adapter.
     */
    PLUGIN,
    /**
     * The current day/time must fall inside a schedule window. Config keys:
     * `days` (comma-separated ISO day numbers 1=Mon..7=Sun, empty = every day),
     * `start` (HH:mm), `end` (HH:mm; overnight windows that cross midnight are
     * supported, e.g. 22:00-06:00). Mirrors BlackList-style schedule rules so
     * any task — including call screening — can be time-gated without a
     * separate TIME trigger.
     */
    SCHEDULE
}
