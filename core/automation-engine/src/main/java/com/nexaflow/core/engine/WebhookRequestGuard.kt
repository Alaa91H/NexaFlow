package com.nexaflow.core.engine

import java.io.InputStream
import java.io.IOException
import java.net.URI
import java.net.URLDecoder

/** Limits are enforced before allocating a complete line or header block. */
object WebhookRequestGuard {
    const val MAX_LINE_BYTES = 8192
    const val MAX_HEADER_BYTES = 32768
    data class Request(val method: String, val path: String, val token: String?)

    fun readLine(input: InputStream, limit: Int = MAX_LINE_BYTES): String {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next < 0) throw IOException("Incomplete request")
            if (next == 13) {
                if (input.read() != 10) throw IOException("Expected CRLF")
                return bytes.toString("US-ASCII")
            }
            if (next == 10 || next > 126 || next < 32 && next != 9) throw IOException("Invalid HTTP byte")
            if (bytes.size() >= limit) throw OversizedRequest()
            bytes.write(next)
        }
    }

    class OversizedRequest : IOException("Request too large")

    fun readRequest(input: InputStream): Request {
        val parts = readLine(input).split(' ')
        require(parts.size == 3 && parts[2] in setOf("HTTP/1.0", "HTTP/1.1"))
        require(parts[0] in setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"))
        val uri = URI(parts[1])
        require(!uri.isAbsolute && uri.rawAuthority == null && uri.rawFragment == null)
        val path = canonicalPath(uri.rawPath)
        val queryTokens = uri.rawQuery.orEmpty().split('&').filter { it.substringBefore('=') == "token" }
        require(queryTokens.size <= 1)
        val queryToken = queryTokens.singleOrNull()?.substringAfter('=', "")?.let { URLDecoder.decode(it, "UTF-8") }
        var headerToken: String? = null
        var total = 0
        var count = 0
        while (true) {
            val line = readLine(input, minOf(MAX_LINE_BYTES, MAX_HEADER_BYTES - total))
            total += line.length + 2
            if (total > MAX_HEADER_BYTES || ++count > 100) throw OversizedRequest()
            if (line.isEmpty()) break
            require(':' in line && !line.startsWith(' ') && !line.startsWith('\t'))
            if (line.substringBefore(':').equals("X-NexaFlow-Token", true)) {
                require(headerToken == null)
                headerToken = line.substringAfter(':').trim()
            }
        }
        require(headerToken == null || queryToken == null || headerToken == queryToken)
        return Request(parts[0], path, headerToken ?: queryToken)
    }

    fun canonicalPath(raw: String): String {
        require(raw.startsWith('/') && !raw.startsWith("//") && raw.length <= MAX_LINE_BYTES)
        val decoded = URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8")
        require(decoded.none { it.code < 32 || it == '\\' || it == '%' || it == '?' || it == '#' })
        require(decoded.split('/').none { it == "." || it == ".." })
        return decoded
    }
}
