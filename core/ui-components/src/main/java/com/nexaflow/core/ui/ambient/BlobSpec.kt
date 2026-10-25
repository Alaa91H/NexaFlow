package com.nexaflow.core.ui.ambient

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * One soft shape drawn behind the app's content.
 *
 * The ambient shapes are the app's depth mechanism: they give the canvas a
 * sense of light without a single shadow, and they are what makes a dark
 * screen read as designed rather than as a black rectangle.
 *
 * @param color The shape's color. Its own alpha is applied on top of [alpha].
 * @param radiusFraction The shape's radius as a fraction of the canvas's smaller side.
 * @param centerX Horizontal centre as a fraction of the canvas width (0 = start, 1 = end).
 * @param centerY Vertical centre as a fraction of the canvas height (0 = top, 1 = bottom).
 * @param alpha How strongly the shape paints at its centre.
 * @param driftFraction How far the shape travels while it animates, as a fraction of the canvas.
 */
@Immutable
data class BlobSpec(
    val color: Color,
    val radiusFraction: Float,
    val centerX: Float,
    val centerY: Float,
    val alpha: Float = 0.18f,
    val driftFraction: Float = 0.03f
)
