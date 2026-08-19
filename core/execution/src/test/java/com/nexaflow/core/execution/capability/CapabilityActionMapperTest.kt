package com.nexaflow.core.execution.capability

import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.VerificationMode
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapabilityActionMapperTest {
    @Test
    fun mapsOpenUrlToPublicIntentCapability() {
        val request = CapabilityActionMapper.requestFor(
            action = Action(ActionType.SYSTEM_OPEN_URL, mapOf("url" to "https://example.com/path")),
            workflowId = "workflow-1",
            executionId = "run-1"
        )

        requireNotNull(request)
        assertEquals(CapabilityId.INTENT_LAUNCH, request.capability)
        assertEquals("https://example.com/path", request.parameters["url"])
        assertEquals(VerificationMode.NONE, request.verification)
        assertEquals("workflow-1", request.workflowId)
        assertEquals("run-1", request.executionId)
    }

    @Test
    fun doesNotMapPrivilegedLegacyActions() {
        val request = CapabilityActionMapper.requestFor(
            action = Action(ActionType.ADVANCED_ROOT, mapOf("command" to "id")),
            workflowId = "workflow-1",
            executionId = "run-1"
        )

        assertNull(request)
    }
}
