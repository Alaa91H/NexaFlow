package com.nexaflow.core.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The gradients the app paints with.
 *
 * Gradients are the app's quick-glance coding: an automation, an app or a
 * status carries its own color so a list can be scanned without reading a
 * word. The palette is a fixed set of pairs, not a random hue per item —
 * a random hue reads as noise, a curated set reads as a system.
 */
object NexaFlowGradients {

    /** The brand gradient: indigo → violet → aqua. */
    val brand: List<Color> = listOf(
        NexaFlowBrandStart,
        NexaFlowBrandViolet,
        NexaFlowBrandEnd
    )

    /**
     * The brand gradient for surfaces that carry white glyphs or white text.
     *
     * Both stops clear 4.5:1 against white, so a glyph stays legible
     * wherever it lands on the gradient. The expressive [brand] gradient is
     * for large fills that carry no text.
     */
    val brandSolid: List<Color> = listOf(
        NexaFlowBrandStart,
        Color(0xFF6D3BC0)
    )

    /** The brand gradient tuned for the dark canvas. */
    val brandDark: List<Color> = listOf(
        NexaFlowBrandStartDark,
        NexaFlowBrandVioletDark,
        NexaFlowBrandEndDark
    )

    /**
     * The item gradients. Each is a two-stop pair so the pair reads as one
     * color family at small sizes, with the brand gradient kept for the
     * items that should feel like the app's own.
     */
    val items: List<List<Color>> = listOf(
        listOf(Color(0xFF3B5BFF), Color(0xFF6D3BC0)),
        listOf(Color(0xFF0B6E62), Color(0xFF00768F)),
        listOf(Color(0xFF7A3EC8), Color(0xFF9333EA)),
        listOf(Color(0xFF127A4C), Color(0xFF2F7D46)),
        listOf(Color(0xFF8A5200), Color(0xFF9A5B00)),
        listOf(Color(0xFFA8241E), Color(0xFFC62A2F)),
        listOf(Color(0xFF1B4ED8), Color(0xFF00629E)),
        listOf(Color(0xFF7326AE), Color(0xFFA62162))
    )

    /**
     * The gradient for the item at [index]. The index is taken modulo the
     * palette size, so a caller can hand it any stable identifier — a
     * task id hashed to an Int — and always land on the same color for the
     * same item.
     */
    fun itemAt(index: Int): List<Color> = items[((index % items.size) + items.size) % items.size]

    /**
     * The gradient for a stable key. A hash of the key picks the entry, so
     * the same automation keeps its color across launches and reinstalls.
     */
    fun itemFor(key: String): List<Color> = itemAt(key.hashCode())

    /** The brand gradient as a brush, for the current canvas. */
    fun brandBrush(dark: Boolean): Brush =
        Brush.linearGradient(if (dark) brandDark else brand)

    /** An item gradient as a brush. */
    fun itemBrush(colors: List<Color>): Brush = Brush.linearGradient(colors)
}
