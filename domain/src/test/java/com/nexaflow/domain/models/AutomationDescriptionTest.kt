package com.nexaflow.domain.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationDescriptionTest {
    @Test
    fun recognizesLegacyGeneratedEnglishDescription() {
        assertTrue("When application, then 1 action(s)".isLegacyGeneratedAutomationDescription())
        assertTrue("When configured, then 12 action(s)".isLegacyGeneratedAutomationDescription())
    }

    @Test
    fun keepsActualUserDescriptionVisible() {
        assertFalse("Enable NFC when I open my payment app".isLegacyGeneratedAutomationDescription())
        assertFalse("عند فتح تطبيق الدفع فعّل NFC".isLegacyGeneratedAutomationDescription())
    }
}
