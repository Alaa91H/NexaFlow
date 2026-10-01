package com.nexaflow.core.common

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/**
 * Shared URL/DNS security primitives for outbound HTTP and AI transports.
 * Protocol-specific layers still own paths, headers and response budgets.
 */
object EndpointSecurityPolicy {
    fun validateBaseUri(
        raw: String,
        allowHttp: Boolean,
        local: Boolean,
        hasCredential: Boolean,
    ): URI {
        val uri = URI(raw.trim())
        val scheme = uri.scheme?.lowercase()
        require(scheme == "https" || (allowHttp && scheme == "http")) {
            "Endpoint must use an allowed HTTP scheme"
        }
        require(!uri.host.isNullOrBlank()) { "Endpoint must include a host" }
        require(uri.userInfo == null) { "Endpoint must not include user info" }
        require(uri.fragment == null) { "Endpoint must not include a fragment" }
        require(uri.query == null) { "Endpoint base URL must not include a query" }
        require(uri.port == -1 || uri.port in 1..65535) { "Endpoint port is invalid" }
        if (scheme == "http") {
            require(local) { "Cleartext HTTP is restricted to local endpoints" }
            require(!hasCredential) { "Credentials must never be sent over cleartext HTTP" }
        }
        return uri
    }

    fun requireAddressScope(addresses: List<InetAddress>, local: Boolean) {
        require(addresses.isNotEmpty()) { "Endpoint host did not resolve" }
        require(addresses.none { it.isAnyLocalAddress || it.isMulticastAddress }) {
            "Endpoint resolved to an invalid destination"
        }
        if (local) {
            require(addresses.all(::isLocalAddress)) {
                "Local endpoint resolved outside private address space"
            }
        } else {
            require(addresses.none(::isLocalAddress)) {
                "Remote endpoint resolved to a private address"
            }
        }
    }

    fun isLocalAddress(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) return true

        val bytes = address.address.map { it.toInt() and 0xff }
        return when (bytes.size) {
            4 -> bytes[0] == 0 ||
                (bytes[0] == 100 && bytes[1] in 64..127) ||
                bytes[0] >= 224
            16 -> {
                val uniqueLocal = address is Inet6Address && (bytes[0] and 0xfe) == 0xfc
                val mappedV4 = bytes.take(10).all { it == 0 } &&
                    bytes[10] == 0xff && bytes[11] == 0xff
                uniqueLocal || (
                    mappedV4 &&
                        isLocalAddress(InetAddress.getByAddress(address.address.copyOfRange(12, 16)))
                    )
            }
            else -> true
        }
    }
}
