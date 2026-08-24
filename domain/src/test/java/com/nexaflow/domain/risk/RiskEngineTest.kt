package com.nexaflow.domain.risk

import com.nexaflow.domain.risk.RiskLevel
import com.nexaflow.domain.risk.RiskAssessment
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.PrivilegeLevel
import org.junit.Assert.*
import org.junit.Test

class RiskEngineTest {

    private val engine = RiskEngine()

    @Test
    fun `LOW capability returns LOW assessment with auto-execute`() {
        val result = engine.assess(CapabilityId.DEVICE_STATE_READ)
        assertEquals(RiskLevel.LOW, result.level)
        assertTrue(result.canAutoExecute)
        assertFalse(result.requiresConfirmation)
        assertFalse(result.requiresSignedPolicy)
    }

    @Test
    fun `HIGH capability requires confirmation`() {
        val result = engine.assess(CapabilityId.PACKAGE_FORCE_STOP)
        assertEquals(RiskLevel.HIGH, result.level)
        assertTrue(result.requiresConfirmation)
        assertFalse(result.canAutoExecute)
    }

    @Test
    fun `ROOT privilege elevates to CRITICAL`() {
        val result = engine.assess(
            capability = CapabilityId.SYSTEM_SETTING_WRITE,
            privilege = PrivilegeLevel.ROOT
        )
        assertEquals(RiskLevel.CRITICAL, result.level)
        assertTrue(result.requiresSignedPolicy)
    }

    @Test
    fun `AI-generated LOW capability is escalated to MEDIUM`() {
        val result = engine.assess(
            capability = CapabilityId.DEVICE_STATE_READ,
            isAiGenerated = true
        )
        assertEquals(RiskLevel.MEDIUM, result.level)
        assertTrue(result.reasons.any { "AI" in it })
    }

    @Test
    fun `AI-generated HIGH capability is escalated to CRITICAL`() {
        val result = engine.assess(
            capability = CapabilityId.PACKAGE_FORCE_STOP,
            isAiGenerated = true
        )
        assertEquals(RiskLevel.CRITICAL, result.level)
        assertTrue(result.requiresSignedPolicy)
    }

    @Test
    fun `plugin request is at least MEDIUM`() {
        val result = engine.assess(
            capability = CapabilityId.DEVICE_STATE_READ,
            isFromPlugin = true
        )
        assertTrue(result.level.isAtLeast(RiskLevel.MEDIUM))
    }

    @Test
    fun `override replaces default risk`() {
        val customEngine = RiskEngine(
            overrides = mapOf(CapabilityId.NETWORK_HTTP_REQUEST to RiskLevel.LOW)
        )
        val result = customEngine.assess(CapabilityId.NETWORK_HTTP_REQUEST)
        assertEquals(RiskLevel.LOW, result.level)
    }

    @Test
    fun `CRITICAL is never escalated beyond CRITICAL`() {
        val result = engine.assess(
            capability = CapabilityId.SYSTEM_SETTING_WRITE,
            privilege = PrivilegeLevel.ROOT,
            isAiGenerated = true,
            isFromPlugin = true
        )
        assertEquals(RiskLevel.CRITICAL, result.level)
    }
}
