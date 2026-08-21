package com.nexaflow.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsDestinationTest {

    @Test
    fun executionHistory_usesTheRegisteredHistoryRoute() {
        assertEquals("history", SettingsDestination.EXECUTION_HISTORY_ROUTE)
    }
}
