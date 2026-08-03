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

private val OneUIDarkColorScheme = darkColorScheme(
    primary = OneUIBlueDark,
    onPrimary = Color(0xFF0D0F12),
    primaryContainer = OneUISurfaceVariantDark,
    onPrimaryContainer = OneUIBlueDark,
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

private val OneUILightColorScheme = lightColorScheme(
    primary = OneUIBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0F5FB),
    onPrimaryContainer = OneUIBlue,
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

@Composable
fun NexaFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) OneUIDarkColorScheme else OneUILightColorScheme
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
