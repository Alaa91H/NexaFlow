package com.nexaflow.core.airuntime

import java.net.InetAddress
import java.net.URI
import com.nexaflow.core.common.EndpointSecurityPolicy

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

    fun requireLocalAddresses(addresses: List<InetAddress>) =
        EndpointSecurityPolicy.requireAddressScope(addresses, local = true)

    fun requireAddresses(addresses: List<InetAddress>, local: Boolean) =
        EndpointSecurityPolicy.requireAddressScope(addresses, local)

    fun isLocalAddress(address: InetAddress): Boolean =
        EndpointSecurityPolicy.isLocalAddress(address)

    private fun validatedBaseUri(
        config: OpenAiCompatibleProviderConfig,
        hasApiKey: Boolean
    ): URI {
        return EndpointSecurityPolicy.validateBaseUri(
            raw = config.baseUrl,
            allowHttp = true,
            local = config.local,
            hasCredential = hasApiKey,
        )
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
