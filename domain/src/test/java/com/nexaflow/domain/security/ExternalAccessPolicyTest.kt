package com.nexaflow.domain.security
import org.junit.Assert.*
import org.junit.Test
class ExternalAccessPolicyTest {
    @Test fun blankAndOversizedCapabilitiesFailClosed() {
        assertFalse(ExternalAccessPolicy.authorized(null, null))
        assertFalse(ExternalAccessPolicy.authorized("", ""))
        assertFalse(ExternalAccessPolicy.authorized("a".repeat(4096) + "x", "a".repeat(4096) + "y"))
        val token = ExternalAccessPolicy.newToken()
        assertEquals(43, token.length)
        assertTrue(ExternalAccessPolicy.authorized(token, token))
        assertFalse(ExternalAccessPolicy.authorized(token, ExternalAccessPolicy.newToken()))
    }
    @Test fun localPermissionDependsOnExplicitOptInAndSdk() {
        assertTrue(HttpAccessPolicy.runtimePermissions(mapOf("url" to "https://example.com"), 37).isEmpty())
        assertTrue(HttpAccessPolicy.runtimePermissions(mapOf("allowPrivateNetwork" to "true"), 36).isEmpty())
        assertEquals(listOf(HttpAccessPolicy.LOCAL_NETWORK_PERMISSION),
            HttpAccessPolicy.runtimePermissions(mapOf("allowPrivateNetwork" to "true"), 37))
    }
}
