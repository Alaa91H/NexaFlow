package com.nexaflow.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * NexaFlow One — the shared palette every module reads.
 *
 * The palette lives here, in `core/ui-components`, because every feature
 * module, the Wear app and the widgets all need it and none of them may
 * depend on the `app` module. `app/ui/theme/Color.kt` layers the app-level
 * accent on top of this file; nothing else defines a color.
 *
 * The brand is an original identity: an indigo → violet → aqua flow
 * gradient. It deliberately does not reuse another product's brand hues —
 * a design language can be learned from, a brand cannot be borrowed.
 *
 * Every value below is a Material 3 role. The dark set is the primary
 * experience: the app's canvas is a near-black surface with soft ambient
 * shapes behind it, so the dark roles are tuned for that, not for a plain
 * grey-on-grey inversion.
 */

// --- Brand gradient ------------------------------------------------------------

/** Start of the brand gradient — the primary accent. */
val NexaFlowBrandStart: Color = Color(0xFF3B5BFF)

/** Middle of the brand gradient. */
val NexaFlowBrandViolet: Color = Color(0xFF7A5AF8)

/** End of the brand gradient — the secondary accent. */
val NexaFlowBrandEnd: Color = Color(0xFF00D6B4)

/** Brand start, tuned for the dark canvas. */
val NexaFlowBrandStartDark: Color = Color(0xFF8AA0FF)

/** Brand end, tuned for the dark canvas. */
val NexaFlowBrandEndDark: Color = Color(0xFF4FE3C8)

/** Brand violet, tuned for the dark canvas. */
val NexaFlowBrandVioletDark: Color = Color(0xFFB9A3FF)

/** The neutral accent used by the monochrome trait, light canvas. */
val NexaFlowMonoAmbientOne: Color = Color(0xFF8AA0FF)

/** The neutral accent used by the monochrome trait, dark canvas. */
val NexaFlowMonoAmbientTwo: Color = Color(0xFF4FE3C8)

/** Warning / attention accent, shared by both canvases. */
val NexaFlowBrandAmber: Color = Color(0xFFF59E0B)

/** Danger accent, shared by both canvases. */
val NexaFlowBrandRose: Color = Color(0xFFE5484D)

/**
 * Tonal anchor points for one accent seed, light + dark.
 *
 * Mirrors the Material 3 role set: tone 40 primary / tone 90 container in
 * light, tone 80 primary / tone 30 container in dark.
 */
data class AccentSeed(
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val lightSecondary: Color,
    val lightOnSecondary: Color,
    val lightSecondaryContainer: Color,
    val lightOnSecondaryContainer: Color,
    val lightTertiary: Color,
    val lightOnTertiary: Color,
    val lightTertiaryContainer: Color,
    val lightOnTertiaryContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
    val darkSecondary: Color,
    val darkOnSecondary: Color,
    val darkSecondaryContainer: Color,
    val darkOnSecondaryContainer: Color,
    val darkTertiary: Color,
    val darkOnTertiary: Color,
    val darkTertiaryContainer: Color,
    val darkOnTertiaryContainer: Color
)

/** Indigo — the default accent and the brand's own seed. */
val IndigoSeed = AccentSeed(
    lightPrimary = Color(0xFF3B5BFF),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightPrimaryContainer = Color(0xFFE2E7FF),
    lightOnPrimaryContainer = Color(0xFF121C4A),
    lightSecondary = Color(0xFF008573),
    lightOnSecondary = Color(0xFFFFFFFF),
    lightSecondaryContainer = Color(0xFFCFF5EE),
    lightOnSecondaryContainer = Color(0xFF00382F),
    lightTertiary = Color(0xFF7A5AF8),
    lightOnTertiary = Color(0xFFFFFFFF),
    lightTertiaryContainer = Color(0xFFE9E2FF),
    lightOnTertiaryContainer = Color(0xFF2B1660),
    darkPrimary = Color(0xFF8AA0FF),
    darkOnPrimary = Color(0xFF0C1240),
    darkPrimaryContainer = Color(0xFF1E2A6B),
    darkOnPrimaryContainer = Color(0xFFDDE3FF),
    darkSecondary = Color(0xFF4FE3C8),
    darkOnSecondary = Color(0xFF00382F),
    darkSecondaryContainer = Color(0xFF00493E),
    darkOnSecondaryContainer = Color(0xFFB6F5E9),
    darkTertiary = Color(0xFFB9A3FF),
    darkOnTertiary = Color(0xFF2B1660),
    darkTertiaryContainer = Color(0xFF3B2A6B),
    darkOnTertiaryContainer = Color(0xFFE4DBFF)
)

/** Teal — the cool secondary of the brand. */
val TealSeed = AccentSeed(
    lightPrimary = Color(0xFF008573),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightPrimaryContainer = Color(0xFFB2F0E6),
    lightOnPrimaryContainer = Color(0xFF00201C),
    lightSecondary = Color(0xFF3B5BFF),
    lightOnSecondary = Color(0xFFFFFFFF),
    lightSecondaryContainer = Color(0xFFE2E7FF),
    lightOnSecondaryContainer = Color(0xFF121C4A),
    lightTertiary = Color(0xFF7A5AF8),
    lightOnTertiary = Color(0xFFFFFFFF),
    lightTertiaryContainer = Color(0xFFE9E2FF),
    lightOnTertiaryContainer = Color(0xFF2B1660),
    darkPrimary = Color(0xFF4FE3C8),
    darkOnPrimary = Color(0xFF00382F),
    darkPrimaryContainer = Color(0xFF00493E),
    darkOnPrimaryContainer = Color(0xFFB6F5E9),
    darkSecondary = Color(0xFF8AA0FF),
    darkOnSecondary = Color(0xFF0C1240),
    darkSecondaryContainer = Color(0xFF1E2A6B),
    darkOnSecondaryContainer = Color(0xFFDDE3FF),
    darkTertiary = Color(0xFFB9A3FF),
    darkOnTertiary = Color(0xFF2B1660),
    darkTertiaryContainer = Color(0xFF3B2A6B),
    darkOnTertiaryContainer = Color(0xFFE4DBFF)
)

/** Violet — the expressive accent. */
val VioletSeed = AccentSeed(
    lightPrimary = Color(0xFF7A5AF8),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightPrimaryContainer = Color(0xFFE9E2FF),
    lightOnPrimaryContainer = Color(0xFF2B1660),
    lightSecondary = Color(0xFF3B5BFF),
    lightOnSecondary = Color(0xFFFFFFFF),
    lightSecondaryContainer = Color(0xFFE2E7FF),
    lightOnSecondaryContainer = Color(0xFF121C4A),
    lightTertiary = Color(0xFF008573),
    lightOnTertiary = Color(0xFFFFFFFF),
    lightTertiaryContainer = Color(0xFFB2F0E6),
    lightOnTertiaryContainer = Color(0xFF00201C),
    darkPrimary = Color(0xFFB9A3FF),
    darkOnPrimary = Color(0xFF2B1660),
    darkPrimaryContainer = Color(0xFF3B2A6B),
    darkOnPrimaryContainer = Color(0xFFE4DBFF),
    darkSecondary = Color(0xFF8AA0FF),
    darkOnSecondary = Color(0xFF0C1240),
    darkSecondaryContainer = Color(0xFF1E2A6B),
    darkOnSecondaryContainer = Color(0xFFDDE3FF),
    darkTertiary = Color(0xFF4FE3C8),
    darkOnTertiary = Color(0xFF00382F),
    darkTertiaryContainer = Color(0xFF00493E),
    darkOnTertiaryContainer = Color(0xFFB6F5E9)
)

/** Emerald — the calm, high-legibility accent. */
val EmeraldSeed = AccentSeed(
    lightPrimary = Color(0xFF178050),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightPrimaryContainer = Color(0xFFBFF0D6),
    lightOnPrimaryContainer = Color(0xFF00281A),
    lightSecondary = Color(0xFF3B5BFF),
    lightOnSecondary = Color(0xFFFFFFFF),
    lightSecondaryContainer = Color(0xFFE2E7FF),
    lightOnSecondaryContainer = Color(0xFF121C4A),
    lightTertiary = Color(0xFF7A5AF8),
    lightOnTertiary = Color(0xFFFFFFFF),
    lightTertiaryContainer = Color(0xFFE9E2FF),
    lightOnTertiaryContainer = Color(0xFF2B1660),
    darkPrimary = Color(0xFF5BD6A0),
    darkOnPrimary = Color(0xFF003824),
    darkPrimaryContainer = Color(0xFF0B3D26),
    darkOnPrimaryContainer = Color(0xFFBFF0D6),
    darkSecondary = Color(0xFF8AA0FF),
    darkOnSecondary = Color(0xFF0C1240),
    darkSecondaryContainer = Color(0xFF1E2A6B),
    darkOnSecondaryContainer = Color(0xFFDDE3FF),
    darkTertiary = Color(0xFFB9A3FF),
    darkOnTertiary = Color(0xFF2B1660),
    darkTertiaryContainer = Color(0xFF3B2A6B),
    darkOnTertiaryContainer = Color(0xFFE4DBFF)
)

/** Amber — the warm accent, used for attention-heavy screens. */
val AmberSeed = AccentSeed(
    lightPrimary = Color(0xFF9A5B00),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightPrimaryContainer = Color(0xFFFFDEA8),
    lightOnPrimaryContainer = Color(0xFF2B1700),
    lightSecondary = Color(0xFF3B5BFF),
    lightOnSecondary = Color(0xFFFFFFFF),
    lightSecondaryContainer = Color(0xFFE2E7FF),
    lightOnSecondaryContainer = Color(0xFF121C4A),
    lightTertiary = Color(0xFF008573),
    lightOnTertiary = Color(0xFFFFFFFF),
    lightTertiaryContainer = Color(0xFFB2F0E6),
    lightOnTertiaryContainer = Color(0xFF00201C),
    darkPrimary = Color(0xFFFBBF24),
    darkOnPrimary = Color(0xFF402C00),
    darkPrimaryContainer = Color(0xFF5A3D00),
    darkOnPrimaryContainer = Color(0xFFFFDEA8),
    darkSecondary = Color(0xFF8AA0FF),
    darkOnSecondary = Color(0xFF0C1240),
    darkSecondaryContainer = Color(0xFF1E2A6B),
    darkOnSecondaryContainer = Color(0xFFDDE3FF),
    darkTertiary = Color(0xFF4FE3C8),
    darkOnTertiary = Color(0xFF00382F),
    darkTertiaryContainer = Color(0xFF00493E),
    darkOnTertiaryContainer = Color(0xFFB6F5E9)
)

/** Rose — the alert accent. */
val RoseSeed = AccentSeed(
    lightPrimary = Color(0xFFC62A2F),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightPrimaryContainer = Color(0xFFFFDAD6),
    lightOnPrimaryContainer = Color(0xFF410002),
    lightSecondary = Color(0xFF3B5BFF),
    lightOnSecondary = Color(0xFFFFFFFF),
    lightSecondaryContainer = Color(0xFFE2E7FF),
    lightOnSecondaryContainer = Color(0xFF121C4A),
    lightTertiary = Color(0xFF9A5B00),
    lightOnTertiary = Color(0xFFFFFFFF),
    lightTertiaryContainer = Color(0xFFFFDEA8),
    lightOnTertiaryContainer = Color(0xFF2B1700),
    darkPrimary = Color(0xFFFF8A8E),
    darkOnPrimary = Color(0xFF690005),
    darkPrimaryContainer = Color(0xFF93000A),
    darkOnPrimaryContainer = Color(0xFFFFDAD6),
    darkSecondary = Color(0xFF8AA0FF),
    darkOnSecondary = Color(0xFF0C1240),
    darkSecondaryContainer = Color(0xFF1E2A6B),
    darkOnSecondaryContainer = Color(0xFFDDE3FF),
    darkTertiary = Color(0xFFFBBF24),
    darkOnTertiary = Color(0xFF402C00),
    darkTertiaryContainer = Color(0xFF5A3D00),
    darkOnTertiaryContainer = Color(0xFFFFDEA8)
)

/** The accent seeds offered in Appearance, keyed by their stored id. */
val NexaFlowAccentSeeds: Map<String, AccentSeed> = mapOf(
    "indigo" to IndigoSeed,
    "teal" to TealSeed,
    "violet" to VioletSeed,
    "emerald" to EmeraldSeed,
    "amber" to AmberSeed,
    "rose" to RoseSeed
)

/** The accent used when nothing has been chosen yet. */
const val DefaultAccentId: String = "indigo"

/** The accent seed for [accentId], falling back to the default. */
fun accentSeedFor(accentId: String?): AccentSeed =
    NexaFlowAccentSeeds[accentId] ?: NexaFlowAccentSeeds.getValue(DefaultAccentId)

// --- Neutral families ----------------------------------------------------------

/** Light neutrals — a near-white canvas with cool grey steps. */
object NeutralLight {
    val background: Color = Color(0xFFFBFBFD)
    val onBackground: Color = Color(0xFF101319)
    val surface: Color = Color(0xFFFFFFFF)
    val onSurface: Color = Color(0xFF101319)
    val surfaceVariant: Color = Color(0xFFE8EBF2)
    val onSurfaceVariant: Color = Color(0xFF5A6172)
    val outline: Color = Color(0xFF8A91A2)
    val outlineVariant: Color = Color(0xFFD3D8E3)
    val inverseSurface: Color = Color(0xFF1B1E27)
    val inverseOnSurface: Color = Color(0xFFF2F4F8)
    val inversePrimary: Color = Color(0xFF3B5BFF)
    val scrim: Color = Color(0xFF000000)
    val surfaceDim: Color = Color(0xFFE4E7EE)
    val surfaceBright: Color = Color(0xFFFFFFFF)
    val surfaceContainerLowest: Color = Color(0xFFFFFFFF)
    val surfaceContainerLow: Color = Color(0xFFF5F6FA)
    val surfaceContainer: Color = Color(0xFFEFF1F6)
    val surfaceContainerHigh: Color = Color(0xFFE8EBF2)
    val surfaceContainerHighest: Color = Color(0xFFE1E5EE)
}

/** Dark neutrals — the canvas the app is designed around. */
object NeutralDark {
    val background: Color = Color(0xFF0A0B10)
    val onBackground: Color = Color(0xFFEDEFF5)
    val surface: Color = Color(0xFF101219)
    val onSurface: Color = Color(0xFFEDEFF5)
    val surfaceVariant: Color = Color(0xFF20242E)
    val onSurfaceVariant: Color = Color(0xFF9AA2B5)
    val outline: Color = Color(0xFF6D7486)
    val outlineVariant: Color = Color(0xFF282D39)
    val inverseSurface: Color = Color(0xFFEDEFF5)
    val inverseOnSurface: Color = Color(0xFF101319)
    val inversePrimary: Color = Color(0xFF8AA0FF)
    val scrim: Color = Color(0xFF000000)
    val surfaceDim: Color = Color(0xFF08090D)
    val surfaceBright: Color = Color(0xFF282D39)
    val surfaceContainerLowest: Color = Color(0xFF06070A)
    val surfaceContainerLow: Color = Color(0xFF14161E)
    val surfaceContainer: Color = Color(0xFF191C25)
    val surfaceContainerHigh: Color = Color(0xFF20242E)
    val surfaceContainerHighest: Color = Color(0xFF282D39)
}

// --- Error roles ---------------------------------------------------------------

object ErrorLight {
    val error: Color = Color(0xFFD13A3F)
    val onError: Color = Color(0xFFFFFFFF)
    val errorContainer: Color = Color(0xFFFFDAD6)
    val onErrorContainer: Color = Color(0xFF410002)
}

object ErrorDark {
    val error: Color = Color(0xFFFF8A8E)
    val onError: Color = Color(0xFF690005)
    val errorContainer: Color = Color(0xFF93000A)
    val onErrorContainer: Color = Color(0xFFFFDAD6)
}

// --- Scheme construction -------------------------------------------------------

/**
 * Build the Material 3 scheme for [seed] on the given canvas.
 *
 * The neutral families are shared across every accent — only the primary,
 * secondary and tertiary families move with the seed. That keeps the app's
 * depth consistent when the user changes accent, which is the point of a
 * seed-based scheme.
 */
fun nexaFlowColorScheme(seed: AccentSeed, dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = seed.darkPrimary,
            onPrimary = seed.darkOnPrimary,
            primaryContainer = seed.darkPrimaryContainer,
            onPrimaryContainer = seed.darkOnPrimaryContainer,
            secondary = seed.darkSecondary,
            onSecondary = seed.darkOnSecondary,
            secondaryContainer = seed.darkSecondaryContainer,
            onSecondaryContainer = seed.darkOnSecondaryContainer,
            tertiary = seed.darkTertiary,
            onTertiary = seed.darkOnTertiary,
            tertiaryContainer = seed.darkTertiaryContainer,
            onTertiaryContainer = seed.darkOnTertiaryContainer,
            background = NeutralDark.background,
            onBackground = NeutralDark.onBackground,
            surface = NeutralDark.surface,
            onSurface = NeutralDark.onSurface,
            surfaceVariant = NeutralDark.surfaceVariant,
            onSurfaceVariant = NeutralDark.onSurfaceVariant,
            outline = NeutralDark.outline,
            outlineVariant = NeutralDark.outlineVariant,
            inverseSurface = NeutralDark.inverseSurface,
            inverseOnSurface = NeutralDark.inverseOnSurface,
            inversePrimary = NeutralDark.inversePrimary,
            scrim = NeutralDark.scrim,
            surfaceDim = NeutralDark.surfaceDim,
            surfaceBright = NeutralDark.surfaceBright,
            surfaceContainerLowest = NeutralDark.surfaceContainerLowest,
            surfaceContainerLow = NeutralDark.surfaceContainerLow,
            surfaceContainer = NeutralDark.surfaceContainer,
            surfaceContainerHigh = NeutralDark.surfaceContainerHigh,
            surfaceContainerHighest = NeutralDark.surfaceContainerHighest,
            error = ErrorDark.error,
            onError = ErrorDark.onError,
            errorContainer = ErrorDark.errorContainer,
            onErrorContainer = ErrorDark.onErrorContainer
        )
    } else {
        lightColorScheme(
            primary = seed.lightPrimary,
            onPrimary = seed.lightOnPrimary,
            primaryContainer = seed.lightPrimaryContainer,
            onPrimaryContainer = seed.lightOnPrimaryContainer,
            secondary = seed.lightSecondary,
            onSecondary = seed.lightOnSecondary,
            secondaryContainer = seed.lightSecondaryContainer,
            onSecondaryContainer = seed.lightOnSecondaryContainer,
            tertiary = seed.lightTertiary,
            onTertiary = seed.lightOnTertiary,
            tertiaryContainer = seed.lightTertiaryContainer,
            onTertiaryContainer = seed.lightOnTertiaryContainer,
            background = NeutralLight.background,
            onBackground = NeutralLight.onBackground,
            surface = NeutralLight.surface,
            onSurface = NeutralLight.onSurface,
            surfaceVariant = NeutralLight.surfaceVariant,
            onSurfaceVariant = NeutralLight.onSurfaceVariant,
            outline = NeutralLight.outline,
            outlineVariant = NeutralLight.outlineVariant,
            inverseSurface = NeutralLight.inverseSurface,
            inverseOnSurface = NeutralLight.inverseOnSurface,
            inversePrimary = NeutralLight.inversePrimary,
            scrim = NeutralLight.scrim,
            surfaceDim = NeutralLight.surfaceDim,
            surfaceBright = NeutralLight.surfaceBright,
            surfaceContainerLowest = NeutralLight.surfaceContainerLowest,
            surfaceContainerLow = NeutralLight.surfaceContainerLow,
            surfaceContainer = NeutralLight.surfaceContainer,
            surfaceContainerHigh = NeutralLight.surfaceContainerHigh,
            surfaceContainerHighest = NeutralLight.surfaceContainerHighest,
            error = ErrorLight.error,
            onError = ErrorLight.onError,
            errorContainer = ErrorLight.errorContainer,
            onErrorContainer = ErrorLight.onErrorContainer
        )
    }
