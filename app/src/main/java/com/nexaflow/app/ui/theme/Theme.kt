package com.nexaflow.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.nexaflow.core.datastore.ThemeMode
import com.nexaflow.core.ui.GlassPreference
import com.nexaflow.core.ui.LocalGlassPreference
import com.nexaflow.core.ui.LocalGlassState
import com.nexaflow.core.ui.rememberGlassState
import com.nexaflow.core.ui.theme.DarkNexaFlowCategoryColors
import com.nexaflow.core.ui.theme.DarkNexaFlowColors
import com.nexaflow.core.ui.theme.LightNexaFlowCategoryColors
import com.nexaflow.core.ui.theme.LightNexaFlowColors
import com.nexaflow.core.ui.theme.LocalNexaFlowCategoryColors
import com.nexaflow.core.ui.theme.LocalNexaFlowColors
import com.nexaflow.core.ui.theme.LocalNexaFlowType
import com.nexaflow.core.ui.theme.NexaFlowColors

/**
 * Ink Glass shape system (§5.3): 24dp content cards, 28dp grouped surfaces and
 * sheets, fully-rounded pills. Content has no shadows; only the Dock does.
 */
private val InkShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NexaFlowTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: String = "ink",
    dynamicColor: Boolean = false,
    glass: GlassPreference = GlassPreference.AUTO,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Material You — wallpaper-sourced palette; the Ink alphas still apply.
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        googleColorScheme(
            seed = accentSeeds[accent] ?: accentSeeds.getValue(DEFAULT_ACCENT),
            dark = darkTheme
        )
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

    // Dynamic colour has no dedicated label roles, so the eyebrow and the Ask
    // pill follow primary — a wallpaper scheme's `onPrimaryContainer` can sit
    // darker than the pill fill and turn the label invisible.
    val baseColors = if (darkTheme) DarkNexaFlowColors else LightNexaFlowColors
    val nexaFlowColors: NexaFlowColors = if (dynamicColor) {
        baseColors.copy(
            accentLabel = colorScheme.primary,
            askPillLabel = colorScheme.primary
        )
    } else {
        baseColors
    }
    // The family is per locale, so the locale is the key: a locale change is
    // the only thing that can produce a different family. Kept as a plain
    // `remember` here rather than a value-returning @Composable, because the
    // Compose stability budget counts those as non-skippable composables.
    val locale = LocalConfiguration.current.locales[0]
    val fontFamily = remember(locale.language) { contentFontFamily(locale.language) }
    val nexaType = remember(fontFamily) { com.nexaflow.core.ui.theme.nexaFlowType(fontFamily) }
    val m3Typography = remember(fontFamily) { nexaFlowM3Typography(fontFamily) }
    val glassState = rememberGlassState()

    CompositionLocalProvider(
        LocalNexaFlowColors provides nexaFlowColors,
        LocalNexaFlowCategoryColors provides
            if (darkTheme) DarkNexaFlowCategoryColors else LightNexaFlowCategoryColors,
        LocalNexaFlowType provides nexaType,
        LocalGlassPreference provides glass,
        LocalGlassState provides glassState
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            // The expressive motion scheme (springs) keeps every built-in
            // animation in the same language as the app's motion helpers.
            motionScheme = MotionScheme.expressive(),
            typography = m3Typography,
            shapes = InkShapes,
            content = content
        )
    }
}

/** Key of the default accent seed. Ink ships as the default (§14.1). */
const val DEFAULT_ACCENT = "ink"
