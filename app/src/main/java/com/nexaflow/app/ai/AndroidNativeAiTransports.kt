package com.nexaflow.app.ai

import com.nexaflow.core.airuntime.GeminiNativeEndpointPolicy
import com.nexaflow.core.airuntime.GeminiNativeProviderConfig
import com.nexaflow.core.airuntime.GeminiNativeTransport
import com.nexaflow.core.airuntime.GeminiNativeTransportResponse
import com.nexaflow.core.airuntime.OpenAiResponsesEndpointPolicy
import com.nexaflow.core.airuntime.OpenAiResponsesProviderConfig
import com.nexaflow.core.airuntime.OpenAiResponsesTransport
import com.nexaflow.core.airuntime.OpenAiResponsesTransportResponse
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

class AndroidOpenAiResponsesTransport : OpenAiResponsesTransport {
    override suspend fun postResponses(
        config: OpenAiResponsesProviderConfig,
        body: JsonObject,
        apiKey: String
    ): OpenAiResponsesTransportResponse =
        postResponsesWithHeaders(config, body, apiKey, emptyMap())

    override suspend fun postResponsesWithHeaders(
        config: OpenAiResponsesProviderConfig,
        body: JsonObject,
        apiKey: String,
        headers: Map<String, String>
    ): OpenAiResponsesTransportResponse = withContext(Dispatchers.IO) {
        val endpoint = OpenAiResponsesEndpointPolicy.responsesUri(config)
        OpenAiResponsesEndpointPolicy.requireAddresses(
            InetAddress.getAllByName(endpoint.host).toList()
        )
        NativeHttpsJsonClient.execute(
            endpoint = endpoint,
            method = "POST",
            headers = AndroidAiGatewayHeaders.normalized(headers) +
                mapOf("Authorization" to "Bearer $apiKey"),
            payload = body.toString().toByteArray(Charsets.UTF_8)
        ).let { OpenAiResponsesTransportResponse(it.statusCode, it.body) }
    }

    override suspend fun getModels(
        config: OpenAiResponsesProviderConfig,
        apiKey: String
    ): OpenAiResponsesTransportResponse = withContext(Dispatchers.IO) {
        val endpoint = OpenAiResponsesEndpointPolicy.modelsUri(config)
        OpenAiResponsesEndpointPolicy.requireAddresses(
            InetAddress.getAllByName(endpoint.host).toList()
        )
        NativeHttpsJsonClient.execute(
            endpoint = endpoint,
            method = "GET",
            headers = mapOf("Authorization" to "Bearer $apiKey")
        ).let { OpenAiResponsesTransportResponse(it.statusCode, it.body) }
    }
}

class AndroidGeminiNativeTransport : GeminiNativeTransport {
    override suspend fun generateContent(
        config: GeminiNativeProviderConfig,
        body: JsonObject,
        apiKey: String
    ): GeminiNativeTransportResponse =
        generateContentWithHeaders(config, body, apiKey, emptyMap())

    override suspend fun generateContentWithHeaders(
        config: GeminiNativeProviderConfig,
        body: JsonObject,
        apiKey: String,
        headers: Map<String, String>
    ): GeminiNativeTransportResponse = withContext(Dispatchers.IO) {
        val endpoint = GeminiNativeEndpointPolicy.generateContentUri(config)
        GeminiNativeEndpointPolicy.requireAddresses(
            InetAddress.getAllByName(endpoint.host).toList()
        )
        NativeHttpsJsonClient.execute(
            endpoint = endpoint,
            method = "POST",
            headers = AndroidAiGatewayHeaders.normalized(headers) +
                mapOf("x-goog-api-key" to apiKey),
            payload = body.toString().toByteArray(Charsets.UTF_8)
        ).let { GeminiNativeTransportResponse(it.statusCode, it.body) }
    }

    override suspend fun getModels(
        config: GeminiNativeProviderConfig,
        apiKey: String
    ): GeminiNativeTransportResponse = withContext(Dispatchers.IO) {
        val endpoint = GeminiNativeEndpointPolicy.modelsUri(config)
        GeminiNativeEndpointPolicy.requireAddresses(
            InetAddress.getAllByName(endpoint.host).toList()
        )
        NativeHttpsJsonClient.execute(
            endpoint = endpoint,
            method = "GET",
            headers = mapOf("x-goog-api-key" to apiKey)
        ).let { GeminiNativeTransportResponse(it.statusCode, it.body) }
    }
}

private data class NativeHttpResponse(
    val statusCode: Int,
    val body: String
)

private object NativeHttpsJsonClient {
    fun execute(
        endpoint: URI,
        method: String,
        headers: Map<String, String>,
        payload: ByteArray? = null
    ): NativeHttpResponse {
        require(endpoint.scheme == "https")
        require(payload == null || payload.size <= MAX_REQUEST_BYTES)

        val connection = endpoint.toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Encoding", "identity")
            headers.forEach { (name, value) ->
                require(name.lowercase() !in FORBIDDEN_HEADERS)
                connection.setRequestProperty(name, value)
            }
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
            }

            val status = connection.responseCode
            if (status in 300..399) {
                return NativeHttpResponse(status, """{"error":"redirect_rejected"}""")
            }

            val contentLength = connection.contentLengthLong
            require(contentLength < 0 || contentLength <= MAX_RESPONSE_BYTES) {
                "AI provider response exceeds maximum size"
            }

            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val bytes = stream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_RESPONSE_BYTES) {
                        "AI provider response exceeds maximum size"
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return NativeHttpResponse(status, bytes.toString(Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    private val FORBIDDEN_HEADERS = setOf(
        "host",
        "content-length",
        "connection",
        "transfer-encoding"
    )
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val MAX_REQUEST_BYTES = 1 * 1024 * 1024
    private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    private const val BUFFER_SIZE = 8192
}
