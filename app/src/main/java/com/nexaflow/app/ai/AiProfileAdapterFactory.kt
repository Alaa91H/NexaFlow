package com.nexaflow.app.ai

import com.nexaflow.core.airuntime.AiApiDialect
import com.nexaflow.core.airuntime.AiCredentialReferences
import com.nexaflow.core.airuntime.AiCredentialStore
import com.nexaflow.core.airuntime.AiGatewayCatalog
import com.nexaflow.core.airuntime.AiGatewayDialectResolver
import com.nexaflow.core.airuntime.AiModelProvider
import com.nexaflow.core.airuntime.AiProviderProtocol
import com.nexaflow.core.airuntime.AnthropicMessagesProvider
import com.nexaflow.core.airuntime.AnthropicMessagesProviderConfig
import com.nexaflow.core.airuntime.AnthropicMessagesTransport
import com.nexaflow.core.airuntime.GeminiNativeProvider
import com.nexaflow.core.airuntime.GeminiNativeProviderConfig
import com.nexaflow.core.airuntime.GeminiNativeTransport
import com.nexaflow.core.airuntime.OpenAiCompatibleProvider
import com.nexaflow.core.airuntime.OpenAiCompatibleProviderConfig
import com.nexaflow.core.airuntime.OpenAiCompatibleTransport
import com.nexaflow.core.airuntime.OpenAiResponsesProvider
import com.nexaflow.core.airuntime.OpenAiResponsesProviderConfig
import com.nexaflow.core.airuntime.OpenAiResponsesTransport
import com.nexaflow.core.datastore.AiProviderProfileSettings

class AiProfileAdapterFactory(
    private val chatTransport: OpenAiCompatibleTransport,
    private val responsesTransport: OpenAiResponsesTransport,
    private val anthropicTransport: AnthropicMessagesTransport,
    private val geminiTransport: GeminiNativeTransport,
    private val credentialStore: AiCredentialStore
) {
    fun create(profile: AiProviderProfileSettings): AiModelProvider? {
        val dialect = effectiveDialect(profile) ?: return null
        val gatewaySession = AiGatewayCatalog.rulesFor(profile.presetId) != null
        val apiKeyProvider = suspend {
            credentialStore.resolve(AiCredentialReferences.forProfile(profile.id))
        }

        return when (dialect) {
            AiApiDialect.OPENAI_CHAT_COMPLETIONS ->
                OpenAiCompatibleProvider(
                    transport = chatTransport,
                    apiKeyProvider = apiKeyProvider
                ).apply {
                    configure(
                        OpenAiCompatibleProviderConfig(
                            enabled = profile.enabled,
                            providerId = profile.id,
                            displayName = profile.displayName,
                            baseUrl = profile.baseUrl,
                            modelId = profile.modelId,
                            local = profile.local,
                            reasoningEffort = profile.reasoningEffort.takeIf {
                                profile.presetId == "openai"
                            },
                            gatewaySession = gatewaySession
                        )
                    )
                }

            AiApiDialect.OPENAI_RESPONSES ->
                OpenAiResponsesProvider(
                    transport = responsesTransport,
                    apiKeyProvider = apiKeyProvider
                ).apply {
                    configure(
                        OpenAiResponsesProviderConfig(
                            id = profile.id,
                            enabled = profile.enabled,
                            displayName = profile.displayName,
                            baseUrl = profile.baseUrl,
                            modelId = profile.modelId,
                            reasoningEffort = profile.reasoningEffort,
                            gatewaySession = gatewaySession
                        )
                    )
                }

            AiApiDialect.ANTHROPIC_MESSAGES ->
                AnthropicMessagesProvider(
                    transport = anthropicTransport,
                    apiKeyProvider = apiKeyProvider
                ).apply {
                    configure(
                        AnthropicMessagesProviderConfig(
                            id = profile.id,
                            enabled = profile.enabled,
                            displayName = profile.displayName,
                            baseUrl = profile.baseUrl,
                            modelId = profile.modelId,
                            local = profile.local,
                            reasoningEffort = profile.reasoningEffort,
                            gatewaySession = gatewaySession
                        )
                    )
                }

            AiApiDialect.GEMINI_GENERATE_CONTENT ->
                GeminiNativeProvider(
                    transport = geminiTransport,
                    apiKeyProvider = apiKeyProvider
                ).apply {
                    configure(
                        GeminiNativeProviderConfig(
                            id = profile.id,
                            enabled = profile.enabled,
                            displayName = profile.displayName,
                            baseUrl = profile.baseUrl,
                            modelId = profile.modelId,
                            gatewaySession = gatewaySession
                        )
                    )
                }
        }
    }

    fun effectiveDialect(profile: AiProviderProfileSettings): AiApiDialect? {
        if (profile.presetId == "gemini") {
            return AiApiDialect.GEMINI_GENERATE_CONTENT
        }

        val gatewayRules = AiGatewayCatalog.rulesFor(profile.presetId)
        if (gatewayRules != null && profile.modelId.isNotBlank()) {
            return AiGatewayDialectResolver.resolve(
                modelId = profile.modelId,
                rules = gatewayRules
            ).dialect
        }

        return runCatching {
            AiProviderProtocol.valueOf(profile.protocol).toDialect()
        }.getOrNull()
    }
}
