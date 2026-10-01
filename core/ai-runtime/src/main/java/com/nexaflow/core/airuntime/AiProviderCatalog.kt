package com.nexaflow.core.airuntime

/** Wire protocol used by a provider profile. Adding a protocol requires an adapter. */
enum class AiProviderProtocol {
    OPENAI_CHAT_COMPLETIONS,
    ANTHROPIC_MESSAGES
}

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
    val protocol: AiProviderProtocol,
    val baseUrl: String,
    val defaultModelId: String,
    val local: Boolean = false
)

object AiProviderCatalog {
    val presets: List<AiProviderPreset> = listOf(
        AiProviderPreset(
            id = "openai",
            displayName = "OpenAI",
            protocol = AiProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://api.openai.com/v1",
            defaultModelId = "gpt-5.6"
        ),
        AiProviderPreset(
            id = "claude",
            displayName = "Claude",
            protocol = AiProviderProtocol.ANTHROPIC_MESSAGES,
            baseUrl = "https://api.anthropic.com/v1",
            defaultModelId = "claude-sonnet-5-5"
        ),
        AiProviderPreset(
            id = "gemini",
            displayName = "Gemini",
            protocol = AiProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            defaultModelId = "gemini-3.8-flash"
        ),
        AiProviderPreset(
            id = "opencode_zen",
            displayName = "OpenCode Zen",
            protocol = AiProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://opencode.ai/zen/v1",
            defaultModelId = "kimi-k2.7-code"
        )
    )

    fun preset(id: String): AiProviderPreset? = presets.firstOrNull { it.id == id }
}
