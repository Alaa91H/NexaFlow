package com.nexaflow.app.di

import com.nexaflow.app.agent.NexaFlowAiToolExecutor
import com.nexaflow.core.agentapi.AgentApiController
import com.nexaflow.core.airuntime.AiConversationEngine
import com.nexaflow.core.airuntime.AiProviderRegistry
import com.nexaflow.core.airuntime.AiToolExecutor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiRuntimeModule {

    @Provides
    @Singleton
    fun provideAiProviderRegistry(): AiProviderRegistry =
        AiProviderRegistry()

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
