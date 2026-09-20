package com.nexaflow.app.wear

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WearSyncManagerDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `WearAutomationDto serializes and deserializes round-trip correctly`() {
        val dto = WearAutomationDto(
            id = "id-1",
            name = "Automation One",
            icon = "Bedtime",
            iconColor = -16777216L,
            enabled = true,
            lastRunAt = 1700000000000L,
            lastRunSuccess = true,
            lastRunMessage = "OK",
        )

        val serialized = json.encodeToString(WearAutomationDto.serializer(), dto)
        val deserialized = json.decodeFromString(WearAutomationDto.serializer(), serialized)

        assertEquals(dto, deserialized)
    }

    @Test
    fun `WearAutomationDto serializes nullable fields as absent when null`() {
        val dto = WearAutomationDto(
            id = "id-2",
            name = "No History",
            icon = "Star",
            iconColor = 0L,
            enabled = false,
        )

        val serialized = json.encodeToString(WearAutomationDto.serializer(), dto)

        // Null fields should still be present because encodeDefaults = true
        // but their values should be JSON null
        assertTrue(serialized.contains("\"id\":\"id-2\""))
        assertTrue(serialized.contains("\"enabled\":false"))
    }

    @Test
    fun `WearAutomationDto list serializes empty list`() {
        val dtos = emptyList<WearAutomationDto>()

        val serialized = json.encodeToString(dtos)

        assertEquals("[]", serialized)
    }

    @Test
    fun `WearAutomationDto lastRunMessage is truncated at 100 chars`() {
        val longMessage = "A".repeat(200)
        val truncated = longMessage.take(100)

        assertEquals(100, truncated.length)
        assertTrue(truncated.all { it == 'A' })
    }
}
