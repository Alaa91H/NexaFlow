package com.nexaflow.core.execution.handler

import org.junit.Assert.*
import org.junit.Test

class HttpRequestOptionsTest {
    @Test fun acceptsExplicitAuthAndContentType() {
        assertEquals(mapOf("Authorization" to "Bearer sample", "Content-Type" to "application/json"),
            HttpRequestOptions.headers("Authorization: Bearer sample\nContent-Type: application/json"))
    }

    @Test fun rejectsFramingDuplicatesAndInvalidBytes() {
        for (header in listOf("Host: attacker", "Content-Length: 1", "X: a\nx: b", "X: a\u0000b", "MissingColon", "X: " + "a".repeat(8192))) {
            assertTrue(header.take(30), runCatching { HttpRequestOptions.headers(header) }.isFailure)
        }
    }

    @Test fun originIncludesSchemeHostAndEffectivePort() {
        assertTrue(HttpRequestOptions.sameOrigin("https://EXAMPLE.com/a", "https://example.com:443/b"))
        assertFalse(HttpRequestOptions.sameOrigin("https://example.com", "https://other.example.com"))
        assertFalse(HttpRequestOptions.sameOrigin("https://example.com", "https://example.com:8443"))
    }
}
