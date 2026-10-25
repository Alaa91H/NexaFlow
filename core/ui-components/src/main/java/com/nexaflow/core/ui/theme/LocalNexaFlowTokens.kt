package com.nexaflow.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The design tokens that are not part of Material 3's own theme object:
 * the semantic NexaFlow colors and the appearance traits.
 *
 * Shapes and typography stay on `MaterialTheme` — they are standard M3
 * slots and every component already reads them from there. Only the roles
 * M3 has no slot for travel through this holder.
 */
@Immutable
data class NexaFlowTokens(
    val colors: NexaFlowColors,
    val traits: NexaFlowTraits
)

/** The active NexaFlow tokens. Provided once by the app theme. */
val LocalNexaFlowTokens = staticCompositionLocalOf {
    NexaFlowTokens(colors = LightNexaFlowColors, traits = NexaFlowTraits())
}

/** Central accessor mirroring [androidx.compose.material3.MaterialTheme]. */
object NexaFlowTokenHolder {
    val current: NexaFlowTokens
        @Composable @ReadOnlyComposable get() = LocalNexaFlowTokens.current
}
