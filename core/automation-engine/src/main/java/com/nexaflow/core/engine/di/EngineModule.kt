package com.nexaflow.core.engine.di

import android.content.Context
import com.nexaflow.core.engine.PluginEventIngress
import com.nexaflow.core.engine.PluginEventRouter
import com.nexaflow.core.engine.PluginEventSource
import com.nexaflow.core.engine.CanonicalBatteryTriggerDefinition
import com.nexaflow.core.engine.TriggerIndex
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.canonical.CanonicalTriggerDispatcher
import com.nexaflow.core.execution.canonical.CanonicalTriggerHandlerRegistry
import com.nexaflow.core.execution.canonical.CanonicalTriggerSourceHandler
import com.nexaflow.core.execution.capability.CapabilityStateStore
import com.nexaflow.domain.canonical.CanonicalTriggerEvaluator
import com.nexaflow.domain.canonical.CanonicalTriggerSourceContract
import com.nexaflow.domain.canonical.CanonicalTriggerSourceRegistry
import com.nexaflow.core.execution.events.InMemoryNexaFlowEventBus
import com.nexaflow.domain.events.NexaFlowEventBus
import com.nexaflow.domain.repositories.AutomationRepository
import kotlinx.coroutines.CoroutineScope
import dagger.Module
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import dagger.multibindings.ElementsIntoSet

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @ElementsIntoSet
    fun provideCanonicalBatteryTriggerContract(): Set<CanonicalTriggerSourceContract> =
        setOf(CanonicalBatteryTriggerDefinition.contract)

    @Provides
    @ElementsIntoSet
    fun provideCanonicalBatteryTriggerHandler(
        @ApplicationContext context: Context,
    ): Set<CanonicalTriggerSourceHandler> = setOf(
        CanonicalBatteryTriggerDefinition.handler { node ->
            CanonicalBatteryTriggerDefinition.readLiveState(context, node)
        }
    )

    @Provides
    @Singleton
    fun provideCanonicalTriggerDispatcher(
        contracts: Set<@JvmSuppressWildcards CanonicalTriggerSourceContract>,
        handlers: Set<@JvmSuppressWildcards CanonicalTriggerSourceHandler>,
        capabilityStateStore: CapabilityStateStore,
    ): CanonicalTriggerDispatcher {
        val registry = CanonicalTriggerSourceRegistry(contracts.toList())
        return CanonicalTriggerDispatcher(
            evaluator = CanonicalTriggerEvaluator(registry),
            handlers = CanonicalTriggerHandlerRegistry(handlers.toList()),
            sources = registry,
            capabilitySnapshot = { capabilityStateStore.snapshot.value },
        )
    }

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

    /**
     * One in-process bus shared by adapters over existing monitors. Its scope is
     * application-owned; it registers no Android receiver and creates no new
     * runtime service.
     */
    @Provides
    @Singleton
    fun provideEventBus(
        @ApplicationScope scope: CoroutineScope,
    ): NexaFlowEventBus = InMemoryNexaFlowEventBus(scope)

    @Provides
    @Singleton
    fun providePluginEventIngress(
        triggerIndex: TriggerIndex,
        eventBus: NexaFlowEventBus
    ): PluginEventIngress = PluginEventIngress(triggerIndex, eventBus)

    @Provides
    @Singleton
    fun providePluginEventSource(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        ingress: PluginEventIngress
    ): PluginEventSource = PluginEventSource(context, scope, ingress)

    @Provides
    @Singleton
    fun providePluginEventRouter(
        @ApplicationScope scope: CoroutineScope,
        eventBus: NexaFlowEventBus,
        triggerIndex: TriggerIndex,
        executionEngine: ExecutionEngine
    ): PluginEventRouter = PluginEventRouter(scope, eventBus, triggerIndex, executionEngine)
}
