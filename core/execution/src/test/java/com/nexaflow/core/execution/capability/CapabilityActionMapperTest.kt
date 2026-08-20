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
    fun mapsAllowlistedSettingsPageToPublicIntentCapability() {
        val request = CapabilityActionMapper.requestFor(
            action = Action(ActionType.SYSTEM_OPEN_SETTINGS, mapOf("page" to "WIFI")),
            workflowId = "workflow-1",
            executionId = "run-1"
        )

        requireNotNull(request)
        assertEquals(CapabilityId.SETTINGS_LAUNCH, request.capability)
        assertEquals("WIFI", request.parameters["page"])
        assertEquals(VerificationMode.NONE, request.verification)
    }

    @Test
    fun mapsApprovedPluginActionUsingOnlyOpaqueReference() {
        val request = CapabilityActionMapper.requestFor(
            action = Action(
                ActionType.PLUGIN_FIRE,
                mapOf(
                    "pluginInstance" to "plugin:instance-1",
                    "pluginApproval" to "approved",
                    "bundleJson" to "{\"secret_like_but_not_a_request_parameter\":true}"
                )
            ),
            workflowId = "workflow-1",
            executionId = "run-1"
        )

        requireNotNull(request)
        assertEquals(CapabilityId.PLUGIN_ACTION, request.capability)
        assertEquals(mapOf("pluginInstance" to "plugin:instance-1"), request.parameters)
        assertEquals(VerificationMode.BEST_EFFORT, request.verification)
    }

    @Test
    fun keepsLegacyPluginActionOnExistingHandlerPathUntilReconfigured() {
        val request = CapabilityActionMapper.requestFor(
            action = Action(ActionType.PLUGIN_FIRE, mapOf("package" to "com.example.plugin")),
            workflowId = "workflow-1",
            executionId = "run-1"
        )

        assertNull(request)
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
