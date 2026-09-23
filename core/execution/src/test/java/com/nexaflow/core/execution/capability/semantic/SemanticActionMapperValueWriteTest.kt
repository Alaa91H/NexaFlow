package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SemanticActionMapperValueWriteTest {

    @Test
    fun brightnessDoesNotRequireEnabledParameter() {
        val request = SemanticActionMapper.requestFor(
            action = Action(
                ActionType.SYSTEM_BRIGHTNESS,
                mapOf("value" to "123", "configVersion" to "2")
            ),
            workflowId = "workflow",
            executionId = "run",
            allowPrivilegedStrategies = true
        )

        assertNotNull(request)
        assertEquals(SemanticOperationId.BRIGHTNESS_SET, request?.operation)
        assertEquals(mapOf("value" to "123"), request?.parameters)
    }

    @Test
    fun screenTimeoutDoesNotRequireEnabledParameter() {
        val request = SemanticActionMapper.requestFor(
            action = Action(
                ActionType.SYSTEM_SCREEN_TIMEOUT,
                mapOf("seconds" to "30", "configVersion" to "2")
            ),
            workflowId = "workflow",
            executionId = "run",
            allowPrivilegedStrategies = true
        )

        assertNotNull(request)
        assertEquals(SemanticOperationId.SCREEN_TIMEOUT_SET, request?.operation)
        assertEquals(mapOf("seconds" to "30"), request?.parameters)
    }

    @Test
    fun booleanToggleStillRequiresExplicitEnabledForModernConfig() {
        val request = SemanticActionMapper.requestFor(
            action = Action(
                ActionType.SYSTEM_BLUETOOTH,
                mapOf("configVersion" to "2")
            ),
            workflowId = "workflow",
            executionId = "run",
            allowPrivilegedStrategies = true
        )

        assertNull(request)
    }
}
