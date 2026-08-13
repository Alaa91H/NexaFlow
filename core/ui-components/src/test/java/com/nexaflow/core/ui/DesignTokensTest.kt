package com.nexaflow.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.nexaflow.core.ui.theme.DarkNexaFlowColors
import com.nexaflow.core.ui.theme.LightNexaFlowColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the design-token system so a future edit that drifts a token value
 * (or accidentally replaces a semantic role with a raw color) fails loudly.
 */
class DesignTokensTest {

    // --- Dimens: 4dp spacing grid, M3-expressive radii ---
    @Test
    fun dimens_follows4dpGrid() {
        assertEquals(Dimens.Space1, 4.dp)
        assertEquals(Dimens.Space2, 8.dp)
        assertEquals(Dimens.Space3, 12.dp)
        assertEquals(Dimens.Space4, 16.dp)
        assertEquals(Dimens.Space5, 20.dp)
        assertEquals(Dimens.Space6, 24.dp)
    }

    @Test
    fun dimens_screenPaddingUsesGrid() {
        assertEquals(Dimens.ScreenHorizontalPadding, 16.dp)
        assertEquals(Dimens.NavigationBarHeight, 80.dp)
    }

    // --- NexaFlowColors: dark-aware success/warning families ---
    @Test
    fun light_successMatchesM3GreenTone40() {
        assertEquals(Color(0xFF006D3C), LightNexaFlowColors.success)
        // Container must be a light tint — never the same as the foreground.
        assertTrue(LightNexaFlowColors.successContainer.luminance() > 0.8f)
    }

    @Test
    fun dark_successUsesLightTone80_notRawGreen() {
        assertEquals(Color(0xFF78DAA0), DarkNexaFlowColors.success)
        // The dark bug we fixed: a raw #006D3C on a dark surface is invisible.
        // The dark container must be dark and the foreground light.
        assertTrue(DarkNexaFlowColors.successContainer.luminance() < 0.2f)
        assertTrue(DarkNexaFlowColors.success.luminance() > 0.4f)
    }

    @Test
    fun dark_warningContainerIsDark() {
        assertTrue(DarkNexaFlowColors.warningContainer.luminance() < 0.2f)
        assertTrue(DarkNexaFlowColors.warning.luminance() > 0.4f)
    }

    @Test
    fun lightContainerRolesDifferFromForeground() {
        // A container role must never equal its on-color in the same scheme.
        assertTrue(LightNexaFlowColors.successContainer != LightNexaFlowColors.success)
        assertTrue(LightNexaFlowColors.warningContainer != LightNexaFlowColors.warning)
        assertTrue(DarkNexaFlowColors.successContainer != DarkNexaFlowColors.success)
        assertTrue(DarkNexaFlowColors.warningContainer != DarkNexaFlowColors.warning)
    }
}
