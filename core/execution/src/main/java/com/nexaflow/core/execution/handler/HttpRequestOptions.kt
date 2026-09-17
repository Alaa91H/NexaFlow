package com.nexaflow.core.execution.handler

import java.net.URI

/** User headers cannot override HTTP framing or transport-owned identity. */
internal object HttpRequestOptions {
    private val reserved = setOf("host", "connection", "content-length", "transfer-encoding", "upgrade", "proxy-authorization", "proxy-connection", "idempotency-key", "user-agent", "te", "trailer")
    fun headers(text: String): Map<String, String> {
        require(text.toByteArray(Charsets.UTF_8).size <= 8192)
        val lines = text.lines().filter { it.isNotBlank() }
        require(lines.size <= 32)
        val result = linkedMapOf<String, String>()
        for (line in lines) {
            val separator = line.indexOf(':')
            require(separator > 0)
            val name = line.take(separator).trim()
            val value = line.drop(separator + 1).trim()
            require(name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")))
            require(name.lowercase(java.util.Locale.ROOT) !in reserved)
            require(result.keys.none { it.equals(name, true) })
            require(value.all { it.code in 32..126 || it == '\t' })
            result[name] = value
        }
        return result
    }

    fun sameOrigin(first: String, second: String): Boolean {
        val a = URI(first)
        val b = URI(second)
        return a.scheme.equals(b.scheme, true) && a.host.equals(b.host, true) &&
            (if (a.port == -1) 443 else a.port) == (if (b.port == -1) 443 else b.port)
    }
}
