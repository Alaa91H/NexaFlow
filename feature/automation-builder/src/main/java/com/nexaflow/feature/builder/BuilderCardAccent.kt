package com.nexaflow.feature.builder

import androidx.compose.ui.graphics.Color

/**
 * Ordered neutral treatment for the selected cards in the builder.
 *
 * Cards deliberately alternate only between grey and darker grey, then repeat,
 * so a dense routine remains calm while adjacent selected triggers/actions are
 * still visually separable. The darker badge preserves an at-a-glance order
 * marker on both container shades.
 */
internal val builderCardAccentPalette: List<Color> = listOf(
    Color(0xFF5F6368),
    Color(0xFF3C4043)
)

private val builderCardContainerPalette: List<Color> = listOf(
    Color(0xFF2F3336), // Grey.
    Color(0xFF1E2022)  // Dark grey.
)

/** Light content remains legible on both ordered neutral container shades. */
internal val builderCardContentColor: Color = Color(0xFFF5F7F8)

internal fun builderCardAccent(index: Int): Color =
    builderCardAccentPalette[index % builderCardAccentPalette.size]

internal fun builderCardContainerColor(index: Int): Color =
    builderCardContainerPalette[index % builderCardContainerPalette.size]
