package com.nexaflow.core.execution.handler

import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemActionsHandlerExpansionTest {

    @Test
    fun `routine expansion intent actions are owned by the system handler`() {
        val supported = SystemActionsHandler().supportedTypes

        assertTrue(ActionType.SYSTEM_SET_TIMER in supported)
        assertTrue(ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS in supported)
        assertTrue(ActionType.SYSTEM_OPEN_SYSTEM_UPDATE_SETTINGS in supported)
        assertTrue(ActionType.SYSTEM_MEDIA_PLAY_FROM_SEARCH in supported)
    }
}
