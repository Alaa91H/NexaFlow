package com.nexaflow.core.airuntime

import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OpenAiResponsesDiscoveryTest {

    private fun config() = OpenAiResponsesProviderConfig(
        enabled = true,
        baseUrl = "https://api.openai.com/v1",
        modelId = "gpt-5.1"
    )

    @Test
    fun missingModelIsClassifiedFromTheErrorBody() = runTest {
        val provider = OpenAiResponsesProvider(
            transport = object : OpenAiResponsesTransport {
                override suspend fun postResponses(
                    config: OpenAiResponsesProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ) = error("unused")

                override suspend fun getModels(
                    config: OpenAiResponsesProviderConfig,
                    apiKey: String
                ) = OpenAiResponsesTransportResponse(
                    404,
                    """{"error":{"message":"The model gpt-5.1 does not exist"}}"""
                )
            },
            apiKeyProvider = { "secret" }
        )
        provider.configure(config())

        val discovery = provider.listModels()

        assertFalse(discovery.success)
        assertEquals(404, discovery.httpStatus)
        assertEquals(AiConnectionFailure.MODEL_NOT_FOUND, discovery.failure)
    }

    @Test
    fun offlineDiscoveryReportsANetworkFailure() = runTest {
        val provider = OpenAiResponsesProvider(
            transport = object : OpenAiResponsesTransport {
                override suspend fun postResponses(
                    config: OpenAiResponsesProviderConfig,
                    body: JsonObject,
                    apiKey: String
                ) = error("unused")

                override suspend fun getModels(
                    config: OpenAiResponsesProviderConfig,
                    apiKey: String
                ): OpenAiResponsesTransportResponse = throw IOException("offline")
            },
            apiKeyProvider = { "secret" }
        )
        provider.configure(config())

        assertEquals(AiConnectionFailure.NETWORK, provider.listModels().failure)
    }
}
