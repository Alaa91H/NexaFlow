package com.nexaflow.feature.builder

import androidx.compose.ui.graphics.Color

/**
 * Stable accent cycle for cards in a builder section.
 *
 * The index belongs to the user-visible ordered list, so cards remain visually
 * distinct even when two entries have the same trigger/action type. The palette
 * uses deep Material-compatible tones; callers use a low alpha for the card
 * container and the full tone for badges/icons.
 */
internal val builderCardAccentPalette: List<Color> = listOf(
    Color(0xFF0B57D0), // blue
    Color(0xFF6750A4), // violet
    Color(0xFF006A6C), // teal
    Color(0xFF8F4C00), // amber
    Color(0xFF006D3C), // green
    Color(0xFFC2185B), // pink
    Color(0xFF455A64), // slate
    Color(0xFF387908)  // lime
)

internal fun builderCardAccent(index: Int): Color =
    builderCardAccentPalette[index.mod(builderCardAccentPalette.size)]

internal fun builderCardContainerColor(index: Int): Color =
    builderCardAccent(index).copy(alpha = 0.13f)
