package com.nexaflow.domain.catalog

import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerNodeSchemasTest {
    @Test
    fun `device event schema enumerates supported monitor events and keeps USB state contract`() {
        val deviceEvent = requireNotNull(TriggerNodeSchemas.schemaFor(TriggerType.DEVICE).field("event"))
        assertEquals(NodeConfigValueType.ENUM, deviceEvent.valueType)
        assertEquals("SCREEN_ON", deviceEvent.defaultValue)
        assertEquals(
            listOf(
                "SCREEN_ON",
                "SCREEN_OFF",
                "POWER_CONNECTED",
                "POWER_DISCONNECTED",
                "HEADSET_CONNECTED",
                "HEADSET_DISCONNECTED",
                "BLUETOOTH_CONNECTED",
                "BLUETOOTH_DISCONNECTED"
            ),
            deviceEvent.allowedValues
        )

        val usbState = requireNotNull(TriggerNodeSchemas.schemaFor(TriggerType.USB_CONNECTED).field("state"))
        assertEquals(NodeConfigValueType.ENUM, usbState.valueType)
        assertEquals(listOf("ON", "OFF"), usbState.allowedValues)
    }

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
