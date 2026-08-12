package com.nexaflow.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Google Material 3 color system — the "Material You" design language used by
 * Google's own apps (Gmail, Tasks, Calendar, Clock). The baseline scheme is
 * derived from Google Blue (#0B57D0), the exact primary Google apps ship in
 * their light theme. Every accent seed expands to the full M3 role set with
 * the spec's tonal structure (tone 40 primary / tone 90 container in light,
 * tone 80 primary / tone 30 container in dark). On Android 12+ dynamic color
 * (wallpaper-sourced) is the default experience — these schemes are the
 * faithful Google fallback beneath it.
 */

/** Tonal anchor points for one accent seed, light + dark. */
internal data class AccentSeed(
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

/**
 * Google Blue — seed #0B57D0. The default. Secondary is Google's signature
 * neutral gray (#5F6368 light), tertiary a muted mauve (M3 blue-seed output).
 */
private val GoogleBlue = AccentSeed(
    lightPrimary = Color(0xFF0B57D0),
    lightOnPrimary = Color.White,
    lightPrimaryContainer = Color(0xFFD3E3FD),
    lightOnPrimaryContainer = Color(0xFF041E49),
    lightSecondary = Color(0xFF5F6368),
    lightOnSecondary = Color.White,
    lightSecondaryContainer = Color(0xFFE9EEF9),
    lightOnSecondaryContainer = Color(0xFF1C2022),
    lightTertiary = Color(0xFF705575),
    lightOnTertiary = Color.White,
    lightTertiaryContainer = Color(0xFFF9D8FD),
    lightOnTertiaryContainer = Color(0xFF28132E),
    darkPrimary = Color(0xFFA8C7FA),
    darkOnPrimary = Color(0xFF062E6F),
    darkPrimaryContainer = Color(0xFF0842A0),
    darkOnPrimaryContainer = Color(0xFFD3E3FD),
    darkSecondary = Color(0xFFBEC6DC),
    darkOnSecondary = Color(0xFF283141),
    darkSecondaryContainer = Color(0xFF3E4759),
    darkOnSecondaryContainer = Color(0xFFDAE2F9),
    darkTertiary = Color(0xFFDCBCE0),
    darkOnTertiary = Color(0xFF3E2844),
    darkTertiaryContainer = Color(0xFF563E5C),
    darkOnTertiaryContainer = Color(0xFFF9D8FD)
)

/** Green — seed #2FA84F (tone 40 ≈ #006D3C). */
private val GoogleGreen = AccentSeed(
    lightPrimary = Color(0xFF006D3C),
    lightOnPrimary = Color.White,
    lightPrimaryContainer = Color(0xFF94F7B6),
    lightOnPrimaryContainer = Color(0xFF00210D),
    lightSecondary = Color(0xFF4F6354),
    lightOnSecondary = Color.White,
    lightSecondaryContainer = Color(0xFFD2E8D5),
    lightOnSecondaryContainer = Color(0xFF0D2014),
    lightTertiary = Color(0xFF3A6471),
    lightOnTertiary = Color.White,
    lightTertiaryContainer = Color(0xFFBDE9F8),
    lightOnTertiaryContainer = Color(0xFF001F27),
    darkPrimary = Color(0xFF78DAA0),
    darkOnPrimary = Color(0xFF00391D),
    darkPrimaryContainer = Color(0xFF00522B),
    darkOnPrimaryContainer = Color(0xFF94F7B6),
    darkSecondary = Color(0xFFB6CCBA),
    darkOnSecondary = Color(0xFF223528),
    darkSecondaryContainer = Color(0xFF384B3E),
    darkOnSecondaryContainer = Color(0xFFD2E8D5),
    darkTertiary = Color(0xFFA1CDDB),
    darkOnTertiary = Color(0xFF003641),
    darkTertiaryContainer = Color(0xFF214C58),
    darkOnTertiaryContainer = Color(0xFFBDE9F8)
)

/** Red — seed #E5533D (tone 40 ≈ #BA1A1A, the M3 red). */
private val GoogleRed = AccentSeed(
    lightPrimary = Color(0xFFBA1A1A),
    lightOnPrimary = Color.White,
    lightPrimaryContainer = Color(0xFFFFDAD6),
    lightOnPrimaryContainer = Color(0xFF410002),
    lightSecondary = Color(0xFF775652),
    lightOnSecondary = Color.White,
    lightSecondaryContainer = Color(0xFFFFDAD6),
    lightOnSecondaryContainer = Color(0xFF2C1512),
    lightTertiary = Color(0xFF6F5B6D),
    lightOnTertiary = Color.White,
    lightTertiaryContainer = Color(0xFFF8DCF3),
    lightOnTertiaryContainer = Color(0xFF281828),
    darkPrimary = Color(0xFFFFB4AB),
    darkOnPrimary = Color(0xFF690005),
    darkPrimaryContainer = Color(0xFF93000A),
    darkOnPrimaryContainer = Color(0xFFFFDAD6),
    darkSecondary = Color(0xFFE7BDB7),
    darkOnSecondary = Color(0xFF442A26),
    darkSecondaryContainer = Color(0xFF5D403B),
    darkOnSecondaryContainer = Color(0xFFFFDAD6),
    darkTertiary = Color(0xFFDBC1D7),
    darkOnTertiary = Color(0xFF3F2C3D),
    darkTertiaryContainer = Color(0xFF574254),
    darkOnTertiaryContainer = Color(0xFFF8DCF3)
)

/** Purple — seed #7A5BD1 (tone 40 ≈ #6750A4, the M3 baseline purple). */
private val GooglePurple = AccentSeed(
    lightPrimary = Color(0xFF6750A4),
    lightOnPrimary = Color.White,
    lightPrimaryContainer = Color(0xFFEADDFF),
    lightOnPrimaryContainer = Color(0xFF21005D),
    lightSecondary = Color(0xFF625B71),
    lightOnSecondary = Color.White,
    lightSecondaryContainer = Color(0xFFE8DEF8),
    lightOnSecondaryContainer = Color(0xFF1E192B),
    lightTertiary = Color(0xFF7D5260),
    lightOnTertiary = Color.White,
    lightTertiaryContainer = Color(0xFFFFD8E4),
    lightOnTertiaryContainer = Color(0xFF31111D),
    darkPrimary = Color(0xFFD0BCFF),
    darkOnPrimary = Color(0xFF381E72),
    darkPrimaryContainer = Color(0xFF4F378B),
    darkOnPrimaryContainer = Color(0xFFEADDFF),
    darkSecondary = Color(0xFFCCC2DC),
    darkOnSecondary = Color(0xFF332D41),
    darkSecondaryContainer = Color(0xFF4A4458),
    darkOnSecondaryContainer = Color(0xFFE8DEF8),
    darkTertiary = Color(0xFFEFB8C8),
    darkOnTertiary = Color(0xFF492532),
    darkTertiaryContainer = Color(0xFF633B48),
    darkOnTertiaryContainer = Color(0xFFFFD8E4)
)

/** Amber — seed #E8A33D (tone 40 ≈ #8F4C00). */
private val GoogleAmber = AccentSeed(
    lightPrimary = Color(0xFF8F4C00),
    lightOnPrimary = Color.White,
    lightPrimaryContainer = Color(0xFFFFDCC2),
    lightOnPrimaryContainer = Color(0xFF2D1600),
    lightSecondary = Color(0xFF705A42),
    lightOnSecondary = Color.White,
    lightSecondaryContainer = Color(0xFFFADEBD),
    lightOnSecondaryContainer = Color(0xFF271805),
    lightTertiary = Color(0xFF586338),
    lightOnTertiary = Color.White,
    lightTertiaryContainer = Color(0xFFBCE8B2),
    lightOnTertiaryContainer = Color(0xFF161E00),
    darkPrimary = Color(0xFFFFB86C),
    darkOnPrimary = Color(0xFF4A2800),
    darkPrimaryContainer = Color(0xFF683900),
    darkOnPrimaryContainer = Color(0xFFFFDCC2),
    darkSecondary = Color(0xFFDDC2A5),
    darkOnSecondary = Color(0xFF3E2D18),
    darkSecondaryContainer = Color(0xFF57432C),
    darkOnSecondaryContainer = Color(0xFFFADEBD),
    darkTertiary = Color(0xFFA1CB97),
    darkOnTertiary = Color(0xFF0B3500),
    darkTertiaryContainer = Color(0xFF274B1E),
    darkOnTertiaryContainer = Color(0xFFBCE8B2)
)

/** Teal — seed #13A5A8 (tone 40 ≈ #006A6C). */
private val GoogleTeal = AccentSeed(
    lightPrimary = Color(0xFF006A6C),
    lightOnPrimary = Color.White,
    lightPrimaryContainer = Color(0xFF9CF1F3),
    lightOnPrimaryContainer = Color(0xFF002020),
    lightSecondary = Color(0xFF4A6363),
    lightOnSecondary = Color.White,
    lightSecondaryContainer = Color(0xFFCCE8E8),
    lightOnSecondaryContainer = Color(0xFF051F1F),
    lightTertiary = Color(0xFF4E5C7D),
    lightOnTertiary = Color.White,
    lightTertiaryContainer = Color(0xFFD8E2FF),
    lightOnTertiaryContainer = Color(0xFF081A36),
    darkPrimary = Color(0xFF80D4D6),
    darkOnPrimary = Color(0xFF003737),
    darkPrimaryContainer = Color(0xFF004F50),
    darkOnPrimaryContainer = Color(0xFF9CF1F3),
    darkSecondary = Color(0xFFB1CCCC),
    darkOnSecondary = Color(0xFF1B3434),
    darkSecondaryContainer = Color(0xFF334B4B),
    darkOnSecondaryContainer = Color(0xFFCCE8E8),
    darkTertiary = Color(0xFFB7C6EA),
    darkOnTertiary = Color(0xFF212F4D),
    darkTertiaryContainer = Color(0xFF374564),
    darkOnTertiaryContainer = Color(0xFFD8E2FF)
)

/** Neutral surface family (light). */
private object NeutralLight {
    val background = Color(0xFFFDFBFF)
    val onBackground = Color(0xFF1A1C20)
    val surface = Color(0xFFFDFBFF)
    val onSurface = Color(0xFF1A1C20)
    val surfaceVariant = Color(0xFFE1E2EC)
    val onSurfaceVariant = Color(0xFF44474F)
    val outline = Color(0xFF74777F)
    val outlineVariant = Color(0xFFC4C6D0)
    val inverseSurface = Color(0xFF2F3036)
    val onInverseSurface = Color(0xFFF1F0F4)
    val surfaceContainerLowest = Color.White
    val surfaceContainerLow = Color(0xFFF7F8FC)
    val surfaceContainer = Color(0xFFF1F1F7)
    val surfaceContainerHigh = Color(0xFFECECF2)
    val surfaceContainerHighest = Color(0xFFE6E6EC)
    val surfaceDim = Color(0xFFDEDDE3)
    val surfaceBright = Color(0xFFFDFBFF)
}

/** Neutral surface family (dark). */
private object NeutralDark {
    val background = Color(0xFF131316)
    val onBackground = Color(0xFFE2E2E8)
    val surface = Color(0xFF131316)
    val onSurface = Color(0xFFE2E2E8)
    val surfaceVariant = Color(0xFF44474F)
    val onSurfaceVariant = Color(0xFFC4C6D0)
    val outline = Color(0xFF8E9099)
    val outlineVariant = Color(0xFF44474F)
    val inverseSurface = Color(0xFFE2E2E8)
    val onInverseSurface = Color(0xFF2F3036)
    val surfaceContainerLowest = Color(0xFF0E0E11)
    val surfaceContainerLow = Color(0xFF1B1B1F)
    val surfaceContainer = Color(0xFF1F1F24)
    val surfaceContainerHigh = Color(0xFF2A2A2E)
    val surfaceContainerHighest = Color(0xFF343439)
    val surfaceDim = Color(0xFF131316)
    val surfaceBright = Color(0xFF3A3A3F)
}

/** M3 error roles (identical in every scheme). */
private object ErrorLight {
    val error = Color(0xFFBA1A1A)
    val onError = Color.White
    val errorContainer = Color(0xFFFFDAD6)
    val onErrorContainer = Color(0xFF410002)
}

private object ErrorDark {
    val error = Color(0xFFFFB4AB)
    val onError = Color(0xFF690005)
    val errorContainer = Color(0xFF93000A)
    val onErrorContainer = Color(0xFFFFDAD6)
}

internal val accentSeeds = mapOf(
    "blue" to GoogleBlue,
    "green" to GoogleGreen,
    "red" to GoogleRed,
    "purple" to GooglePurple,
    "amber" to GoogleAmber,
    "teal" to GoogleTeal
)

/** Builds the complete Google M3 [ColorScheme] for one accent seed. */
internal fun googleColorScheme(seed: AccentSeed, dark: Boolean): ColorScheme {
    if (!dark) {
        return lightColorScheme(
            primary = seed.lightPrimary,
            onPrimary = seed.lightOnPrimary,
            primaryContainer = seed.lightPrimaryContainer,
            onPrimaryContainer = seed.lightOnPrimaryContainer,
            inversePrimary = seed.darkPrimary,
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
            surfaceTint = seed.lightPrimary,
            inverseSurface = NeutralLight.inverseSurface,
            inverseOnSurface = NeutralLight.onInverseSurface,
            outline = NeutralLight.outline,
            outlineVariant = NeutralLight.outlineVariant,
            scrim = Color.Black,
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
    return darkColorScheme(
        primary = seed.darkPrimary,
        onPrimary = seed.darkOnPrimary,
        primaryContainer = seed.darkPrimaryContainer,
        onPrimaryContainer = seed.darkOnPrimaryContainer,
        inversePrimary = seed.lightPrimary,
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
        surfaceTint = seed.darkPrimary,
        inverseSurface = NeutralDark.inverseSurface,
        inverseOnSurface = NeutralDark.onInverseSurface,
        outline = NeutralDark.outline,
        outlineVariant = NeutralDark.outlineVariant,
        scrim = Color.Black,
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
}
