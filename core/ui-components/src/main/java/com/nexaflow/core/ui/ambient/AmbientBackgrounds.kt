package com.nexaflow.core.ui.ambient

import androidx.compose.ui.graphics.Color
import com.nexaflow.core.ui.theme.NexaFlowBrandAmber
import com.nexaflow.core.ui.theme.NexaFlowBrandEnd
import com.nexaflow.core.ui.theme.NexaFlowBrandRose
import com.nexaflow.core.ui.theme.NexaFlowBackground
import com.nexaflow.core.ui.theme.NexaFlowBrandStart
import com.nexaflow.core.ui.theme.NexaFlowBrandViolet
import com.nexaflow.core.ui.theme.NexaFlowMonoAmbientOne
import com.nexaflow.core.ui.theme.NexaFlowMonoAmbientTwo

/**
 * The ambient backgrounds the appearance settings offer.
 *
 * Each is a fixed set of shapes rather than a random arrangement: a random
 * arrangement reads as noise, a curated set reads as a system the user
 * chose. The light-canvas variants use the same hues at a lower alpha, so
 * switching canvas keeps the background's identity.
 */
object AmbientBackgrounds {

    /** The default — the brand's own indigo, aqua and violet. */
    val ambient: List<BlobSpec> = listOf(
        BlobSpec(NexaFlowBrandStart, radiusFraction = 0.62f, centerX = 0.92f, centerY = 0.04f, alpha = 0.30f),
        BlobSpec(NexaFlowBrandEnd, radiusFraction = 0.54f, centerX = 0.04f, centerY = 0.42f, alpha = 0.18f),
        BlobSpec(NexaFlowBrandViolet, radiusFraction = 0.50f, centerX = 0.86f, centerY = 0.86f, alpha = 0.24f),
        BlobSpec(NexaFlowBrandStart, radiusFraction = 0.40f, centerX = 0.18f, centerY = 0.98f, alpha = 0.12f)
    )

    /** A warmer, calmer field. */
    val ember: List<BlobSpec> = listOf(
        BlobSpec(NexaFlowBrandAmber, radiusFraction = 0.60f, centerX = 0.88f, centerY = 0.08f, alpha = 0.22f),
        BlobSpec(NexaFlowBrandRose, radiusFraction = 0.48f, centerX = 0.06f, centerY = 0.52f, alpha = 0.16f),
        BlobSpec(NexaFlowBrandViolet, radiusFraction = 0.46f, centerX = 0.80f, centerY = 0.90f, alpha = 0.18f)
    )

    /** A single wide field, for people who want the quietest canvas. */
    val quiet: List<BlobSpec> = listOf(
        BlobSpec(NexaFlowBrandStart, radiusFraction = 0.72f, centerX = 0.70f, centerY = 0.10f, alpha = 0.20f),
        BlobSpec(NexaFlowBrandViolet, radiusFraction = 0.60f, centerX = 0.20f, centerY = 0.92f, alpha = 0.14f)
    )

    /** No shapes at all — the flat canvas, for the lowest-power devices. */
    val none: List<BlobSpec> = emptyList()

    /** Every background by its stored id, in the order the picker shows them. */
    val all: Map<String, List<BlobSpec>> = mapOf(
        "ambient" to ambient,
        "ember" to ember,
        "quiet" to quiet,
        "none" to none
    )

    /** The background for [id], falling back to the default. */
    fun forId(id: String?): List<BlobSpec> = all[id] ?: ambient

    /** The background for [background]. */
    fun forBackground(background: NexaFlowBackground): List<BlobSpec> = when (background) {
        NexaFlowBackground.AMBIENT -> ambient
        NexaFlowBackground.EMBER -> ember
        NexaFlowBackground.QUIET -> quiet
        NexaFlowBackground.NONE -> none
    }

    /**
     * The same shapes, softened further, for the light canvas. The light
     * canvas shows the shapes through a white surface, so the same alpha
     * would read as a stain rather than as light.
     */
    fun forLightCanvas(specs: List<BlobSpec>): List<BlobSpec> = specs.map { spec ->
        spec.copy(alpha = spec.alpha * 0.55f)
    }

    /** A neutral field for the monochrome trait. */
    val monochrome: List<BlobSpec> = listOf(
        BlobSpec(NexaFlowMonoAmbientOne, radiusFraction = 0.60f, centerX = 0.90f, centerY = 0.06f, alpha = 0.10f),
        BlobSpec(NexaFlowMonoAmbientTwo, radiusFraction = 0.50f, centerX = 0.06f, centerY = 0.46f, alpha = 0.07f)
    )
}
