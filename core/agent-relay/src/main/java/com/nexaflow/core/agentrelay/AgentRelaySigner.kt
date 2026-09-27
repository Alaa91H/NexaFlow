package com.nexaflow.core.agentrelay

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 link-key signatures for relay frames.
 *
 * The link key is provisioned out-of-band (Settings -> AI & Agents -> Remote
 * Access) and stored in [AgentRelayLinkStore]; it never travels inside relay
 * frames and is never exposed to agents. Comparisons are constant-time.
 */
object AgentRelaySigner {

    fun newLinkKey(byteCount: Int = 32): ByteArray {
        require(byteCount >= 32) { "Relay link key is too short" }
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    fun encodeKey(key: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(key)

    fun decodeKey(encoded: String): ByteArray? = runCatching {
        Base64.getUrlDecoder().decode(encoded)
    }.getOrNull()?.takeIf { it.size >= 32 }

    fun sign(key: ByteArray, canonical: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(canonical))
    }

    fun verify(key: ByteArray, canonical: ByteArray, signature: String): Boolean {
        val presented = runCatching {
            Base64.getUrlDecoder().decode(signature)
        }.getOrNull() ?: return false
        val expected = runCatching {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            mac.doFinal(canonical)
        }.getOrNull() ?: return false
        if (presented.size != expected.size) return false
        return MessageDigest.isEqual(presented, expected)
    }
}
