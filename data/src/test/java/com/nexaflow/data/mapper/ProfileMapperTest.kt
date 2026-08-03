package com.nexaflow.data.mapper

import com.nexaflow.domain.models.Profile
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileMapperTest {

    @Test
    fun domainToEntityAndBackRoundTrips() {
        val profile = Profile(
            id = "p1",
            name = "Work",
            description = "Focus setup",
            icon = "bolt",
            color = 4278216375L,
            active = true,
            automationIds = listOf("a1", "a2", "a3"),
            createdAt = 123456789L
        )

        val entity = profile.toEntity()
        assertEquals(profile.id, entity.id)
        assertEquals(profile.name, entity.name)
        assertEquals(profile.automationIds.size, 3)

        val restored = entity.toDomain()
        assertEquals(profile, restored)
    }

    @Test
    fun emptyAutomationIdsRoundTrips() {
        val profile = Profile(
            id = "p2",
            name = "Empty",
            description = "",
            icon = "home",
            color = 0L,
            active = false,
            automationIds = emptyList(),
            createdAt = 0L
        )

        assertEquals(profile, profile.toEntity().toDomain())
    }

    @Test
    fun malformedJsonFallsBackToEmptyList() {
        val entity = com.nexaflow.core.database.ProfileEntity(
            id = "p3",
            name = "Broken",
            description = "",
            icon = "home",
            color = 0L,
            active = false,
            automationIdsJson = "not-json",
            createdAt = 0L
        )

        assertEquals(emptyList<String>(), entity.toDomain().automationIds)
    }
}
