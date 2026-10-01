package com.nexaflow.app.ai

import com.nexaflow.core.airuntime.OpenAiCompatibleProviderConfig
import com.nexaflow.core.airuntime.OpenAiCompatibleTransport
import com.nexaflow.core.airuntime.OpenAiCompatibleTransportResponse
import com.nexaflow.core.airuntime.OpenAiEndpointPolicy
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

class AndroidOpenAiCompatibleTransport : OpenAiCompatibleTransport {

    override suspend fun getModels(
        config: OpenAiCompatibleProviderConfig,
        apiKey: String?
    ): OpenAiCompatibleTransportResponse = withContext(Dispatchers.IO) {
        val endpoint = OpenAiEndpointPolicy.modelsUri(
            config = config,
            hasApiKey = !apiKey.isNullOrBlank()
        )
        val addresses = InetAddress.getAllByName(endpoint.host).toList()
        OpenAiEndpointPolicy.requireAddresses(addresses, config.local)

        when (endpoint.scheme) {
            "https" -> getHttps(endpoint, apiKey)
            "http" -> getPrivateHttp(endpoint, addresses.first())
            else -> error("Unsupported provider URL scheme")
        }
    }

    override fun streamChatCompletions(
        config: OpenAiCompatibleProviderConfig,
        body: JsonObject,
        apiKey: String?
    ): Flow<String> = AndroidOpenAiStreamingTransport.stream(
        config = config,
        body = body,
        apiKey = apiKey
    )

    override suspend fun postChatCompletions(
        config: OpenAiCompatibleProviderConfig,
        body: JsonObject,
        apiKey: String?
    ): OpenAiCompatibleTransportResponse = withContext(Dispatchers.IO) {
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
            "https" -> postHttps(endpoint, payload, apiKey)
            "http" -> postPrivateHttp(endpoint, addresses.first(), payload)
            else -> error("Unsupported provider URL scheme")
        }
    }

    private fun getHttps(
        endpoint: URI,
        apiKey: String?
    ): OpenAiCompatibleTransportResponse {
        val connection = endpoint.toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Encoding", "identity")
            apiKey?.takeIf(String::isNotBlank)?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
            }

            val status = connection.responseCode
            if (status in 300..399) {
                return OpenAiCompatibleTransportResponse(
                    statusCode = status,
                    body = """{"error":"redirect_rejected"}"""
                )
            }
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_RESPONSE_BYTES) {
                throw IllegalArgumentException(
                    "Provider response exceeds maximum size"
                )
            }
            return OpenAiCompatibleTransportResponse(
                statusCode = status,
                body = stream.use {
                    readBounded(it, MAX_RESPONSE_BYTES)
                }.toString(Charsets.UTF_8)
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun getPrivateHttp(
        endpoint: URI,
        address: InetAddress
    ): OpenAiCompatibleTransportResponse {
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
                append("GET ").append(target).append(" HTTP/1.1\r\n")
                append("Host: ").append(hostHeader).append("\r\n")
                append("Accept: application/json\r\n")
                append("Accept-Encoding: identity\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(Charsets.ISO_8859_1)

            socket.getOutputStream().apply {
                write(header)
                flush()
            }
            return parseHttpResponse(
                BufferedInputStream(socket.getInputStream())
            )
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun postHttps(
        endpoint: URI,
        payload: ByteArray,
        apiKey: String?
    ): OpenAiCompatibleTransportResponse {
        val connection = endpoint.toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept-Encoding", "identity")
            apiKey?.takeIf(String::isNotBlank)?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
            }
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }

            val status = connection.responseCode
            if (status in 300..399) {
                return OpenAiCompatibleTransportResponse(
                    statusCode = status,
                    body = """{"error":"redirect_rejected"}"""
                )
            }
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_RESPONSE_BYTES) {
                throw IllegalArgumentException("Provider response exceeds maximum size")
            }
            val bytes = stream.use { readBounded(it, MAX_RESPONSE_BYTES) }
            return OpenAiCompatibleTransportResponse(
                statusCode = status,
                body = bytes.toString(Charsets.UTF_8)
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun postPrivateHttp(
        endpoint: URI,
        address: InetAddress,
        payload: ByteArray
    ): OpenAiCompatibleTransportResponse {
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
                append("Accept: application/json\r\n")
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

            return parseHttpResponse(BufferedInputStream(socket.getInputStream()))
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun parseHttpResponse(input: BufferedInputStream): OpenAiCompatibleTransportResponse {
        val statusLine = readAsciiLine(input, MAX_STATUS_LINE_LENGTH)
            ?: throw IllegalArgumentException("Provider returned an empty response")
        val status = statusLine.split(' ')
            .getOrNull(1)
            ?.toIntOrNull()
            ?: throw IllegalArgumentException("Provider returned an invalid HTTP status")

        val headers = linkedMapOf<String, String>()
        var headerBytes = statusLine.length
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
            val name = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            headers[name] = value
        }

        if (status in 300..399) {
            return OpenAiCompatibleTransportResponse(
                statusCode = status,
                body = """{"error":"redirect_rejected"}"""
            )
        }

        val bytes = when {
            headers["transfer-encoding"]
                ?.split(',')
                ?.any { it.trim().equals("chunked", ignoreCase = true) } == true ->
                readChunked(input)
            headers["content-length"] != null -> {
                val length = headers.getValue("content-length").toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid Content-Length")
                require(length in 0..MAX_RESPONSE_BYTES.toLong()) {
                    "Provider response exceeds maximum size"
                }
                readExactly(input, length.toInt())
            }
            else -> readBounded(input, MAX_RESPONSE_BYTES)
        }

        return OpenAiCompatibleTransportResponse(
            statusCode = status,
            body = bytes.toString(Charsets.UTF_8)
        )
    }

    private fun readChunked(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readAsciiLine(input, MAX_CHUNK_LINE_LENGTH)
                ?: throw IllegalArgumentException("Chunked response ended unexpectedly")
            val size = sizeLine.substringBefore(';').trim().toLongOrNull(16)
                ?: throw IllegalArgumentException("Invalid chunk size")
            require(size in 0..MAX_RESPONSE_BYTES.toLong()) {
                "Provider response chunk exceeds maximum size"
            }
            if (size == 0L) {
                while (true) {
                    val trailer = readAsciiLine(input, MAX_HEADER_LINE_LENGTH)
                        ?: break
                    if (trailer.isEmpty()) break
                }
                break
            }
            require(output.size().toLong() + size <= MAX_RESPONSE_BYTES) {
                "Provider response exceeds maximum size"
            }
            output.write(readExactly(input, size.toInt()))
            val terminator = readAsciiLine(input, 2)
            require(terminator == "") { "Invalid chunk terminator" }
        }
        return output.toByteArray()
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(result, offset, length - offset)
            if (read < 0) {
                throw IllegalArgumentException("Provider response ended unexpectedly")
            }
            offset += read
        }
        return result
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

    private fun readAsciiLine(input: InputStream, maxLength: Int): String? {
        val output = ByteArrayOutputStream()
        var sawAny = false
        while (output.size() <= maxLength) {
            val value = input.read()
            if (value < 0) {
                return if (sawAny) output.toString(Charsets.ISO_8859_1.name()) else null
            }
            sawAny = true
            if (value == '\n'.code) {
                val bytes = output.toByteArray()
                val length = if (bytes.lastOrNull() == '\r'.code.toByte()) {
                    bytes.size - 1
                } else {
                    bytes.size
                }
                return String(bytes, 0, length, Charsets.ISO_8859_1)
            }
            output.write(value)
        }
        throw IllegalArgumentException("Provider response line exceeds maximum size")
    }

    private fun buildHostHeader(host: String, port: Int): String {
        val normalized = if (host.contains(':')) "[$host]" else host
        return if (port == DEFAULT_HTTP_PORT) normalized else "$normalized:$port"
    }

    private companion object {
        const val DEFAULT_HTTP_PORT = 80
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 45_000
        const val MAX_REQUEST_BYTES = 1 * 1024 * 1024
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val MAX_HEADER_BYTES = 64 * 1024
        const val MAX_STATUS_LINE_LENGTH = 1024
        const val MAX_HEADER_LINE_LENGTH = 8192
        const val MAX_CHUNK_LINE_LENGTH = 128
        const val BUFFER_SIZE = 8192
    }
}
