package com.nexaflow.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.nexaflow.core.datastore.ThemeMode

/** Samsung One UI shape system: soft, rounded corners everywhere. */
private val SamsungShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

data class AccentColors(
    val primary: Color,
    val primaryDark: Color
)

fun accentPalette(accent: String): AccentColors {
    return when (accent) {
        "green" -> AccentColors(OneUIGreen, OneUIGreenDark)
        "red" -> AccentColors(OneUIRed, OneUIRedDark)
        "purple" -> AccentColors(OneUIPurple, OneUIPurpleDark)
        "amber" -> AccentColors(OneUIAmber, OneUIAmberDark)
        "teal" -> AccentColors(OneUITeal, OneUITealDark)
        else -> AccentColors(OneUIBlue, OneUIBlueDark)
    }
}

@Composable
fun oneUIColorScheme(darkTheme: Boolean, accent: String): androidx.compose.material3.ColorScheme {
    val palette = accentPalette(accent)
    return if (darkTheme) {
        darkColorScheme(
            primary = palette.primaryDark,
            onPrimary = Color(0xFF0D0F12),
            primaryContainer = OneUISurfaceVariantDark,
            onPrimaryContainer = palette.primaryDark,
            secondary = OneUITextMutedDark,
            onSecondary = Color(0xFF0D0F12),
            secondaryContainer = OneUISurfaceVariantDark,
            onSecondaryContainer = OneUITextMutedDark,
            background = OneUIBackgroundDark,
            onBackground = Color.White,
            surface = OneUISurfaceDark,
            onSurface = Color.White,
            surfaceVariant = OneUISurfaceVariantDark,
            onSurfaceVariant = OneUITextMutedDark,
            outline = OneUIOutlineDark,
            outlineVariant = OneUIOutlineVariantDark
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF0F5FB),
            onPrimaryContainer = palette.primary,
            secondary = OneUITextMutedLight,
            onSecondary = Color.White,
            secondaryContainer = OneUISurfaceVariantLight,
            onSecondaryContainer = OneUITextMutedLight,
            background = OneUIBackgroundLight,
            onBackground = Color(0xFF1A1B1E),
            surface = OneUISurfaceLight,
            onSurface = Color(0xFF1A1B1E),
            surfaceVariant = OneUISurfaceVariantLight,
            onSurfaceVariant = OneUITextMutedLight,
            outline = OneUIOutlineLight,
            outlineVariant = OneUIOutlineVariantLight
        )
    }
}

@Composable
fun NexaFlowTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: String = "blue",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = oneUIColorScheme(darkTheme, accent)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge is enabled in MainActivity, so the system bars are
            // transparent and the app draws behind them. Only the icon
            // appearance (dark/light) needs to follow the theme here.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = SamsungShapes,
        content = content
    )
}
