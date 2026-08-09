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
    HEADSET
}
