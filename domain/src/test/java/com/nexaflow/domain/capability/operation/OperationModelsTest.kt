package com.nexaflow.domain.capability.operation

import com.nexaflow.domain.capability.CapabilityParameterSpec
import com.nexaflow.domain.capability.CapabilityParameterType
import com.nexaflow.domain.capability.CapabilityRiskLevel
import com.nexaflow.domain.capability.CapabilitySideEffectLevel
import com.nexaflow.domain.capability.VerificationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class OperationModelsTest {

    @Test
    fun `read and write classification covers every semantic operation`() {
        val readOnly = SemanticOperationId.entries.filter { it.isReadOnly }.toSet()
        val writes = SemanticOperationId.entries.filter { it.isWrite }.toSet()

        assertTrue(SemanticOperationId.WIFI_GET_STATE in readOnly)
        assertTrue(SemanticOperationId.BRIGHTNESS_GET in readOnly)
        assertTrue(SemanticOperationId.PACKAGE_GET_ENABLED_STATE in readOnly)
        assertTrue(SemanticOperationId.WIFI_SET_STATE in writes)
        assertTrue(SemanticOperationId.BRIGHTNESS_SET in writes)
        assertTrue(SemanticOperationId.PACKAGE_FORCE_STOP in writes)
        assertTrue(readOnly.intersect(writes).isEmpty())
        assertEquals(SemanticOperationId.entries.size, readOnly.size + writes.size)
    }

    @Test
    fun `counterpart lookup is symmetric for state and scalar operations`() {
        val pairs = listOf(
            SemanticOperationId.WIFI_GET_STATE to SemanticOperationId.WIFI_SET_STATE,
            SemanticOperationId.BLUETOOTH_GET_STATE to SemanticOperationId.BLUETOOTH_SET_STATE,
            SemanticOperationId.MOBILE_DATA_GET_STATE to SemanticOperationId.MOBILE_DATA_SET_STATE,
            SemanticOperationId.HOTSPOT_GET_STATE to SemanticOperationId.HOTSPOT_SET_STATE,
            SemanticOperationId.NFC_GET_STATE to SemanticOperationId.NFC_SET_STATE,
            SemanticOperationId.LOCATION_GET_STATE to SemanticOperationId.LOCATION_SET_STATE,
            SemanticOperationId.AIRPLANE_MODE_GET_STATE to SemanticOperationId.AIRPLANE_MODE_SET_STATE,
            SemanticOperationId.ROTATION_GET_STATE to SemanticOperationId.ROTATION_SET_STATE,
            SemanticOperationId.BRIGHTNESS_GET to SemanticOperationId.BRIGHTNESS_SET,
            SemanticOperationId.SCREEN_TIMEOUT_GET to SemanticOperationId.SCREEN_TIMEOUT_SET,
            SemanticOperationId.DND_GET_STATE to SemanticOperationId.DND_SET_STATE,
            SemanticOperationId.DATA_SAVER_GET_STATE to SemanticOperationId.DATA_SAVER_SET_STATE
        )

        pairs.forEach { (read, write) ->
            assertEquals(write, SemanticOperationId.counterpartOf(read))
            assertEquals(read, SemanticOperationId.counterpartOf(write))
        }
    }

    @Test
    fun `package operations use the explicit enabled-state counterpart`() {
        assertEquals(
            SemanticOperationId.PACKAGE_GET_ENABLED_STATE,
            SemanticOperationId.counterpartOf(SemanticOperationId.PACKAGE_FORCE_STOP)
        )
        assertEquals(
            SemanticOperationId.PACKAGE_GET_ENABLED_STATE,
            SemanticOperationId.counterpartOf(SemanticOperationId.PACKAGE_CLEAR_DATA)
        )
        assertEquals(
            SemanticOperationId.PACKAGE_GET_ENABLED_STATE,
            SemanticOperationId.counterpartOf(SemanticOperationId.PACKAGE_SET_ENABLED_STATE)
        )
        assertEquals(
            SemanticOperationId.PACKAGE_SET_ENABLED_STATE,
            SemanticOperationId.counterpartOf(SemanticOperationId.PACKAGE_GET_ENABLED_STATE)
        )
    }

    @Test
    fun `operation spec derives conservative defaults and resolves schemas`() {
        val enabled = CapabilityParameterSpec(
            name = "enabled",
            type = CapabilityParameterType.BOOLEAN,
            required = true
        )
        val write = OperationSpec(
            id = SemanticOperationId.WIFI_SET_STATE,
            displayName = "Set Wi-Fi",
            parameters = listOf(enabled),
            strategies = listOf(StrategyId.ANDROID_PUBLIC_API, StrategyId.SHIZUKU_USER_SERVICE)
        )
        assertEquals(enabled, write.parameterSchema("enabled"))
        assertNull(write.parameterSchema("missing"))
        assertEquals(CapabilitySideEffectLevel.REVERSIBLE, write.sideEffectLevel)
        assertEquals(VerificationMode.REQUIRED, write.verificationMode)
        assertEquals(CapabilityRiskLevel.LOW, write.risk)

        val read = OperationSpec(
            id = SemanticOperationId.WIFI_GET_STATE,
            displayName = "Read Wi-Fi"
        )
        assertEquals(CapabilitySideEffectLevel.NONE, read.sideEffectLevel)
        assertEquals(VerificationMode.NONE, read.verificationMode)
        assertFalse(read.id.isWrite)
    }

    @Test
    fun `operation spec rejects duplicate parameters and inverted api range`() {
        val parameter = CapabilityParameterSpec(
            name = "value",
            type = CapabilityParameterType.INTEGER
        )

        assertThrows(IllegalArgumentException::class.java) {
            OperationSpec(
                id = SemanticOperationId.BRIGHTNESS_SET,
                displayName = "Brightness",
                parameters = listOf(parameter, parameter)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationSpec(
                id = SemanticOperationId.BRIGHTNESS_GET,
                displayName = "Brightness",
                minAndroidApi = 35,
                maxAndroidApi = 34
            )
        }
    }
}
