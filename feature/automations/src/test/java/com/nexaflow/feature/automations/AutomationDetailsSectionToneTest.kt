package com.nexaflow.feature.automations

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationDetailsSectionToneTest {

    @Test
    fun fourTaskDetailSectionsAlternateGraySurfaceTones() {
        assertEquals(AutomationDetailsSectionTone.LIGHT_GRAY, automationDetailsSectionTone(0))
        assertEquals(AutomationDetailsSectionTone.DARK_GRAY, automationDetailsSectionTone(1))
        assertEquals(AutomationDetailsSectionTone.LIGHT_GRAY, automationDetailsSectionTone(2))
        assertEquals(AutomationDetailsSectionTone.DARK_GRAY, automationDetailsSectionTone(3))
    }
}
