package com.nexaflow.feature.dashboard

import com.nexaflow.core.ui.theme.NexaFlowCategory
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ink replaced the alternating grey card surfaces with category meaning. These
 * tests pin the resolution rules from §5.2: first trigger, else first action,
 * else Advanced.
 */
class RoutineCategoryTest {

    private fun automation(
        triggers: List<Trigger> = emptyList(),
        actions: List<Action> = emptyList()
    ) = Automation(
        id = "a",
        name = "Category",
        description = "",
        icon = "bolt",
        iconColor = 0xFFFFFFFF,
        backgroundColor = 0xFF000000,
        category = "custom",
        priority = 0,
        enabled = true,
        triggers = triggers,
        actions = actions,
        createdAt = 0,
        updatedAt = 0
    )

    @Test
    fun firstTriggerWins() {
        assertEquals(
            NexaFlowCategory.TIME,
            automationCategory(automation(listOf(Trigger(TriggerType.TIME, emptyMap()))))
        )
        assertEquals(
            NexaFlowCategory.CONNECTIVITY,
            automationCategory(automation(listOf(Trigger(TriggerType.WIFI_CONNECTED, emptyMap()))))
        )
        assertEquals(
            NexaFlowCategory.POWER,
            automationCategory(automation(listOf(Trigger(TriggerType.BATTERY, emptyMap()))))
        )
        assertEquals(
            NexaFlowCategory.SOUND,
            automationCategory(automation(listOf(Trigger(TriggerType.RINGER_MODE, emptyMap()))))
        )
    }

    @Test
    fun actionIsTheFallbackWhenThereAreNoTriggers() {
        assertEquals(
            NexaFlowCategory.CONNECTIVITY,
            automationCategory(automation(actions = listOf(Action(ActionType.SYSTEM_WIFI, emptyMap()))))
        )
        assertEquals(
            NexaFlowCategory.NOTIFICATIONS,
            automationCategory(
                automation(actions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, emptyMap())))
            )
        )
    }

    @Test
    fun unknownNodesFallBackToAdvanced() {
        assertEquals(NexaFlowCategory.ADVANCED, automationCategory(automation()))
    }

    @Test
    fun nextRunIsNullWhenDisabled() {
        val disabled = automation(listOf(Trigger(TriggerType.TIME, mapOf("time" to "08:00"))))
            .copy(enabled = false)
        assertEquals(null, nextRunMillis(disabled, System.currentTimeMillis()))
    }
}
