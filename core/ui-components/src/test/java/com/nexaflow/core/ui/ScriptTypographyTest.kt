package com.nexaflow.core.ui

import androidx.compose.ui.unit.sp
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.nexaflow.core.ui.theme.containsArabicScript
import com.nexaflow.core.ui.theme.eyebrowText
import com.nexaflow.core.ui.theme.forText
import com.nexaflow.core.ui.theme.nexaFlowType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Arabic must never receive tracking or uppercasing; the gate is the string's
 * own script, not the app locale (§5.5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScriptTypographyTest {

    private val eyebrow = nexaFlowType().eyebrow

    @Test
    fun arabicDetectionCoversMixedStrings() {
        assertTrue(containsArabicScript("تشغيل GPS"))
        assertTrue(containsArabicScript("وضع النوم"))
        assertFalse(containsArabicScript("O2 2GB"))
        assertFalse(containsArabicScript("Morning mode"))
    }

    @Test
    fun trackingIsRemovedForArabicOnly() {
        assertEquals(0.sp, eyebrow.forText("تشغيل GPS").letterSpacing)
        assertEquals(eyebrow.letterSpacing, eyebrow.forText("Morning mode").letterSpacing)
    }

    @Test
    fun eyebrowsAreUppercasedOnlyForLatinScript() {
        assertEquals("NEXT RUN", eyebrowText("Next run"))
        assertEquals("التشغيل القادم", eyebrowText("التشغيل القادم"))
        assertEquals("تشغيل GPS", eyebrowText("تشغيل GPS"))
    }

    @Test
    fun typeScaleMatchesTheReferenceTokens() {
        val type = nexaFlowType()
        assertEquals(30.sp, type.screenTitle.fontSize)
        assertEquals(22.sp, type.subScreenTitle.fontSize)
        assertEquals(16.sp, type.cardTitle.fontSize)
        assertEquals(12.5.sp, type.meta.fontSize)
        assertEquals(11.sp, type.eyebrow.fontSize)
    }
}
