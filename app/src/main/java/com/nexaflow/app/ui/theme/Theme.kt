package com.nexaflow.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.nexaflow.core.datastore.ThemeMode

/**
 * Material 3 Expressive shape system (Google's 2025-2026 design language):
 * 8 / 12 / 16 / 24 / 28 dp. Compared with the original M3 scale (4/8/12/16/28)
 * the radii step up across the board — pills on small controls, 24dp cards,
 * 28dp sheets — which is exactly how Google's current apps (Tasks, Calendar,
 * Clock) present surfaces. Every component consumes these tokens via
 * `MaterialTheme.shapes`, so one change re-skins the whole app.
 */
private val GoogleShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NexaFlowTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: String = "blue",
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Material You — wallpaper-sourced palette, exactly like Google apps.
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        googleColorScheme(accentSeeds[accent] ?: accentSeeds.getValue("blue"), darkTheme)
    }
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
        shapes = GoogleShapes,
        content = content
    )
}
