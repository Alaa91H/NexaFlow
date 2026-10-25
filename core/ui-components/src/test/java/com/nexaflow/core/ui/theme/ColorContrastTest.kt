package com.nexaflow.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Every text/background pair the design system ships must clear WCAG AA.
 *
 * The palette is hand-tuned, and a hand-tuned palette drifts: a seed gets
 * re-tinted for vibrancy and a label that used to be readable quietly stops
 * being. This suite turns that into a build failure instead of a bug
 * report.
 *
 * The check runs over every accent seed, on both canvases, plus the semantic
 * success/warning families and the gradient stops — the gradient stops
 * matter because white text sits on them.
 */
class ColorContrastTest {

    /** WCAG AA for body text. */
    private val minimumTextContrast = 4.5f

    @Test
    fun everySeedClearsTextContrastOnBothCanvases() {
        NexaFlowAccentSeeds.forEach { (accentId, seed) ->
            listOf(false to "light", true to "dark").forEach { (dark, canvas) ->
                val scheme = nexaFlowColorScheme(seed, dark)
                assertContrast(
                    scheme.onPrimary, scheme.primary,
                    "$accentId/$canvas onPrimary on primary"
                )
                assertContrast(
                    scheme.onPrimaryContainer, scheme.primaryContainer,
                    "$accentId/$canvas onPrimaryContainer on primaryContainer"
                )
                assertContrast(
                    scheme.onSecondary, scheme.secondary,
                    "$accentId/$canvas onSecondary on secondary"
                )
                assertContrast(
                    scheme.onSecondaryContainer, scheme.secondaryContainer,
                    "$accentId/$canvas onSecondaryContainer on secondaryContainer"
                )
                assertContrast(
                    scheme.onTertiary, scheme.tertiary,
                    "$accentId/$canvas onTertiary on tertiary"
                )
                assertContrast(
                    scheme.onTertiaryContainer, scheme.tertiaryContainer,
                    "$accentId/$canvas onTertiaryContainer on tertiaryContainer"
                )
                assertContrast(
                    scheme.onError, scheme.error,
                    "$accentId/$canvas onError on error"
                )
                assertContrast(
                    scheme.onErrorContainer, scheme.errorContainer,
                    "$accentId/$canvas onErrorContainer on errorContainer"
                )
            }
        }
    }

    @Test
    fun neutralTextClearsContrastOnEverySurfaceTier() {
        listOf(false to "light", true to "dark").forEach { (dark, canvas) ->
            val scheme = nexaFlowColorScheme(IndigoSeed, dark)
            listOf(
                scheme.background,
                scheme.surface,
                scheme.surfaceContainerLow,
                scheme.surfaceContainer,
                scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest
            ).forEach { surface ->
                assertContrast(
                    scheme.onSurface, surface,
                    "$canvas onSurface on $surface"
                )
                assertContrast(
                    scheme.onSurfaceVariant, surface,
                    "$canvas onSurfaceVariant on $surface"
                )
            }
        }
    }

    @Test
    fun semanticFamiliesClearContrast() {
        listOf(
            LightNexaFlowColors to "light",
            DarkNexaFlowColors to "dark"
        ).forEach { (colors, canvas) ->
            assertContrast(colors.onSuccess, colors.success, "$canvas onSuccess on success")
            assertContrast(
                colors.onSuccessContainer, colors.successContainer,
                "$canvas onSuccessContainer on successContainer"
            )
            assertContrast(colors.onWarning, colors.warning, "$canvas onWarning on warning")
            assertContrast(
                colors.onWarningContainer, colors.warningContainer,
                "$canvas onWarningContainer on warningContainer"
            )
        }
    }

    @Test
    fun gradientStopsClearContrastAgainstWhiteText() {
        NexaFlowGradients.items.forEachIndexed { index, stops ->
            stops.forEach { stop ->
                assertContrast(
                    Color.White, stop,
                    "gradient $index stop $stop against white text"
                )
            }
        }
        NexaFlowGradients.brandSolid.forEach { stop ->
            assertContrast(Color.White, stop, "brand solid stop $stop against white glyph")
        }
    }

    @Test
    fun theSchemeActuallyDiffersBetweenCanvases() {
        // A scheme that ignores the canvas flag would still pass every
        // contrast check above while shipping a broken dark theme.
        val light = nexaFlowColorScheme(IndigoSeed, dark = false)
        val dark = nexaFlowColorScheme(IndigoSeed, dark = true)
        assertTrue(
            light.background != dark.background,
            "the light and dark canvases must not share a background"
        )
        assertTrue(
            light.onSurface != dark.onSurface,
            "the light and dark canvases must not share a text color"
        )
    }

    private fun assertContrast(foreground: Color, background: Color, label: String) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            ratio >= minimumTextContrast,
            "$label has a contrast ratio of ${"%.2f".format(ratio)}, below the " +
                "WCAG AA minimum of $minimumTextContrast"
        )
    }

    /** The WCAG 2.x relative-luminance contrast ratio between two opaque colors. */
    private fun contrastRatio(foreground: Color, background: Color): Float {
        val lighter = max(relativeLuminance(foreground), relativeLuminance(background))
        val darker = min(relativeLuminance(foreground), relativeLuminance(background))
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun relativeLuminance(color: Color): Float {
        val r = linearize(color.red)
        val g = linearize(color.green)
        val b = linearize(color.blue)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun linearize(channel: Float): Float =
        if (channel <= 0.03928f) {
            channel / 12.92f
        } else {
            ((channel + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
}
