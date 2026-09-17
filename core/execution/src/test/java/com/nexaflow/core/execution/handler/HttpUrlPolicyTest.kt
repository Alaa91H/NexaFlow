package com.nexaflow.core.execution.handler
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test
class HttpUrlPolicyTest {
    private fun ip(value: String) = InetAddress.getByName(value)
    @Test fun mixedAnswersAreRejectedInEitherOrder() {
        val public = ip("93.184.216.34")
        val private = ip("192.168.1.10")
        for (answers in listOf(listOf(public, private), listOf(private, public))) {
            assertTrue(runCatching { HttpUrlPolicy.inspect("https://example.com", false) { answers } }.isFailure)
        }
    }
    @Test fun unresolvedFailsClosed() {
        assertTrue(runCatching { HttpUrlPolicy.inspect("https://example.com", false) { emptyList() } }.isFailure)
        assertTrue(runCatching { HttpUrlPolicy.inspect("https://example.com", false) { throw java.net.UnknownHostException() } }.isFailure)
    }
    @Test fun localRangesAndOptIn() {
        for (value in listOf("100.64.0.1", "100.127.255.254", "127.0.0.1", "10.0.0.1", "169.254.169.254", "fd00::1", "::1")) {
            assertTrue(value, HttpUrlPolicy.isLocal(ip(value)))
            assertTrue(runCatching { HttpUrlPolicy.inspect("https://example.com", false) { listOf(ip(value)) } }.isFailure)
            assertEquals(listOf(ip(value)), HttpUrlPolicy.inspect("https://example.com", true) { listOf(ip(value)) }.addresses)
        }
        assertFalse(HttpUrlPolicy.isLocal(ip("100.128.0.1")))
    }
    @Test fun httpsOnlyRejectsDowngradesIndependentOfCase() {
        for (url in listOf("http://example.com", "HTTP://example.com", "file:///tmp/x", "https://a:b@example.com"))
            assertTrue(runCatching { HttpUrlPolicy.inspect(url, true) { listOf(ip("8.8.8.8")) } }.isFailure)
        assertEquals("HTTPS", HttpUrlPolicy.inspect("HTTPS://example.com", false) { listOf(ip("8.8.8.8")) }.uri.scheme)
    }
}
