package com.nexaflow.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic color roles that Material 3's [androidx.compose.material3.ColorScheme]
 * does not define but this app needs: a success family (green tonal palette,
 * dark-aware). Error roles are already part of the M3 scheme and must be read
 * from `MaterialTheme.colorScheme` — never duplicated here.
 *
 * Both variants are dark-aware so a raw green like #006D3C never lands on a
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
    val onWarningContainer: Color
)

/** Google-green tonal palette (seed #2FA84F), tone 40/80 split per M3 spec. */
val LightNexaFlowColors = NexaFlowColors(
    success = Color(0xFF006D3C),
    onSuccess = Color.White,
    successContainer = Color(0xFFE4F4E9),
    onSuccessContainer = Color(0xFF00210D),
    warning = Color(0xFF8F4C00),
    onWarning = Color.White,
    warningContainer = Color(0xFFFDF3E0),
    onWarningContainer = Color(0xFF2D1600)
)

val DarkNexaFlowColors = NexaFlowColors(
    success = Color(0xFF78DAA0),
    onSuccess = Color(0xFF00391D),
    successContainer = Color(0xFF00522B),
    onSuccessContainer = Color(0xFF94F7B6),
    warning = Color(0xFFFFB86C),
    onWarning = Color(0xFF4A2800),
    warningContainer = Color(0xFF683900),
    onWarningContainer = Color(0xFFFFDCC2)
)

/** Composition local — provided by the app theme, read by every module. */
val LocalNexaFlowColors = staticCompositionLocalOf { LightNexaFlowColors }

/** Central accessor mirroring [androidx.compose.material3.MaterialTheme]. */
object NexaFlowTheme {
    val colors: NexaFlowColors
        @androidx.compose.runtime.Composable
        get() = LocalNexaFlowColors.current
}
