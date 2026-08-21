package com.nexaflow.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Neutral surfaces for repeated, interactive content. Semantic states such as
 * selected, error, warning, and success deliberately keep their own colours;
 * this tone is only for the normal resting state.
 */
@Immutable
enum class AlternatingSurfaceTone {
    GRAY,
    DARK_GRAY
}

/** The stable even/odd pattern used by repeated cards, tabs, and option rows. */
fun alternatingSurfaceTone(index: Int): AlternatingSurfaceTone =
    if (index % 2 == 0) AlternatingSurfaceTone.GRAY else AlternatingSurfaceTone.DARK_GRAY

/**
 * Resolves a neutral, contrast-safe Material surface in either system theme.
 * The two container tiers intentionally differ enough to distinguish adjacent
 * entries without competing with icons, text, or semantic status colours.
 */
@Composable
fun alternatingSurfaceColor(index: Int): Color = when (alternatingSurfaceTone(index)) {
    AlternatingSurfaceTone.GRAY -> MaterialTheme.colorScheme.surfaceContainerLow
    AlternatingSurfaceTone.DARK_GRAY -> MaterialTheme.colorScheme.surfaceContainerHigh
}
