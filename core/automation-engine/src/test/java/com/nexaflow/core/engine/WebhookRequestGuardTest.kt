package com.nexaflow.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Hardening rules for the loopback webhook request pipeline. */
@RunWith(JUnit4::class)
class WebhookRequestGuardTest {

    private fun headers(vararg lines: String) = listOf(*lines)

    @Test
    fun parse_acceptsWellFormedRequest() {
        val parsed = WebhookRequestGuard.parse("POST /run?ignored=1 HTTP/1.1", headers("Host: 127.0.0.1:8765"))
        val request = parsed as WebhookRequestGuard.Parsed.Request
        assertEquals("POST", request.method)
        assertEquals("/run", request.path)
        assertNull(request.headerToken)
    }

    @Test
    fun parse_readsSecretHeaderOnly() {
        val parsed = WebhookRequestGuard.parse(
            "POST /run HTTP/1.1",
            headers("Host: x", "x-nexaflow-token: s3cret", "Accept: */*")
        )
        assertEquals("s3cret", (parsed as WebhookRequestGuard.Parsed.Request).headerToken)
    }

    @Test
    fun parse_rejectsDisallowedMethod() {
        val parsed = WebhookRequestGuard.parse("TRACE /run HTTP/1.1", emptyList())
        val rejected = parsed as WebhookRequestGuard.Parsed.Rejected
        assertEquals(405, rejected.code)
    }

    @Test
    fun parse_rejectsMalformedRequestLines() {
        listOf(
            WebhookRequestGuard.parse(null, emptyList()),
            WebhookRequestGuard.parse("", emptyList()),
            WebhookRequestGuard.parse("GET", emptyList()),
            WebhookRequestGuard.parse("GET ", emptyList()),
            WebhookRequestGuard.parse("GET /a b HTTP/1.1", emptyList())
        ).forEach {
            assertTrue(it is WebhookRequestGuard.Parsed.Rejected)
        }
    }

    @Test
    fun parse_rejectsOversizedRequestLineAndHeaders() {
        val bigLine = "GET /" + "a".repeat(WebhookRequestGuard.MAX_REQUEST_LINE_CHARS + 1) + " HTTP/1.1"
        assertTrue(WebhookRequestGuard.parse(bigLine, emptyList()) is WebhookRequestGuard.Parsed.Rejected)

        val bigHeader = "X-Big: " + "b".repeat(WebhookRequestGuard.MAX_HEADER_LINE_CHARS + 1)
        assertTrue(WebhookRequestGuard.parse("GET / HTTP/1.1", headers(bigHeader)) is WebhookRequestGuard.Parsed.Rejected)

        val manyHeaders = (1..WebhookRequestGuard.MAX_HEADER_LINES + 1).map { "X-H$it: v" }
        assertTrue(WebhookRequestGuard.parse("GET / HTTP/1.1", manyHeaders) is WebhookRequestGuard.Parsed.Rejected)

        val heavyTotal = (1..10).map { "X-Pad$it: " + "p".repeat(4 * 1024) }
        assertTrue(WebhookRequestGuard.parse("GET / HTTP/1.1", heavyTotal) is WebhookRequestGuard.Parsed.Rejected)
    }

    @Test
    fun constantTimeEquals_matchesOnlyExactSecrets() {
        assertTrue(WebhookRequestGuard.constantTimeEquals("s3cret", "s3cret"))
        assertFalse(WebhookRequestGuard.constantTimeEquals("s3cret", "s3creT"))
        assertFalse(WebhookRequestGuard.constantTimeEquals("s3cret", null))
        assertFalse(WebhookRequestGuard.constantTimeEquals(null, "s3cret"))
        assertFalse(WebhookRequestGuard.constantTimeEquals("", "s3cret"))
        // Oversized secrets are clamped, never crash, and still compare.
        val big = "x".repeat(WebhookRequestGuard.MAX_TOKEN_BYTES + 500)
        assertFalse(WebhookRequestGuard.constantTimeEquals(big, "y".repeat(10)))
        assertTrue(WebhookRequestGuard.constantTimeEquals(big, big))
    }

    @Test
    fun matcher_requiresHeaderTokenWhenConfigured() {
        val config = mapOf("path" to "/run", "token" to "s3cret")
        // Header token matches.
        assertTrue(WebhookTriggerMatcher.matches(config, "POST", "/run", "s3cret"))
        // No token presented → deny.
        assertFalse(WebhookTriggerMatcher.matches(config, "POST", "/run", null))
        // Wrong value → deny.
        assertFalse(WebhookTriggerMatcher.matches(config, "POST", "/run", "wrong"))
        // Blank configured token → open (documented legacy behavior).
        assertTrue(WebhookTriggerMatcher.matches(mapOf("path" to "/run"), "POST", "/run", null))
    }
}
