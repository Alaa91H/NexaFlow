package com.nexaflow.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.nexaflow.core.datastore.ThemeMode
import com.nexaflow.core.ui.theme.DarkNexaFlowColors
import com.nexaflow.core.ui.theme.LightNexaFlowColors
import com.nexaflow.core.ui.theme.LocalNexaFlowColors
import com.nexaflow.core.ui.theme.LocalNexaFlowTokens
import com.nexaflow.core.ui.theme.LocalNexaFlowTraits
import com.nexaflow.core.ui.theme.NexaFlowBackground
import com.nexaflow.core.ui.theme.NexaFlowDensity
import com.nexaflow.core.ui.theme.NexaFlowShapes
import com.nexaflow.core.ui.theme.NexaFlowTokens
import com.nexaflow.core.ui.theme.NexaFlowTraits
import com.nexaflow.core.ui.theme.accentSeedFor
import com.nexaflow.core.ui.theme.nexaFlowColorScheme

/**
 * The app's theme.
 *
 * NexaFlow One: a near-black canvas, a brand gradient, a set of composable
 * appearance traits and one shape scale that every screen reads through
 * `MaterialTheme`.
 *
 * The traits are what make the appearance settings interact rather than
 * sit next to each other. Monochrome flattens the accents, the outline
 * switch removes the hairlines, the ambient switch removes the shapes, and
 * the density switch tightens the layout — and all of them compose, so the
 * screen a user lands on is the one they assembled.
 */
@Composable
fun NexaFlowTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: String = "indigo",
    dynamicColor: Boolean = false,
    monochrome: Boolean = false,
    colorAccents: Boolean = true,
    outlines: Boolean = true,
    blur: Boolean = true,
    ambientBackground: Boolean = true,
    density: String = "comfortable",
    background: String = "ambient",
    pureBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current

    val baseScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Material You — wallpaper-sourced colors. The user opted into
        // these; the app's own palette is the default.
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        nexaFlowColorScheme(accentSeedFor(accent), darkTheme)
    }

    val colorScheme = if (pureBlack && darkTheme) {
        // Pure black for OLED panels: the canvas and the lowest surface tier
        // go to true black so pixels stay off, while the elevated tiers keep
        // their separation.
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A)
        )
    } else {
        baseScheme
    }

    val traits = NexaFlowTraits(
        monochrome = monochrome,
        colorAccents = colorAccents,
        outlines = outlines,
        blur = blur,
        ambientBackground = ambientBackground,
        density = if (density == "compact") NexaFlowDensity.COMPACT else NexaFlowDensity.COMFORTABLE,
        background = NexaFlowBackground.fromStored(background)
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge is enabled in MainActivity, so the system bars
            // are transparent and the app draws behind them. Only the icon
            // appearance (dark/light) needs to follow the theme here.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val semanticColors = if (darkTheme) DarkNexaFlowColors else LightNexaFlowColors

    CompositionLocalProvider(
        LocalNexaFlowColors provides semanticColors,
        LocalNexaFlowTraits provides traits,
        LocalNexaFlowTokens provides NexaFlowTokens(colors = semanticColors, traits = traits)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            // The expressive motion scheme (springs at ~0.6-0.8 damping /
            // 200-800 stiffness). Every built-in component that animates
            // from the scheme follows it automatically, and the app's
            // custom motion helpers read MaterialTheme.motionScheme so they
            // stay in the same language.
            motionScheme = MotionScheme.expressive(),
            typography = Typography,
            shapes = NexaFlowShapes,
            content = content
        )
    }
}
