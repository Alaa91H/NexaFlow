package com.nexaflow.app.di

import android.content.Context
import com.nexaflow.app.agent.NexaFlowAiToolExecutor
import com.nexaflow.app.ai.AndroidOpenAiCompatibleTransport
import com.nexaflow.app.ai.AiProfileAdapterFactory
import com.nexaflow.app.ai.AndroidOpenAiResponsesTransport
import com.nexaflow.app.ai.AndroidGeminiNativeTransport
import com.nexaflow.app.ai.AndroidAnthropicMessagesTransport
import com.nexaflow.app.ai.VaultBackedAiCredentialStore
import com.nexaflow.core.agentapi.AgentApiController
import com.nexaflow.core.airuntime.AiConversationEngine
import com.nexaflow.core.airuntime.AiCredentialReferences
import com.nexaflow.core.airuntime.AiCredentialStore
import com.nexaflow.core.airuntime.AiProviderRegistry
import com.nexaflow.core.airuntime.AiRoutingMode
import com.nexaflow.core.airuntime.AiRoutingPolicy
import com.nexaflow.core.airuntime.AiToolExecutor
import com.nexaflow.core.airuntime.OpenAiCompatibleProvider
import com.nexaflow.core.airuntime.OpenAiCompatibleProviderConfig
import com.nexaflow.core.airuntime.OpenAiCompatibleTransport
import com.nexaflow.core.airuntime.OpenAiResponsesTransport
import com.nexaflow.core.airuntime.GeminiNativeTransport
import com.nexaflow.core.airuntime.AiProviderProtocol
import com.nexaflow.core.airuntime.AnthropicMessagesProvider
import com.nexaflow.core.airuntime.AnthropicMessagesTransport
import com.nexaflow.core.airuntime.AnthropicMessagesProviderConfig
import com.nexaflow.core.datastore.AiProviderPreferences
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.security.SecretVault
import com.nexaflow.core.security.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Module
@InstallIn(SingletonComponent::class)
object AiRuntimeModule {

    @Provides
    @Singleton
    fun provideAiProviderPreferences(
        @ApplicationContext context: Context
    ): AiProviderPreferences = AiProviderPreferences(context)

    @Provides
    @Singleton
    fun provideAiCredentialStore(
        secretVault: SecretVault,
        secureStorage: SecureStorage
    ): AiCredentialStore = VaultBackedAiCredentialStore(secretVault, secureStorage)

    @Provides
    @Singleton
    fun provideOpenAiCompatibleTransport(): OpenAiCompatibleTransport =
        AndroidOpenAiCompatibleTransport()

    @Provides
    @Singleton
    fun provideAnthropicMessagesTransport(): AnthropicMessagesTransport =
        AndroidAnthropicMessagesTransport()

    @Provides
    @Singleton
    fun provideOpenAiResponsesTransport(): OpenAiResponsesTransport =
        AndroidOpenAiResponsesTransport()

    @Provides
    @Singleton
    fun provideGeminiNativeTransport(): GeminiNativeTransport =
        AndroidGeminiNativeTransport()

    @Provides
    @Singleton
    fun provideOpenAiCompatibleProvider(
        transport: OpenAiCompatibleTransport,
        credentialStore: AiCredentialStore
    ): OpenAiCompatibleProvider = OpenAiCompatibleProvider(
        transport = transport,
        apiKeyProvider = {
            credentialStore.resolve(AiCredentialReferences.legacySingleProvider)
        }
    )

    @Provides
    @Singleton
    fun provideAiProfileAdapterFactory(
        chatTransport: OpenAiCompatibleTransport,
        responsesTransport: OpenAiResponsesTransport,
        anthropicTransport: AnthropicMessagesTransport,
        geminiTransport: GeminiNativeTransport,
        credentialStore: AiCredentialStore
    ): AiProfileAdapterFactory = AiProfileAdapterFactory(
        chatTransport = chatTransport,
        responsesTransport = responsesTransport,
        anthropicTransport = anthropicTransport,
        geminiTransport = geminiTransport,
        credentialStore = credentialStore
    )

    @Provides
    @Singleton
    fun provideAiProviderRegistry(
        provider: OpenAiCompatibleProvider,
        credentialStore: AiCredentialStore,
        adapterFactory: AiProfileAdapterFactory,
        preferences: AiProviderPreferences,
        @ApplicationScope scope: CoroutineScope
    ): AiProviderRegistry {
        val registry = AiProviderRegistry(listOf(provider))
        scope.launch {
            runCatching {
                preferences.migrateLegacyProfileIfNeeded()
                preferences.currentProfiles()
                    .firstOrNull {
                        it.presetId == AiProviderPreferences.LEGACY_PROFILE_PRESET_ID
                    }
                    ?.let { legacy ->
                        val legacyReference = AiCredentialReferences.legacySingleProvider
                        val profileReference = AiCredentialReferences.forProfile(legacy.id)
                        val legacyKey = credentialStore.resolve(legacyReference)
                        val profileKey = credentialStore.resolve(profileReference)
                        if (profileKey.isNullOrBlank() && !legacyKey.isNullOrBlank()) {
                            credentialStore.store(profileReference, legacyKey)
                        }
                        if (!legacyKey.isNullOrBlank() &&
                            !credentialStore.resolve(profileReference).isNullOrBlank()
                        ) {
                            credentialStore.delete(legacyReference)
                        }
                    }
            }
            preferences.profiles.collect { profiles ->
                val adapters = profiles.mapNotNull(adapterFactory::create)
                registry.replaceProviders(adapters)
            }
        }
        scope.launch {
            preferences.settings.collect { settings ->
                provider.configure(
                    OpenAiCompatibleProviderConfig(
                        enabled = settings.enabled,
                        displayName = settings.displayName,
                        baseUrl = settings.baseUrl,
                        modelId = settings.modelId,
                        local = settings.local
                    )
                )
                registry.updateRoutingPolicy(
                    AiRoutingPolicy(
                        mode = runCatching {
                            AiRoutingMode.valueOf(settings.routingMode)
                        }.getOrDefault(AiRoutingMode.AUTOMATIC),
                        selectedProviderId = settings.selectedProviderId,
                        allowCloudFallback = settings.allowCloudFallback
                    )
                )
                registry.refreshDescriptors()
            }
        }
        return registry
    }


    @Provides
    @Singleton
    fun provideAiToolExecutor(
        controller: AgentApiController
    ): AiToolExecutor = NexaFlowAiToolExecutor(controller)

    @Provides
    @Singleton
    fun provideAiConversationEngine(
        registry: AiProviderRegistry,
        toolExecutor: AiToolExecutor
    ): AiConversationEngine = AiConversationEngine(
        registry = registry,
        toolExecutor = toolExecutor
    )
}
