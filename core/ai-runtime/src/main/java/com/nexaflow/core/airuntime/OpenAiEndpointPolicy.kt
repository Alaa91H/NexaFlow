package com.nexaflow.core.airuntime

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

object OpenAiEndpointPolicy {

    fun chatCompletionsUri(
        config: OpenAiCompatibleProviderConfig,
        hasApiKey: Boolean
    ): URI = endpointUri(
        base = validatedBaseUri(config, hasApiKey),
        suffix = CHAT_COMPLETIONS_PATH
    )

    fun modelsUri(
        config: OpenAiCompatibleProviderConfig,
        hasApiKey: Boolean
    ): URI = endpointUri(
        base = validatedBaseUri(config, hasApiKey),
        suffix = MODELS_PATH
    )

    fun requireLocalAddresses(addresses: List<InetAddress>) {
        require(addresses.isNotEmpty()) { "Provider host did not resolve" }
        require(addresses.all(::isLocalAddress)) {
            "Local provider resolved outside private address space"
        }
    }

    fun isLocalAddress(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress
        ) {
            return true
        }
        if (address is Inet6Address) {
            val bytes = address.address
            if (bytes.isNotEmpty() && (bytes[0].toInt() and 0xFE) == 0xFC) {
                return true
            }
        }
        return false
    }

    private fun validatedBaseUri(
        config: OpenAiCompatibleProviderConfig,
        hasApiKey: Boolean
    ): URI {
        val base = URI(config.baseUrl)
        require(base.scheme in setOf("http", "https")) {
            "Provider URL must use HTTP or HTTPS"
        }
        require(!base.host.isNullOrBlank()) { "Provider URL must include a host" }
        require(base.userInfo == null) { "Provider URL must not include user info" }
        require(base.fragment == null) { "Provider URL must not include a fragment" }
        require(base.query == null) { "Provider base URL must not include a query" }
        if (base.scheme == "http") {
            require(config.local) { "Cleartext HTTP is restricted to local providers" }
            require(!hasApiKey) { "API keys must never be sent over cleartext HTTP" }
        }
        return base
    }

    private fun endpointUri(base: URI, suffix: String): URI {
        val basePath = base.path.orEmpty().trimEnd('/')
        val path = (if (basePath.isBlank()) "" else basePath) + suffix
        return URI(
            base.scheme,
            null,
            base.host,
            base.port,
            path,
            null,
            null
        )
    }

    private const val CHAT_COMPLETIONS_PATH = "/chat/completions"
    private const val MODELS_PATH = "/models"
}
