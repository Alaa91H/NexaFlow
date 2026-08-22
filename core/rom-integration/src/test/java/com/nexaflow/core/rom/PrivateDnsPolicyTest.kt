package com.nexaflow.core.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateDnsPolicyTest {

    @Test
    fun `hostname mode accepts a normal provider hostname`() {
        val request = PrivateDnsPolicy.request("HOSTNAME", "  dns.example.net ").getOrThrow()

        assertEquals(PrivateDnsMode.HOSTNAME, request.mode)
        assertEquals("dns.example.net", request.hostname)
    }

    @Test
    fun `hostname mode rejects an IP address and unsafe hostname`() {
        assertTrue(PrivateDnsPolicy.request("HOSTNAME", "1.1.1.1").isFailure)
        assertTrue(PrivateDnsPolicy.request("HOSTNAME", "dns provider.example").isFailure)
        assertTrue(PrivateDnsPolicy.request("HOSTNAME", "-dns.example.net").isFailure)
    }

    @Test
    fun `automatic and off clear stale provider hostname`() {
        val automatic = PrivateDnsPolicy.request("AUTOMATIC", "old.example.net").getOrThrow()
        val off = PrivateDnsPolicy.request("off", "old.example.net").getOrThrow()

        assertEquals(PrivateDnsMode.AUTOMATIC, automatic.mode)
        assertEquals("", automatic.hostname)
        assertEquals(PrivateDnsMode.OFF, off.mode)
        assertEquals("", off.hostname)
    }

    @Test
    fun `unknown mode is rejected`() {
        assertFalse(PrivateDnsPolicy.request("VPN", "dns.example.net").isSuccess)
    }
}
