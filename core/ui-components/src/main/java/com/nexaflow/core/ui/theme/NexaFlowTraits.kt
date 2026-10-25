package com.nexaflow.core.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the appearance settings dress every screen beyond the color scheme
 * itself. Components read it through [NexaFlowTraitsDefaults] rather than
 * from here directly, so a new override lands in a single place.
 *
 * The point of this type is that appearance becomes *composable*: the old
 * shape of the settings was a flat list of independent switches (theme
 * mode, accent, dynamic color) that never interacted. These traits
 * interact by design, and every one of their combinations has to render
 * — which is why the combination count is a testable property rather than
 * an accident.
 *
 * @param monochrome Whether the monochrome style flattens the app's accent-heavy visuals into neutral tokens.
 * @param colorAccents Whether items wear their own colors, or the theme's accent instead.
 * @param outlines Whether cards, panels and buttons draw a hairline edge around them.
 * @param blur Whether headers, bars and panels blur what scrolls under them.
 * @param ambientBackground Whether the soft ambient shapes draw behind the content.
 * @param density How much room each control takes.
 * @param background Which ambient field to draw behind the content.
 */
@Immutable
data class NexaFlowTraits(
    val monochrome: Boolean = false,
    val colorAccents: Boolean = true,
    val outlines: Boolean = true,
    val blur: Boolean = true,
    val ambientBackground: Boolean = true,
    val density: NexaFlowDensity = NexaFlowDensity.COMFORTABLE,
    val background: NexaFlowBackground = NexaFlowBackground.AMBIENT
)

/** The ambient field drawn behind the content. */
enum class NexaFlowBackground {
    AMBIENT,
    EMBER,
    QUIET,
    NONE;

    /** The id this background is stored under. */
    val storedId: String
        get() = name.lowercase()

    companion object {
        /** The background for [value], falling back to the default. */
        fun fromStored(value: String?): NexaFlowBackground = when (value) {
            "ember" -> EMBER
            "quiet" -> QUIET
            "none" -> NONE
            else -> AMBIENT
        }
    }
}

/** How much room the app's controls take. */
enum class NexaFlowDensity {
    /** The default. Room for two-line labels and comfortable touch targets. */
    COMFORTABLE,

    /** Tightened for small screens and the Wear app. */
    COMPACT
}

/** The active traits. Provided once by the app theme. */
val LocalNexaFlowTraits = staticCompositionLocalOf { NexaFlowTraits() }

/**
 * Adapters that turn [NexaFlowTraits] into what components draw with,
 * passing the original values through where no trait asks otherwise.
 * Component code routes through here instead of branching on
 * [LocalNexaFlowTraits] so that a new override lands in a single place.
 */
object NexaFlowTraitsDefaults {

    private val traits: NexaFlowTraits
        @Composable @ReadOnlyComposable get() = LocalNexaFlowTraits.current

    /**
     * [base], an item's own color, or the theme's accent in its place where
     * monochrome or the appearance settings leave items without colors of
     * their own.
     */
    @Composable
    fun accentColor(base: Color): Color =
        if (traits.monochrome || !traits.colorAccents) MaterialTheme.colorScheme.primary else base

    @Composable
    fun surfaceColor(base: Color, selected: Boolean = false): Color {
        if (!traits.monochrome) return base

        val colors = MaterialTheme.colorScheme
        return if (selected) colors.primaryContainer else colors.surfaceContainerHigh
    }

    /** Content paired with a fill [surfaceColor] tinted, or [neutral] where monochrome drops the tint. */
    @Composable
    fun contentColor(base: Color, neutral: Color): Color = if (traits.monochrome) neutral else base

    /** [base] for a translucent control, or a faint one where monochrome flattens the control. */
    @Composable
    fun borderColor(base: Color, selected: Boolean = false): Color {
        if (!traits.monochrome) return base

        val colors = MaterialTheme.colorScheme
        val edge = if (selected) colors.primary else colors.outlineVariant
        return edge.copy(alpha = if (isDarkCanvas()) 0.42f else 0.34f)
    }

    /** Edge in [color], or none where the appearance settings take outlines off. */
    @Composable
    fun outline(color: Color, width: Dp): BorderStroke? =
        if (traits.outlines) BorderStroke(width, color) else null

    /** Whether a surface floating over content blurs it, or stands solid where the settings say not to. */
    @Composable
    fun blur(): Boolean = traits.blur

    @Composable
    fun cardElevation(base: Dp): Dp = if (traits.monochrome) 0.dp else base

    /** Divider between the rows of a card, tinted after the theme unless monochrome keeps it neutral. */
    @Composable
    fun dividerColor(): Color {
        val colors = MaterialTheme.colorScheme
        return if (traits.monochrome) {
            colors.outlineVariant.copy(alpha = 0.28f)
        } else {
            lerp(colors.outlineVariant, colors.surfaceTint, 0.18f).copy(alpha = 0.55f)
        }
    }

    /**
     * Text shadows read as noise on a flat monochrome palette, so drop them
     * when monochrome is active and pass them through otherwise.
     */
    @Composable
    fun textShadow(base: androidx.compose.ui.graphics.Shadow): androidx.compose.ui.graphics.Shadow? =
        if (traits.monochrome) null else base

    /**
     * Solid neutral fill in monochrome mode, gradient fill otherwise. Used by
     * the decorative circular icons that would otherwise carry a colored gradient.
     */
    @Composable
    fun iconBackground(gradient: List<Color>): Brush =
        if (traits.monochrome) {
            SolidColor(MaterialTheme.colorScheme.primaryContainer)
        } else {
            Brush.linearGradient(gradient)
        }

    /**
     * Paired with [iconBackground], which fills these icons with the primary
     * container. Primary itself sits too close to that fill once a custom
     * accent derives both from one color.
     */
    @Composable
    fun iconTint(base: Color): Color =
        if (traits.monochrome) MaterialTheme.colorScheme.onPrimaryContainer else base

    /** How strongly the ambient shapes draw. The switch off keeps a trace of them. */
    @Composable
    fun ambientAlpha(): Float = if (traits.ambientBackground) 1f else 0.08f

    /** The active density. */
    @Composable
    fun density(): NexaFlowDensity = traits.density

    /** Height of the floating navigation pill under the active density. */
    @Composable
    fun navigationHeight(): Dp = when (traits.density) {
        NexaFlowDensity.COMFORTABLE -> 72.dp
        NexaFlowDensity.COMPACT -> 64.dp
    }

    /** Whether the canvas is the dark one. Read from the scheme's background luminance. */
    @Composable
    @ReadOnlyComposable
    fun isDarkCanvas(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
}
