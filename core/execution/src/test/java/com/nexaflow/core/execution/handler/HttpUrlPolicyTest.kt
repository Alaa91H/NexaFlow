package com.nexaflow.core.execution.handler

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Offline tests for the HTTP action's destination policy (SSRF gate) using
 * synthetic IP-literal hosts — no network access needed.
 */
@RunWith(JUnit4::class)
class HttpUrlPolicyTest {

    private fun resolveLiteral(host: String): Array<InetAddress> = arrayOf(InetAddress.getByName(host))

    @Test
    fun inspect_allowsPublicHttpAndHttps() {
        for (url in listOf("https://8.8.8.8/x", "http://93.184.216.34/api")) {
            val verdict = HttpUrlPolicy.inspect(url, allowPrivateNetwork = false, resolve = ::resolveLiteral)
            assertTrue(url, verdict is HttpUrlPolicy.Verdict.Allowed)
        }
    }

    @Test
    fun inspect_rejectsNonHttpSchemes() {
        for (url in listOf("ftp://8.8.8.8/x", "file:///etc/passwd", "gopher://8.8.8.8", "javascript://x")) {
            val verdict = HttpUrlPolicy.inspect(url, allowPrivateNetwork = false, resolve = ::resolveLiteral)
            assertTrue(url, verdict is HttpUrlPolicy.Verdict.Denied)
        }
    }

    @Test
    fun inspect_rejectsEmbeddedCredentials() {
        val verdict = HttpUrlPolicy.inspect(
            "https://user:pass@8.8.8.8/x", allowPrivateNetwork = false, resolve = ::resolveLiteral
        )
        assertTrue(verdict is HttpUrlPolicy.Verdict.Denied)
    }

    @Test
    fun inspect_blocksPrivateTargetsUnlessOptedIn() {
        val loopback = "http://127.0.0.1:8765/run"
        val lan = "http://192.168.1.10/admin"
        val linkLocal = "http://169.254.169.254/latest/meta-data"

        for (url in listOf(loopback, lan, linkLocal)) {
            val blocked = HttpUrlPolicy.inspect(url, allowPrivateNetwork = false, resolve = ::resolveLiteral)
            assertTrue(url, blocked is HttpUrlPolicy.Verdict.Denied)
            val allowed = HttpUrlPolicy.inspect(url, allowPrivateNetwork = true, resolve = ::resolveLiteral)
            assertTrue(url, allowed is HttpUrlPolicy.Verdict.Allowed)
        }
    }

    @Test
    fun inspect_rejectsMulticastAndZeroNetwork() {
        val multicast = HttpUrlPolicy.inspect("http://224.0.0.1/x", allowPrivateNetwork = true, resolve = ::resolveLiteral)
        assertTrue(multicast is HttpUrlPolicy.Verdict.Denied)
        val zero = HttpUrlPolicy.inspect("http://0.0.0.0/x", allowPrivateNetwork = true, resolve = ::resolveLiteral)
        assertTrue(zero is HttpUrlPolicy.Verdict.Denied)
    }

    @Test
    fun inspect_classifiesIpv6Locals() {
        val v6Loopback = HttpUrlPolicy.inspect("http://[::1]:8080/x", allowPrivateNetwork = true, resolve = ::resolveLiteral)
        assertTrue(v6Loopback is HttpUrlPolicy.Verdict.Allowed)
        val v6Ula = HttpUrlPolicy.inspect("http://[fd00::1]/x", allowPrivateNetwork = false, resolve = ::resolveLiteral)
        assertTrue(v6Ula is HttpUrlPolicy.Verdict.Denied)
    }

    @Test
    fun inspect_unresolvableHostPerMode() {
        val bestEffort = HttpUrlPolicy.inspect(
            "https://a.example/x", allowPrivateNetwork = false, allowUnresolvable = true,
            resolve = { error("no dns") }
        )
        assertTrue(bestEffort is HttpUrlPolicy.Verdict.Allowed)
        val strict = HttpUrlPolicy.inspect(
            "https://a.example/x", allowPrivateNetwork = false, allowUnresolvable = false,
            resolve = { error("no dns") }
        )
        assertTrue(strict is HttpUrlPolicy.Verdict.Denied)
    }

    @Test
    fun redirectHelpers_followChainAndDowngradeRules() {
        assertTrue(HttpUrlPolicy.isRedirect(301))
        assertTrue(HttpUrlPolicy.isRedirect(308))
        assertEquals(false, HttpUrlPolicy.isRedirect(200))

        assertEquals(
            "https://example.com/v2",
            HttpUrlPolicy.resolveRedirect("https://example.com/v1", "/v2")
        )
        assertEquals(
            "https://other.example/a",
            HttpUrlPolicy.resolveRedirect("https://example.com/v1", "https://other.example/a")
        )
        assertNull(HttpUrlPolicy.resolveRedirect("https://example.com/v1", "http://[bad"))

        // 303 always switches to GET; 301/302 only from POST.
        assertTrue(HttpUrlPolicy.downgradesToGet(303, "POST"))
        assertTrue(HttpUrlPolicy.downgradesToGet(301, "POST"))
        assertTrue(HttpUrlPolicy.downgradesToGet(302, "POST"))
        assertTrue(!HttpUrlPolicy.downgradesToGet(301, "GET"))
        assertTrue(!HttpUrlPolicy.downgradesToGet(307, "POST"))
        assertTrue(!HttpUrlPolicy.downgradesToGet(308, "PUT"))
    }
}
