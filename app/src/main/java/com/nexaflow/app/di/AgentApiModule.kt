package com.nexaflow.app.di

import android.content.Context
import com.nexaflow.app.agent.AndroidAgentApiRuntime
import com.nexaflow.core.agentapi.AgentApiController
import com.nexaflow.core.agentapi.AgentApiHostPolicy
import com.nexaflow.core.agentapi.AgentApiRuntime
import com.nexaflow.core.agentapi.AgentApiServer
import com.nexaflow.core.agentapi.AgentMcpController
import com.nexaflow.core.agentapi.AgentMcpRestToolExecutor
import com.nexaflow.core.agentapi.AgentMcpToolExecutor
import com.nexaflow.core.agentsecurity.AgentAccessManager
import com.nexaflow.core.agentsecurity.AgentRequestAuthorizer
import com.nexaflow.core.automationcontrol.AutomationCommandService
import com.nexaflow.core.automationcontrol.schedule.AgentSchedulePreviewService
import com.nexaflow.core.automationcontrol.schema.AutomationSchemaRegistry
import com.nexaflow.core.automationcontrol.simulation.AgentSimulationService
import com.nexaflow.core.datastore.AgentNetworkPreferences
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object AgentApiModule {

    @Provides
    @Singleton
    fun provideAgentNetworkPreferences(
        @ApplicationContext context: Context
    ): AgentNetworkPreferences = AgentNetworkPreferences(context)

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
    fun provideAgentApiHostPolicy(): AgentApiHostPolicy =
        AgentApiHostPolicy()

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
        runtime: AgentApiRuntime,
        hostPolicy: AgentApiHostPolicy
    ): AgentApiController = AgentApiController(
        repository = repository,
        commandService = commandService,
        simulationService = simulationService,
        schedulePreviewService = schedulePreviewService,
        schemaRegistry = schemaRegistry,
        accessManager = accessManager,
        authorizer = authorizer,
        runtime = runtime,
        hostPolicy = hostPolicy
    )

    @Provides
    @Singleton
    fun provideAgentMcpToolExecutor(
        controller: AgentApiController
    ): AgentMcpToolExecutor = AgentMcpRestToolExecutor(controller)

    @Provides
    @Singleton
    fun provideAgentMcpController(
        accessManager: AgentAccessManager,
        authorizer: AgentRequestAuthorizer,
        toolExecutor: AgentMcpToolExecutor,
        hostPolicy: AgentApiHostPolicy
    ): AgentMcpController = AgentMcpController(
        accessManager = accessManager,
        authorizer = authorizer,
        toolExecutor = toolExecutor,
        hostPolicy = hostPolicy
    )

    @Provides
    @Singleton
    fun provideAgentApiServer(
        controller: AgentApiController,
        mcpController: AgentMcpController,
        hostPolicy: AgentApiHostPolicy,
        @ApplicationScope scope: CoroutineScope
    ): AgentApiServer = AgentApiServer(
        controller = controller,
        mcpController = mcpController,
        hostPolicy = hostPolicy,
        scope = scope
    )
}
