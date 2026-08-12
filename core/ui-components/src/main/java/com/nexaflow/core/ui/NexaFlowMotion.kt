package com.nexaflow.core.ui

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
