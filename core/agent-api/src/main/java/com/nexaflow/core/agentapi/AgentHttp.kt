package com.nexaflow.core.agentapi

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

data class AgentHttpRequest(
    val method: String,
    val target: String,
    val headers: Map<String, String>,
    val body: ByteArray
) {
    fun header(name: String): String? = headers[name.lowercase(Locale.US)]
}

data class AgentHttpResponse(
    val status: Int,
    val body: ByteArray = ByteArray(0),
    val headers: Map<String, String> = emptyMap()
)

class AgentHttpProtocolException(
    val status: Int,
    val code: String,
    message: String
) : IllegalArgumentException(message)

/**
 * Small bounded HTTP/1.1 parser for the loopback API.
 *
 * Chunked transfer encoding is intentionally rejected. Agent SDKs know the
 * payload size and must send Content-Length, which keeps allocation and parser
 * state strictly bounded.
 */
object AgentHttpRequestParser {
    const val MAX_HEADER_BYTES = 32 * 1024
    const val MAX_BODY_BYTES = 256 * 1024
    const val MAX_HEADERS = 64
    const val MAX_TARGET_LENGTH = 4096

    fun read(input: InputStream): AgentHttpRequest {
        val headerBytes = readHeaderBlock(input)
        val headerText = headerBytes.toString(StandardCharsets.ISO_8859_1)
        val lines = headerText.split("\r\n")
        val requestLine = lines.firstOrNull()
            ?: throw protocol(400, "bad_request", "Missing request line")
        val parts = requestLine.split(' ')
        if (parts.size != 3 || parts[2] != "HTTP/1.1") {
            throw protocol(400, "bad_request", "Only HTTP/1.1 requests are accepted")
        }

        val method = parts[0].uppercase(Locale.US)
        if (method !in ALLOWED_METHODS) {
            throw protocol(405, "method_not_allowed", "HTTP method is not supported")
        }
        val target = parts[1]
        if (!target.startsWith("/") || target.length > MAX_TARGET_LENGTH) {
            throw protocol(414, "invalid_target", "Request target is invalid")
        }

        val headers = LinkedHashMap<String, String>()
        lines.drop(1).filter { it.isNotEmpty() }.forEach { line ->
            if (headers.size >= MAX_HEADERS) {
                throw protocol(431, "too_many_headers", "Too many request headers")
            }
            val separator = line.indexOf(':')
            if (separator <= 0) {
                throw protocol(400, "bad_header", "Malformed request header")
            }
            val name = line.substring(0, separator).trim().lowercase(Locale.US)
            val value = line.substring(separator + 1).trim()
            if (name.isEmpty() || name.length > 128 || value.length > 8192) {
                throw protocol(431, "header_too_large", "Request header is too large")
            }
            if (headers.put(name, value) != null) {
                throw protocol(400, "duplicate_header", "Duplicate request header")
            }
        }

        if (headers["transfer-encoding"] != null) {
            throw protocol(400, "unsupported_transfer_encoding", "Transfer-Encoding is not accepted")
        }

        val contentLength = headers["content-length"]?.let { raw ->
            raw.toIntOrNull()?.takeIf { it >= 0 }
                ?: throw protocol(400, "invalid_content_length", "Invalid Content-Length")
        } ?: 0
        if (contentLength > MAX_BODY_BYTES) {
            throw protocol(413, "payload_too_large", "Request payload is too large")
        }

        val body = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val read = input.read(body, offset, contentLength - offset)
            if (read < 0) {
                throw protocol(400, "truncated_body", "Request body ended before Content-Length")
            }
            offset += read
        }
        return AgentHttpRequest(method, target, headers, body)
    }

    private fun readHeaderBlock(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        var matched = 0
        val terminator = byteArrayOf(13, 10, 13, 10)
        while (true) {
            val value = input.read()
            if (value < 0) {
                throw protocol(400, "truncated_headers", "Request headers are incomplete")
            }
            output.write(value)
            if (output.size() > MAX_HEADER_BYTES) {
                throw protocol(431, "headers_too_large", "Request headers are too large")
            }
            matched = if (value.toByte() == terminator[matched]) matched + 1
            else if (value.toByte() == terminator[0]) 1 else 0
            if (matched == terminator.size) {
                val bytes = output.toByteArray()
                return bytes.copyOf(bytes.size - terminator.size)
            }
        }
    }

    private fun protocol(status: Int, code: String, message: String) =
        AgentHttpProtocolException(status, code, message)

    private val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE")
}
