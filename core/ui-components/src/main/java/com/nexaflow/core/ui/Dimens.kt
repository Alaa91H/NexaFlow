package com.nexaflow.core.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Central spacing & dimension tokens (Material 3 4dp grid).
 *
 * Every component must consume these instead of raw `12.dp`/`16.dp` literals
 * so density, tablet padding, and accessibility scaling stay coherent in one
 * place. Named after M3 spec roles (space-1 … space-12) plus component-level
 * dimensions that recur across screens.
 */
object Dimens {

    // --- Spacing scale (4dp grid, M3) ---
    val Space1: Dp = 4.dp
    val Space2: Dp = 8.dp
    val Space3: Dp = 12.dp
    val Space4: Dp = 16.dp
    val Space5: Dp = 20.dp
    val Space6: Dp = 24.dp
    val Space8: Dp = 32.dp
    val Space10: Dp = 40.dp
    val Space12: Dp = 48.dp
    val Space14: Dp = 56.dp

    // --- Component dimensions ---
    /** Horizontal gutter for screen content (16 on compact, 24 on expanded). */
    val ScreenHorizontalPadding: Dp = Space4

    /** Icon badge sizes — 40 on settings rows, 56 on key actions. */
    const val IconBadgeSmall: Int = 40
    const val IconBadgeLarge: Int = 56

    /** Settings row leading-icon circle. */
    const val RowIconSize: Int = 40

    /** Empty-state illustration size. */
    const val EmptyStateIconSize: Int = 56

    /** Bottom navigation bar / top app bar heights. */
    val TopAppBarHeight: Dp = 64.dp
    val NavigationBarHeight: Dp = 80.dp

    /** Navigation rail width (M3 spec). */
    val NavigationRailWidth: Dp = 80.dp

    /** Max content width for large screens (list-detail / centered panes). */
    val MaxContentWidth: Dp = 840.dp
}
