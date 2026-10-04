package com.nexaflow.core.agentapi

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Host-header policy for the local agent server.
 *
 * Loopback is always accepted. Cleartext LAN access remains disabled until the
 * listener has an authenticated TLS transport; a preference alone cannot enable it.
 * DNS hostnames are never accepted in LAN mode, which keeps the HTTP boundary
 * resistant to DNS rebinding when secure LAN transport becomes available.
 */
class AgentApiHostPolicy {

    @Volatile
    var lanAccessEnabled: Boolean = false
        private set

    fun setLanAccessEnabled(enabled: Boolean) {
        lanAccessEnabled = enabled && LAN_TLS_TRANSPORT_AVAILABLE
    }

    fun supportsLanAccess(): Boolean = LAN_TLS_TRANSPORT_AVAILABLE

    fun isAllowed(hostHeader: String?): Boolean {
        val host = normalizeHost(hostHeader) ?: return false
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") {
            return true
        }
        if (!lanAccessEnabled || !isNumericAddress(host)) {
            return false
        }
        val address = runCatching { InetAddress.getByName(host) }.getOrNull()
            ?: return false
        return when (address) {
            is Inet4Address -> isPrivateIpv4(address.address)
            is Inet6Address -> isPrivateIpv6(address.address)
            else -> false
        }
    }

    private fun normalizeHost(value: String?): String? {
        val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (raw.startsWith("[")) {
            val end = raw.indexOf(']')
            if (end <= 1) return null
            if (end + 1 < raw.length) {
                val suffix = raw.substring(end + 1)
                if (!suffix.startsWith(":") || suffix.drop(1).toIntOrNull() == null) {
                    return null
                }
            }
            return raw.substring(1, end).lowercase()
        }

        val colonCount = raw.count { it == ':' }
        return when (colonCount) {
            0 -> raw.lowercase()
            1 -> {
                val host = raw.substringBefore(':')
                val port = raw.substringAfter(':')
                if (host.isBlank() || port.toIntOrNull() == null) null
                else host.lowercase()
            }
            else -> raw.lowercase()
        }
    }

    private fun isNumericAddress(host: String): Boolean =
        IPV4.matches(host) || host.contains(':')

    private fun isPrivateIpv4(bytes: ByteArray): Boolean {
        if (bytes.size != 4) return false
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        return first == 10 ||
            first == 127 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 169 && second == 254)
    }

    private fun isPrivateIpv6(bytes: ByteArray): Boolean {
        if (bytes.size != 16) return false
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        val loopback = bytes.dropLast(1).all { it.toInt() == 0 } &&
            bytes.last().toInt() == 1
        val uniqueLocal = first and 0xfe == 0xfc
        val linkLocal = first == 0xfe && second and 0xc0 == 0x80
        return loopback || uniqueLocal || linkLocal
    }

    private companion object {
        const val LAN_TLS_TRANSPORT_AVAILABLE = false
        val IPV4 = Regex(
            """(?:25[0-5]|2[0-4]\d|1?\d?\d)(?:\.(?:25[0-5]|2[0-4]\d|1?\d?\d)){3}"""
        )
    }
}
