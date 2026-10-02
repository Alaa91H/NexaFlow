package com.nexaflow.app.ai

import com.nexaflow.core.airuntime.OpenAiCompatibleProviderConfig
import com.nexaflow.core.airuntime.OpenAiEndpointPolicy
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonObject

internal object AndroidOpenAiStreamingTransport {

    fun stream(
        config: OpenAiCompatibleProviderConfig,
        body: JsonObject,
        apiKey: String?,
        gatewayHeaders: Map<String, String> = emptyMap()
    ): Flow<String> = flow {
        val payload = body.toString().toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_REQUEST_BYTES) {
            "Provider request exceeds maximum size"
        }

        val endpoint = OpenAiEndpointPolicy.chatCompletionsUri(
            config = config,
            hasApiKey = !apiKey.isNullOrBlank()
        )
        val addresses = InetAddress.getAllByName(endpoint.host).toList()
        OpenAiEndpointPolicy.requireAddresses(addresses, config.local)

        when (endpoint.scheme) {
            "https" -> streamHttps(endpoint, payload, apiKey, gatewayHeaders) { emit(it) }
            "http" -> {
                require(gatewayHeaders.isEmpty()) { "Gateway headers require HTTPS" }
                streamPrivateHttp(
                    endpoint = endpoint,
                    address = addresses.first(),
                    payload = payload
                ) { emit(it) }
            }
            else -> error("Unsupported provider URL scheme")
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun streamHttps(
        endpoint: URI,
        payload: ByteArray,
        apiKey: String?,
        gatewayHeaders: Map<String, String>,
        emitPayload: suspend (String) -> Unit
    ) {
        val connection = endpoint.toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Accept", "text/event-stream, application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept-Encoding", "identity")
            apiKey?.takeIf(String::isNotBlank)?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
            }
            AndroidAiGatewayHeaders.apply(connection, gatewayHeaders)
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }

            val status = connection.responseCode
            require(status !in 300..399) { "Provider redirect was rejected" }
            require(status in 200..299) { "Provider returned HTTP $status" }
            val contentLength = connection.contentLengthLong
            require(contentLength <= MAX_STREAM_BYTES || contentLength < 0) {
                "Provider response exceeds maximum size"
            }
            connection.inputStream.use { input ->
                emitPayloads(
                    input = BoundedInputStream(input, MAX_STREAM_BYTES),
                    contentType = connection.contentType,
                    emitPayload = emitPayload
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun streamPrivateHttp(
        endpoint: URI,
        address: InetAddress,
        payload: ByteArray,
        emitPayload: suspend (String) -> Unit
    ) {
        val port = endpoint.port.takeIf { it > 0 } ?: DEFAULT_HTTP_PORT
        val socket = Socket()
        try {
            socket.soTimeout = READ_TIMEOUT_MS
            socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
            val hostHeader = buildHostHeader(endpoint.host, port)
            val target = buildString {
                append(endpoint.rawPath.takeIf { it.isNotBlank() } ?: "/")
                endpoint.rawQuery?.let { append('?').append(it) }
            }
            val header = buildString {
                append("POST ").append(target).append(" HTTP/1.1\r\n")
                append("Host: ").append(hostHeader).append("\r\n")
                append("Accept: text/event-stream, application/json\r\n")
                append("Accept-Encoding: identity\r\n")
                append("Content-Type: application/json; charset=utf-8\r\n")
                append("Content-Length: ").append(payload.size).append("\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(Charsets.ISO_8859_1)

            socket.getOutputStream().apply {
                write(header)
                write(payload)
                flush()
            }

            val input = BufferedInputStream(socket.getInputStream())
            val statusLine = readAsciiLine(input, MAX_STATUS_LINE_LENGTH)
                ?: throw IllegalArgumentException("Provider returned an empty response")
            val status = statusLine.split(' ')
                .getOrNull(1)
                ?.toIntOrNull()
                ?: throw IllegalArgumentException("Provider returned an invalid HTTP status")
            val headers = readHeaders(input, statusLine.length)
            require(status !in 300..399) { "Provider redirect was rejected" }
            require(status in 200..299) { "Provider returned HTTP $status" }

            val contentLength = headers["content-length"]?.toLongOrNull()
            if (contentLength != null) {
                require(contentLength in 0..MAX_STREAM_BYTES.toLong()) {
                    "Provider response exceeds maximum size"
                }
            }

            val decoded = if (
                headers["transfer-encoding"]
                    ?.split(',')
                    ?.any { it.trim().equals("chunked", ignoreCase = true) } == true
            ) {
                ChunkedInputStream(input)
            } else {
                input
            }
            emitPayloads(
                input = BoundedInputStream(decoded, MAX_STREAM_BYTES),
                contentType = headers["content-type"],
                emitPayload = emitPayload
            )
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun readHeaders(
        input: InputStream,
        initialBytes: Int
    ): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        var headerBytes = initialBytes
        while (true) {
            val line = readAsciiLine(input, MAX_HEADER_LINE_LENGTH)
                ?: throw IllegalArgumentException("Provider response headers are incomplete")
            headerBytes += line.length
            require(headerBytes <= MAX_HEADER_BYTES) {
                "Provider response headers exceed maximum size"
            }
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            require(separator > 0) { "Provider returned an invalid HTTP header" }
            headers[line.substring(0, separator).trim().lowercase()] =
                line.substring(separator + 1).trim()
        }
        return headers
    }

    private suspend fun emitPayloads(
        input: InputStream,
        contentType: String?,
        emitPayload: suspend (String) -> Unit
    ) {
        if (contentType?.contains("application/json", ignoreCase = true) == true) {
            val body = readBounded(input, MAX_STREAM_BYTES).toString(Charsets.UTF_8)
            if (body.isNotBlank()) emitPayload(body)
            return
        }

        while (true) {
            val line = readUtf8Line(input, MAX_STREAM_LINE_BYTES) ?: break
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val payload = when {
                trimmed.startsWith("data:") ->
                    trimmed.removePrefix("data:").trimStart()
                trimmed.startsWith("{") -> trimmed
                else -> null
            } ?: continue
            if (payload == "[DONE]") return
            if (payload.isNotBlank()) emitPayload(payload)
        }
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            require(output.size() + read <= limit) {
                "Provider response exceeds maximum size"
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun readAsciiLine(input: InputStream, maxLength: Int): String? =
        readLine(input, maxLength, Charsets.ISO_8859_1)

    private fun readUtf8Line(input: InputStream, maxLength: Int): String? =
        readLine(input, maxLength, Charsets.UTF_8)

    private fun readLine(
        input: InputStream,
        maxLength: Int,
        charset: java.nio.charset.Charset
    ): String? {
        val output = ByteArrayOutputStream()
        var sawAny = false
        while (output.size() <= maxLength) {
            val value = input.read()
            if (value < 0) {
                return if (sawAny) output.toString(charset.name()) else null
            }
            sawAny = true
            if (value == '\n'.code) {
                val bytes = output.toByteArray()
                val length = if (bytes.lastOrNull() == '\r'.code.toByte()) {
                    bytes.size - 1
                } else {
                    bytes.size
                }
                return String(bytes, 0, length, charset)
            }
            output.write(value)
        }
        throw IllegalArgumentException("Provider response line exceeds maximum size")
    }

    private fun buildHostHeader(host: String, port: Int): String {
        val normalized = if (host.contains(':')) "[$host]" else host
        return if (port == DEFAULT_HTTP_PORT) normalized else "$normalized:$port"
    }

    private class BoundedInputStream(
        private val delegate: InputStream,
        private val limit: Int
    ) : InputStream() {
        private var count = 0

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) increment(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = delegate.read(buffer, offset, length)
            if (read > 0) increment(read)
            return read
        }

        private fun increment(amount: Int) {
            count += amount
            require(count <= limit) { "Provider response exceeds maximum size" }
        }
    }

    private class ChunkedInputStream(
        private val input: InputStream
    ) : InputStream() {
        private var remaining = 0L
        private var finished = false
        private var consumeTerminator = false

        override fun read(): Int {
            val single = ByteArray(1)
            return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (finished) return -1
            prepareChunk()
            if (finished) return -1

            val requested = minOf(length.toLong(), remaining).toInt()
            val read = input.read(buffer, offset, requested)
            if (read < 0) {
                throw IllegalArgumentException("Chunked response ended unexpectedly")
            }
            remaining -= read
            return read
        }

        private fun prepareChunk() {
            if (remaining > 0 || finished) return
            if (consumeTerminator) {
                require(readAsciiLine(input, 2) == "") {
                    "Invalid chunk terminator"
                }
                consumeTerminator = false
            }
            val sizeLine = readAsciiLine(input, MAX_CHUNK_LINE_LENGTH)
                ?: throw IllegalArgumentException("Chunked response ended unexpectedly")
            val size = sizeLine.substringBefore(';').trim().toLongOrNull(16)
                ?: throw IllegalArgumentException("Invalid chunk size")
            require(size in 0..MAX_STREAM_BYTES.toLong()) {
                "Provider response chunk exceeds maximum size"
            }
            if (size == 0L) {
                while (true) {
                    val trailer = readAsciiLine(input, MAX_HEADER_LINE_LENGTH)
                        ?: break
                    if (trailer.isEmpty()) break
                }
                finished = true
                return
            }
            remaining = size
            consumeTerminator = true
        }
    }

    private const val DEFAULT_HTTP_PORT = 80
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 45_000
    private const val MAX_REQUEST_BYTES = 1 * 1024 * 1024
    private const val MAX_STREAM_BYTES = 2 * 1024 * 1024
    private const val MAX_HEADER_BYTES = 64 * 1024
    private const val MAX_STATUS_LINE_LENGTH = 1024
    private const val MAX_HEADER_LINE_LENGTH = 8192
    private const val MAX_CHUNK_LINE_LENGTH = 128
    private const val MAX_STREAM_LINE_BYTES = 512 * 1024
    private const val BUFFER_SIZE = 8192
}
