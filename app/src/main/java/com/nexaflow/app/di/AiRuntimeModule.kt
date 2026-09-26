package com.nexaflow.app.di

import android.content.Context
import com.nexaflow.app.agent.NexaFlowAiToolExecutor
import com.nexaflow.app.ai.AndroidOpenAiCompatibleTransport
import com.nexaflow.core.agentapi.AgentApiController
import com.nexaflow.core.airuntime.AiConversationEngine
import com.nexaflow.core.airuntime.AiProviderRegistry
import com.nexaflow.core.airuntime.AiToolExecutor
import com.nexaflow.core.airuntime.OpenAiCompatibleProvider
import com.nexaflow.core.airuntime.OpenAiCompatibleProviderConfig
import com.nexaflow.core.airuntime.OpenAiCompatibleTransport
import com.nexaflow.core.datastore.AiProviderPreferences
import com.nexaflow.core.engine.di.ApplicationScope
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
    fun provideOpenAiCompatibleTransport(): OpenAiCompatibleTransport =
        AndroidOpenAiCompatibleTransport()

    @Provides
    @Singleton
    fun provideOpenAiCompatibleProvider(
        transport: OpenAiCompatibleTransport,
        secureStorage: SecureStorage
    ): OpenAiCompatibleProvider = OpenAiCompatibleProvider(
        transport = transport,
        apiKeyProvider = {
            secureStorage.get(OpenAiCompatibleProvider.API_KEY_STORAGE_KEY)
        }
    )

    @Provides
    @Singleton
    fun provideAiProviderRegistry(
        provider: OpenAiCompatibleProvider,
        preferences: AiProviderPreferences,
        @ApplicationScope scope: CoroutineScope
    ): AiProviderRegistry {
        val registry = AiProviderRegistry(listOf(provider))
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
