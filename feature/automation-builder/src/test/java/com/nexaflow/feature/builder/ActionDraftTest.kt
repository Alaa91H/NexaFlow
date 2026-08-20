package com.nexaflow.feature.builder

import androidx.compose.ui.graphics.Color
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ActionDraftTest {

    @Test
    fun duplicateActionTypes_keepIndependentConfigsAndEndBehavior() {
        val option = actionOptions.first { it.actionType == ActionType.SYSTEM_VOLUME }
        val first = ActionDraft(
            id = "first-volume",
            option = option,
            config = mapOf("value" to "20"),
            endBehavior = EndBehavior(EndMode.SET_VALUE, mapOf("value" to "10"))
        )
        val second = ActionDraft(
            id = "second-volume",
            option = option,
            config = mapOf("value" to "80")
        )

        val actions = listOf(first, second).map { it.toAction() }

        assertEquals(2, actions.size)
        assertEquals(ActionType.SYSTEM_VOLUME, actions[0].type)
        assertEquals(ActionType.SYSTEM_VOLUME, actions[1].type)
        assertEquals("20", actions[0].config["value"])
        assertEquals("80", actions[1].config["value"])
        assertEquals(EndMode.SET_VALUE, actions[0].endBehavior?.mode)
        assertEquals(null, actions[1].endBehavior)
    }

    @Test
    fun cardAccent_cycleDistinguishesAdjacentCards_andRepeatsOnlyAfterPalette() {
        assertNotEquals(builderCardAccent(0), builderCardAccent(1))
        assertNotEquals(builderCardAccent(1), builderCardAccent(2))
        assertEquals(
            builderCardAccent(0),
            builderCardAccent(builderCardAccentPalette.size)
        )
    }

    @Test
    fun cardContainer_cycleUsesGreyAndDarkGrey_withLightReadableContent() {
        assertEquals(Color(0xFF2F3336), builderCardContainerColor(0))
        assertEquals(Color(0xFF1E2022), builderCardContainerColor(1))
        assertNotEquals(builderCardContainerColor(0), builderCardContainerColor(1))
        assertEquals(builderCardContainerColor(0), builderCardContainerColor(2))
        assertEquals(Color(0xFFF5F7F8), builderCardContentColor)
    }
}
