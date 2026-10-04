package com.nexaflow.core.agentapi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentApiHostPolicyTest {

    @Test
    fun loopbackHostsAreAlwaysAllowed() {
        val policy = AgentApiHostPolicy()

        assertTrue(policy.isAllowed("127.0.0.1:8766"))
        assertTrue(policy.isAllowed("localhost:8766"))
        assertTrue(policy.isAllowed("[::1]:8766"))
    }

    @Test
    fun nonLoopbackHostsStayBlockedUntilTlsIsAvailable() {
        val policy = AgentApiHostPolicy()

        assertFalse(policy.isAllowed("192.168.1.20:8766"))
        assertFalse(policy.isAllowed("10.0.0.5:8766"))

        policy.setLanAccessEnabled(true)

        assertFalse(policy.lanAccessEnabled)
        assertFalse(policy.isAllowed("192.168.1.20:8766"))
        assertFalse(policy.isAllowed("10.0.0.5:8766"))
        assertFalse(policy.isAllowed("172.16.0.5:8766"))
        assertFalse(policy.isAllowed("169.254.10.2:8766"))
        assertFalse(policy.isAllowed("[fd12:3456::1]:8766"))
        assertFalse(policy.isAllowed("[fe80::1]:8766"))
    }

    @Test
    fun publicOrDnsHostsRemainRejectedInLanMode() {
        val policy = AgentApiHostPolicy()
        policy.setLanAccessEnabled(true)

        assertFalse(policy.isAllowed("8.8.8.8:8766"))
        assertFalse(policy.isAllowed("example.com:8766"))
        assertFalse(policy.isAllowed("192.0.2.1:8766"))
        assertFalse(policy.isAllowed(null))
    }

    @Test
    fun lanCapabilityIsExplicitlyReportedUnavailableUntilTlsExists() {
        val policy = AgentApiHostPolicy()
        assertFalse(policy.supportsLanAccess())
        policy.setLanAccessEnabled(true)
        assertFalse(policy.lanAccessEnabled)
    }
}
