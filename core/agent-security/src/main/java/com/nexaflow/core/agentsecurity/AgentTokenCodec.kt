package com.nexaflow.core.agentsecurity

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal data class ParsedAgentToken(
    val id: String,
    val secret: String
)

internal object AgentTokenCodec {

    private val secureRandom = SecureRandom()

    fun newSecret(byteCount: Int = DEFAULT_SECRET_BYTES): String {
        require(byteCount >= MIN_SECRET_BYTES) { "Agent secret is too short" }
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun compose(id: String, secret: String): String {
        require(id.isNotBlank()) { "Token id must not be blank" }
        require(secret.isNotBlank()) { "Token secret must not be blank" }
        return "$id.$secret"
    }

    fun parse(token: String): ParsedAgentToken? {
        val separator = token.indexOf('.')
        if (separator <= 0 || separator == token.lastIndex) return null
        if (token.indexOf('.', separator + 1) >= 0) return null
        return ParsedAgentToken(
            id = token.substring(0, separator),
            secret = token.substring(separator + 1)
        )
    }

    fun hash(secret: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(secret.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun matches(expectedHash: String, presentedSecret: String): Boolean {
        val actualHash = hash(presentedSecret)
        return MessageDigest.isEqual(
            expectedHash.toByteArray(Charsets.US_ASCII),
            actualHash.toByteArray(Charsets.US_ASCII)
        )
    }

    private const val MIN_SECRET_BYTES = 32
    private const val DEFAULT_SECRET_BYTES = 32
}
