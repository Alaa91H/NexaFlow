package com.nexaflow.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndBehaviorCatalogTest {

    @Test
    fun `toggle value and revert-only actions expose the intended end behavior`() {
        assertTrue(EndBehaviorCatalog.supportsEndBehavior(ActionType.SYSTEM_WIFI))
        assertTrue(EndBehaviorCatalog.supportsRevert(ActionType.SYSTEM_WIFI))

        assertTrue(EndBehaviorCatalog.supportsEndBehavior(ActionType.SYSTEM_BRIGHTNESS))
        assertTrue(EndBehaviorCatalog.supportsRevert(ActionType.SYSTEM_BRIGHTNESS))

        assertTrue(EndBehaviorCatalog.supportsEndBehavior(ActionType.SYSTEM_SET_RINGTONE))
        assertFalse(EndBehaviorCatalog.supportsRevert(ActionType.SYSTEM_SET_RINGTONE))
    }

    @Test
    fun `flashlight can be ended but cannot claim unsupported state restoration`() {
        assertTrue(ActionType.SYSTEM_FLASHLIGHT in EndBehaviorCatalog.toggleActions)
        assertTrue(EndBehaviorCatalog.supportsEndBehavior(ActionType.SYSTEM_FLASHLIGHT))
        assertFalse(EndBehaviorCatalog.supportsRevert(ActionType.SYSTEM_FLASHLIGHT))
    }

    @Test
    fun `ordinary one-shot actions do not advertise per-action end state`() {
        assertFalse(EndBehaviorCatalog.supportsEndBehavior(ActionType.SYSTEM_SEND_NOTIFICATION))
        assertFalse(EndBehaviorCatalog.supportsRevert(ActionType.SYSTEM_SEND_NOTIFICATION))
    }

    @Test
    fun `catalog sets stay disjoint where behavior semantics differ`() {
        assertTrue(EndBehaviorCatalog.toggleActions.intersect(EndBehaviorCatalog.valueActions).isEmpty())
        assertTrue(EndBehaviorCatalog.toggleActions.intersect(EndBehaviorCatalog.revertOnlyActions).isEmpty())
        assertTrue(EndBehaviorCatalog.valueActions.intersect(EndBehaviorCatalog.revertOnlyActions).isEmpty())
    }

    @Test
    fun `end behavior defaults to leave and keeps set-value configuration`() {
        assertEquals(EndMode.LEAVE, EndBehavior().mode)
        assertTrue(EndBehavior().config.isEmpty())

        val behavior = EndBehavior(
            mode = EndMode.SET_VALUE,
            config = mapOf("value" to "25")
        )
        assertEquals(EndMode.SET_VALUE, behavior.mode)
        assertEquals("25", behavior.config["value"])
    }
}
