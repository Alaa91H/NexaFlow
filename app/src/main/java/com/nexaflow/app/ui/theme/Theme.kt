package com.nexaflow.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: String = "blue",
    content: @Composable () -> Unit
) {
    val colorScheme = oneUIColorScheme(darkTheme, accent)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
