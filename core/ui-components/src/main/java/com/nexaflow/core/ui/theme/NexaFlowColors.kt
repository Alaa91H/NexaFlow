package com.nexaflow.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic color roles that Material 3's [androidx.compose.material3.ColorScheme]
 * does not define but this app needs: a success family, a warning family and
 * the brand's own gradient stops. Error roles are already part of the M3
 * scheme and must be read from `MaterialTheme.colorScheme` — never
 * duplicated here.
 *
 * Both variants are dark-aware so a raw green like #1D8A5B never lands on a
 * dark surface (the classic invisible-text bug).
 */
@Immutable
data class NexaFlowColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    /** Start of the brand gradient. */
    val brandStart: Color = NexaFlowBrandStart,
    /** Middle of the brand gradient. */
    val brandViolet: Color = NexaFlowBrandViolet,
    /** End of the brand gradient. */
    val brandEnd: Color = NexaFlowBrandEnd,
    /** The hairline color used for card edges and dividers. */
    val hairline: Color = NexaFlowBrandEnd
)

/** The light semantic palette. */
val LightNexaFlowColors = NexaFlowColors(
    success = Color(0xFF178050),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFBFF0D6),
    onSuccessContainer = Color(0xFF00281A),
    warning = Color(0xFF9A5B00),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDEA8),
    onWarningContainer = Color(0xFF2B1700),
    brandStart = NexaFlowBrandStart,
    brandViolet = NexaFlowBrandViolet,
    brandEnd = NexaFlowBrandEnd,
    hairline = Color(0xFFD3D8E3)
)

/** The dark semantic palette. */
val DarkNexaFlowColors = NexaFlowColors(
    success = Color(0xFF5BD6A0),
    onSuccess = Color(0xFF003824),
    successContainer = Color(0xFF0B3D26),
    onSuccessContainer = Color(0xFFBFF0D6),
    warning = Color(0xFFFBBF24),
    onWarning = Color(0xFF402C00),
    warningContainer = Color(0xFF5A3D00),
    onWarningContainer = Color(0xFFFFDEA8),
    brandStart = NexaFlowBrandStartDark,
    brandViolet = NexaFlowBrandVioletDark,
    brandEnd = NexaFlowBrandEndDark,
    hairline = Color(0xFF282D39)
)

/** Composition local — provided by the app theme, read by every module. */
val LocalNexaFlowColors = staticCompositionLocalOf { LightNexaFlowColors }

/** Central accessor mirroring [androidx.compose.material3.MaterialTheme]. */
object NexaFlowTheme {
    val colors: NexaFlowColors
        @androidx.compose.runtime.Composable
        get() = LocalNexaFlowColors.current
}
