package com.nexaflow.app.ai

import com.nexaflow.core.airuntime.AiGatewaySessionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAiGatewayHeadersTest {

    @Test
    fun normalizesOnlySupportedGatewayHeaders() {
        val normalized = AndroidAiGatewayHeaders.normalized(
            mapOf(
                AiGatewaySessionPolicy.HEADER_NAME to " session / one ",
                AiGatewaySessionPolicy.USER_AGENT_HEADER to "untrusted"
            )
        )

        assertEquals(
            "session---one",
            normalized[AiGatewaySessionPolicy.HEADER_NAME]
        )
        assertEquals(
            AiGatewaySessionPolicy.CLIENT_USER_AGENT,
            normalized[AiGatewaySessionPolicy.USER_AGENT_HEADER]
        )
    }

    @Test
    fun rejectsCredentialAndTransportHeaders() {
        for (name in listOf("Authorization", "Host", "Content-Length", "X-Custom")) {
            assertTrue(
                runCatching {
                    AndroidAiGatewayHeaders.normalized(mapOf(name to "value"))
                }.isFailure
            )
        }
    }

    @Test
    fun rejectsHeaderInjection() {
        assertTrue(
            runCatching {
                AndroidAiGatewayHeaders.normalized(
                    mapOf(AiGatewaySessionPolicy.HEADER_NAME to "ok\r\nX-Evil: value")
                )
            }.isFailure
        )
    }
}
