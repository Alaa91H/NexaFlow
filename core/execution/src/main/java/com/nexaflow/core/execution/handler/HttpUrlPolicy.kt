package com.nexaflow.core.execution.handler

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/**
 * Destination policy for the HTTP action — the SSRF gate.
 *
 * Every URL an HTTP action talks to (including every redirect hop) must pass
 * [inspect] before a connection is opened. The rules:
 *
 *  - only `http` and `https` schemes are allowed;
 *  - URLs carrying embedded credentials (`user:pass@host`) are rejected —
 *    secrets belong in headers, not in strings that leak into logs;
 *  - multicast and unresolved hosts are always rejected;
 *  - loopback / link-local / private-LAN destinations are rejected unless the
 *    task explicitly opts in (`allowPrivateNetwork=true`) — imported workflows
 *    must not silently learn to poke local services.
 *
 * Pure and resolver-injectable so every rule is unit-testable offline
 * (IP-literal hosts resolve without DNS).
 */
object HttpUrlPolicy {

    enum class DestinationClass { PUBLIC, LOOPBACK, LINK_LOCAL, PRIVATE_LAN, MULTICAST, ANY_LOCAL }

    sealed interface Verdict {
        data class Allowed(val destination: DestinationClass) : Verdict

        /** Denied before any connection — [reason] never contains the URL itself. */
        data class Denied(val reason: String) : Verdict
    }

    val ALLOWED_SCHEMES: Set<String> = setOf("http", "https")

    /** Upper bound on redirect hops the handler will follow. */
    const val MAX_REDIRECTS: Int = 5

    private val REDIRECT_CODES: Set<Int> = setOf(301, 302, 303, 307, 308)

    /**
     * Inspects a URL before connection. [resolve] is injectable for offline
     * tests; the default resolver treats IP literals without touching DNS.
     *
     * When the host name cannot be resolved at inspect time: IP-literal
     * targets never hit this path (they classify directly), but a host NAME
     * that fails lookup cannot be classified — with [allowUnresolvable]
     * (default) the connection attempt is still made and will fail on its
     * own (best-effort mode); with it disabled, unresolvable hosts are
     * denied outright (strict mode).
     */
    fun inspect(
        url: String,
        allowPrivateNetwork: Boolean,
        allowUnresolvable: Boolean = true,
        resolve: (String) -> Array<InetAddress> = { InetAddress.getAllByName(it) }
    ): Verdict {
        val uri = runCatching { URI(url) }.getOrElse {
            return Verdict.Denied("Malformed URL")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme !in ALLOWED_SCHEMES) return Verdict.Denied("Scheme not allowed")
        if (uri.userInfo != null) return Verdict.Denied("Embedded credentials are not allowed")
        val host = uri.host ?: return Verdict.Denied("URL has no host")

        val addresses = runCatching { resolve(host) }.getOrNull()
        if (addresses.isNullOrEmpty()) {
            return if (allowUnresolvable) {
                Verdict.Allowed(DestinationClass.PUBLIC)
            } else {
                Verdict.Denied("Host cannot be resolved")
            }
        }

        return when (val destination = classify(addresses.first())) {
            DestinationClass.MULTICAST -> Verdict.Denied("Multicast destination is not allowed")
            DestinationClass.ANY_LOCAL -> Verdict.Denied("Any-local destination is not allowed")
            DestinationClass.PUBLIC -> Verdict.Allowed(destination)
            DestinationClass.LOOPBACK,
            DestinationClass.LINK_LOCAL,
            DestinationClass.PRIVATE_LAN ->
                if (allowPrivateNetwork) {
                    Verdict.Allowed(destination)
                } else {
                    Verdict.Denied(
                        "Private network destination requires the allowPrivateNetwork option"
                    )
                }
        }
    }

    /** Classifies an already-resolved address (offline-testable core rule). */
    fun classify(address: InetAddress): DestinationClass = when {
        address.isMulticastAddress -> DestinationClass.MULTICAST
        address.isAnyLocalAddress -> DestinationClass.ANY_LOCAL
        address.isLoopbackAddress -> DestinationClass.LOOPBACK
        address.isLinkLocalAddress -> DestinationClass.LINK_LOCAL
        address.isSiteLocalAddress -> DestinationClass.PRIVATE_LAN
        isIpv6UniqueLocal(address) -> DestinationClass.PRIVATE_LAN
        isIpv4ThisNetwork(address) -> DestinationClass.LOOPBACK
        else -> DestinationClass.PUBLIC
    }

    /** fc00::/7 unique-local addresses. */
    private fun isIpv6UniqueLocal(address: InetAddress): Boolean {
        if (address !is Inet6Address) return false
        val first = address.address[0].toInt() and 0xFF
        return (first and 0xFE) == 0xFC
    }

    /** 0.0.0.0/8 "this network" targets (excluding the any-local wildcard itself). */
    private fun isIpv4ThisNetwork(address: InetAddress): Boolean =
        address is Inet4Address && !address.isAnyLocalAddress && address.address[0].toInt() == 0

    /**
     * Resolves a redirect `Location` against the current URL. Returns null
     * when the Location is unusable — the caller must treat that as failure,
     * never silently stay on the old target.
     */
    fun resolveRedirect(baseUrl: String, location: String): String? =
        runCatching {
            URI(baseUrl).resolve(location.trim()).toString()
        }.getOrNull()

    fun isRedirect(code: Int): Boolean = code in REDIRECT_CODES

    /**
     * Whether following this redirect may change the HTTP method semantics:
     * 301/302 downgrade POST→GET per browser practice, 303 always does.
     */
    fun downgradesToGet(code: Int, method: String): Boolean =
        code == 303 || ((code == 301 || code == 302) && method == "POST")
}
