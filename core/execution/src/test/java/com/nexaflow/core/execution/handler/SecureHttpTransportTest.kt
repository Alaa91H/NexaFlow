package com.nexaflow.core.execution.handler

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class SecureHttpTransportTest {
    @Test fun httpsUsesValidatedDnsExactlyOnceAndReturnsMetadata() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("service.example").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.enqueue(MockResponse().setBody("{\"ok\":true}").setHeader("Content-Type", "application/json"))
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            var lookups = 0
            val transport = SecureHttpTransport(true, {
                lookups++; listOf(InetAddress.getByName("127.0.0.1"))
            }, { OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager) })
            val url = "https://service.example:${server.port}/data"
            val result = transport.execute(url, "GET", "", 15000, emptyMap())
            assertEquals(200, result.code); assertEquals(1, lookups)
            assertEquals("application/json", result.contentType); assertEquals(url, result.finalUrl)
            assertFalse(result.truncated); assertEquals(11, result.bytesRead)
        }
    }
    @Test fun oversizedResponseFailsWithoutPublishingPartialJsonAndDowngradeIsBlocked() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.enqueue(MockResponse().setBody("x".repeat(SecureHttpTransport.MAX_RESPONSE_BYTES + 1)))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "HTTP://localhost:1/unsafe"))
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            val transport = SecureHttpTransport(true, { listOf(InetAddress.getByName("127.0.0.1")) },
                { OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager) })
            val first = transport.execute("https://localhost:${server.port}/", "GET", "", 15000, emptyMap())
            assertTrue(first.toString(), first.truncated); assertEquals("", first.snippet)
            val redirect = transport.execute("https://localhost:${server.port}/", "GET", "", 15000, emptyMap())
            assertEquals(-1, redirect.code); assertEquals(2, server.requestCount)
        }
    }
}
