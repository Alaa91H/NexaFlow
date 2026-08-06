package com.nexaflow.data.mapper

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationMapperTest {

    private val automation = Automation(
        id = "a1",
        name = "Night mode",
        description = "Dim the screen at night",
        icon = "dnd",
        iconColor = 0xFF1B62B7,
        backgroundColor = 0xFF111111,
        category = "custom",
        priority = 2,
        enabled = true,
        triggers = listOf(
            Trigger(TriggerType.TIME, config = mapOf("time" to "22:00")),
            Trigger(TriggerType.NOTIFICATION, config = mapOf("packages" to "com.whatsapp", "contains" to "order", "event" to "POSTED"))
        ),
        actions = listOf(
            Action(ActionType.SYSTEM_BRIGHTNESS, config = mapOf("value" to "40")),
            Action(ActionType.SYSTEM_DND, config = mapOf("enabled" to "true")),
            Action(ActionType.SYSTEM_BLOCK_NOTIFICATION, config = mapOf("package" to "com.game", "enabled" to "true")),
            Action(ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS, config = mapOf("package" to "com.whatsapp"))
        ),
        createdAt = 1000L,
        updatedAt = 2000L
    )

    @Test
    fun domainToEntityAndBackRoundTrips() {
        val entity = automation.toEntity()
        assertEquals(automation.id, entity.id)
        assertEquals(automation.name, entity.name)
        assertEquals(automation.actions.size, 4)
        assertEquals(automation.triggers.size, 2)

        assertEquals(automation, entity.toDomain())
    }

    @Test
    fun roundTripPreservesJsonColumns() {
        val entity = automation.toEntity()
        val restored = entity.toDomain()
        assertEquals(automation.triggers, restored.triggers)
        assertEquals(automation.actions, restored.actions)
    }

    @Test
    fun emptyListsRoundTrip() {
        val plain = automation.copy(
            triggers = emptyList(),
            actions = emptyList()
        )
        assertEquals(plain, plain.toEntity().toDomain())
    }
}
