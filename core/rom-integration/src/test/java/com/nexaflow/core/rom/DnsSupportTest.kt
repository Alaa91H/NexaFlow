package com.nexaflow.core.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsSupportTest {
    @Test
    fun privateDnsPolicyAcceptsProviderHostnamesAndRejectsAddresses() {
        assertTrue(PrivateDnsPolicy.isValidHostname("dns.google"))
        assertTrue(PrivateDnsPolicy.isValidHostname("one.one.one.one"))
        assertFalse(PrivateDnsPolicy.isValidHostname("1.1.1.1"))
        assertFalse(PrivateDnsPolicy.isValidHostname("https://dns.google"))
        assertFalse(PrivateDnsPolicy.isValidHostname("dns google"))
    }

    @Test
    fun providerCatalogContainsValidatedBuiltInProfiles() {
        val providers = DnsProviderCatalog.all()
        assertTrue(providers.size >= 5)
        assertEquals(providers.size, providers.map { it.hostname }.distinct().size)
        assertTrue(providers.all { PrivateDnsPolicy.isValidHostname(it.hostname) })
    }

    @Test
    fun currentProviderIsIncludedOnlyOnce() {
        val providers = DnsProviderCatalog.all(currentHostname = "dns.google")
        assertEquals(1, providers.count { it.hostname == "dns.google" })
    }
}
