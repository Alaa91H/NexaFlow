package com.nexaflow.core.airuntime

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiEndpointPolicyTest {

    @Test
    fun appendsChatCompletionsToVersionedBaseUrl() {
        val uri = OpenAiEndpointPolicy.chatCompletionsUri(
            OpenAiCompatibleProviderConfig(
                enabled = true,
                baseUrl = "http://192.168.1.20:11434/v1/",
                modelId = "qwen3",
                local = true
            ),
            hasApiKey = false
        )

        assertEquals(
            "http://192.168.1.20:11434/v1/chat/completions",
            uri.toString()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsApiKeyOverCleartextHttp() {
        OpenAiEndpointPolicy.chatCompletionsUri(
            OpenAiCompatibleProviderConfig(
                enabled = true,
                baseUrl = "http://127.0.0.1:11434/v1",
                modelId = "qwen3",
                local = true
            ),
            hasApiKey = true
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPublicCleartextProvider() {
        OpenAiEndpointPolicy.chatCompletionsUri(
            OpenAiCompatibleProviderConfig(
                enabled = true,
                baseUrl = "http://example.com/v1",
                modelId = "model",
                local = false
            ),
            hasApiKey = false
        )
    }

    @Test
    fun recognizesPrivateAndLoopbackAddresses() {
        assertTrue(
            OpenAiEndpointPolicy.isLocalAddress(
                InetAddress.getByName("127.0.0.1")
            )
        )
        assertTrue(
            OpenAiEndpointPolicy.isLocalAddress(
                InetAddress.getByName("192.168.50.4")
            )
        )
        assertTrue(
            OpenAiEndpointPolicy.isLocalAddress(
                InetAddress.getByName("10.20.30.40")
            )
        )
        assertTrue(
            OpenAiEndpointPolicy.isLocalAddress(
                InetAddress.getByName("fd12:3456::1")
            )
        )
        assertFalse(
            OpenAiEndpointPolicy.isLocalAddress(
                InetAddress.getByName("8.8.8.8")
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun localResolutionRejectsMixedPublicAddressSet() {
        OpenAiEndpointPolicy.requireLocalAddresses(
            listOf(
                InetAddress.getByName("192.168.1.5"),
                InetAddress.getByName("8.8.8.8")
            )
        )
    }
}
