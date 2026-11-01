package com.nexaflow.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Which ambient wash a screen draws behind its content. The glow is static —
 * pure `drawBehind` with two radial gradients — and exists for two reasons:
 * it gives the Ink background depth, and it gives the glass Dock something
 * worth refracting.
 */
enum class ScreenGlowVariant {
    /** Two washes: primary top-start, tertiary lower-end. */
    Dashboard,

    /** One primary wash at the top-end; pickers, details, history, settings. */
    SubScreen
}

private val GLOW_TOP_START_OFFSET = Offset(-90f, -80f)
private const val SUB_SCREEN_TOP = -90f
private const val DASHBOARD_GLOW_B_TOP = 430f
private const val END_INSET = 120f

/**
 * Draws the Ink ambient glow behind this composable. The caller must place it
 * on the screen root so the wash sits beneath cards but above the background.
 * Alphas: 33% in dark, 12% in light (the light preview is derived, see §8).
 */
@Composable
fun Modifier.screenGlow(variant: ScreenGlowVariant = ScreenGlowVariant.SubScreen): Modifier {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val alpha = if (isDark) 0.33f else 0.12f
    return drawBehind {
        val unit = 1.dp.toPx()
        fun glow(color: Color, sizeDp: Dp, xDp: Float, yDp: Float) {
            val radius = sizeDp.toPx() / 2f
            val center = Offset(xDp * unit + radius, yDp * unit + radius)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = alpha),
                    0.7f to color.copy(alpha = 0f),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        when (variant) {
            ScreenGlowVariant.Dashboard -> {
                glow(
                    color = primary,
                    sizeDp = Dimens.GlowA,
                    xDp = GLOW_TOP_START_OFFSET.x,
                    yDp = GLOW_TOP_START_OFFSET.y
                )
                glow(
                    color = tertiary,
                    sizeDp = Dimens.GlowB,
                    // "end −120": the wash starts 120dp inside the end edge and
                    // overflows off-screen, so it reads as a corner light.
                    xDp = size.width / unit - END_INSET,
                    yDp = DASHBOARD_GLOW_B_TOP
                )
            }

            ScreenGlowVariant.SubScreen -> {
                glow(
                    color = primary,
                    sizeDp = Dimens.GlowB,
                    xDp = size.width / unit - END_INSET,
                    yDp = SUB_SCREEN_TOP
                )
            }
        }
    }
}
