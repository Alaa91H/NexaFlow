package com.nexaflow.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * What a task should do with one of its actions when the task's condition
 * stops being true (Samsung Modes & Routines style).
 *
 * - [EndMode.LEAVE] — the change stays as the task left it.
 * - [EndMode.REVERT] — restore the device setting to its pre-run state.
 * - [EndMode.SET_VALUE] — apply a specific value at the end (e.g. volume 0).
 */
@Serializable
enum class EndMode {
    LEAVE,
    REVERT,
    SET_VALUE
}

@Immutable
@Serializable
// config must never be mutated in place (Compose @Immutable contract).
data class EndBehavior(
    val mode: EndMode = EndMode.LEAVE,
    /** Value applied at the end when [mode] is [EndMode.SET_VALUE]. */
    val config: Map<String, String> = emptyMap()
)

/**
 * Classifies every [ActionType] by the end-behavior options it can offer, so
 * the builder shows adaptive "when the task ends" chips per action:
 * toggles get on/off/revert, value actions get a value editor/revert, and the
 * remaining actions can only be left as they are.
 */
object EndBehaviorCatalog {

    /** Actions whose end options are on / off / revert to previous state. */
    val toggleActions: Set<ActionType> = setOf(
        ActionType.SYSTEM_WIFI,
        ActionType.SYSTEM_BLUETOOTH,
        ActionType.SYSTEM_NFC,
        ActionType.SYSTEM_MOBILE_DATA,
        ActionType.SYSTEM_HOTSPOT,
        ActionType.SYSTEM_AIRPLANE_MODE,
        ActionType.SYSTEM_DND,
        ActionType.SYSTEM_LOCATION,
        ActionType.SYSTEM_POWER_SAVER,
        ActionType.SYSTEM_ANIMATIONS,
        ActionType.SYSTEM_STAY_AWAKE,
        ActionType.SYSTEM_AUTO_BRIGHTNESS,
        ActionType.SYSTEM_DARK_MODE,
        ActionType.SYSTEM_FLASHLIGHT
    )

    /** Actions whose end options are a specific value / revert to previous. */
    val valueActions: Set<ActionType> = setOf(
        ActionType.SYSTEM_BRIGHTNESS,
        ActionType.SYSTEM_VOLUME,
        ActionType.SYSTEM_STREAM_VOLUME,
        ActionType.SYSTEM_RING_VOLUME,
        ActionType.SYSTEM_RINGER_MODE,
        ActionType.SYSTEM_SCREEN_TIMEOUT,
        ActionType.SYSTEM_SCREEN_ROTATION,
        ActionType.SYSTEM_NETWORK_MODE
    )

    /**
     * Actions whose only end option is "restore the previous value" — a
     * non-numeric change (a chosen ringtone) where a fixed end value would be
     * meaningless, but reverting to what the user had before is natural.
     */
    val revertOnlyActions: Set<ActionType> = setOf(
        ActionType.SYSTEM_SET_RINGTONE
    )

    /** True when the action offers per-action end behavior at all. */
    fun supportsEndBehavior(type: ActionType): Boolean =
        type in toggleActions || type in valueActions || type in revertOnlyActions

    /**
     * True when the action can restore its previous state on exit.
     * The flashlight is excluded: Android exposes no public API to read the
     * current torch state, so a "restore original" could never be captured.
     */
    fun supportsRevert(type: ActionType): Boolean =
        type != ActionType.SYSTEM_FLASHLIGHT && (type in toggleActions || type in valueActions)
}
