package com.nexaflow.core.ui

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.NexaFlowAlphas
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * User preference for the glass chrome (§7.4 Themes → Glass effects).
 * `AUTO` lets the platform decide: blur on API 31+, solid in Battery Saver.
 */
enum class GlassPreference {
    AUTO,
    ON,
    OFF
}

/** The resolved rendering mode of a [GlassSurface]. */
enum class GlassMode {
    /** Backdrop blur (Haze) with a 65% `surfaceContainerHigh` fill. */
    Blur,

    /** Opaque-enough fill at 94%, same border, same size, same shadow. */
    Solid
}

/**
 * Pure resolution of the Dock material so it can be unit-tested without a
 * device: API < 31 or Battery Saver or an explicit Off always yields [GlassMode.Solid].
 */
fun resolveGlassMode(
    sdkInt: Int,
    batterySaver: Boolean,
    preference: GlassPreference
): GlassMode = when (preference) {
    GlassPreference.OFF -> GlassMode.Solid
    GlassPreference.ON -> if (sdkInt >= Build.VERSION_CODES.S) GlassMode.Blur else GlassMode.Solid
    GlassPreference.AUTO ->
        if (sdkInt >= Build.VERSION_CODES.S && !batterySaver) GlassMode.Blur else GlassMode.Solid
}

/** Composition local supplied by the app theme from persisted preferences. */
val LocalGlassPreference = staticCompositionLocalOf { GlassPreference.AUTO }

/**
 * One blur source per screen. Wrapping the Haze state keeps the third-party
 * types out of feature modules: screens call [Modifier.glassSource], surfaces
 * call [GlassSurface].
 */
@Stable
class GlassState internal constructor(internal val haze: HazeState)

@Composable
fun rememberGlassState(): GlassState = remember { GlassState(HazeState()) }

val LocalGlassState = staticCompositionLocalOf<GlassState?> { null }

/**
 * Marks this layout node as the backdrop the Dock refracts. Call it once on
 * the screen root — never per list item.
 */
@Composable
fun Modifier.glassSource(state: GlassState? = LocalGlassState.current): Modifier =
    if (state == null) this else this.hazeSource(state.haze)

/** Reads SDK level, Battery Saver and the preference, then resolves the mode. */
@Composable
fun rememberGlassMode(): GlassMode {
    val context = LocalContext.current
    val preference = LocalGlassPreference.current
    val sdkInt = Build.VERSION.SDK_INT
    val batterySaver = remember(context) {
        runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode
                ?: false
        }.getOrDefault(false)
    }
    return remember(sdkInt, batterySaver, preference) {
        resolveGlassMode(sdkInt, batterySaver, preference)
    }
}

object GlassDefaults {
    val BlurRadius: Dp = 22.dp
    const val FillAlpha: Float = 0.65f
    const val SolidAlpha: Float = 0.94f
    const val Saturation: Float = 1.6f

    /** Haze 1.x has no saturation control; a faint tint stands in for it. */
    const val SaturationTint: Float = 0.10f
}

/**
 * The only blurred element in the app. Layout is identical in [GlassMode.Blur]
 * and [GlassMode.Solid], so switching never reflows the Dock.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimens.DockRadius),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    borderColor: Color = MaterialTheme.colorScheme.onSurface,
    shadowElevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val mode = rememberGlassMode()
    val state = LocalGlassState.current
    val fillAlpha = if (mode == GlassMode.Blur) GlassDefaults.FillAlpha else GlassDefaults.SolidAlpha

    var surface = modifier
    if (shadowElevation > 0.dp) {
        val shadow = Color.Black.copy(alpha = 0.40f)
        surface = surface.shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = shadow,
            spotColor = shadow
        )
    }
    surface = surface.clip(shape)

    if (mode == GlassMode.Blur && state != null) {
        surface = surface.hazeEffect(
            state = state.haze,
            style = HazeStyle(
                blurRadius = GlassDefaults.BlurRadius,
                tint = HazeTint(containerColor.copy(alpha = GlassDefaults.SaturationTint))
            )
        ) {
            blurEnabled = true
        }
    }

    Box(
        modifier = surface
            .background(containerColor.copy(alpha = fillAlpha), shape)
            .border(Dimens.HairlineBorder, borderColor.copy(alpha = NexaFlowAlphas.GlassBorder), shape),
        content = content
    )
}
