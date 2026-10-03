package com.nexaflow.app.di

import com.nexaflow.core.execution.canonical.CanonicalNodeHandler
import com.nexaflow.core.execution.canonical.CanonicalDelayHandler
import com.nexaflow.domain.canonical.CanonicalNodeExecutionContract
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Typed providers shown in builder only when contract and runtime are both registered. */
@dagger.Module
@InstallIn(SingletonComponent::class)
object CanonicalNodeBindingsModule {
    @dagger.Provides
    @dagger.multibindings.ElementsIntoSet
    fun canonicalNodeContracts(): Set<CanonicalNodeExecutionContract> =
        setOf(com.nexaflow.core.execution.canonical.CanonicalDelayDefinition.contract)

    @dagger.Provides
    @dagger.multibindings.ElementsIntoSet
    fun canonicalNodeHandlers(): Set<CanonicalNodeHandler> = setOf(CanonicalDelayHandler())
}
