package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the strict typed-parsing contract of the legacy action bridge: no
 * dangerous boolean defaults for explicit configurations, honest rejection of
 * unparseable values, and legacy-absence tolerance only where documented.
 */
class SemanticActionMapperTest {

    @Test
    fun routesWifiToggleToSemanticOperation() {
        val request = SemanticActionMapper.requestFor(
            action = Action(ActionType.SYSTEM_WIFI, mapOf("enabled" to "true")),
            workflowId = "wf",
            executionId = "run",
            allowPrivilegedStrategies = true
        )
        assertEquals(SemanticOperationId.WIFI_SET_STATE, request?.operation)
        assertEquals("true", request?.parameters?.get("enabled"))
    }

    @Test
    fun legacyAutomationWithoutExplicitValueKeepsHistoricalDefault() {
        // Automations persisted before the configVersion marker had no
        // "enabled" key; the historical behavior was ON.
        val request = SemanticActionMapper.requestFor(
            action = Action(ActionType.SYSTEM_WIFI, emptyMap()),
            workflowId = "wf",
            executionId = "run",
            allowPrivilegedStrategies = false
        )
        assertEquals("true", request?.parameters?.get("enabled"))
    }

    @Test
    fun unparseableBooleanIsRejectedNotSilentlyDefaulted() {
        val request = SemanticActionMapper.requestFor(
            action = Action(ActionType.SYSTEM_WIFI, mapOf("enabled" to "maybe")),
            workflowId = "wf",
            executionId = "run",
            allowPrivilegedStrategies = false
        )
        assertNull(request)
    }

    @Test
    fun missingBooleanForNewStyleAutomationIsRejected() {
        val request = SemanticActionMapper.requestFor(
            action = Action(ActionType.SYSTEM_WIFI, mapOf("configVersion" to "2")),
            workflowId = "wf",
            executionId = "run",
            allowPrivilegedStrategies = false
        )
        assertNull(request)
    }

    @Test
    fun unmigratedActionsStayOnLegacyHandlers() {
        val request = SemanticActionMapper.requestFor(
            action = Action(ActionType.SYSTEM_FLASHLIGHT, mapOf("enabled" to "true")),
            workflowId = "wf",
            executionId = "run",
            allowPrivilegedStrategies = false
        )
        assertNull(request)
    }

    @Test
    fun brightnessRequiresValue() {
        assertNull(
            SemanticActionMapper.requestFor(
                action = Action(ActionType.SYSTEM_BRIGHTNESS, emptyMap()),
                workflowId = "wf",
                executionId = "run",
                allowPrivilegedStrategies = false
            )
        )
        val request = SemanticActionMapper.requestFor(
            action = Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("value" to "128")),
            workflowId = "wf",
            executionId = "run",
            allowPrivilegedStrategies = false
        )
        assertEquals(SemanticOperationId.BRIGHTNESS_SET, request?.operation)
        assertEquals("128", request?.parameters?.get("value"))
    }

    @Test
    fun allMigratedTypesMap() {
        val migrated = listOf(
            ActionType.SYSTEM_WIFI to SemanticOperationId.WIFI_SET_STATE,
            ActionType.SYSTEM_BLUETOOTH to SemanticOperationId.BLUETOOTH_SET_STATE,
            ActionType.SYSTEM_LOCATION to SemanticOperationId.LOCATION_SET_STATE,
            ActionType.SYSTEM_AIRPLANE_MODE to SemanticOperationId.AIRPLANE_MODE_SET_STATE,
            ActionType.SYSTEM_SCREEN_ROTATION to SemanticOperationId.ROTATION_SET_STATE,
            ActionType.SYSTEM_DND to SemanticOperationId.DND_SET_STATE,
            ActionType.SYSTEM_NFC to SemanticOperationId.NFC_SET_STATE,
            ActionType.SYSTEM_HOTSPOT to SemanticOperationId.HOTSPOT_SET_STATE,
            ActionType.SYSTEM_MOBILE_DATA to SemanticOperationId.MOBILE_DATA_SET_STATE,
            ActionType.SYSTEM_DATA_SAVER to SemanticOperationId.DATA_SAVER_SET_STATE
        )
        migrated.forEach { (type, operation) ->
            val request = SemanticActionMapper.requestFor(
                action = Action(type, mapOf("enabled" to "false")),
                workflowId = "wf",
                executionId = "run",
                allowPrivilegedStrategies = false
            )
            assertEquals(operation, request?.operation)
            assertEquals("false", request?.parameters?.get("enabled"))
        }
    }
}
