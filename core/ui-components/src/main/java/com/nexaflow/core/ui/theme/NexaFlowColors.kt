package com.nexaflow.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic color roles that Material 3's [androidx.compose.material3.ColorScheme]
 * does not define but this app needs: a success family (green tonal palette,
 * dark-aware) plus the Ink Glass `accentLabel` role used by eyebrows and the
 * WHEN / THEN flow labels.
 *
 * Error roles are already part of the M3 scheme and must be read from
 * `MaterialTheme.colorScheme` — never duplicated here.
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
    val onWarningContainer: Color,
    /**
     * Eyebrow / WHEN-THEN label colour. Ink defines a dedicated periwinkle so
     * the label never competes with the primary action colour; under dynamic
     * colour it falls back to `primary` (see the app theme).
     */
    val accentLabel: Color,
    /**
     * Thumb of an off switch / knob of a disabled control. Ink §5.1 pairs the
     * off track (`surfaceContainerHighest`) with a lighter neutral than
     * `outline`, because `outline` on that track measures 2.2:1.
     */
    val switchThumbOff: Color,
    /**
     * Ask-pill label. Ink pairs the 6% `onSurface` pill with its own periwinkle;
     * under dynamic colour it falls back to `primary`, because a wallpaper
     * scheme can hand back an `onPrimaryContainer` that is darker than the pill
     * and turns the label invisible (seen with a Material You wallpaper palette
     * on Android 17).
     */
    val askPillLabel: Color
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
    onWarningContainer = Color(0xFF2D1600),
    accentLabel = Color(0xFF3F51B5),
    switchThumbOff = Color(0xFF6E778C),
    askPillLabel = Color(0xFF2E3E8F)
)

val DarkNexaFlowColors = NexaFlowColors(
    success = Color(0xFF78DAA0),
    onSuccess = Color(0xFF00391D),
    successContainer = Color(0xFF00522B),
    onSuccessContainer = Color(0xFF94F7B6),
    warning = Color(0xFFFFB86C),
    onWarning = Color(0xFF4A2800),
    warningContainer = Color(0xFF683900),
    onWarningContainer = Color(0xFFFFDCC2),
    accentLabel = Color(0xFF8B9AFF),
    switchThumbOff = Color(0xFF8E99B0),
    askPillLabel = Color(0xFFC9D3FF)
)

/** Composition local — provided by the app theme, read by every module. */
val LocalNexaFlowColors = staticCompositionLocalOf { LightNexaFlowColors }

/**
 * Ink Glass category hues. A routine takes the colour of its first trigger's
 * category, falling back to its first action's category; icon tiles use the
 * hue at 16% (14% in pickers) and the icon itself at full strength.
 *
 * Light variants are the same hue at tone 40, checked ≥ 4.5:1 against their
 * own 16% tinted tile over `surfaceContainerLow`.
 */
@Immutable
data class NexaFlowCategoryColors(
    val time: Color,
    val location: Color,
    val power: Color,
    val connectivity: Color,
    val notifications: Color,
    val sound: Color,
    val advanced: Color
) {
    /** Resolves the hue used by [NexaFlowCategory]. */
    fun of(category: NexaFlowCategory): Color = when (category) {
        NexaFlowCategory.TIME -> time
        NexaFlowCategory.LOCATION -> location
        NexaFlowCategory.POWER -> power
        NexaFlowCategory.CONNECTIVITY -> connectivity
        NexaFlowCategory.NOTIFICATIONS -> notifications
        NexaFlowCategory.SOUND -> sound
        NexaFlowCategory.ADVANCED -> advanced
    }
}

/**
 * The seven Ink categories shared by triggers and actions. Domain and feature
 * enums map into this set; the palette itself lives in the theme so a light
 * and a dark variant stay in sync.
 */
enum class NexaFlowCategory {
    TIME,
    LOCATION,
    POWER,
    CONNECTIVITY,
    NOTIFICATIONS,
    SOUND,
    ADVANCED
}

val DarkNexaFlowCategoryColors = NexaFlowCategoryColors(
    time = Color(0xFFB9A4FF),
    location = Color(0xFF8AB4FF),
    power = Color(0xFF7FE0A6),
    connectivity = Color(0xFF6FD6F0),
    notifications = Color(0xFFFFB86C),
    sound = Color(0xFFFF9EC4),
    advanced = Color(0xFFB8C1D6)
)

val LightNexaFlowCategoryColors = NexaFlowCategoryColors(
    time = Color(0xFF6A4FA8),
    location = Color(0xFF2C5CAD),
    power = Color(0xFF176B45),
    connectivity = Color(0xFF00626F),
    notifications = Color(0xFF8A4B00),
    sound = Color(0xFF8A3A5C),
    advanced = Color(0xFF4E5668)
)

val LocalNexaFlowCategoryColors = staticCompositionLocalOf { LightNexaFlowCategoryColors }

/**
 * Alpha tokens, all derived from `onSurface` (or an explicit role) so they
 * follow any scheme, including dynamic colour. Feature code must use these
 * instead of literal `Color.copy(alpha = …)` values.
 */
object NexaFlowAlphas {
    const val HairlineBorder = 0.07f
    const val StrongBorder = 0.12f
    const val ChipBorder = 0.17f
    const val GlassBorder = 0.15f
    const val Divider = 0.05f
    const val FlowChipFill = 0.07f
    const val AskPillFill = 0.06f
    const val IconButtonFill = 0.05f
    const val SelectedRowTint = 0.12f
    const val SuccessPillFill = 0.17f
    const val CategoryTileFill = 0.16f
    const val CategoryTileFillPicker = 0.14f
    const val LoadingShimmer = 0.06f
    const val EmptyStateTile = 0.14f
}

/** Central accessor mirroring [androidx.compose.material3.MaterialTheme]. */
object NexaFlowTheme {
    val colors: NexaFlowColors
        @androidx.compose.runtime.Composable
        get() = LocalNexaFlowColors.current

    val categories: NexaFlowCategoryColors
        @androidx.compose.runtime.Composable
        get() = LocalNexaFlowCategoryColors.current

    val type: NexaFlowType
        @androidx.compose.runtime.Composable
        get() = LocalNexaFlowType.current
}
