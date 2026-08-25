package com.nexaflow.domain.risk

import com.nexaflow.domain.capability.CapabilityId
import org.junit.Assert.*
import org.junit.Test

class AiSecurityLayerTest {

    @Test
    fun `rejects forbidden capabilities`() {
        val policy = AiRoutinePolicy(forbiddenCapabilities = setOf(CapabilityId.PACKAGE_UNINSTALL))
        val securityLayer = AiSecurityLayer(policy)

        val requested = setOf(CapabilityId.PACKAGE_UNINSTALL, CapabilityId.DEVICE_STATE_READ)
        val result = securityLayer.validateAiCapabilities(requested)

        assertFalse(result.isSafe)
        assertTrue(result.violations.any { it.contains("forbidden") })
    }

    @Test
    fun `flags required human approvals`() {
        val policy = AiRoutinePolicy(
            forbiddenCapabilities = emptySet(),
            requireHumanApproval = setOf(CapabilityId.PACKAGE_FORCE_STOP)
        )
        val securityLayer = AiSecurityLayer(policy)

        val requested = setOf(CapabilityId.PACKAGE_FORCE_STOP)
        val result = securityLayer.validateAiCapabilities(requested)

        assertTrue(result.isSafe)
        assertTrue(result.requiresForcedHumanApproval)
        assertEquals(setOf(CapabilityId.PACKAGE_FORCE_STOP), result.approvalRequiredFor)
    }

    @Test
    fun `passes safe capabilities`() {
        val policy = AiRoutinePolicy()
        val securityLayer = AiSecurityLayer(policy)

        val requested = setOf(CapabilityId.DEVICE_STATE_READ, CapabilityId.PLUGIN_CONDITION_READ)
        val result = securityLayer.validateAiCapabilities(requested)

        assertTrue(result.isSafe)
        assertFalse(result.requiresForcedHumanApproval)
        assertTrue(result.violations.isEmpty())
    }
}
