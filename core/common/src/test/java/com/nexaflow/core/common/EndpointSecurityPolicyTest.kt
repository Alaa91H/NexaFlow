package com.nexaflow.core.common

import java.net.InetAddress
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointSecurityPolicyTest {
    @Test
    fun credentialsCannotUseCleartext() {
        assertTrue(runCatching {
            EndpointSecurityPolicy.validateBaseUri(
                raw = "http://127.0.0.1:11434/v1",
                allowHttp = true,
                local = true,
                hasCredential = true,
            )
        }.isFailure)
    }

    @Test
    fun localEndpointMustStayLocalAfterDnsResolution() {
        assertTrue(runCatching {
            EndpointSecurityPolicy.requireAddressScope(
                listOf(InetAddress.getByName("203.0.113.8")),
                local = true,
            )
        }.isFailure)
    }

    @Test
    fun remoteEndpointRejectsPrivateDnsAnswer() {
        assertTrue(runCatching {
            EndpointSecurityPolicy.requireAddressScope(
                listOf(InetAddress.getByName("10.0.0.8")),
                local = false,
            )
        }.isFailure)
    }

    @Test
    fun publicRemoteAddressIsAccepted() {
        EndpointSecurityPolicy.requireAddressScope(
            listOf(InetAddress.getByName("203.0.113.8")),
            local = false,
        )
    }
}
