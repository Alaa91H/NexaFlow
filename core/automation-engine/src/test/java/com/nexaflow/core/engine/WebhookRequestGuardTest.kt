package com.nexaflow.core.engine
import java.net.ServerSocket
import java.net.Socket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
class WebhookRequestGuardTest {
    @Test fun socketRejectsOversizedLineWithoutWaitingForNewline() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
                val result = executor.submit<Boolean> {
                    server.accept().use { socket ->
                        socket.soTimeout = 2000
                        try { WebhookRequestGuard.readRequest(socket.getInputStream()); false }
                        catch (_: WebhookRequestGuard.OversizedRequest) { true }
                    }
                }
                Socket(InetAddress.getLoopbackAddress(), server.localPort).use { client ->
                    client.getOutputStream().write(ByteArray(8193) { 65 })
                    client.getOutputStream().flush()
                    assertTrue(result.get(3, TimeUnit.SECONDS))
                }
            }
        } finally { executor.shutdownNow() }
    }
    @Test fun canonicalAuthenticatedRequestMatchesProductionMatcher() {
        val request = WebhookRequestGuard.readRequest("POST /%72un HTTP/1.1\r\nX-NexaFlow-Token: secret\r\n\r\n".byteInputStream())
        assertTrue(WebhookTriggerMatcher.matches(mapOf("path" to "/run", "method" to "POST", "token" to "secret"), request.method, request.path, request.token))
    }
    @Test fun ambiguousHeadersAndTraversalAreRejected() {
        for (request in listOf("GET /../run HTTP/1.1\r\n\r\n", "GET /run HTTP/1.1\r\nX-NexaFlow-Token: a\r\nX-NexaFlow-Token: b\r\n\r\n"))
            assertTrue(runCatching { WebhookRequestGuard.readRequest(request.byteInputStream()) }.isFailure)
    }
    @Test fun totalHeadersBounded() {
        val request = "POST /run HTTP/1.1\r\n" + ("X: " + "a".repeat(8000) + "\r\n").repeat(5) + "\r\n"
        assertTrue(runCatching { WebhookRequestGuard.readRequest(request.byteInputStream()) }.exceptionOrNull() is WebhookRequestGuard.OversizedRequest)
    }
}
