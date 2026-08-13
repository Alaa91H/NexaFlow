package com.nexaflow.core.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
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
 * Google 2026 (M3 Expressive) motion, sourced from the active
 * [MotionScheme] (material3 1.5+, where the scheme is public API).
 *
 * material3 1.4.0 shipped `MotionScheme` as an internal API, so we previously
 * exposed the documented M3 Expressive spring constants by hand. Since the
 * project moved to material3 1.5.0-alpha26, `MotionScheme`, its
 * `standard()`/`expressive()` factories, `MaterialTheme.motionScheme` and the
 * `MotionSchemeKeyTokens` accessors are public — so this file now reads the
 * actual scheme the app's theme was built with instead of duplicating
 * constants. One motion language across the app, driven by the real tokens.
 */
object NexaFlowSprings {
    /**
     * Default entrance/exit spring — the scheme's default spatial spec
     * (slight overshoot, the 2025–2026 Google "emphasized" language).
     */
    @Composable
    fun default(): FiniteAnimationSpec<Float> {
        if (isSystemReduceMotionEnabled()) return tween(0)
        return MaterialTheme.motionScheme.defaultSpatialSpec()
    }

    /** Fast micro-interaction spring — the scheme's fast spatial spec. */
    @Composable
    fun fast(): FiniteAnimationSpec<Float> {
        if (isSystemReduceMotionEnabled()) return tween(0)
        return MaterialTheme.motionScheme.fastSpatialSpec()
    }
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

/** Reads the current [MotionScheme] from the app theme. */
@Composable
private fun currentMotionScheme(): MotionScheme = MaterialTheme.motionScheme

/** Spatial spring (expanding/collapsing, sliding) — from the active scheme. */
@Composable
fun <T> nexaFlowSpatialSpec(slow: Boolean = false): FiniteAnimationSpec<T> {
    if (isSystemReduceMotionEnabled()) return tween(0)
    val scheme = currentMotionScheme()
    return if (slow) scheme.slowSpatialSpec() else scheme.defaultSpatialSpec()
}

/** Effects spring (fades) — from the active scheme. */
@Composable
fun <T> nexaFlowEffectsSpec(slow: Boolean = false): FiniteAnimationSpec<T> {
    if (isSystemReduceMotionEnabled()) return tween(0)
    val scheme = currentMotionScheme()
    return if (slow) scheme.slowEffectsSpec() else scheme.defaultEffectsSpec()
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
            animationSpec = NexaFlowSprings.default(),
            label = "nexaflowEntranceAlpha"
        )
        val scale by animateFloatAsState(
            targetValue = if (started) 1f else 0.97f,
            animationSpec = NexaFlowSprings.default(),
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
