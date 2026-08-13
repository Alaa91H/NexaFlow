package com.nexaflow.core.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Google 2026 (M3 Expressive) motion tokens.
 *
 * These are the documented M3 Expressive spring values (m3.material.io —
 * "Emphasized" spring motion): the expressive scheme replaces tweens with
 * springs at 400/800 stiffness and ~0.8 damping, giving the slight overshoot
 * ("squish") that defines the 2025–2026 Google feel. The androidx material3
 * version the Compose BOM resolves (1.4.0) ships `MotionScheme` as an
 * internal API, so we expose the same springs directly and keep one motion
 * language across the app.
 */
object NexaFlowSprings {
    /** Default entrance/exit spring — 400 stiffness, 0.8 damping (slight bounce). */
    val Default = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMedium // 400
    )

    /** Fast micro-interaction spring — 800 stiffness, 0.85 damping (quick, quiet). */
    val Fast = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessHigh // 800
    )
}

/**
 * True when the system "remove animations" / reduced-motion accessibility
 * setting is on. Springs have no duration, so the standard duration-scale
 * mechanism does not cover them — we check the scale explicitly.
 */
@Composable
fun isSystemReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

/**
 * The Material3 1.4.0 expressive-motion APIs (`MotionScheme`, its tokens and
 * the `value()` accessor) are still `internal` in the release the project's
 * Compose BOM resolves — verified against the artifact's Kotlin metadata.
 * The documented M3 Expressive spring constants (400/800 stiffness, ~0.8
 * damping — the 2025–2026 Google "emphasized" language with its slight
 * overshoot) are therefore exposed here in generic form, so spatial motion
 * (IntOffset/IntSize specs for expand/slide) and effects (Float fade specs)
 * share one motion language. Falls back to an instant tween when the user
 * disabled animations.
 */
private fun <T> spatialSpring(slow: Boolean): FiniteAnimationSpec<T> = spring(
    dampingRatio = if (slow) 0.7f else 0.8f,
    stiffness = if (slow) Spring.StiffnessLow else Spring.StiffnessMedium
)

private fun <T> effectsSpring(slow: Boolean): FiniteAnimationSpec<T> = spring(
    dampingRatio = if (slow) 0.9f else 0.85f,
    stiffness = if (slow) Spring.StiffnessMedium else Spring.StiffnessHigh
)

/** Spatial spring (expanding/collapsing, sliding) — M3 Expressive. */
@Composable
fun <T> nexaFlowSpatialSpec(slow: Boolean = false): FiniteAnimationSpec<T> {
    if (isSystemReduceMotionEnabled()) return tween(0)
    return spatialSpring(slow)
}

/** Effects spring (fades) — M3 Expressive. */
@Composable
fun <T> nexaFlowEffectsSpec(slow: Boolean = false): FiniteAnimationSpec<T> {
    if (isSystemReduceMotionEnabled()) return tween(0)
    return effectsSpring(slow)
}

/**
 * Google 2026 accordion open/close: content expands/shrinks with the M3
 * expressive spatial spring while fading with the effects spring. With the
 * system reduce-motion setting on, the content simply appears/disappears.
 */
@Composable
fun NexaFlowAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (isSystemReduceMotionEnabled()) {
        if (visible) content()
        return
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(
            animationSpec = nexaFlowSpatialSpec(),
            clip = false
        ) + fadeIn(animationSpec = nexaFlowEffectsSpec()),
        exit = shrinkVertically(
            animationSpec = nexaFlowSpatialSpec(),
            clip = false
        ) + fadeOut(animationSpec = nexaFlowEffectsSpec())
    ) {
        content()
    }
}

/**
 * Spring entrance for custom dialogs: the surface scales in (0.92 → 1) with
 * the M3 spatial spring while fading with the effects spring — the same
 * language Material3 uses for its own dialogs. Static under reduce-motion.
 */
fun Modifier.nexaFlowDialogEnter(): Modifier = composed {
    if (isSystemReduceMotionEnabled()) {
        this
    } else {
        var started by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (started) 1f else 0.92f,
            animationSpec = nexaFlowSpatialSpec(),
            label = "nexaflowDialogScale"
        )
        val alpha by animateFloatAsState(
            targetValue = if (started) 1f else 0f,
            animationSpec = nexaFlowEffectsSpec(),
            label = "nexaflowDialogAlpha"
        )
        LaunchedEffect(Unit) {
            started = true
        }
        graphicsLayer {
            this.alpha = alpha
            this.scaleX = scale
            this.scaleY = scale
        }
    }
}

/**
 * Google 2026 staggered entrance: the content fades + scales in with the
 * expressive spring, optionally after [delayMillis] (use per-item delays for
 * a Keep-style cascade). Layout is never affected (the transform lives on
 * `graphicsLayer`), and with the system reduce-motion setting on the content
 * is shown statically — no animation at all.
 */
fun Modifier.nexaFlowEntrance(delayMillis: Int = 0): Modifier = composed {
    if (isSystemReduceMotionEnabled()) {
        this
    } else {
        var started by remember { mutableStateOf(false) }
        val alpha by animateFloatAsState(
            targetValue = if (started) 1f else 0f,
            animationSpec = NexaFlowSprings.Default,
            label = "nexaflowEntranceAlpha"
        )
        val scale by animateFloatAsState(
            targetValue = if (started) 1f else 0.97f,
            animationSpec = NexaFlowSprings.Default,
            label = "nexaflowEntranceScale"
        )
        LaunchedEffect(Unit) {
            if (delayMillis > 0) delay(delayMillis.toLong())
            started = true
        }
        graphicsLayer {
            this.alpha = alpha
            this.scaleX = scale
            this.scaleY = scale
        }
    }
}
