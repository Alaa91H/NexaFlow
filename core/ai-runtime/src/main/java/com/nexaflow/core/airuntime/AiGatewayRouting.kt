package com.nexaflow.core.airuntime

enum class AiGatewayRouteSource {
    EXPLICIT_METADATA,
    REMOTE_METADATA,
    CURATED_REGISTRY,
    MODEL_PREFIX,
    DEFAULT
}

data class AiGatewayRemoteModelMetadata(
    val modelId: String,
    val dialect: AiApiDialect? = null,
    val apiStyle: String? = null
)

data class AiGatewayRoutingRules(
    val id: String,
    val googlePrefixes: List<String> = emptyList(),
    val anthropicPrefixes: List<String> = emptyList(),
    val responsesPrefixes: List<String> = emptyList(),
    val chatPrefixes: List<String> = emptyList(),
    val curatedModels: Map<String, AiApiDialect> = emptyMap(),
    val defaultDialect: AiApiDialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS
) {
    init {
        require(id.isNotBlank())
        require(
            (
                googlePrefixes +
                    anthropicPrefixes +
                    responsesPrefixes +
                    chatPrefixes
                ).all(String::isNotBlank)
        )
    }
}

data class AiGatewayRoute(
    val modelId: String,
    val dialect: AiApiDialect,
    val source: AiGatewayRouteSource
)

object AiGatewayDialectResolver {

    fun resolve(
        modelId: String,
        rules: AiGatewayRoutingRules,
        explicitDialect: AiApiDialect? = null,
        remoteMetadata: AiGatewayRemoteModelMetadata? = null
    ): AiGatewayRoute {
        val normalizedModel = normalizeModelId(modelId)

        explicitDialect?.let {
            return AiGatewayRoute(
                modelId = normalizedModel,
                dialect = it,
                source = AiGatewayRouteSource.EXPLICIT_METADATA
            )
        }

        remoteMetadata
            ?.takeIf { normalizeModelId(it.modelId) == normalizedModel }
            ?.let { metadata ->
                val dialect = metadata.dialect ?: apiStyleToDialect(metadata.apiStyle)
                if (dialect != null) {
                    return AiGatewayRoute(
                        modelId = normalizedModel,
                        dialect = dialect,
                        source = AiGatewayRouteSource.REMOTE_METADATA
                    )
                }
            }

        rules.curatedModels.entries
            .firstOrNull { normalizeModelId(it.key) == normalizedModel }
            ?.value
            ?.let {
                return AiGatewayRoute(
                    modelId = normalizedModel,
                    dialect = it,
                    source = AiGatewayRouteSource.CURATED_REGISTRY
                )
            }

        prefixDialect(normalizedModel, rules)?.let {
            return AiGatewayRoute(
                modelId = normalizedModel,
                dialect = it,
                source = AiGatewayRouteSource.MODEL_PREFIX
            )
        }

        return AiGatewayRoute(
            modelId = normalizedModel,
            dialect = rules.defaultDialect,
            source = AiGatewayRouteSource.DEFAULT
        )
    }

    fun apiStyleToDialect(apiStyle: String?): AiApiDialect? =
        when (apiStyle?.trim()?.lowercase()) {
            "google",
            "gemini",
            "google-generate-content",
            "gemini-generate-content" -> AiApiDialect.GEMINI_GENERATE_CONTENT

            "anthropic",
            "claude",
            "anthropic-messages" -> AiApiDialect.ANTHROPIC_MESSAGES

            "openai-responses",
            "responses" -> AiApiDialect.OPENAI_RESPONSES

            "openai",
            "openai-completions",
            "openai-chat-completions",
            "chat-completions" -> AiApiDialect.OPENAI_CHAT_COMPLETIONS

            else -> null
        }

    private fun prefixDialect(
        modelId: String,
        rules: AiGatewayRoutingRules
    ): AiApiDialect? = when {
        matchesAnyPrefix(modelId, rules.googlePrefixes) ->
            AiApiDialect.GEMINI_GENERATE_CONTENT
        matchesAnyPrefix(modelId, rules.anthropicPrefixes) ->
            AiApiDialect.ANTHROPIC_MESSAGES
        matchesAnyPrefix(modelId, rules.responsesPrefixes) ->
            AiApiDialect.OPENAI_RESPONSES
        matchesAnyPrefix(modelId, rules.chatPrefixes) ->
            AiApiDialect.OPENAI_CHAT_COMPLETIONS
        else -> null
    }

    private fun matchesAnyPrefix(
        normalizedModelId: String,
        prefixes: List<String>
    ): Boolean = prefixes.any { prefix ->
        normalizedModelId.startsWith(prefix.trim().lowercase())
    }

    private fun normalizeModelId(value: String): String {
        val normalized = value.trim().lowercase()
        require(normalized.length in 1..AiModelDescriptorV2.MAX_MODEL_ID_LENGTH)
        return normalized
    }
}

object AiGatewayCatalog {
    const val OPENCODE_ZEN = "opencode_zen"
    const val OPENCODE_GO = "opencode_go"

    val openCodeZen = AiGatewayRoutingRules(
        id = OPENCODE_ZEN,
        googlePrefixes = listOf("gemini-"),
        anthropicPrefixes = listOf("claude-", "qwen3."),
        responsesPrefixes = listOf("gpt-", "grok-", "muse-spark"),
        defaultDialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS
    )

    val openCodeGo = AiGatewayRoutingRules(
        id = OPENCODE_GO,
        anthropicPrefixes = listOf("minimax-", "qwen3."),
        responsesPrefixes = listOf("grok-", "gpt-", "muse-spark"),
        defaultDialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS
    )

    private val rulesById = listOf(openCodeZen, openCodeGo).associateBy(AiGatewayRoutingRules::id)

    fun rulesFor(id: String?): AiGatewayRoutingRules? =
        id?.trim()?.lowercase()?.let(rulesById::get)
}

object AiGatewaySessionPolicy {
    const val HEADER_NAME = "x-opencode-session"

    fun requestHeaders(conversationId: String): Map<String, String> =
        mapOf(HEADER_NAME to normalizedSessionId(conversationId))

    fun normalizedSessionId(
        raw: String?,
        fallback: String = "nexaflow"
    ): String {
        val normalized = raw
            ?.trim()
            ?.map { character ->
                when {
                    character.isLetterOrDigit() -> character
                    character == '_' ||
                        character == '.' ||
                        character == ':' ||
                        character == '-' -> character
                    else -> '-'
                }
            }
            ?.joinToString(separator = "")
            ?.take(MAX_LENGTH)
            .orEmpty()

        return normalized.ifBlank {
            fallback
                .trim()
                .take(MAX_LENGTH)
                .ifBlank { "nexaflow" }
        }
    }

    private const val MAX_LENGTH = 128
}
