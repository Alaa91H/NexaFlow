package com.nexaflow.app.ai

import com.nexaflow.core.airuntime.AnthropicEndpointPolicy
import com.nexaflow.core.airuntime.AnthropicMessagesProviderConfig
import com.nexaflow.core.airuntime.AnthropicMessagesTransport
import com.nexaflow.core.airuntime.AnthropicMessagesTransportResponse
import com.nexaflow.core.airuntime.OpenAiCompatibleProvider
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

class AndroidAnthropicMessagesTransport : AnthropicMessagesTransport {
    override suspend fun postMessages(
        config: AnthropicMessagesProviderConfig,
        body: JsonObject,
        apiKey: String?
    ): AnthropicMessagesTransportResponse = withContext(Dispatchers.IO) {
        val endpoint = AnthropicEndpointPolicy.messagesUri(config, !apiKey.isNullOrBlank())
        execute(endpoint, "POST", apiKey, body.toString().toByteArray(Charsets.UTF_8))
    }

    override suspend fun getModels(
        config: AnthropicMessagesProviderConfig,
        apiKey: String?
    ): AnthropicMessagesTransportResponse = withContext(Dispatchers.IO) {
        val endpoint = AnthropicEndpointPolicy.modelsUri(config, !apiKey.isNullOrBlank())
        execute(endpoint, "GET", apiKey, null)
    }

    private fun execute(
        endpoint: java.net.URI,
        method: String,
        apiKey: String?,
        payload: ByteArray?
    ): AnthropicMessagesTransportResponse {
        require(!apiKey.isNullOrBlank()) { "API key is required" }
        require(payload == null || payload.size <= MAX_REQUEST_BYTES)
        val addresses = InetAddress.getAllByName(endpoint.host).toList()
        AnthropicEndpointPolicy.requireAddresses(addresses)

        val connection = endpoint.toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("x-api-key", apiKey)
            connection.setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
            }
            val status = connection.responseCode
            if (status in 300..399) {
                return AnthropicMessagesTransportResponse(status, "{\"error\":\"redirect_rejected\"}")
            }
            val stream = if (status in 200..299) connection.inputStream
                else connection.errorStream ?: connection.inputStream
            require(connection.contentLengthLong <= MAX_RESPONSE_BYTES) {
                "Anthropic response exceeds maximum size"
            }
            val bytes = stream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_RESPONSE_BYTES) {
                        "Anthropic response exceeds maximum size"
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return AnthropicMessagesTransportResponse(status, bytes.toString(Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }


    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 60_000
        const val MAX_REQUEST_BYTES = 1 * 1024 * 1024
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val BUFFER_SIZE = 8192
    }
}
