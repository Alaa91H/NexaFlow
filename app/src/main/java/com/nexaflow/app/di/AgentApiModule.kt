package com.nexaflow.app.di

import com.nexaflow.app.agent.AndroidAgentApiRuntime
import com.nexaflow.core.agentapi.AgentApiController
import com.nexaflow.core.agentapi.AgentApiRuntime
import com.nexaflow.core.agentapi.AgentApiServer
import com.nexaflow.core.agentsecurity.AgentAccessManager
import com.nexaflow.core.agentsecurity.AgentRequestAuthorizer
import com.nexaflow.core.automationcontrol.AutomationCommandService
import com.nexaflow.core.automationcontrol.schedule.AgentSchedulePreviewService
import com.nexaflow.core.automationcontrol.schema.AutomationSchemaRegistry
import com.nexaflow.core.automationcontrol.simulation.AgentSimulationService
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object AgentApiModule {

    @Provides
    @Singleton
    fun provideAgentApiRuntime(
        implementation: AndroidAgentApiRuntime
    ): AgentApiRuntime = implementation

    @Provides
    @Singleton
    fun provideAutomationSchemaRegistry(): AutomationSchemaRegistry =
        AutomationSchemaRegistry()

    @Provides
    @Singleton
    fun provideAgentApiController(
        repository: AutomationRepository,
        commandService: AutomationCommandService,
        simulationService: AgentSimulationService,
        schedulePreviewService: AgentSchedulePreviewService,
        schemaRegistry: AutomationSchemaRegistry,
        accessManager: AgentAccessManager,
        authorizer: AgentRequestAuthorizer,
        runtime: AgentApiRuntime
    ): AgentApiController = AgentApiController(
        repository = repository,
        commandService = commandService,
        simulationService = simulationService,
        schedulePreviewService = schedulePreviewService,
        schemaRegistry = schemaRegistry,
        accessManager = accessManager,
        authorizer = authorizer,
        runtime = runtime
    )

    @Provides
    @Singleton
    fun provideAgentApiServer(
        controller: AgentApiController,
        @ApplicationScope scope: CoroutineScope
    ): AgentApiServer = AgentApiServer(
        controller = controller,
        scope = scope
    )
}
