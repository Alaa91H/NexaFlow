package com.nexaflow.data.agents

import com.nexaflow.core.agentruntime.AgentChoiceSource
import com.nexaflow.core.agentruntime.EnabledAgent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Read-only picker view over the saved agent definitions.
 *
 * Disabled agents are filtered out here as well as refused by
 * `ManagedAgentRunCoordinator`: the editor must not offer an agent whose run
 * would be rejected as `agent_unavailable`.
 */
@Singleton
class RepositoryAgentChoiceSource @Inject constructor(
    private val definitions: AgentDefinitionRepository
) : AgentChoiceSource {

    override fun observeEnabled(): Flow<List<EnabledAgent>> =
        definitions.observeAll().map { agents ->
            agents
                .asSequence()
                .filter { it.enabled }
                .map { agent ->
                    EnabledAgent(
                        id = agent.id,
                        name = agent.name,
                        description = agent.description,
                        modelId = agent.modelId
                    )
                }
                .toList()
        }
}
