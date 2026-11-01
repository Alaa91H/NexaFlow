package com.nexaflow.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.nexaflow.core.ui.theme.DarkNexaFlowCategoryColors
import com.nexaflow.core.ui.theme.DarkNexaFlowColors
import com.nexaflow.core.ui.theme.LightNexaFlowCategoryColors
import com.nexaflow.core.ui.theme.LightNexaFlowColors
import com.nexaflow.core.ui.theme.NexaFlowAlphas
import com.nexaflow.core.ui.theme.NexaFlowCategory
import com.nexaflow.core.ui.theme.NexaFlowCategoryColors
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ink Glass ships two schemes and seven category hues; §5.1/§8 require every
 * text pair to clear 4.5:1 (3:1 for large text and non-text boundaries) and the
 * light theme may never carry a value that was not contrast-checked. This gate
 * checks the real schemes — the same `googleColorScheme` the app renders — and
 * the composited tile fills, so a palette edit that dims a label fails here
 * instead of on a device.
 */
class InkContrastTest {

    private val ink = accentSeeds.getValue("ink")

    private fun scheme(dark: Boolean): ColorScheme = googleColorScheme(ink, dark)

    // --- WCAG 2.1 relative luminance and contrast -------------------------

    private fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(foreground: Color, background: Color): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** Source-over composite of [foreground] at [alpha] on an opaque [background]. */
    private fun over(foreground: Color, alpha: Float, background: Color): Color = Color(
        red = foreground.red * alpha + background.red * (1 - alpha),
        green = foreground.green * alpha + background.green * (1 - alpha),
        blue = foreground.blue * alpha + background.blue * (1 - alpha),
        alpha = 1f
    )

    private fun assertText(token: String, foreground: Color, background: Color) {
        val ratio = contrast(foreground, background)
        assertTrue(
            "$token must reach 4.5:1 but is %.2f:1".format(ratio),
            ratio >= 4.5
        )
    }

    private fun assertBoundary(token: String, foreground: Color, background: Color) {
        val ratio = contrast(foreground, background)
        assertTrue(
            "$token must reach 3:1 but is %.2f:1".format(ratio),
            ratio >= 3.0
        )
    }

    // --- Dark Ink ---------------------------------------------------------

    @Test
    fun darkTextPairsClearBodyTextContrast() {
        val colors = scheme(dark = true)
        assertText("primary text on background", colors.onSurface, colors.surface)
        assertText("meta text on card", colors.onSurfaceVariant, colors.surfaceContainerLow)
        assertText("section label on background", colors.onSurfaceVariant, colors.surface)
        assertText("eyebrow accentLabel on background", DarkNexaFlowColors.accentLabel, colors.surface)
        assertText("Add button label on primary", colors.onPrimary, colors.primary)
        assertText("picker row title on card", colors.onSurface, colors.surfaceContainerLow)
    }

    @Test
    fun darkDockStaysReadableInBothGlassModes() {
        val colors = scheme(dark = true)
        // Blur mode: the dock fill is 65% surfaceContainerHigh over screen content.
        val blurred = over(colors.surfaceContainerHigh, 0.65f, colors.surfaceContainerLow)
        // Fallback mode (§10): 94% surfaceContainerHigh.
        val solid = over(colors.surfaceContainerHigh, 0.94f, colors.surfaceContainerLow)
        assertText("dock label in glass mode", colors.onSurface, blurred)
        assertText("dock label in fallback mode", colors.onSurface, solid)
        // Ask pill: 6% onSurface fill inside the dock, label uses onPrimaryContainer.
        val pill = over(colors.onSurface, NexaFlowAlphas.AskPillFill, solid)
        assertText("ask pill label", DarkNexaFlowColors.askPillLabel, pill)
        assertText("dock primary button label", colors.onPrimary, colors.primary)
    }

    @Test
    fun darkBordersStayVisibleWithoutBeingLoud() {
        val colors = scheme(dark = true)
        assertBoundary("unselected checkbox border", colors.outline, colors.surfaceContainerLow)
        // Material's default `outline` thumb measures 2.2:1 here, so Ink names
        // its own off-thumb neutral (§5.1) and the shared switch uses it.
        assertBoundary(
            "toggle off thumb",
            DarkNexaFlowColors.switchThumbOff,
            colors.surfaceContainerHighest
        )
    }

    @Test
    fun lightToggleOffThumbReadsOnItsTrack() {
        val colors = scheme(dark = false)
        assertBoundary(
            "toggle off thumb",
            LightNexaFlowColors.switchThumbOff,
            colors.surfaceContainerHighest
        )
    }

    @Test
    fun darkCategoryIconsReadOnTheirOwnTiles() {
        val colors = scheme(dark = true)
        assertCategories(
            palette = DarkNexaFlowCategoryColors,
            card = colors.surfaceContainerLow
        )
    }

    @Test
    fun darkStatusPillsReadOnTheirTint() {
        val colors = scheme(dark = true)
        val card = colors.surfaceContainerLow
        val running = over(DarkNexaFlowColors.success, NexaFlowAlphas.SuccessPillFill, card)
        assertText("running pill label", DarkNexaFlowColors.success, running)
        val permission = over(colors.errorContainer, NexaFlowAlphas.SuccessPillFill, card)
        assertText("needs-permission pill label", colors.onErrorContainer, permission)
    }

    // --- Light Ink --------------------------------------------------------

    @Test
    fun lightTextPairsClearBodyTextContrast() {
        val colors = scheme(dark = false)
        assertText("primary text on background", colors.onSurface, colors.surface)
        assertText("meta text on card", colors.onSurfaceVariant, colors.surfaceContainerLow)
        assertText("section label on background", colors.onSurfaceVariant, colors.surface)
        assertText("eyebrow accentLabel on background", LightNexaFlowColors.accentLabel, colors.surface)
        assertText("Add button label on primary", colors.onPrimary, colors.primary)
        assertText("picker row title on card", colors.onSurface, colors.surfaceContainerLow)
    }

    @Test
    fun lightDockStaysReadableInBothGlassModes() {
        val colors = scheme(dark = false)
        // §8: light dock fill is `surface` at 72% with a 60% white border.
        val blurred = over(colors.surface, 0.72f, colors.surfaceContainerLow)
        val solid = over(colors.surface, 0.94f, colors.surfaceContainerLow)
        assertText("dock label in glass mode", colors.onSurface, blurred)
        assertText("dock label in fallback mode", colors.onSurface, solid)
        val pill = over(colors.onSurface, NexaFlowAlphas.AskPillFill, solid)
        assertText("ask pill label", LightNexaFlowColors.askPillLabel, pill)
        assertText("dock primary button label", colors.onPrimary, colors.primary)
    }

    @Test
    fun lightCategoryIconsReadOnTheirOwnTiles() {
        val colors = scheme(dark = false)
        assertCategories(
            palette = LightNexaFlowCategoryColors,
            card = colors.surfaceContainerLow
        )
    }

    @Test
    fun lightStatusPillsReadOnTheirTint() {
        val colors = scheme(dark = false)
        val card = colors.surfaceContainerLow
        val running = over(LightNexaFlowColors.success, NexaFlowAlphas.SuccessPillFill, card)
        assertText("running pill label", LightNexaFlowColors.success, running)
        val permission = over(colors.errorContainer, NexaFlowAlphas.SuccessPillFill, card)
        assertText("needs-permission pill label", colors.onErrorContainer, permission)
    }

    // --- Shared -----------------------------------------------------------

    /** Every category, at both the routine (16%) and picker (14%) tile fill. */
    private fun assertCategories(palette: NexaFlowCategoryColors, card: Color) {
        NexaFlowCategory.entries.forEach { category ->
            val hue = palette.of(category)
            listOf(
                NexaFlowAlphas.CategoryTileFill to "routine",
                NexaFlowAlphas.CategoryTileFillPicker to "picker"
            ).forEach { (alpha, kind) ->
                val tile = over(hue, alpha, card)
                assertText("$kind tile icon (${category.name.lowercase()})", hue, tile)
            }
        }
    }
}
