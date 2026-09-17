package com.nexaflow.core.execution.handler

import java.net.InetAddress
import java.net.URI

/** Validates every DNS answer. The returned addresses must be used by the transport. */
object HttpUrlPolicy {
    data class Destination(val uri: URI, val addresses: List<InetAddress>)
    fun inspect(url: String, allowPrivateNetwork: Boolean,
        resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() }
    ): Destination {
        val uri = URI(url)
        require(uri.scheme.equals("https", true)) { "HTTPS_REQUIRED" }
        require(uri.userInfo == null && uri.fragment == null && !uri.host.isNullOrBlank()) { "INVALID_URL" }
        require(uri.port == -1 || uri.port in 1..65535) { "INVALID_PORT" }
        val addresses = resolve(uri.host.removeSurrounding("[", "]"))
        require(addresses.isNotEmpty()) { "DNS_UNRESOLVED" }
        require(addresses.none { it.isAnyLocalAddress || it.isMulticastAddress }) { "INVALID_DESTINATION" }
        require(allowPrivateNetwork || addresses.none(::isLocal)) { "PRIVATE_NETWORK_DENIED" }
        return Destination(uri, addresses)
    }

    fun isLocal(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return true
        val b = address.address.map { it.toInt() and 255 }
        return when (b.size) {
            4 -> b[0] == 0 || b[0] == 100 && b[1] in 64..127 || b[0] >= 224
            16 -> b[0] and 0xfe == 0xfc || // IPv6 unique-local
                (b.take(10).all { it == 0 } && b[10] == 255 && b[11] == 255 &&
                    isLocal(InetAddress.getByAddress(address.address.copyOfRange(12, 16))))
            else -> true
        }
    }
}
