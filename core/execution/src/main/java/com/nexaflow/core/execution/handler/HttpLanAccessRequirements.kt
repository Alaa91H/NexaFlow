package com.nexaflow.core.execution.handler

import java.net.InetAddress

/**
 * P0.6 — conditional `ACCESS_LOCAL_NETWORK` requirement for the HTTP action.
 *
 * Instead of requesting the Android 17 local-network runtime permission for
 * *every* HTTP action, the requirement is inferred from the actual URL
 * destination (same classification the SSRF gate applies):
 *
 * - Public internet destination → no LAN permission needed.
 * - Private/LAN/loopback destination → required.
 * - Destination cannot be classified statically (unparseable, a host NAME
 *   whose DNS answer isn't known at build time, or contains `{variables}`
 *   resolved at run time) → the requirement is **conditional**: it applies
 *   only if the resolved destination turns out to be private. The UI surfaces
 *   this as a conditional row and the runtime re-checks it from the resolved
 *   URL before the request fires.
 */
object HttpLanAccessRequirements {

    const val URL_KEY = "url"

    /** Statically-known state of the LAN requirement for a config value. */
    sealed interface Requirement {
        /** Public destination — never needs ACCESS_LOCAL_NETWORK. */
        data object NotRequired : Requirement

        /** Private/LAN/loopback destination — the permission is required. */
        data object Required : Requirement

        /** Not statically decidable — required only if the runtime URL is private. */
        data object Conditional : Requirement
    }

    /**
     * Classifies the requirement from the raw config `url` value.
     *
     * @param url raw config value (may contain `{variables}`, whitespace, etc.)
     */
    fun lanRequirementFor(url: String?): Requirement {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return Requirement.Conditional
        if (raw.contains('{') || raw.contains('}')) return Requirement.Conditional

        val host = runCatching { URI_HOST_EXTRACTOR(raw) }.getOrNull()
            ?: return Requirement.Conditional

        val address = literalAddress(host) ?: return Requirement.Conditional
        return when (HttpUrlPolicy.classify(address)) {
            HttpUrlPolicy.DestinationClass.PUBLIC -> Requirement.NotRequired
            // These can never be a legitimate public destination: always required.
            HttpUrlPolicy.DestinationClass.LOOPBACK,
            HttpUrlPolicy.DestinationClass.LINK_LOCAL,
            HttpUrlPolicy.DestinationClass.PRIVATE_LAN,
            HttpUrlPolicy.DestinationClass.MULTICAST,
            HttpUrlPolicy.DestinationClass.ANY_LOCAL -> Requirement.Required
        }
    }

    /** Runtime re-check from the fully-resolved URL (post variable expansion). */
    fun isLanDestination(resolvedUrl: String): Boolean {
        val parsed = runCatching { java.net.URI(resolvedUrl.trim()) }.getOrNull()
        val host = parsed?.host?.takeIf { it.isNotBlank() } ?: return true
        val address = literalAddress(host)
            // Host name: cannot prove it is public without DNS — treat as LAN
            // for requirement purposes (fail-closed on the permission).
            ?: return true
        return HttpUrlPolicy.classify(address) != HttpUrlPolicy.DestinationClass.PUBLIC
    }

    /**
     * Parses a host as an IP literal without touching DNS. Returns null for
     * host names (DNS-dependent → conditional) and malformed input.
     */
    private fun literalAddress(host: String): InetAddress? {
        val h = host.removePrefix("[").removeSuffix("]")
        if (h.isEmpty()) return null
        // IPv6 literal (contains ':') and IPv4 literals never hit DNS in
        // getByName; host names do, so they are excluded up front.
        val isLiteral = h.contains(':') || h.matches(IPV4_REGEX)
        if (!isLiteral) return null
        return runCatching { InetAddress.getByName(h) }.getOrNull()
    }

    private val IPV4_REGEX =
        Regex("""^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$""")

    /** Extracts the host part of a URL-ish string without full URI parsing. */
    private val URI_HOST_EXTRACTOR: (String) -> String? = { raw ->
        runCatching {
            val withScheme = if ("://" in raw) raw else "https://$raw"
            java.net.URI(withScheme).host
        }.getOrNull()
    }
}
