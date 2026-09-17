package com.nexaflow.feature.builder

import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.EndBehaviorCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the "every value action supports a complete end-with-value editor"
 * contract: selecting SET_VALUE on any action listed in
 * [EndBehaviorCatalog.valueActions] must produce a non-empty end config and
 * must have a concrete value editor branch, so the engine never receives an
 * empty end value the builder silently dropped.
 */
class EndBehaviorCoverageTest {

    @Test
    fun `every value action has a default end value`() {
        val missing = EndBehaviorCatalog.valueActions
            .filter { defaultEndValue(it).isEmpty() }
        assertTrue(
            "Value actions without a default end value: $missing",
            missing.isEmpty()
        )
    }

    @Test
    fun `every value action has an end value editor branch`() {
        // EndValueEditor is private; its branch list is mirrored here so a new
        // valueAction without an editor branch fails this test loudly. Keep in
        // sync with EndBehaviorEditor#EndValueEditor.
        val editorBranches = setOf(
            ActionType.SYSTEM_BRIGHTNESS,
            ActionType.SYSTEM_VOLUME,
            ActionType.SYSTEM_RING_VOLUME,
            ActionType.SYSTEM_STREAM_VOLUME,
            ActionType.SYSTEM_RINGER_MODE,
            ActionType.SYSTEM_SCREEN_TIMEOUT,
            ActionType.SYSTEM_SCREEN_ROTATION,
            ActionType.SYSTEM_NETWORK_MODE,
            ActionType.SYSTEM_POINTER_SPEED,
            ActionType.SYSTEM_SCREENSAVER_TIMEOUT,
            ActionType.SYSTEM_FONT_SCALE,
            ActionType.SYSTEM_DISPLAY_DENSITY,
        )
        val missing = EndBehaviorCatalog.valueActions - editorBranches
        assertTrue(
            "Value actions without an EndValueEditor branch: $missing",
            missing.isEmpty()
        )
        assertEquals(EndBehaviorCatalog.valueActions, editorBranches)
    }

    @Test
    fun `toggle actions support revert`() {
        val nonRevertible = EndBehaviorCatalog.toggleActions.filterNot {
            EndBehaviorCatalog.supportsRevert(it)
        }
        // Only the flashlight is exempt (no public torch-state API).
        assertEquals(setOf(ActionType.SYSTEM_FLASHLIGHT), nonRevertible.toSet())
    }
}
