package com.nexaflow.data.mapper

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.MaintenanceKind
import com.nexaflow.domain.models.MaintenanceProfile
import com.nexaflow.domain.models.MaintenanceWindow
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            Trigger(TriggerType.BATTERY, config = mapOf("direction" to "ABOVE", "above" to "80", "chargerType" to "WIRELESS")),
            Trigger(TriggerType.NOTIFICATION, config = mapOf("packages" to "com.whatsapp", "contains" to "order", "event" to "POSTED")),
            Trigger(TriggerType.CALENDAR, config = mapOf("calendar" to "Personal", "contains" to "meeting", "event" to "EVENT_START", "beforeMinutes" to "15"))
        ),
        actions = listOf(
            Action(ActionType.SYSTEM_BRIGHTNESS, config = mapOf("value" to "40")),
            Action(ActionType.SYSTEM_DND, config = mapOf("enabled" to "true")),
            Action(ActionType.SYSTEM_BLOCK_NOTIFICATION, config = mapOf("package" to "com.game", "enabled" to "true")),
            Action(ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS, config = mapOf("package" to "com.whatsapp"))
        ),
        constraints = listOf(
            Constraint(ConstraintType.WIFI),
            Constraint(ConstraintType.BATTERY, mapOf("direction" to "ABOVE", "level" to "30"))
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
        assertEquals(automation.triggers.size, 4)

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
    fun constraintsRoundTrip() {
        val entity = automation.toEntity()
        val restored = entity.toDomain()
        assertEquals(automation.constraints, restored.constraints)
        assertTrue(
            "constraintsJson must be persisted",
            entity.constraintsJson.contains("WIFI") && entity.constraintsJson.contains("BATTERY")
        )
    }

    @Test
    fun maintenanceProfileRoundTripsWithAutomation() {
        val maintenance = automation.copy(
            maintenanceProfile = MaintenanceProfile(
                kind = MaintenanceKind.NIGHT,
                window = MaintenanceWindow(
                    startTime = "02:00",
                    endTime = "05:00",
                    allowedDays = setOf(1, 2, 3, 4, 5),
                    minimumBatteryPercent = 50,
                    chargingRequired = true,
                    unmeteredWifiRequired = true
                )
            )
        )

        val entity = maintenance.toEntity()
        assertTrue(entity.maintenanceJson.orEmpty().contains("NIGHT"))
        assertEquals(maintenance, entity.toDomain())
    }

    @Test
    fun emptyListsRoundTrip() {
        val plain = automation.copy(
            triggers = emptyList(),
            actions = emptyList(),
            constraints = emptyList()
        )
        assertEquals(plain, plain.toEntity().toDomain())
    }
}
