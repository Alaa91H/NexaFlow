package com.nexaflow.core.engine.di

import com.nexaflow.core.engine.TriggerIndex
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    /**
     * The trigger index is a projection of the Room-backed automation flow:
     * every save/delete/enable toggle re-emits and rebuilds the index, so
     * monitors can reach subscribers in O(1) instead of rescanning the whole
     * table on each event.
     */
    @Provides
    @Singleton
    fun provideTriggerIndex(
        repository: AutomationRepository,
    ): TriggerIndex = TriggerIndex(repository.getAutomations())
}
