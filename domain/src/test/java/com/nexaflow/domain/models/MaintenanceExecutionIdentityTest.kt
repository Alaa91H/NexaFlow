package com.nexaflow.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MaintenanceExecutionIdentityTest {

    private val zone = ZoneId.of("UTC")
    private val automation = Automation(
        id = "nightly",
        name = "Nightly maintenance",
        description = "",
        icon = "moon",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "maintenance",
        priority = 0,
        enabled = true,
        triggers = emptyList(),
        actions = emptyList(),
        createdAt = 0L,
        updatedAt = 0L,
        maintenanceProfile = MaintenanceProfile(
            kind = MaintenanceKind.NIGHT,
            window = MaintenanceWindow(startTime = "22:00", endTime = "05:00")
        )
    )

    @Test
    fun `overnight post-midnight time uses preceding window occurrence`() {
        val startNight = at(day = 2, hour = 23)
        val afterMidnight = at(day = 3, hour = 2)

        assertEquals(
            MaintenanceExecutionIdentity.occurrenceKey(automation, startNight),
            MaintenanceExecutionIdentity.occurrenceKey(automation, afterMidnight)
        )
    }

    @Test
    fun `separate maintenance dates have distinct occurrence keys`() {
        assertNotEquals(
            MaintenanceExecutionIdentity.occurrenceKey(automation, at(day = 2, hour = 23)),
            MaintenanceExecutionIdentity.occurrenceKey(automation, at(day = 3, hour = 23))
        )
    }

    private fun at(day: Int, hour: Int): ZonedDateTime =
        ZonedDateTime.of(2026, 8, day, hour, 0, 0, 0, zone)
}
