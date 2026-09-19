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

    @Test
    fun mapsForceStopAppToPrivilegedCapability() {
        val request = CapabilityActionMapper.requestFor(
            action = Action(
                ActionType.SYSTEM_FORCE_STOP_APP,
                mapOf("package" to "com.example.target", "backend" to "SHIZUKU")
            ),
            workflowId = "wf-force-stop",
            executionId = "run-fs"
        )

        requireNotNull(request)
        assertEquals(CapabilityId.PACKAGE_FORCE_STOP, request.capability)
        assertEquals(mapOf("packageName" to "com.example.target"), request.parameters)
        assertEquals(VerificationMode.BEST_EFFORT, request.verification)
        assertEquals(listOf(com.nexaflow.domain.capability.CapabilityBackendId.SHIZUKU), request.policy.allowedBackends)
        assertEquals(true, request.policy.allowPrivilegedBackends)
    }

    @Test
    fun mapsApplicationCloseAppToPrivilegedCapability() {
        val request = CapabilityActionMapper.requestFor(
            action = Action(
                ActionType.APPLICATION_CLOSE_APP,
                mapOf("packageName" to "com.example.close", "backend" to "ROOT")
            ),
            workflowId = "wf-close",
            executionId = "run-close"
        )

        requireNotNull(request)
        assertEquals(CapabilityId.PACKAGE_FORCE_STOP, request.capability)
        assertEquals(mapOf("packageName" to "com.example.close"), request.parameters)
        assertEquals(listOf(com.nexaflow.domain.capability.CapabilityBackendId.ROOT), request.policy.allowedBackends)
    }

    @Test
    fun mapsClearAppDataToPrivilegedCapability() {
        val request = CapabilityActionMapper.requestFor(
            action = Action(
                ActionType.SYSTEM_CLEAR_APP_DATA,
                mapOf("package" to "com.example.clear", "backend" to "SHIZUKU")
            ),
            workflowId = "wf-clear",
            executionId = "run-clear"
        )

        requireNotNull(request)
        assertEquals(CapabilityId.PACKAGE_CLEAR_DATA, request.capability)
        assertEquals(mapOf("packageName" to "com.example.clear"), request.parameters)
        assertEquals(VerificationMode.BEST_EFFORT, request.verification)
    }

    @Test
    fun mapsAllowlistedSettingWriteToPrivilegedCapability() {
        val request = CapabilityActionMapper.requestFor(
            action = Action(
                ActionType.SYSTEM_SET_SETTING,
                mapOf(
                    "namespace" to "GLOBAL",
                    "key" to "airplane_mode_on",
                    "value" to "1",
                    "backend" to "ROOT"
                )
            ),
            workflowId = "wf-setting",
            executionId = "run-setting"
        )

        requireNotNull(request)
        assertEquals(CapabilityId.SYSTEM_SETTING_WRITE, request.capability)
        assertEquals("GLOBAL", request.parameters["namespace"])
        assertEquals("airplane_mode_on", request.parameters["key"])
        assertEquals("1", request.parameters["value"])
        assertEquals(VerificationMode.REQUIRED, request.verification)
        assertEquals(listOf(com.nexaflow.domain.capability.CapabilityBackendId.ROOT), request.policy.allowedBackends)
    }

    @Test
    fun rejectsUnallowlistedSettingWrite() {
        val request = CapabilityActionMapper.requestFor(
            action = Action(
                ActionType.SYSTEM_SET_SETTING,
                mapOf(
                    "namespace" to "GLOBAL",
                    "key" to "unallowlisted_arbitrary_key",
                    "value" to "1"
                )
            ),
            workflowId = "wf-bad-setting",
            executionId = "run-bad"
        )

        assertNull(request)
    }

    @Test
    fun mapsSettingsShortcutsToPublicSettingsLaunch() {
        val wifiReq = CapabilityActionMapper.requestFor(
            action = Action(ActionType.SYSTEM_OPEN_WIFI_SETTINGS, emptyMap()),
            workflowId = "wf-wifi",
            executionId = "run-wifi"
        )
        requireNotNull(wifiReq)
        assertEquals(CapabilityId.SETTINGS_LAUNCH, wifiReq.capability)
        assertEquals("WIFI", wifiReq.parameters["page"])

        val btReq = CapabilityActionMapper.requestFor(
            action = Action(ActionType.SYSTEM_OPEN_BLUETOOTH_SETTINGS, emptyMap()),
            workflowId = "wf-bt",
            executionId = "run-bt"
        )
        requireNotNull(btReq)
        assertEquals("BLUETOOTH", btReq.parameters["page"])
    }

    @Test
    fun emptyPackageReturnsNull() {
        val request = CapabilityActionMapper.requestFor(
            action = Action(ActionType.SYSTEM_FORCE_STOP_APP, mapOf("package" to "")),
            workflowId = "wf-empty",
            executionId = "run-empty"
        )
        assertNull(request)
    }
}
