package com.nexaflow.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * NexaFlow One shape scale.
 *
 * The radii step up across the board compared with the Material 3 baseline
 * (4/8/12/16/28): cards read as 26dp, bottom sheets as 32dp, and dialogs as
 * 40dp. Every component consumes these through `MaterialTheme.shapes`, so one
 * change re-skins the whole app.
 */
val NexaFlowShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Radii the Material 3 [Shapes] scale has no slot for. */
object NexaFlowRadii {
    /** Dialogs and the largest panels. */
    val Giant: Dp = 40.dp

    /** The fully rounded ends of the navigation pill and of icon-only chips. */
    val Pill: Dp = 999.dp
}
