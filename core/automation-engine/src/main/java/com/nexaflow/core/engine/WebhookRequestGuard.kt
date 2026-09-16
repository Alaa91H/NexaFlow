package com.nexaflow.core.engine

import java.security.MessageDigest

/**
 * Pure request-parsing and bounds enforcement for the loopback webhook
 * server, kept separate from [WebhookServer] so every hardening rule is
 * unit-testable without a socket.
 *
 * The server is deliberately loopback-only, but any co-resident app can open
 * a localhost connection — so the request shape is bounded before any
 * matching runs: request line and header sizes are capped, the method set is
 * allow-listed, and the secret is only ever compared in constant time.
 */
object WebhookRequestGuard {

    /** Maximum characters accepted for the HTTP request line. */
    const val MAX_REQUEST_LINE_CHARS: Int = 8 * 1024

    /** Maximum number of header lines accepted. */
    const val MAX_HEADER_LINES: Int = 64

    /** Maximum characters per header line. */
    const val MAX_HEADER_LINE_CHARS: Int = 8 * 1024

    /** Maximum total characters across the whole header block. */
    const val MAX_TOTAL_HEADER_CHARS: Int = 32 * 1024

    /** Maximum body bytes drained after the header block. */
    const val MAX_BODY_DRAIN_BYTES: Int = 64 * 1024

    /** Methods the webhook accepts; anything else is a 405. */
    val ALLOWED_METHODS: Set<String> = setOf("GET", "POST", "PUT", "HEAD", "OPTIONS", "DELETE")

    /** Header carrying the shared secret (the only accepted channel). */
    const val TOKEN_HEADER: String = "X-NexaFlow-Token"

    /** Bytes compared per token — anything longer is clamped before hashing. */
    const val MAX_TOKEN_BYTES: Int = 4096

    sealed interface Parsed {
        /** A well-formed, allow-listed request ready for trigger matching. */
        data class Request(
            val method: String,
            val path: String,
            /** Token from the secret header only — query tokens are never accepted. */
            val headerToken: String?
        ) : Parsed

        /** Malformed/oversized/not-allowed request; respond with [code] and drop. */
        data class Rejected(val code: Int, val reason: String) : Parsed
    }

    /**
     * Parses the request line plus the header block. Returns [Parsed.Rejected]
     * for anything that violates the bounds — the caller must not dispatch it.
     */
    fun parse(requestLine: String?, headerLines: List<String>): Parsed {
        if (requestLine == null) return Parsed.Rejected(400, "Empty request")
        if (requestLine.length > MAX_REQUEST_LINE_CHARS) return Parsed.Rejected(431, "Request line too large")

        // Request line: "METHOD SP request-target SP HTTP/x.y". Only the
        // first two tokens are meaningful; the target itself may not contain
        // whitespace (a second target token means a malformed line).
        val firstSpace = requestLine.indexOf(' ')
        if (firstSpace <= 0) return Parsed.Rejected(400, "Malformed request line")
        val secondSpace = requestLine.indexOf(' ', firstSpace + 1)
        if (secondSpace < 0) return Parsed.Rejected(400, "Missing protocol token")
        val rawMethod = requestLine.substring(0, firstSpace)
        val target = requestLine.substring(firstSpace + 1, secondSpace)
        if (target.isEmpty() || target.contains(' ')) return Parsed.Rejected(400, "Malformed target")
        // Exactly three tokens: the remainder must be the protocol token.
        val protocol = requestLine.substring(secondSpace + 1).trim()
        if (!protocol.startsWith("HTTP/", ignoreCase = true)) return Parsed.Rejected(400, "Malformed protocol token")

        val method = rawMethod.uppercase()
        if (method !in ALLOWED_METHODS) return Parsed.Rejected(405, "Method not allowed")

        // Strip the query string — it never carries the token and never
        // participates in matching beyond the path.
        val path = target.substringBefore('?')

        var totalHeaderChars = 0
        var headerToken: String? = null
        for ((index, line) in headerLines.withIndex()) {
            if (index >= MAX_HEADER_LINES) return Parsed.Rejected(431, "Too many headers")
            if (line.length > MAX_HEADER_LINE_CHARS) return Parsed.Rejected(431, "Header too large")
            totalHeaderChars += line.length
            if (totalHeaderChars > MAX_TOTAL_HEADER_CHARS) return Parsed.Rejected(431, "Headers too large")
            if (line.startsWith("$TOKEN_HEADER:", ignoreCase = true)) {
                headerToken = line.substringAfter(':').trim()
            }
        }

        return Parsed.Request(method = method, path = path, headerToken = headerToken)
    }

    /**
     * Constant-time equality for shared secrets. Both sides are UTF-8
     * encoded and clamped to [maxBytes] before [MessageDigest.isEqual] so
     * oversized inputs cannot change the comparison cost shape.
     */
    fun constantTimeEquals(left: String?, right: String?, maxBytes: Int = MAX_TOKEN_BYTES): Boolean {
        if (left == null || right == null) return false
        val l = left.toByteArray(Charsets.UTF_8).let { if (it.size > maxBytes) it.copyOf(maxBytes) else it }
        val r = right.toByteArray(Charsets.UTF_8).let { if (it.size > maxBytes) it.copyOf(maxBytes) else it }
        return MessageDigest.isEqual(l, r)
    }
}
