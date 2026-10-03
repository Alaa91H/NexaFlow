package com.nexaflow.core.execution.handler

import java.net.InetAddress
import java.net.URI
import com.nexaflow.core.common.EndpointSecurityPolicy

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

    fun isLocal(address: InetAddress): Boolean =
        EndpointSecurityPolicy.isLocalAddress(address)

}
