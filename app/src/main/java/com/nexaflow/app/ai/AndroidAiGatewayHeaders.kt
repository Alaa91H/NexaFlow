package com.nexaflow.app.ai

import com.nexaflow.core.airuntime.AiGatewaySessionPolicy
import java.net.URLConnection

internal object AndroidAiGatewayHeaders {
    private val allowedNames = setOf(
        AiGatewaySessionPolicy.HEADER_NAME.lowercase(),
        AiGatewaySessionPolicy.USER_AGENT_HEADER.lowercase()
    )

    fun normalized(headers: Map<String, String>): Map<String, String> {
        if (headers.isEmpty()) return emptyMap()
        require(headers.size <= allowedNames.size) { "Unsupported gateway headers" }

        val normalized = linkedMapOf<String, String>()
        headers.forEach { (rawName, rawValue) ->
            val name = rawName.trim()
            val key = name.lowercase()
            require(key in allowedNames) { "Unsupported gateway header" }
            require('\r' !in rawValue && '\n' !in rawValue) {
                "Gateway header value contains a line break"
            }
            when (key) {
                AiGatewaySessionPolicy.HEADER_NAME ->
                    normalized[AiGatewaySessionPolicy.HEADER_NAME] =
                        AiGatewaySessionPolicy.normalizedSessionId(rawValue)

                AiGatewaySessionPolicy.USER_AGENT_HEADER.lowercase() ->
                    normalized[AiGatewaySessionPolicy.USER_AGENT_HEADER] =
                        AiGatewaySessionPolicy.CLIENT_USER_AGENT
            }
        }
        return normalized
    }

    fun apply(connection: URLConnection, headers: Map<String, String>) {
        normalized(headers).forEach(connection::setRequestProperty)
    }
}
