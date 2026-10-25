package com.nexaflow.core.ui.ambient

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.nexaflow.core.ui.isSystemReduceMotionEnabled
import com.nexaflow.core.ui.theme.LocalNexaFlowTraits
import com.nexaflow.core.ui.theme.NexaFlowTraitsDefaults
import kotlin.math.sin

/**
 * The app's canvas: a flat background with soft shapes behind the content.
 *
 * The shapes are painted with a single [Canvas] and read only in the draw
 * phase, so the animation never triggers a recomposition — it redraws, and
 * the cost is a handful of radial gradients per frame.
 *
 * The drift is driven by the frame clock rather than by an infinite
 * transition, so a still canvas costs nothing: the frame loop only runs
 * while the shapes actually move.
 */
@Composable
fun AmbientCanvas(
    modifier: Modifier = Modifier,
    blobs: List<BlobSpec>? = null,
    animated: Boolean = true,
    content: @Composable () -> Unit
) {
    val reduceMotion = isSystemReduceMotionEnabled()
    val ambientAlpha = NexaFlowTraitsDefaults.ambientAlpha()
    val isDark = NexaFlowTraitsDefaults.isDarkCanvas()
    val traits = LocalNexaFlowTraits.current

    val resolvedBlobs = blobs ?: AmbientBackgrounds.forBackground(traits.background)

    val effectiveBlobs = remember(resolvedBlobs, isDark, ambientAlpha) {
        if (isDark || ambientAlpha < 0.5f) {
            resolvedBlobs
        } else {
            AmbientBackgrounds.forLightCanvas(resolvedBlobs)
        }
    }

    val drifting = animated && !reduceMotion && effectiveBlobs.isNotEmpty()
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(drifting) {
        if (!drifting) return@LaunchedEffect
        val periodNanos = 18_000_000_000L
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                phase = ((now - start) % periodNanos).toFloat() / periodNanos.toFloat() * 6.2831855f
            }
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        if (effectiveBlobs.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                effectiveBlobs.forEachIndexed { index, blob ->
                    val drift = if (drifting) {
                        sin(phase + index * 1.9f) * blob.driftFraction
                    } else {
                        0f
                    }
                    val center = Offset(
                        x = size.width * (blob.centerX + drift),
                        y = size.height * (blob.centerY + drift * 0.6f)
                    )
                    val radius = size.minDimension * blob.radiusFraction
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                blob.color.copy(alpha = blob.alpha * ambientAlpha),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                }
            }
        }
        content()
    }
}
