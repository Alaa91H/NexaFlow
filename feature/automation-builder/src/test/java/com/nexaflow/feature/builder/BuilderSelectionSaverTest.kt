package com.nexaflow.feature.builder

import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Test

class BuilderSelectionSaverTest {

    @Test
    fun actionSelection_restoreKeepsMultipleKnownTypes_andDropsUnknownValues() {
        val restored = ActionTypeSelectionSaver.restore(
            arrayListOf(ActionType.SYSTEM_VOLUME.name, "MISSING_ACTION", ActionType.SYSTEM_DND.name)
        )

        assertEquals(
            listOf(ActionType.SYSTEM_VOLUME, ActionType.SYSTEM_DND),
            restored?.toList()
        )
    }

    @Test
    fun triggerSelection_restoreKeepsMultipleKnownTypes_andDropsUnknownValues() {
        val restored = TriggerTypeSelectionSaver.restore(
            arrayListOf(TriggerType.TIME.name, "MISSING_TRIGGER", TriggerType.APPLICATION.name)
        )

        assertEquals(
            listOf(TriggerType.TIME, TriggerType.APPLICATION),
            restored?.toList()
        )
    }
}
