package com.nexaflow.core.airuntime

enum class AiReasoningLevel(val apiValue: String) {
    FAST("low"),
    BALANCED("medium"),
    DEEP("high");

    companion object {
        fun fromStoredValue(value: String): AiReasoningLevel =
            entries.firstOrNull {
                it.apiValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            } ?: BALANCED
    }
}

/** Public provider defaults only; credentials are deliberately stored elsewhere. */
data class AiProviderPreset(
    val id: String,
    val definitionId: String,
    val displayName: String,
    val providerKind: AiProviderKind,
    val dialect: AiApiDialect,
    val authScheme: AiAuthScheme,
    val baseUrl: String,
    val defaultModelId: String,
    val local: Boolean = false,
    val legacyProtocol: AiProviderProtocol? = null
) {
    /** Compatibility bridge for settings/runtime callers migrated in later tasks. */
    val protocol: AiProviderProtocol
        get() = legacyProtocol ?: requireNotNull(AiProviderProtocol.fromDialect(dialect)) {
            "Preset " + id + " is not executable by the legacy provider surface"
        }
}

/**
 * Canonical provider metadata registry used by UI, runtime adapter selection,
 * validation and model discovery. Persistence stores stable IDs only.
 */
object AiProviderDefinitionRegistry {
    val definitions: List<AiProviderDefinition> = listOf(
        definition(
            id = "openai",
            displayName = "OpenAI",
            kind = AiProviderKind.OPENAI,
            dialects = setOf(
                AiApiDialect.OPENAI_CHAT_COMPLETIONS,
                AiApiDialect.OPENAI_RESPONSES
            ),
            defaultDialect = AiApiDialect.OPENAI_RESPONSES,
            auth = setOf(AiAuthScheme.BEARER_TOKEN)
        ),
        definition(
            id = "anthropic",
            displayName = "Anthropic",
            kind = AiProviderKind.ANTHROPIC,
            dialects = setOf(AiApiDialect.ANTHROPIC_MESSAGES),
            defaultDialect = AiApiDialect.ANTHROPIC_MESSAGES,
            auth = setOf(AiAuthScheme.X_API_KEY)
        ),
        definition(
            id = "google",
            displayName = "Google Gemini",
            kind = AiProviderKind.GOOGLE,
            dialects = setOf(
                AiApiDialect.GEMINI_GENERATE_CONTENT,
                AiApiDialect.OPENAI_CHAT_COMPLETIONS
            ),
            defaultDialect = AiApiDialect.GEMINI_GENERATE_CONTENT,
            auth = setOf(AiAuthScheme.GOOGLE_API_KEY, AiAuthScheme.BEARER_TOKEN)
        ),
        definition(
            id = "opencode",
            displayName = "OpenCode",
            kind = AiProviderKind.OPENCODE,
            dialects = AiApiDialect.entries.toSet(),
            defaultDialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS,
            auth = setOf(AiAuthScheme.BEARER_TOKEN)
        ),
        openAiCompatibleDefinition("openrouter", "OpenRouter", AiProviderKind.OPENROUTER),
        openAiCompatibleDefinition("groq", "Groq", AiProviderKind.GROQ),
        openAiCompatibleDefinition("mistral", "Mistral", AiProviderKind.MISTRAL),
        openAiCompatibleDefinition("deepseek", "DeepSeek", AiProviderKind.DEEPSEEK),
        openAiCompatibleDefinition("xai", "xAI", AiProviderKind.XAI),
        definition(
            id = "ollama",
            displayName = "Ollama",
            kind = AiProviderKind.OLLAMA,
            dialects = setOf(AiApiDialect.OPENAI_CHAT_COMPLETIONS),
            defaultDialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS,
            auth = setOf(AiAuthScheme.NONE),
            supportsCustomEndpoint = true
        ),
        definition(
            id = "lm_studio",
            displayName = "LM Studio",
            kind = AiProviderKind.LM_STUDIO,
            dialects = setOf(AiApiDialect.OPENAI_CHAT_COMPLETIONS),
            defaultDialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS,
            auth = setOf(AiAuthScheme.NONE),
            supportsCustomEndpoint = true
        ),
        definition(
            id = "openai_compatible",
            displayName = "OpenAI-compatible",
            kind = AiProviderKind.OPENAI_COMPATIBLE,
            dialects = setOf(AiApiDialect.OPENAI_CHAT_COMPLETIONS),
            defaultDialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS,
            auth = setOf(AiAuthScheme.BEARER_TOKEN, AiAuthScheme.NONE),
            supportsCustomEndpoint = true
        ),
        definition(
            id = "custom",
            displayName = "Custom",
            kind = AiProviderKind.CUSTOM,
            dialects = AiApiDialect.entries.toSet(),
            defaultDialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS,
            auth = AiAuthScheme.entries.toSet(),
            supportsCustomEndpoint = true
        )
    )

    val presets: List<AiProviderPreset> = listOf(
        AiProviderPreset(
            id = "openai",
            definitionId = "openai",
            displayName = "OpenAI",
            providerKind = AiProviderKind.OPENAI,
            dialect = AiApiDialect.OPENAI_RESPONSES,
            authScheme = AiAuthScheme.BEARER_TOKEN,
            baseUrl = "https://api.openai.com/v1",
            defaultModelId = "gpt-5.6",
            legacyProtocol = AiProviderProtocol.OPENAI_CHAT_COMPLETIONS
        ),
        AiProviderPreset(
            id = "claude",
            definitionId = "anthropic",
            displayName = "Claude",
            providerKind = AiProviderKind.ANTHROPIC,
            dialect = AiApiDialect.ANTHROPIC_MESSAGES,
            authScheme = AiAuthScheme.X_API_KEY,
            baseUrl = "https://api.anthropic.com/v1",
            defaultModelId = "claude-sonnet-5-5"
        ),
        AiProviderPreset(
            id = "gemini",
            definitionId = "google",
            displayName = "Gemini",
            providerKind = AiProviderKind.GOOGLE,
            dialect = AiApiDialect.GEMINI_GENERATE_CONTENT,
            authScheme = AiAuthScheme.GOOGLE_API_KEY,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            defaultModelId = "gemini-3.8-flash",
            legacyProtocol = AiProviderProtocol.OPENAI_CHAT_COMPLETIONS
        ),
        AiProviderPreset(
            id = AiGatewayCatalog.OPENCODE_ZEN,
            definitionId = "opencode",
            displayName = "OpenCode Zen",
            providerKind = AiProviderKind.OPENCODE,
            dialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS,
            authScheme = AiAuthScheme.BEARER_TOKEN,
            baseUrl = "https://opencode.ai/zen/v1",
            defaultModelId = "kimi-k2.7-code"
        )
    )

    private val definitionsById = definitions.associateBy(AiProviderDefinition::id)
    private val presetsById = presets.associateBy(AiProviderPreset::id)

    init {
        require(definitionsById.size == definitions.size) { "Duplicate AI provider definition id" }
        require(presetsById.size == presets.size) { "Duplicate AI provider preset id" }
        presets.forEach(::validatePreset)
    }

    fun definition(id: String?): AiProviderDefinition? =
        id?.trim()?.lowercase()?.let(definitionsById::get)

    fun preset(id: String?): AiProviderPreset? =
        id?.trim()?.lowercase()?.let(presetsById::get)

    fun definitionForPreset(presetId: String?): AiProviderDefinition? =
        preset(presetId)?.let { definition(it.definitionId) }

    fun resolveDialect(
        presetId: String?,
        storedProtocol: AiProviderProtocol?,
        modelId: String,
        explicitDialect: AiApiDialect? = null,
        remoteMetadata: AiGatewayRemoteModelMetadata? = null
    ): AiApiDialect? {
        explicitDialect?.let { candidate ->
            return candidate.takeIf { supportsDialect(presetId, candidate) }
        }

        AiGatewayCatalog.rulesFor(presetId)
            ?.takeIf { modelId.isNotBlank() }
            ?.let { rules ->
                val route = AiGatewayDialectResolver.resolve(
                    modelId = modelId,
                    rules = rules,
                    remoteMetadata = remoteMetadata
                )
                return route.dialect.takeIf { supportsDialect(presetId, it) }
            }

        preset(presetId)?.dialect?.let { return it }
        return storedProtocol?.toDialect()
    }

    fun supportsDialect(presetId: String?, dialect: AiApiDialect): Boolean {
        val provider = definitionForPreset(presetId) ?: return true
        return dialect in provider.supportedDialects
    }

    fun supportsAuth(presetId: String?, authScheme: AiAuthScheme): Boolean {
        val provider = definitionForPreset(presetId) ?: return true
        return authScheme in provider.authSchemes
    }

    fun isCustomEndpointAllowed(presetId: String?): Boolean =
        definitionForPreset(presetId)?.supportsCustomEndpoint ?: true

    private fun validatePreset(preset: AiProviderPreset) {
        val provider = requireNotNull(definition(preset.definitionId)) {
            "Unknown provider definition for preset " + preset.id
        }
        require(provider.kind == preset.providerKind) {
            "Provider kind mismatch for preset " + preset.id
        }
        require(preset.dialect in provider.supportedDialects) {
            "Unsupported dialect for preset " + preset.id
        }
        require(preset.authScheme in provider.authSchemes) {
            "Unsupported authentication for preset " + preset.id
        }
        require(preset.baseUrl.startsWith("https://") || preset.local) {
            "Cloud preset must use HTTPS: " + preset.id
        }
    }

    private fun definition(
        id: String,
        displayName: String,
        kind: AiProviderKind,
        dialects: Set<AiApiDialect>,
        defaultDialect: AiApiDialect,
        auth: Set<AiAuthScheme>,
        supportsCustomEndpoint: Boolean = false
    ) = AiProviderDefinition(
        id = id,
        displayName = displayName,
        kind = kind,
        supportedDialects = dialects,
        defaultDialect = defaultDialect,
        authSchemes = auth,
        supportsCustomEndpoint = supportsCustomEndpoint
    )

    private fun openAiCompatibleDefinition(
        id: String,
        displayName: String,
        kind: AiProviderKind
    ) = definition(
        id = id,
        displayName = displayName,
        kind = kind,
        dialects = setOf(AiApiDialect.OPENAI_CHAT_COMPLETIONS),
        defaultDialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS,
        auth = setOf(AiAuthScheme.BEARER_TOKEN),
        supportsCustomEndpoint = false
    )
}

/** Compatibility facade retained while callers migrate to the canonical registry. */
object AiProviderCatalog {
    val presets: List<AiProviderPreset>
        get() = AiProviderDefinitionRegistry.presets

    val definitions: List<AiProviderDefinition>
        get() = AiProviderDefinitionRegistry.definitions

    fun preset(id: String): AiProviderPreset? =
        AiProviderDefinitionRegistry.preset(id)

    fun definition(id: String): AiProviderDefinition? =
        AiProviderDefinitionRegistry.definition(id)
}
