package com.nexaflow.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AppForegroundRules]: system chrome (notification shade,
 * permission dialogs, keyboards) must never end a task's "while open" session,
 * while every real app package must.
 */
class AppForegroundRulesTest {

    @Test
    fun `android and systemui are chrome, not foreground apps`() {
        assertFalse(AppForegroundRules.isForegroundPackage("android"))
        assertFalse(AppForegroundRules.isForegroundPackage("com.android.systemui"))
    }

    @Test
    fun `input-method keyboards are chrome, not foreground apps`() {
        assertFalse(AppForegroundRules.isForegroundPackage("com.google.android.inputmethod.latin"))
        assertFalse(AppForegroundRules.isForegroundPackage("com.android.inputmethod.latin"))
        assertFalse(AppForegroundRules.isForegroundPackage("org.chromium.chrome.inputmethod.ime"))
        assertFalse(AppForegroundRules.isForegroundPackage("com.samsung.android.honeyboard"))
    }

    @Test
    fun `real apps pass the filter`() {
        assertTrue(AppForegroundRules.isForegroundPackage("com.whatsapp"))
        assertTrue(AppForegroundRules.isForegroundPackage("com.instagram.android"))
        assertTrue(AppForegroundRules.isForegroundPackage("com.google.android.apps.nexuslauncher"))
        assertTrue(AppForegroundRules.isForegroundPackage("com.samsung.android.apps.settings"))
    }

    @Test
    fun `case does not affect the keyboard heuristic`() {
        assertFalse(AppForegroundRules.isForegroundPackage("Com.Google.Android.InputMethod.Latin"))
        assertTrue(AppForegroundRules.isForegroundPackage("COM.WHATSAPP"))
    }
}
