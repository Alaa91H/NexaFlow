package com.nexaflow.feature.settings

import com.nexaflow.core.airuntime.AiApiDialect
import com.nexaflow.core.airuntime.AiGatewayCatalog
import com.nexaflow.core.airuntime.AiProviderAdapter
import com.nexaflow.core.airuntime.AiProviderDefinitionRegistry
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

internal class AiProviderDraftAdapterFactory(
    private val compatibleTransport: OpenAiCompatibleTransport,
    private val responsesTransport: OpenAiResponsesTransport,
    private val anthropicTransport: AnthropicMessagesTransport,
    private val geminiTransport: GeminiNativeTransport
) {
    fun create(
        profileId: String?,
        presetId: String?,
        protocol: AiProviderProtocol,
        displayName: String,
        baseUrl: String,
        modelId: String,
        local: Boolean,
        reasoningEffort: String?,
        apiKeyProvider: suspend () -> String?
    ): AiProviderAdapter? {
        val dialect = AiProviderDefinitionRegistry.resolveDialect(
            presetId = presetId,
            storedProtocol = protocol,
            modelId = modelId
        ) ?: return null
        val id = profileId ?: presetId ?: "custom-draft"
        val gatewaySession = AiGatewayCatalog.rulesFor(presetId) != null
        return when (dialect) {
            AiApiDialect.OPENAI_CHAT_COMPLETIONS ->
                OpenAiCompatibleProvider(
                    transport = compatibleTransport,
                    apiKeyProvider = apiKeyProvider
                ).apply {
                    configure(
                        OpenAiCompatibleProviderConfig(
                            enabled = true,
                            providerId = id,
                            displayName = displayName,
                            baseUrl = baseUrl,
                            modelId = modelId,
                            local = local,
                            reasoningEffort = reasoningEffort,
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
                            id = id,
                            enabled = true,
                            displayName = displayName,
                            baseUrl = baseUrl,
                            modelId = modelId,
                            reasoningEffort = reasoningEffort,
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
                            id = id,
                            enabled = true,
                            displayName = displayName,
                            baseUrl = baseUrl,
                            modelId = modelId,
                            local = local,
                            reasoningEffort = reasoningEffort,
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
                            id = id,
                            enabled = true,
                            displayName = displayName,
                            baseUrl = baseUrl,
                            modelId = modelId,
                            gatewaySession = gatewaySession
                        )
                    )
                }
        }
    }
}
