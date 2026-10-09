package com.nexaflow.domain.catalog

import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerNodeSchemasTest {
    @Test
    fun `wifi trigger schema declares optional network capability and access point filters`() {
        val schema = TriggerNodeSchemas.schemaFor(TriggerType.WIFI_CONNECTED)

        assertEquals(
            setOf("state", "validated", "captivePortal", "metered", "ssid", "bssid"),
            schema.knownKeys
        )
        listOf("validated", "captivePortal", "metered").forEach { key ->
            val field = requireNotNull(schema.field(key))
            assertEquals(NodeConfigValueType.ENUM, field.valueType)
            assertEquals("ANY", field.defaultValue)
            assertEquals(listOf("ANY", "YES", "NO"), field.allowedValues)
        }
        listOf("ssid", "bssid").forEach { key ->
            val field = requireNotNull(schema.field(key))
            assertEquals(NodeConfigValueType.STRING, field.valueType)
            assertTrue(!field.required)
        }
    }
}
