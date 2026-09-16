package com.nexaflow.core.execution.handler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Offline tests for the conditional `ACCESS_LOCAL_NETWORK` requirement
 * inference (P0.6). IP-literal hosts classify without DNS; host names and
 * variable-templated URLs must fall back to the conditional state.
 */
@RunWith(JUnit4::class)
class HttpLanAccessRequirementsTest {

    private fun requirement(url: String?) =
        HttpLanAccessRequirements.lanRequirementFor(url)

    @Test
    fun `public literal destination does not require the LAN permission`() {
        assertEquals(
            HttpLanAccessRequirements.Requirement.NotRequired,
            requirement("https://8.8.8.8/v1/api")
        )
    }

    @Test
    fun `private LAN literal destination requires the permission`() {
        assertEquals(
            HttpLanAccessRequirements.Requirement.Required,
            requirement("http://192.168.1.10:8080/status")
        )
    }

    @Test
    fun `loopback destination requires the permission`() {
        assertEquals(
            HttpLanAccessRequirements.Requirement.Required,
            requirement("http://127.0.0.1:9000/hook")
        )
    }

    @Test
    fun `link-local destination requires the permission`() {
        assertEquals(
            HttpLanAccessRequirements.Requirement.Required,
            requirement("http://169.254.10.20/x")
        )
    }

    @Test
    fun `variable-templated url stays conditional`() {
        assertEquals(
            HttpLanAccessRequirements.Requirement.Conditional,
            requirement("https://{server_ip}/status")
        )
    }

    @Test
    fun `host name without dns knowledge stays conditional`() {
        assertEquals(
            HttpLanAccessRequirements.Requirement.Conditional,
            requirement("https://router.local/status")
        )
    }

    @Test
    fun `empty config stays conditional`() {
        assertEquals(HttpLanAccessRequirements.Requirement.Conditional, requirement(null))
        assertEquals(HttpLanAccessRequirements.Requirement.Conditional, requirement(""))
        assertEquals(HttpLanAccessRequirements.Requirement.Conditional, requirement("   "))
    }

    @Test
    fun `malformed url stays conditional`() {
        assertEquals(
            HttpLanAccessRequirements.Requirement.Conditional,
            requirement("http://[bad-ipv6")
        )
    }

    @Test
    fun `runtime recheck flags private destination`() {
        assertTrue(HttpLanAccessRequirements.isLanDestination("http://10.0.0.5/x"))
        assertTrue(HttpLanAccessRequirements.isLanDestination("http://localhost/x"))
        assertTrue(HttpLanAccessRequirements.isLanDestination("https://192.168.0.2/y"))
    }

    @Test
    fun `runtime recheck clears public destination`() {
        assertFalse(HttpLanAccessRequirements.isLanDestination("https://8.8.8.8/v1"))
    }

    @Test
    fun `runtime recheck fails closed for unparseable url`() {
        assertTrue(HttpLanAccessRequirements.isLanDestination("not a url at all"))
    }
}
