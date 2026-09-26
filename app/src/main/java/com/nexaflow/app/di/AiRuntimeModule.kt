package com.nexaflow.app.di

import com.nexaflow.core.airuntime.AiConversationEngine
import com.nexaflow.core.airuntime.AiProviderRegistry
import com.nexaflow.core.airuntime.AiToolExecutor
import com.nexaflow.core.airuntime.EmptyAiToolExecutor
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
    fun provideAiToolExecutor(): AiToolExecutor =
        EmptyAiToolExecutor

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
