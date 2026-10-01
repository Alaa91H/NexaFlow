package com.nexaflow.core.airuntime

enum class AiReasoningLevel(val apiValue: String) {
    FAST("low"),
    BALANCED("medium"),
    DEEP("high");

    companion object {
        fun fromStoredValue(value: String): AiReasoningLevel =
            entries.firstOrNull {
                it.apiValue.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
            } ?: BALANCED
    }
}

/** Public provider defaults only; credentials are deliberately stored elsewhere. */
data class AiProviderPreset(
    val id: String,
    val displayName: String,
    val providerKind: AiProviderKind,
    val dialect: AiApiDialect,
    val authScheme: AiAuthScheme,
    val baseUrl: String,
    val defaultModelId: String,
    val local: Boolean = false
) {
    /** Compatibility bridge for settings/runtime callers migrated in later tasks. */
    val protocol: AiProviderProtocol
        get() = requireNotNull(AiProviderProtocol.fromDialect(dialect)) {
            "Preset $id is not executable by the legacy provider surface"
        }
}

object AiProviderCatalog {
    val presets: List<AiProviderPreset> = listOf(
        AiProviderPreset(
            id = "openai",
            displayName = "OpenAI",
            providerKind = AiProviderKind.OPENAI,
            dialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS,
            authScheme = AiAuthScheme.BEARER_TOKEN,
            baseUrl = "https://api.openai.com/v1",
            defaultModelId = "gpt-5.6"
        ),
        AiProviderPreset(
            id = "claude",
            displayName = "Claude",
            providerKind = AiProviderKind.ANTHROPIC,
            dialect = AiApiDialect.ANTHROPIC_MESSAGES,
            authScheme = AiAuthScheme.X_API_KEY,
            baseUrl = "https://api.anthropic.com/v1",
            defaultModelId = "claude-sonnet-5-5"
        ),
        AiProviderPreset(
            id = "gemini",
            displayName = "Gemini",
            providerKind = AiProviderKind.GOOGLE,
            // Kept compatible until the native Gemini adapter lands in T14.
            dialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS,
            authScheme = AiAuthScheme.BEARER_TOKEN,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            defaultModelId = "gemini-3.8-flash"
        ),
        AiProviderPreset(
            id = "opencode_zen",
            displayName = "OpenCode Zen",
            providerKind = AiProviderKind.OPENCODE,
            // Gateway dialect routing is introduced in T15.
            dialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS,
            authScheme = AiAuthScheme.BEARER_TOKEN,
            baseUrl = "https://opencode.ai/zen/v1",
            defaultModelId = "kimi-k2.7-code"
        )
    )

    val definitions: List<AiProviderDefinition> = listOf(
        AiProviderDefinition(
            id = "openai",
            displayName = "OpenAI",
            kind = AiProviderKind.OPENAI,
            supportedDialects = setOf(
                AiApiDialect.OPENAI_CHAT_COMPLETIONS,
                AiApiDialect.OPENAI_RESPONSES
            ),
            defaultDialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS,
            authSchemes = setOf(AiAuthScheme.BEARER_TOKEN),
            supportsCustomEndpoint = false
        ),
        AiProviderDefinition(
            id = "anthropic",
            displayName = "Anthropic",
            kind = AiProviderKind.ANTHROPIC,
            supportedDialects = setOf(AiApiDialect.ANTHROPIC_MESSAGES),
            defaultDialect = AiApiDialect.ANTHROPIC_MESSAGES,
            authSchemes = setOf(AiAuthScheme.X_API_KEY),
            supportsCustomEndpoint = false
        ),
        AiProviderDefinition(
            id = "google",
            displayName = "Google",
            kind = AiProviderKind.GOOGLE,
            supportedDialects = setOf(
                AiApiDialect.GEMINI_GENERATE_CONTENT,
                AiApiDialect.OPENAI_CHAT_COMPLETIONS
            ),
            defaultDialect = AiApiDialect.GEMINI_GENERATE_CONTENT,
            authSchemes = setOf(AiAuthScheme.GOOGLE_API_KEY, AiAuthScheme.BEARER_TOKEN),
            supportsCustomEndpoint = false
        ),
        AiProviderDefinition(
            id = "opencode",
            displayName = "OpenCode",
            kind = AiProviderKind.OPENCODE,
            supportedDialects = setOf(
                AiApiDialect.OPENAI_CHAT_COMPLETIONS,
                AiApiDialect.OPENAI_RESPONSES,
                AiApiDialect.ANTHROPIC_MESSAGES,
                AiApiDialect.GEMINI_GENERATE_CONTENT
            ),
            defaultDialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS,
            authSchemes = setOf(AiAuthScheme.BEARER_TOKEN),
            supportsCustomEndpoint = false
        ),
        AiProviderDefinition(
            id = "openai_compatible",
            displayName = "OpenAI-compatible",
            kind = AiProviderKind.OPENAI_COMPATIBLE,
            supportedDialects = setOf(AiApiDialect.OPENAI_CHAT_COMPLETIONS),
            defaultDialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS,
            authSchemes = setOf(AiAuthScheme.BEARER_TOKEN, AiAuthScheme.NONE),
            supportsCustomEndpoint = true
        ),
        AiProviderDefinition(
            id = "custom",
            displayName = "Custom",
            kind = AiProviderKind.CUSTOM,
            supportedDialects = AiApiDialect.entries.toSet(),
            defaultDialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS,
            authSchemes = AiAuthScheme.entries.toSet(),
            supportsCustomEndpoint = true
        )
    )

    fun preset(id: String): AiProviderPreset? = presets.firstOrNull { it.id == id }

    fun definition(id: String): AiProviderDefinition? =
        definitions.firstOrNull { it.id == id }
}
