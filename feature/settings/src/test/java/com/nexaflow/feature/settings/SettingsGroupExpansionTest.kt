package com.nexaflow.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsGroupExpansionTest {

    @Test
    fun tappingCollapsedGroupExpandsIt() {
        assertEquals(
            "automation",
            nextExpandedSettingsGroup(
                currentExpandedGroup = null,
                tappedGroup = "automation"
            )
        )
    }

    @Test
    fun tappingExpandedGroupCollapsesIt() {
        assertNull(
            nextExpandedSettingsGroup(
                currentExpandedGroup = "backup",
                tappedGroup = "backup"
            )
        )
    }

    @Test
    fun tappingDifferentGroupReplacesTheExpandedGroup() {
        assertEquals(
            "updates",
            nextExpandedSettingsGroup(
                currentExpandedGroup = "automation",
                tappedGroup = "updates"
            )
        )
    }
}
