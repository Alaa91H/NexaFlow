package com.nexaflow.domain.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Shared capability rules for external ingress. No truncation or blank-token mode. */
object ExternalAccessPolicy {
    const val MAX_TOKEN_BYTES = 4096
    fun newToken(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

    fun authorized(stored: String?, presented: String?): Boolean {
        if (stored.isNullOrBlank() || presented.isNullOrBlank()) return false
        if (stored.length > MAX_TOKEN_BYTES || presented.length > MAX_TOKEN_BYTES) return false
        val a = stored.toByteArray(Charsets.UTF_8)
        val b = presented.toByteArray(Charsets.UTF_8)
        if (a.size > MAX_TOKEN_BYTES || b.size > MAX_TOKEN_BYTES) return false
        return MessageDigest.isEqual(a, b)
    }
}

object HttpAccessPolicy {
    const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    fun runtimePermissions(config: Map<String, String>, sdk: Int): List<String> =
        if (sdk >= 37 && config["allowPrivateNetwork"] == "true") listOf(LOCAL_NETWORK_PERMISSION)
        else emptyList()
}
